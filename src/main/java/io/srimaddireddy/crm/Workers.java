package io.srimaddireddy.crm;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component
public class Workers {
 private final Workflows workflows;private final Campaigns campaigns;private final Orchestrator orchestrator;
 private final ExecutorService pool;private final ScheduledExecutorService heartbeats=Executors.newScheduledThreadPool(2);
 private final Semaphore slots;private final int capacity;private volatile boolean ready;private final boolean enabled;
 private final AtomicInteger active=new AtomicInteger(),peak=new AtomicInteger();
 public Workers(Workflows workflows,Campaigns campaigns,Orchestrator orchestrator,@Value("${crm.workers}") int capacity,@Value("${crm.workers-enabled:true}") boolean enabled){
  if(capacity<1||capacity>50)throw new IllegalArgumentException("crm.workers must be 1..50");
  this.workflows=workflows;this.campaigns=campaigns;this.orchestrator=orchestrator;this.capacity=capacity;this.enabled=enabled;
  slots=new Semaphore(capacity);pool=Executors.newFixedThreadPool(capacity);
 }
 @EventListener(ApplicationReadyEvent.class) public void ready(){ready=enabled;}
 @Scheduled(fixedDelay=200) public void poll(){
  if(!ready)return;workflows.expireExhausted();
  for(String id:workflows.candidates(capacity)){
   if(!slots.tryAcquire())break;
   try{pool.submit(()->process(id));}catch(RejectedExecutionException e){slots.release();}
  }
 }
 private void process(String id){
  boolean counted=false;
  try{
   var claimed=workflows.claim(id,UUID.randomUUID().toString());if(claimed.isEmpty())return;
   var claim=claimed.get();counted=true;int current=active.incrementAndGet();peak.accumulateAndGet(current,Math::max);
   var renewal=heartbeats.scheduleAtFixedRate(()->{try{workflows.renew(claim);}catch(RuntimeException ignored){}},5,5,TimeUnit.SECONDS);
   try{var result=orchestrator.evaluate(campaigns.get(claim.campaignId()));workflows.complete(claim,result);}
   catch(Exception e){workflows.fail(claim,e);}finally{renewal.cancel(false);}
  }finally{if(counted)active.decrementAndGet();slots.release();}
 }
 public int active(){return active.get();}public int peak(){return peak.get();}public int capacity(){return capacity;}
 @PreDestroy public void shutdown(){ready=false;pool.shutdown();heartbeats.shutdown();try{if(!pool.awaitTermination(20,TimeUnit.SECONDS))pool.shutdownNow();}catch(InterruptedException e){Thread.currentThread().interrupt();pool.shutdownNow();}}
}
