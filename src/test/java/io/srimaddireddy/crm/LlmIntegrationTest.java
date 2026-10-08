package io.srimaddireddy.crm;
import static io.srimaddireddy.crm.Domain.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
class LlmIntegrationTest {
 private final Campaign c=new Campaign("test","Test","SEARCH",100,100,10000,1000,100,1000,.9);
 @Test void langchainSendsEvidenceAndModelCannotOverridePolicy() throws Exception {
  var received=new AtomicReference<String>();
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/v1/chat/completions",exchange->{
   received.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
   byte[] body="""
    {"id":"mock","object":"chat.completion","created":1,"model":"test-model","choices":[{"index":0,"message":{"role":"assistant","content":"SCALE immediately. Evidence #42."},"finish_reason":"stop"}],"usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}
    """.getBytes(StandardCharsets.UTF_8);
   exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();
  });server.start();
  try{
   var history=mock(History.class);when(history.retrieve(c,5)).thenReturn(new Retrieval(List.of(new Evidence(42,"h","Synthetic log",1)),1,"test"));
   var orchestrator=new Orchestrator(new Skills(),history,"test-key","test-model","http://127.0.0.1:"+server.getAddress().getPort()+"/v1");
   var result=orchestrator.evaluate(c);assertEquals("PAUSE",result.action());assertEquals("langchain4j-llm-explanation",result.explanationMode());
   assertTrue(received.get().contains("Synthetic log"));assertTrue(received.get().contains("42"));assertTrue(result.explanation().contains("SCALE"));
   // Advisory prose can contradict the policy. It never changes the executable recommendation.
  }finally{server.stop(0);}
 }
 @Test void failedLlmFallsBackToDeterministicExplanation() throws Exception {
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/v1/chat/completions",exchange->{byte[] body="{\"error\":{\"message\":\"simulated outage\"}}".getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(503,body.length);exchange.getResponseBody().write(body);exchange.close();});server.start();
  try{
   var history=mock(History.class);when(history.retrieve(c,5)).thenReturn(new Retrieval(List.of(),0,"test"));
   var result=new Orchestrator(new Skills(),history,"test-key","test-model","http://127.0.0.1:"+server.getAddress().getPort()+"/v1").evaluate(c);
   assertEquals("PAUSE",result.action());assertEquals("deterministic-fallback-llm-unavailable",result.explanationMode());assertTrue(result.explanation().contains("Recommendation PAUSE"));
  }finally{server.stop(0);}
 }
}
