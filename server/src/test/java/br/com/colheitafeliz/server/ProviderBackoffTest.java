package br.com.colheitafeliz.server;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProviderBackoffTest {
    private static final String FCM="https://fcm.googleapis.com/wp/test";
    private static final String APPLE="https://web.push.apple.com/test";
    @Test void suspensionIsPerValidatedHostAndExpiresWithoutJob(){
        var clock=new MutableClock();var backoff=new ProviderBackoff(clock);
        backoff.record(FCM,429,"600");
        assertTrue(backoff.blocked(FCM));assertFalse(backoff.blocked(APPLE));
        assertThrows(ApiFailure.class,()->backoff.blocked("https://example.com/test"));
        clock.now=clock.now.plusSeconds(600);assertFalse(backoff.blocked(FCM));
    }
    @Test void numericAndDateDeadlinesHaveSameBounds(){
        var now=new MutableClock().instant();
        for(long seconds:new long[]{-600,0,10,60,600,86400,172800}){
            var expected=now.plusSeconds(Math.max(60,Math.min(86400,seconds)));
            assertEquals(expected,WebPushSender.retryDeadline(Long.toString(seconds),now));
            assertEquals(expected,WebPushSender.retryDeadline(DateTimeFormatter.RFC_1123_DATE_TIME.format(now.plusSeconds(seconds).atZone(ZoneOffset.UTC)),now));
        }
        assertEquals(now.plusSeconds(60),WebPushSender.retryDeadline(null,now));
        assertEquals(now.plusSeconds(60),WebPushSender.retryDeadline("broken",now));
    }
    @Test void concurrentResponsesNeverShortenSuspension() throws Exception {
        var clock=new MutableClock();var backoff=new ProviderBackoff(clock);
        try(var workers=Executors.newFixedThreadPool(8)){
            var tasks=new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for(int i=0;i<80;i++){int seconds=i%2==0?600:60;tasks.add(workers.submit(()->backoff.record(FCM,503,Integer.toString(seconds))));}
            for(var task:tasks)task.get();
        }
        clock.now=clock.now.plusSeconds(599);assertTrue(backoff.blocked(FCM));
        clock.now=clock.now.plusSeconds(1);assertFalse(backoff.blocked(FCM));
    }
    @Test void saturatedMapPreservesSuspensionsAndKnownUnsuspendedHost(){
        var clock=new MutableClock();var backoff=new ProviderBackoff(clock);
        assertFalse(backoff.blocked(APPLE));
        for(int i=0;i<127;i++)backoff.record(host(i),429,"600");
        assertFalse(backoff.blocked(APPLE));
        backoff.record(APPLE,429,"600");assertTrue(backoff.blocked(host(128)));
        assertTrue(backoff.blocked(host(0)));
        clock.now=clock.now.plusSeconds(600);
        assertFalse(backoff.blocked(host(128)));
    }
    @Test void idleHostsDoNotExhaustCapacity() throws Exception {
        var backoff=new ProviderBackoff(new MutableClock());
        for(int i=0;i<512;i++)assertFalse(backoff.blocked(host(i)));
        var field=ProviderBackoff.class.getDeclaredField("deadlines");field.setAccessible(true);
        assertEquals(128,((java.util.Map<?,?>)field.get(backoff)).size());
    }
    @Test void fullMapCannotForgetAnInflightResponse(){
        var clock=new MutableClock();var backoff=new ProviderBackoff(clock);
        for(int i=0;i<128;i++)backoff.record(host(i),429,"60");
        backoff.record(host(128),503,"600");
        clock.now=clock.now.plusSeconds(60);
        assertTrue(backoff.blocked(host(128)));
        clock.now=clock.now.plusSeconds(540);assertFalse(backoff.blocked(host(128)));
    }
    private static String host(int i){return "https://host"+i+".push.services.mozilla.com/wpush/v2/test";}
    private static final class MutableClock extends Clock {
        private Instant now=Instant.parse("2026-11-13T12:00:00Z");
        @Override public Instant instant(){return now;}
        @Override public ZoneId getZone(){return ZoneOffset.UTC;}
        @Override public Clock withZone(ZoneId zone){return this;}
    }
}
