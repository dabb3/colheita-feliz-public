package br.com.colheitafeliz.domain.notification;

import java.time.Instant;
import java.util.*;
import java.nio.charset.StandardCharsets;

/** The bounded event cursors survive retention and acknowledgement; refreshing cannot recreate old events. */
public final class NotificationInbox {
    public static final int LIMIT=100;
    public record Snapshot(int version,List<GameNotification> entries,Map<String,String> observed) {
        public Snapshot {
            if(version!=1||entries==null||observed==null)throw new IllegalArgumentException("Invalid notification state");
            entries=List.copyOf(entries);observed=Map.copyOf(observed);
            if(entries.size()>LIMIT||observed.size()>48||entries.stream().map(GameNotification::id).distinct().count()!=entries.size()
                    ||observed.entrySet().stream().anyMatch(e->!validKey(e.getKey())||e.getValue().isBlank()||e.getValue().length()>240))
                throw new IllegalArgumentException("Invalid notification history");
        }
    }
    private final List<GameNotification> entries=new ArrayList<>();
    private final Map<String,String> observed=new HashMap<>();
    public NotificationInbox() {}
    public NotificationInbox(Snapshot state){if(state!=null){entries.addAll(state.entries());observed.putAll(state.observed());}}
    private static boolean validKey(String key){return key.equals("christmas-gift-2026")||key.equals("vip")||key.equals("theft")||key.matches("crop:([0-9]|[12][0-9])")||key.matches("animal:[a-z0-9-]{1,60}");}
    public boolean record(String key,String stamp,GameNotification.Type type,String message,Instant now,Map<String,String> metadata){
        if(!validKey(key)||stamp==null||stamp.isBlank()||stamp.length()>240||(!observed.containsKey(key)&&observed.size()>=48))
            throw new IllegalArgumentException("Invalid event cursor");
        if(stamp.equals(observed.get(key)))return false;
        String id=UUID.nameUUIDFromBytes((key+":"+stamp).getBytes(StandardCharsets.UTF_8)).toString();
        var entry=new GameNotification(id,type,message,now.toString(),false,metadata);
        observed.put(key,stamp);
        if(entries.stream().anyMatch(n->n.id().equals(id)))return true;
        entries.addFirst(entry);if(entries.size()>LIMIT)entries.removeLast();return true;
    }
    public List<GameNotification> entries(){return List.copyOf(entries);}
    public int unread(){return (int)entries.stream().filter(n->!n.read()).count();}
    public boolean markRead(String id){for(int i=0;i<entries.size();i++)if(entries.get(i).id().equals(id)){entries.set(i,entries.get(i).asRead());return true;}return false;}
    public boolean markAllRead(){boolean changed=unread()>0;entries.replaceAll(GameNotification::asRead);return changed;}
    public Snapshot snapshot(){return new Snapshot(1,entries,observed);}
}
