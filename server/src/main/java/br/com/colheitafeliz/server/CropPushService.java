package br.com.colheitafeliz.server;

import com.google.gson.JsonObject;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bounded offline work; durable reservations commit before any network call. */
final class CropPushService implements AutoCloseable {
    private static final UUID WORKER=new UUID(0,0);
    private static final int MAX_FARMS_PER_TICK=10;
    private final JdbcOnlineStore store;
    private final CropPushStore crops;
    private final OnlineGameService game;
    private final Clock clock;
    private final PushService.Sender sender;
    private final ScheduledExecutorService scheduler=Executors.newSingleThreadScheduledExecutor(r->Thread.ofPlatform().name("crop-push-scheduler").daemon(true).unstarted(r));
    private final ExecutorService deliveries=Executors.newFixedThreadPool(2,r->Thread.ofPlatform().name("crop-push-delivery").daemon(true).unstarted(r));
    private final AtomicBoolean running=new AtomicBoolean();
    private volatile boolean closed;
    CropPushService(JdbcOnlineStore store,OnlineGameService game,Clock clock,PushService.Sender sender){this.store=store;this.crops=store.cropPushStore();this.game=game;this.clock=clock;this.sender=sender;}
    void start(){scheduler.scheduleWithFixedDelay(this::safeTick,0,30,TimeUnit.SECONDS);}
    private void safeTick(){try{tick();}catch(RuntimeException failure){System.err.println("CROP_PUSH unavailable: "+failure.getClass().getSimpleName());}}
    void tick(){
        if(closed||!running.compareAndSet(false,true))return;
        try{
            Instant now=clock.instant();
            var missing=store.transaction(WORKER,crops::uninitialized);
            for(UUID owner:missing)try{store.transaction(owner,tx->{crops.reconcileSubscription(tx,owner,now);return null;});}catch(RuntimeException failure){
                logFailure();
                try{store.transaction(owner,tx->{tx.lockFarms(owner);if(crops.job(tx,owner)==null){var baseline=new JsonObject();baseline.addProperty("baseline",now.toString());crops.save(tx,owner,new CropPushStore.Job(true,now.plusSeconds(60),null,baseline));}return null;});}catch(ApiFailure unavailable){logFailure();}
            }
            var due=store.transaction(WORKER,tx->{crops.expire(tx,now);return crops.due(tx,now,Math.max(0,MAX_FARMS_PER_TICK-missing.size()));});
            var reserved=new ArrayList<CropPushStore.Delivery>();
            for(UUID owner:due){
                if(closed||Thread.currentThread().isInterrupted())break;
                try{reserved.addAll(store.transaction(owner,tx->{
                    tx.lockFarms(owner);var job=crops.job(tx,owner);
                    if(job==null||!job.enabled()||job.due()==null||job.due().isAfter(now))return List.<CropPushStore.Delivery>of();
                    var player=tx.player(owner);
                    if(player==null||!player.active()||!crops.eligible(tx,owner)){crops.save(tx,owner,new CropPushStore.Job(false,null,job.lastBatch(),job.cursors()));return List.<CropPushStore.Delivery>of();}
                    var snapshot=game.advanceForCropPush(tx,owner);
                    job=crops.job(tx,owner);
                    if(!crops.ready(snapshot,job.cursors(),now))return List.<CropPushStore.Delivery>of();
                    if(job.lastBatch()!=null&&now.isBefore(job.lastBatch().plusSeconds(60)))return List.<CropPushStore.Delivery>of();
                    var result=crops.reserve(tx,owner,now);
                    crops.observeReady(snapshot,job.cursors(),now);
                    crops.save(tx,owner,new CropPushStore.Job(true,crops.next(snapshot,job.cursors(),now,now),now,job.cursors()));
                    return result;
                }));}catch(RuntimeException failure){
                    logFailure();
                    try{store.transaction(owner,tx->{tx.lockFarms(owner);var job=crops.job(tx,owner);if(job!=null&&job.enabled()&&job.due()!=null&&!job.due().isAfter(now))crops.save(tx,owner,new CropPushStore.Job(true,now.plusSeconds(60),job.lastBatch(),job.cursors()));return null;});}catch(ApiFailure unavailable){logFailure();}
                }
            }
            var futures=new ArrayList<Future<?>>();
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(60);
            for(var delivery:reserved){if(closed)break;futures.add(deliveries.submit(()->send(delivery)));}
            for(var future:futures){
                try{long remaining=deadline-System.nanoTime();if(remaining<=0)break;future.get(remaining,TimeUnit.NANOSECONDS);}
                catch(InterruptedException interrupted){Thread.currentThread().interrupt();break;}
                catch(java.util.concurrent.ExecutionException|java.util.concurrent.TimeoutException failure){logFailure();}
            }
            for(var future:futures)future.cancel(true);
            // Queued or interrupted attempts are never retried, including after a crash.
            for(var delivery:reserved)try{store.transaction(delivery.owner(),tx->{crops.finish(tx,delivery,"unavailable",clock.instant());return null;});}catch(ApiFailure failure){logFailure();}
        }finally{running.set(false);}
    }
    private void send(CropPushStore.Delivery delivery){
        if(closed||Thread.currentThread().isInterrupted())return;
        try{
            if(!store.transaction(delivery.owner(),tx->crops.current(tx,delivery)))return;
            String result;
            try{var s=delivery.subscription();var payload=new JsonObject();payload.addProperty("type","CROP_READY");payload.addProperty("bindingId",s.binding());result=PushService.deliveryStatus(sender.send(s.endpoint(),s.p256dh(),s.auth(),payload.toString()));}
            catch(Exception failure){if(failure instanceof InterruptedException)Thread.currentThread().interrupt();result="unavailable";}
            String status=result;
            store.transaction(delivery.owner(),tx->{crops.finish(tx,delivery,status,clock.instant());return null;});
        }catch(ApiFailure failure){logFailure();}
    }
    private static void logFailure(){System.err.println("CROP_PUSH unavailable");}
    @Override public void close(){
        closed=true;scheduler.shutdownNow();deliveries.shutdownNow();
        try{scheduler.awaitTermination(5,TimeUnit.SECONDS);deliveries.awaitTermination(5,TimeUnit.SECONDS);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
    }
}
