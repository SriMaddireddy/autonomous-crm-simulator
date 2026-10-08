package io.srimaddireddy.crm;
import static io.srimaddireddy.crm.Domain.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:crmtest;DB_CLOSE_DELAY=-1","crm.seed-history=100","crm.seed-campaigns=5","crm.workers-enabled=false","crm.llm-api-key="})
@AutoConfigureMockMvc
class WorkflowIntegrationTest {
 @Autowired Workflows workflows;@Autowired Orchestrator orchestrator;@Autowired Campaigns campaigns;
 @Autowired JdbcTemplate db;@Autowired MockMvc http;@Autowired History history;
 @BeforeEach void clear(){db.update("DELETE FROM decisions");db.update("DELETE FROM workflow_events");db.update("DELETE FROM workflows");}
 private Workflow submit(){return workflows.submit("campaign-0001",UUID.randomUUID().toString());}
 private Claim claim(Workflow w){return workflows.claim(w.id(),"test-owner").orElseThrow();}
 @Test void idempotentSubmissionAndKeyConflict(){
  var w=workflows.submit("campaign-0001","same-key");assertEquals(w.id(),workflows.submit("campaign-0001","same-key").id());
  assertThrows(org.springframework.web.server.ResponseStatusException.class,()->workflows.submit("campaign-0002","same-key"));
  assertEquals(1L,db.queryForObject("SELECT COUNT(*) FROM workflows",Long.class));
 }
 @Test void concurrentSubmissionHasOneDurableIdentity() throws Exception {
  try(var pool=Executors.newFixedThreadPool(10)){
   List<Callable<String>> tasks=new ArrayList<>();for(int i=0;i<30;i++)tasks.add(()->workflows.submit("campaign-0001","race-key").id());
   Set<String> ids=new HashSet<>();for(var f:pool.invokeAll(tasks))ids.add(f.get());assertEquals(1,ids.size());
  }
  assertEquals(1L,db.queryForObject("SELECT COUNT(*) FROM workflow_events",Long.class));
 }
 @Test void onlyOneOwnerCanClaim() throws Exception {
  var w=submit();try(var pool=Executors.newFixedThreadPool(10)){
   List<Callable<Boolean>> tasks=new ArrayList<>();for(int i=0;i<20;i++){String owner="owner-"+i;tasks.add(()->workflows.claim(w.id(),owner).isPresent());}
   long successes=0;for(var f:pool.invokeAll(tasks))if(f.get())successes++;assertEquals(1,successes);
  }
 }
 @Test void commitsAllDecisionsWithResultAtomically(){
  var w=submit();var c=claim(w);var result=orchestrator.evaluate(campaigns.get(c.campaignId()));
  assertTrue(workflows.complete(c,result));assertFalse(workflows.complete(c,result));
  assertEquals("SUCCEEDED",workflows.get(w.id()).status());assertEquals(5L,db.queryForObject("SELECT COUNT(*) FROM decisions",Long.class));
  assertEquals(3,workflows.events(w.id()).size());assertNotNull(workflows.get(w.id()).result());
 }
 @Test void failedDecisionInsertRollsBackResultAndEveryDecision(){
  var w=submit();var c=claim(w);var good=orchestrator.evaluate(campaigns.get(c.campaignId()));
  var duplicate=new Result(good.action(),good.explanation(),good.explanationMode(),List.of(good.decisions().get(0),good.decisions().get(0)),good.retrieval());
  assertThrows(org.springframework.dao.DuplicateKeyException.class,()->workflows.complete(c,duplicate));
  assertEquals("RUNNING",workflows.get(w.id()).status());assertNull(workflows.get(w.id()).result());
  assertEquals(0L,db.queryForObject("SELECT COUNT(*) FROM decisions",Long.class));
 }
 @Test void expiredLeaseIsRecoveredAndStaleWorkerIsFenced(){
  var w=submit();var old=claim(w);expire(w.id());assertFalse(workflows.renew(old));
  var next=workflows.claim(w.id(),"new-owner").orElseThrow();assertEquals(2,next.attempt());
  var result=orchestrator.evaluate(campaigns.get(next.campaignId()));
  assertFalse(workflows.complete(old,result));workflows.fail(old,new RuntimeException("stale"));
  assertEquals("new-owner",workflows.get(w.id()).owner());assertTrue(workflows.complete(next,result));
  assertEquals(5L,db.queryForObject("SELECT COUNT(*) FROM decisions",Long.class));
 }
 @Test void retriesAreBoundedAndFinalCrashBecomesFailed(){
  var w=submit();for(int i=1;i<=3;i++){
   var c=claim(w);assertEquals(i,c.attempt());
   if(i<3)workflows.fail(c,new IllegalStateException("simulated"));else expire(w.id());
  }
  workflows.expireExhausted();assertEquals("FAILED",workflows.get(w.id()).status());
  assertTrue(workflows.claim(w.id(),"fourth").isEmpty());
 }
 @Test void leaseCanBeRenewedBeforeExpiration(){var c=claim(submit());assertTrue(workflows.renew(c));}
 @Test void retrievalReturnsRankedTraceableEvidence(){
  var result=history.retrieve(campaigns.get("campaign-0001"),5);assertEquals(5,result.logs().size());
  for(int i=1;i<5;i++)assertTrue(result.logs().get(i-1).similarity()>=result.logs().get(i).similarity());
  assertTrue(result.logs().stream().allMatch(x->x.id()>0&&x.id()<=100));assertEquals("demo-exact-cosine",result.mode());
 }
 @Test void restRejectsInvalidMetricsAndMissingCampaign() throws Exception {
  http.perform(post("/api/campaigns").contentType(MediaType.APPLICATION_JSON).content("""
   {"id":"bad","name":"Bad","channel":"SEARCH","budget":100,"spend":10,"impressions":1,"clicks":2,"conversions":0,"revenue":0,"risk":0}
   """)).andExpect(status().isBadRequest());
  http.perform(post("/api/workflows").contentType(MediaType.APPLICATION_JSON).content("""
{"campaignId":"absent","idempotencyKey":"a"}
""")).andExpect(status().isNotFound());
  http.perform(post("/api/workflows/batch").contentType(MediaType.APPLICATION_JSON).content("""
{"count":501,"runKey":"a"}
""")).andExpect(status().isBadRequest());
 }
 @Test void invalidQueryLimitIsClientError() throws Exception {http.perform(get("/api/campaigns?limit=0")).andExpect(status().isBadRequest());http.perform(get("/api/workflows?limit=501")).andExpect(status().isBadRequest());}
 @Test void mcpInitializeToolsCallNotificationAndErrors() throws Exception {
  http.perform(post("/mcp").contentType(MediaType.APPLICATION_JSON).content("""
{"jsonrpc":"2.0","id":1,"method":"initialize"}
"""))
   .andExpect(status().isOk()).andExpect(jsonPath("$.result.protocolVersion").value("2025-03-26"));
  http.perform(post("/mcp").contentType(MediaType.APPLICATION_JSON).content("""
{"jsonrpc":"2.0","id":2,"method":"tools/list"}
"""))
   .andExpect(jsonPath("$.result.tools.length()").value(7));
  http.perform(post("/mcp").contentType(MediaType.APPLICATION_JSON).content("""
{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"risk","arguments":{"campaignId":"campaign-0001"}}}
"""))
   .andExpect(jsonPath("$.result.isError").value(false));
  http.perform(post("/mcp").contentType(MediaType.APPLICATION_JSON).content("""
{"jsonrpc":"2.0","method":"notifications/initialized"}
"""))
   .andExpect(status().isAccepted());
  http.perform(post("/mcp").contentType(MediaType.APPLICATION_JSON).content("""
{"jsonrpc":"2.0","id":4,"method":"missing"}
"""))
   .andExpect(jsonPath("$.error.code").value(-32601));
 }
 private void expire(String id){db.update("UPDATE workflows SET lease_until=? WHERE id=?",System.currentTimeMillis()-1,id);}
}
