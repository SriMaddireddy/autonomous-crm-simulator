package io.srimaddireddy.crm;
import static io.srimaddireddy.crm.Domain.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
class SkillsTest {
 private final Skills skills=new Skills();
 private Campaign campaign(double spend,double risk,double revenue){return new Campaign("test","Test","SEARCH",1000,spend,10000,1000,100,revenue,risk);}
 @Test void riskAndBudgetOverrideScale(){
  Campaign c=campaign(1000,.9,5000);
  var decisions=Skills.NAMES.stream().map(s->skills.evaluate(s,c,List.of())).toList();
  assertEquals("SCALE",decisions.get(1).action());assertEquals("PAUSE",skills.resolve(decisions));
 }
 @Test void enoughDataAndBoundaries(){
  assertEquals("REDUCE",skills.evaluate("budget",campaign(900,.1,1800),List.of()).action());
  assertEquals("PAUSE",skills.evaluate("budget",campaign(1000,.1,1800),List.of()).action());
  assertEquals("PAUSE",skills.evaluate("risk",campaign(1,.8,2),List.of()).action());
  assertEquals("REVIEW",skills.evaluate("risk",campaign(1,.5,2),List.of()).action());
  Campaign small=new Campaign("tiny","Tiny","SOCIAL",100,0,0,0,0,0,0);
  assertEquals("HOLD",skills.evaluate("performance",small,List.of()).action());
  assertEquals(0,small.ctr());assertEquals(0,small.roas());
 }
 @Test void traceableEvidenceAndNoSilentUnknownSkill(){
  var log=new Evidence(42,"historical","simulated",.9);
  assertEquals(List.of(42L),skills.evaluate("risk",campaign(100,.1,100),List.of(log)).evidenceIds());
  assertThrows(IllegalArgumentException.class,()->skills.evaluate("typo",campaign(100,.1,100),List.of()));
 }
 @Test void embeddingsNormalizedAndDistinguishProfiles(){
  double[] v=History.features(campaign(100,.1,100)),v2=History.features(campaign(1000,.9,5000));
  assertEquals(1,Arrays.stream(v).map(x->x*x).sum(),1e-9);
  double dot=0;for(int i=0;i<v.length;i++)dot+=v[i]*v2[i];assertTrue(dot<.99);
 }
}
