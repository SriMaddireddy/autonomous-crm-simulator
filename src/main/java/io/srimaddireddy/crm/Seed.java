package io.srimaddireddy.crm;
import static io.srimaddireddy.crm.Domain.*;
import java.util.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
@Component
public class Seed implements ApplicationRunner {
 private final JdbcTemplate db;private final History history;private final Campaigns campaigns;
 @Value("${crm.seed-history}") private int logCount;
 @Value("${crm.seed-campaigns}") private int campaignCount;
 public Seed(JdbcTemplate db,History history,Campaigns campaigns){this.db=db;this.history=history;this.campaigns=campaigns;}
 @Override @org.springframework.transaction.annotation.Transactional public void run(ApplicationArguments args) {
  Random random=new Random(42);
  for(int i=1;i<=campaignCount;i++){
   String id=String.format(Locale.ROOT,"campaign-%04d",i);
   Campaign c=sample(id,random);
   if(db.queryForObject("SELECT COUNT(*) FROM campaigns WHERE id=?",Integer.class,id)==0)campaigns.create(c);
  }
  if(db.queryForObject("SELECT COUNT(*) FROM historical_logs",Integer.class)==0){
   List<Object[]> rows=new ArrayList<>();
   for(int i=1;i<=logCount;i++){
    Campaign c=sample("historical-"+i,random);
    String outcome=c.risk()>=.8||c.spend()>=c.budget()?"PAUSE":c.roas()<1?"REDUCE":c.roas()>=2?"SCALE":"HOLD";
    String text=String.format(Locale.ROOT,"Synthetic day %d; %s campaign; spend %.2f of %.2f; CTR %.3f; ROAS %.2f; risk %.2f; rule outcome %s.",i,c.channel(),c.spend(),c.budget(),c.ctr(),c.roas(),c.risk(),outcome);
    rows.add(new Object[]{(long)i,c.id(),text,Arrays.toString(History.features(c))});
   }
   db.batchUpdate("INSERT INTO historical_logs(id,campaign_id,content,features) VALUES (?,?,?,?)",rows,500,
    (ps,r)->{ps.setLong(1,(Long)r[0]);ps.setString(2,(String)r[1]);ps.setString(3,(String)r[2]);ps.setString(4,(String)r[3]);});
  }
  history.initialize();
 }
 static Campaign sample(String id,Random r){
  double budget=100+r.nextDouble()*1900,spend=budget*(.1+r.nextDouble()*1.05);
  long impressions=500+r.nextInt(99500),clicks=(long)(impressions*(.001+r.nextDouble()*.04)),conversions=(long)(clicks*r.nextDouble()*.18);
  return new Campaign(id,"Campaign "+id,List.of("SEARCH","SOCIAL","DISPLAY").get(r.nextInt(3)),budget,spend,impressions,clicks,conversions,spend*r.nextDouble()*4,r.nextDouble());
 }
}
