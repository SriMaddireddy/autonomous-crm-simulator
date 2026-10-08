package io.srimaddireddy.crm;
import static io.srimaddireddy.crm.Domain.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
/** Minimal stateless Streamable HTTP MCP surface: JSON responses; no SSE or sessions. */
@RestController
public class Mcp {
 private final Skills skills;private final History history;private final Campaigns campaigns;private final Workflows workflows;
 public Mcp(Skills skills,History history,Campaigns campaigns,Workflows workflows){this.skills=skills;this.history=history;this.campaigns=campaigns;this.workflows=workflows;}
 @GetMapping("/mcp") public ResponseEntity<Void> noStream(){return ResponseEntity.status(405).build();}
 @PostMapping(value="/mcp",consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)
 public ResponseEntity<?> rpc(@RequestBody Map<String,Object> req){
  Object id=req.get("id");
  if(!"2.0".equals(req.get("jsonrpc"))||!(req.get("method") instanceof String))return error(id,-32600,"Invalid JSON-RPC request");
  String method=(String)req.get("method");
  if(!req.containsKey("id"))return ResponseEntity.accepted().build();
  try{
   Object result=switch(method){
    case "initialize" -> Map.of("protocolVersion","2025-03-26","capabilities",Map.of("tools",Map.of("listChanged",false)),"serverInfo",Map.of("name","crm-simulator","version","1.0.0"),"instructions","Synthetic data only. evaluate_campaign durably queues a workflow; use get_workflow to poll.");
    case "ping" -> Map.of();
    case "tools/list" -> Map.of("tools",tools());
    case "tools/call" -> call(req.get("params"));
    default -> null;
   };
   if(result==null)return error(id,-32601,"Method not found");
   Map<String,Object> response=new LinkedHashMap<>();response.put("jsonrpc","2.0");response.put("id",id);response.put("result",result);return ResponseEntity.ok(response);
  }catch(IllegalArgumentException e){return error(id,-32602,e.getMessage());}
   catch(ResponseStatusException e){return okToolError(id,e.getReason());}
   catch(RuntimeException e){return error(id,-32603,"Internal error");}
 }
 private List<Map<String,Object>> tools(){
  List<Map<String,Object>> tools=new ArrayList<>();
  for(String skill:Skills.NAMES)tools.add(tool(skill,"Evaluate the "+skill+" skill against synthetic campaign metrics",List.of("campaignId")));
  tools.add(tool("evaluate_campaign","Queue all five skills with persistent state and conflict resolution",List.of("campaignId","idempotencyKey")));
  tools.add(tool("get_workflow","Retrieve status and persisted result",List.of("workflowId")));return tools;
 }
 private Map<String,Object> tool(String name,String desc,List<String> fields){
  Map<String,Object> properties=new LinkedHashMap<>();for(String f:fields)properties.put(f,Map.of("type","string","minLength",1,"maxLength",f.equals("idempotencyKey")?120:80));
  return Map.of("name",name,"description",desc,"inputSchema",Map.of("type","object","properties",properties,"required",fields,"additionalProperties",false));
 }
 @SuppressWarnings("unchecked") private Object call(Object raw){
  if(!(raw instanceof Map<?,?> p)||!(p.get("name") instanceof String name)||!(p.get("arguments") instanceof Map<?,?> a))throw new IllegalArgumentException("Expected name and arguments");
  Object output;
  if(Skills.NAMES.contains(name)){
   Campaign c=campaigns.get(string(a,"campaignId",80));output=skills.evaluate(name,c,history.retrieve(c,5).logs());
  }else if(name.equals("evaluate_campaign"))output=workflows.submit(string(a,"campaignId",80),string(a,"idempotencyKey",120));
  else if(name.equals("get_workflow"))output=workflows.get(string(a,"workflowId",80));
  else throw new IllegalArgumentException("Unknown tool");
  try{String text=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(output);return Map.of("content",List.of(Map.of("type","text","text",text)),"isError",false);}
  catch(Exception e){throw new IllegalStateException(e);}
 }
 private String string(Map<?,?> a,String key,int max){Object value=a.get(key);if(!(value instanceof String s)||s.isBlank()||s.length()>max)throw new IllegalArgumentException("Invalid "+key);return (String)value;}
 private ResponseEntity<?> error(Object id,int code,String message){Map<String,Object> response=new LinkedHashMap<>();response.put("jsonrpc","2.0");response.put("id",id);response.put("error",Map.of("code",code,"message",message));return ResponseEntity.ok(response);}
 private ResponseEntity<?> okToolError(Object id,String message){Map<String,Object> response=new LinkedHashMap<>();response.put("jsonrpc","2.0");response.put("id",id);response.put("result",Map.of("content",List.of(Map.of("type","text","text",message)),"isError",true));return ResponseEntity.ok(response);}
}
