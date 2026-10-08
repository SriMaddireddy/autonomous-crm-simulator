package io.srimaddireddy.crm;
import static io.srimaddireddy.crm.Domain.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@Repository
public class Workflows {
 private final JdbcTemplate db;private final ObjectMapper json;private final TransactionTemplate tx;private final Campaigns campaigns;
 private final long leaseMs;private final int maxAttempts;
 public Workflows(JdbcTemplate db,ObjectMapper json,TransactionTemplate tx,Campaigns campaigns,
  @Value("${crm.lease-seconds}") long leaseSeconds,@Value("${crm.max-attempts}") int maxAttempts){
  if(leaseSeconds<20)throw new IllegalArgumentException("Lease must be at least 20 seconds");
  this.db=db;this.json=json;this.tx=tx;this.campaigns=campaigns;this.leaseMs=leaseSeconds*1000;this.maxAttempts=maxAttempts;
 }
 public Workflow submit(String campaignId,String key){
  campaigns.get(campaignId);
  var existing=byKey(key);if(existing.isPresent())return checkCampaign(existing.get(),campaignId);
  String id=UUID.randomUUID().toString();long now=System.currentTimeMillis();
  try {
   tx.executeWithoutResult(s->{
    db.update("INSERT INTO workflows(id,campaign_id,idempotency_key,status,created_at,updated_at) VALUES (?,?,?,'QUEUED',?,?)",id,campaignId,key,now,now);
    event(id,"SUBMITTED",0,"Durably queued",now);
   });
  }catch(DuplicateKeyException e){return checkCampaign(byKey(key).orElseThrow(),campaignId);}
  return get(id);
 }
 private Workflow checkCampaign(Workflow w,String campaignId){
  if(!w.campaignId().equals(campaignId))throw new ResponseStatusException(HttpStatus.CONFLICT,"Idempotency key already belongs to another campaign");return w;
 }
 private Optional<Workflow> byKey(String key){return db.query("SELECT * FROM workflows WHERE idempotency_key=?",this::map,key).stream().findFirst();}
 public Workflow get(String id){return db.query("SELECT * FROM workflows WHERE id=?",this::map,id).stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Workflow not found"));}
 public List<Workflow> list(int limit){return db.query("SELECT * FROM workflows ORDER BY created_at DESC,id LIMIT ?",this::map,limit);}
 private Workflow map(java.sql.ResultSet r,int row) throws java.sql.SQLException {
  Result result=null;String raw=r.getString("result");
  if(raw!=null)try{result=json.readValue(raw,Result.class);}catch(Exception e){throw new IllegalStateException(e);}
  long lease=r.getLong("lease_until");Long leaseUntil=r.wasNull()?null:lease;
  return new Workflow(r.getString("id"),r.getString("campaign_id"),r.getString("idempotency_key"),r.getString("status"),r.getInt("attempts"),r.getString("owner"),leaseUntil,r.getLong("created_at"),r.getLong("updated_at"),r.getString("error"),result);
 }
 public List<String> candidates(int limit){long now=System.currentTimeMillis();return db.query(
  "SELECT id FROM workflows WHERE (status='QUEUED' OR (status='RUNNING' AND lease_until<?)) AND attempts<? ORDER BY created_at,id LIMIT ?",
  (r,n)->r.getString(1),now,maxAttempts,limit);}
 public Optional<Claim> claim(String id,String owner){
  return tx.execute(s->{
   long now=System.currentTimeMillis();
   int updated=db.update("UPDATE workflows SET status='RUNNING',owner=?,attempts=attempts+1,lease_until=?,updated_at=?,error=NULL WHERE id=? AND attempts<? AND (status='QUEUED' OR (status='RUNNING' AND lease_until<?))",owner,now+leaseMs,now,id,maxAttempts,now);
   if(updated==0)return Optional.empty();
   Workflow w=get(id);event(id,"CLAIMED",w.attempts(),"Owner "+owner,now);
   return Optional.of(new Claim(id,w.campaignId(),owner,w.attempts()));
  });
 }
 public boolean renew(Claim c){long now=System.currentTimeMillis();return db.update("UPDATE workflows SET lease_until=?,updated_at=? WHERE id=? AND owner=? AND attempts=? AND status='RUNNING' AND lease_until>?",now+leaseMs,now,c.id(),c.owner(),c.attempt(),now)==1;}
 public boolean complete(Claim c,Result result){
  return Boolean.TRUE.equals(tx.execute(s->{
   long now=System.currentTimeMillis();String raw=encode(result);
   int updated=db.update("UPDATE workflows SET status='SUCCEEDED',result=?,owner=NULL,lease_until=NULL,updated_at=? WHERE id=? AND owner=? AND attempts=? AND status='RUNNING' AND lease_until>?",raw,now,c.id(),c.owner(),c.attempt(),now);
   if(updated==0)return false;
   for(Decision d:result.decisions())db.update("INSERT INTO decisions VALUES (?,?,?,?,?,?)",c.id(),d.skill(),d.action(),d.reason(),encode(d.evidenceIds()),now);
   event(c.id(),"SUCCEEDED",c.attempt(),"All five decisions and result committed atomically",now);
   return true;
  }));
 }
 public void fail(Claim c,Exception error){
  tx.executeWithoutResult(s->{
   long now=System.currentTimeMillis();String status=c.attempt()>=maxAttempts?"FAILED":"QUEUED";
   String detail=error.getClass().getSimpleName();
   int count=db.update("UPDATE workflows SET status=?,owner=NULL,lease_until=NULL,error=?,updated_at=? WHERE id=? AND owner=? AND attempts=? AND status='RUNNING' AND lease_until>?",status,detail,now,c.id(),c.owner(),c.attempt(),now);
   if(count==1)event(c.id(),status,c.attempt(),detail,now);
  });
 }
 public void expireExhausted(){
  for(String id:db.query("SELECT id FROM workflows WHERE status='RUNNING' AND lease_until<? AND attempts>=?",(r,n)->r.getString(1),System.currentTimeMillis(),maxAttempts)){
   tx.executeWithoutResult(s->{long now=System.currentTimeMillis();
    int count=db.update("UPDATE workflows SET status='FAILED',owner=NULL,lease_until=NULL,error='Lease expired after final attempt',updated_at=? WHERE id=? AND status='RUNNING' AND lease_until<? AND attempts>=?",now,id,now,maxAttempts);
    if(count==1)event(id,"FAILED",maxAttempts,"Lease expired after final attempt",now);
   });
  }
 }
 public List<Map<String,Object>> events(String id){get(id);return db.queryForList("SELECT event_type,attempt,detail,created_at FROM workflow_events WHERE workflow_id=? ORDER BY created_at,id",id);}
 public Map<String,Object> stats(){
  Map<String,Object> counts=new LinkedHashMap<>();for(String state:List.of("QUEUED","RUNNING","SUCCEEDED","FAILED"))counts.put(state,db.queryForObject("SELECT COUNT(*) FROM workflows WHERE status=?",Long.class,state));
  counts.put("decisions",db.queryForObject("SELECT COUNT(*) FROM decisions",Long.class));return counts;
 }
 private void event(String id,String type,int attempt,String detail,long now){db.update("INSERT INTO workflow_events(workflow_id,event_type,attempt,detail,created_at) VALUES (?,?,?,?,?)",id,type,attempt,detail,now);}
 private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
}
