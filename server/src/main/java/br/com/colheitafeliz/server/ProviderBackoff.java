package br.com.colheitafeliz.server;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;

/** Bounded provider state; saturation refuses unknown hosts without evicting a suspension. */
final class ProviderBackoff {
    private static final int MAX_HOSTS=128;
    private final Clock clock;
    private final LinkedHashMap<String,Instant> deadlines=new LinkedHashMap<>();
    private Instant overflowUntil=Instant.EPOCH;
    ProviderBackoff(Clock clock){this.clock=clock;}
    synchronized boolean blocked(String endpoint){
        String host=validatedHost(endpoint);var now=clock.instant();removeExpired(now);
        var deadline=deadlines.get(host);
        if(deadline!=null)return now.isBefore(deadline);
        if(now.isBefore(overflowUntil))return true;
        makeRoom();if(deadlines.size()==MAX_HOSTS)return true;
        deadlines.put(host,Instant.EPOCH);return false;
    }
    synchronized void record(String endpoint,int status,String retryAfter){
        String host=validatedHost(endpoint);
        if(status!=429&&status!=503)return;
        var now=clock.instant();removeExpired(now);
        var until=WebPushSender.retryDeadline(retryAfter,now);
        if(!deadlines.containsKey(host))makeRoom();
        if(deadlines.containsKey(host)||deadlines.size()<MAX_HOSTS)
            deadlines.merge(host,until,(old,next)->old.isAfter(next)?old:next);
        else if(until.isAfter(overflowUntil))overflowUntil=until;
    }
    private void makeRoom(){
        if(deadlines.size()<MAX_HOSTS)return;
        var entries=deadlines.entrySet().iterator();
        while(entries.hasNext())if(entries.next().getValue().equals(Instant.EPOCH)){entries.remove();return;}
    }
    private void removeExpired(Instant now){
        deadlines.entrySet().removeIf(entry->!entry.getValue().equals(Instant.EPOCH)&&!now.isBefore(entry.getValue()));
    }
    private static String validatedHost(String endpoint){PushValidation.validateEndpoint(endpoint);return URI.create(endpoint).getHost();}
}
