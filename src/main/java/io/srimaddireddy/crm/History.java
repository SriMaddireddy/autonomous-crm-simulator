package io.srimaddireddy.crm;
import static io.srimaddireddy.crm.Domain.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class History {
 private final JdbcTemplate db; private final ObjectMapper json;
 private volatile List<Log> memory=List.of(); private volatile boolean postgres;
 private final AtomicLong queries=new AtomicLong(); private final AtomicLong nanos=new AtomicLong();
 private record Log(long id,String campaign,String text,double[] vector) {}
 public History(JdbcTemplate db,ObjectMapper json) {this.db=db;this.json=json;}
 public static double[] features(Campaign c) {
  double[] v={c.channel().equals("SEARCH")?1:0,c.channel().equals("SOCIAL")?1:0,c.channel().equals("DISPLAY")?1:0,
   Math.min(c.spend()/c.budget(),2),Math.min(c.ctr()*100,5)/5,Math.min(c.roas(),5)/5,
   c.clicks()==0?0:(double)c.conversions()/c.clicks(),c.risk(),
   Math.min(c.impressions()/100000.0,1),Math.min(c.cpa()/100,1),1,
   Math.min(c.budget()/2000,1),Math.min(c.spend()/2000,1),Math.min(c.conversions()/1000.0,1),
   c.channel().equals("SEARCH")?Math.min(c.ctr()*100,5)/5:0,
   c.channel().equals("SOCIAL")?Math.min(c.ctr()*100,5)/5:0};
  double norm=Math.sqrt(Arrays.stream(v).map(x->x*x).sum());
  return Arrays.stream(v).map(x->x/norm).toArray();
 }
 public void initialize() {
  postgres=db.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>)c->c.getMetaData().getDatabaseProductName().equals("PostgreSQL"));
  if(postgres) {
   db.execute("CREATE EXTENSION IF NOT EXISTS vector");
   db.execute("ALTER TABLE historical_logs ADD COLUMN IF NOT EXISTS embedding vector(16)");
   var rows=db.query("SELECT id,features FROM historical_logs WHERE embedding IS NULL",(r,n)->new Object[]{r.getLong(1),r.getString(2)});
   db.batchUpdate("UPDATE historical_logs SET embedding=CAST(? AS vector) WHERE id=?",rows,500,(ps,row)->{ps.setString(1,(String)row[1]);ps.setLong(2,(Long)row[0]);});
   db.execute("CREATE INDEX IF NOT EXISTS historical_embedding_hnsw ON historical_logs USING hnsw (embedding vector_cosine_ops)");
   db.execute("ANALYZE historical_logs");
  } else {
   memory=db.query("SELECT * FROM historical_logs",(r,n)->{
    try {return new Log(r.getLong("id"),r.getString("campaign_id"),r.getString("content"),json.readValue(r.getString("features"),double[].class));}
    catch(Exception e){throw new IllegalStateException(e);}
   });
  }
 }
 public Retrieval retrieve(Campaign c,int k) {
  long start=System.nanoTime(); double[] v=features(c); List<Evidence> logs;
  if(postgres) {
   String vector=Arrays.toString(v);
   logs=db.query("SELECT id,campaign_id,content,1-(embedding <=> CAST(? AS vector)) AS similarity FROM historical_logs ORDER BY embedding <=> CAST(? AS vector) LIMIT ?",
    (r,n)->new Evidence(r.getLong(1),r.getString(2),r.getString(3),r.getDouble(4)),vector,vector,k);
  } else {
   // Bounded heap avoids sorting every record and allocating 10,000 Evidence objects per request.
   PriorityQueue<Evidence> heap=new PriorityQueue<>(Comparator.comparingDouble(Evidence::similarity).thenComparingLong(Evidence::id));
   for(Log log:memory) {
    double score=0;for(int i=0;i<v.length;i++)score+=v[i]*log.vector()[i];
    if(heap.size()<k || score>heap.peek().similarity()) {
     if(heap.size()==k)heap.poll();heap.add(new Evidence(log.id(),log.campaign(),log.text(),score));
    }
   }
   logs=heap.stream().sorted(Comparator.comparingDouble(Evidence::similarity).reversed().thenComparingLong(Evidence::id)).toList();
  }
  long elapsed=System.nanoTime()-start;queries.incrementAndGet();nanos.addAndGet(elapsed);
  return new Retrieval(logs,elapsed/1_000_000.0,postgres?"postgres-pgvector-hnsw":"demo-exact-cosine");
 }
 public Map<String,Object> stats(){long count=queries.get();return Map.of("queries",count,"meanLatencyMs",count==0?0:nanos.get()/1_000_000.0/count,"mode",postgres?"postgres-pgvector-hnsw":"demo-exact-cosine","featureDimensions",16);}
}
