package io.srimaddireddy.crm;
import static io.srimaddireddy.crm.Domain.*;
import java.util.*;
import org.springframework.stereotype.Component;
/** Five deterministic skills. Thresholds are explicit and reproducible, not learned agents. */
@Component
public class Skills {
 public static final List<String> NAMES=List.of("budget","performance","audience","risk","strategy");
 public Decision evaluate(String skill, Campaign c, List<Evidence> logs) {
  var ids=logs.stream().map(Evidence::id).toList();
  String action, reason;
  switch(skill) {
   case "budget" -> {
    double utilization=c.spend()/c.budget();
    action=utilization>=1?"PAUSE":utilization>=.9?"REDUCE":"HOLD";
    reason=String.format(Locale.ROOT,"Budget utilization %.1f%%; pause at 100%%, reduce at 90%%.",100*utilization);
   }
   case "performance" -> {
    boolean enough=c.impressions()>=1000 && c.conversions()>=10;
    action=!enough?"HOLD":c.roas()<1?"REDUCE":c.roas()>=2?"SCALE":"HOLD";
    reason=String.format(Locale.ROOT,"ROAS %.2f, CPA %.2f; requires 1,000 impressions and 10 conversions; reduce below 1, scale at 2.",c.roas(),c.cpa());
   }
   case "audience" -> {
    action=c.impressions()>=1000 && c.ctr()<.005?"REVIEW":"HOLD";
    reason=String.format(Locale.ROOT,"CTR %.2f%%; review below 0.5%% after 1,000 impressions.",c.ctr()*100);
   }
   case "risk" -> {
    action=c.risk()>=.8?"PAUSE":c.risk()>=.5?"REVIEW":"HOLD";
    reason=String.format(Locale.ROOT,"Synthetic risk %.2f; pause at 0.8, review at 0.5.",c.risk());
   }
   case "strategy" -> {
    action=logs.isEmpty()?"HOLD":"REVIEW";
    reason=logs.isEmpty()?"No historical evidence; preserve budget and collect data.":
      "Retrieved "+logs.size()+" similar metric profiles; inspect historical outcomes before a budget change.";
   }
   default -> throw new IllegalArgumentException("Unknown skill: "+skill);
  }
  return new Decision(skill,action,reason,ids);
 }
 public String resolve(List<Decision> decisions) {
  var actions=decisions.stream().filter(d->!d.skill().equals("strategy")).map(Decision::action).toList();
  for(String action:List.of("PAUSE","REDUCE","REVIEW","SCALE")) if(actions.contains(action)) return action;
  return "HOLD";
 }
}
