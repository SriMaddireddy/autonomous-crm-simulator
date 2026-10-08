package io.srimaddireddy.crm;
import static io.srimaddireddy.crm.Domain.*;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.HttpStatus;
@RestController @RequestMapping("/api") @Validated
public class Api {
 private final Campaigns campaigns;private final Workflows workflows;private final History history;private final Workers workers;
 public Api(Campaigns campaigns,Workflows workflows,History history,Workers workers){this.campaigns=campaigns;this.workflows=workflows;this.history=history;this.workers=workers;}
 @GetMapping("/campaigns") public List<Campaign> campaigns(@RequestParam(defaultValue="100") @Min(1) @Max(500) int limit){return campaigns.list(limit);}
 @PostMapping("/campaigns") @ResponseStatus(HttpStatus.CREATED) public Campaign create(@Valid @RequestBody Campaign c){campaigns.create(c);return c;}
 @GetMapping("/campaigns/{id}") public Campaign campaign(@PathVariable String id){return campaigns.get(id);}
 @GetMapping("/campaigns/{id}/evidence") public Retrieval evidence(@PathVariable String id){return history.retrieve(campaigns.get(id),5);}
 @PostMapping("/workflows") @ResponseStatus(HttpStatus.ACCEPTED) public Workflow submit(@Valid @RequestBody Submit s){return workflows.submit(s.campaignId(),s.idempotencyKey());}
 @PostMapping("/workflows/batch") @ResponseStatus(HttpStatus.ACCEPTED) public List<Workflow> batch(@Valid @RequestBody Batch b){return campaigns.list(b.count()).stream().map(c->workflows.submit(c.id(),b.runKey()+":"+c.id())).toList();}
 @GetMapping("/workflows") public List<Workflow> list(@RequestParam(defaultValue="100") @Min(1) @Max(500) int limit){return workflows.list(limit);}
 @GetMapping("/workflows/{id}") public Workflow workflow(@PathVariable String id){return workflows.get(id);}
 @GetMapping("/workflows/{id}/events") public List<Map<String,Object>> events(@PathVariable String id){return workflows.events(id);}
 @GetMapping("/stats") public Map<String,Object> stats(){return Map.of("workflows",workflows.stats(),"retrieval",history.stats(),"workers",Map.of("active",workers.active(),"peak",workers.peak(),"capacity",workers.capacity()));}
}
