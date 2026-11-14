package br.com.colheitafeliz.server;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Transactional social-event consumer; a batch links event receipts to the shared transport. */
final class SocialPushStore {
    record Delivery(UUID id,UUID event,UUID owner,String type,PushStore.Subscription subscription) {}
    private final String schema;
    SocialPushStore(String schema){this.schema=schema;}
    private record Event(UUID id,String type,long order) {}
    void initialize(JdbcOnlineStore.Tx tx) {
        tx.execute("CREATE TABLE IF NOT EXISTS "+schema+".social_push_devices (endpoint text NOT NULL REFERENCES "+schema+".push_subscriptions(endpoint), type text NOT NULL CHECK(type IN ('THEFT','PEST')), owner uuid NOT NULL REFERENCES "+schema+".players(id), binding text NOT NULL, baseline bigint NOT NULL, PRIMARY KEY(endpoint,type))");
        tx.execute("CREATE INDEX IF NOT EXISTS social_push_devices_owner_idx ON "+schema+".social_push_devices(owner)");
        tx.execute("CREATE TABLE IF NOT EXISTS "+schema+".social_push_cursors (owner uuid PRIMARY KEY REFERENCES "+schema+".players(id), last_event bigint NOT NULL)");
        tx.execute("CREATE TABLE IF NOT EXISTS "+schema+".social_push_deliveries (id uuid PRIMARY KEY, event_id uuid NOT NULL REFERENCES "+schema+".push_events(id), owner uuid NOT NULL REFERENCES "+schema+".players(id), type text NOT NULL CHECK(type IN ('THEFT','PEST')), endpoint text NOT NULL, binding text NOT NULL, device_revision bigint NOT NULL, status text NOT NULL, reserved_at timestamptz NOT NULL, UNIQUE(event_id,endpoint,binding))");
        tx.execute("ALTER TABLE "+schema+".social_push_deliveries ADD COLUMN IF NOT EXISTS batch_id uuid");
        tx.execute("CREATE INDEX IF NOT EXISTS social_push_batch_idx ON "+schema+".social_push_deliveries(batch_id) WHERE batch_id IS NOT NULL");
        tx.execute("CREATE INDEX IF NOT EXISTS social_push_pending_idx ON "+schema+".social_push_deliveries(reserved_at) WHERE status='pending'");
    }
    Instant next(JdbcOnlineStore.Tx tx,UUID owner,Instant now){
        return tx.query("SELECT 1 FROM "+schema+".push_events e JOIN "+schema+".social_push_devices d ON d.owner=e.owner AND d.type=e.type AND e.event_order>d.baseline WHERE e.owner=? AND e.event_order>COALESCE((SELECT last_event FROM "+schema+".social_push_cursors WHERE owner=?),0) LIMIT 1",r->1,owner,owner).isEmpty()?null:now;
    }
    void reconcile(JdbcOnlineStore.Tx tx,UUID owner) {
        tx.lockFarms(owner);
        var subscriptions=subscriptions(tx,owner);
        long baseline=tx.query("SELECT COALESCE(max(event_order),0) FROM "+schema+".push_events WHERE owner=?",r->r.getLong(1),owner).getFirst();
        for(String type:List.of("THEFT","PEST")){
            String key=key(type);
            tx.execute("DELETE FROM "+schema+".social_push_devices d WHERE d.owner=? AND d.type=? AND NOT EXISTS (SELECT 1 FROM "+schema+".push_subscriptions s JOIN "+schema+".players p ON p.id=s.owner WHERE s.endpoint=d.endpoint AND s.owner=d.owner AND s.binding=d.binding AND p.active AND s.active AND s.preferences @> ?::jsonb)",owner,type,consent(key));
            for(var subscription:subscriptions)if(subscription.preferenceEnabled(key))
                tx.execute("INSERT INTO "+schema+".social_push_devices(endpoint,type,owner,binding,baseline) VALUES(?,?,?,?,?) ON CONFLICT(endpoint,type) DO NOTHING",subscription.endpoint(),type,owner,subscription.binding(),baseline);
        }
    }
    List<Delivery> consume(JdbcOnlineStore.Tx tx,UUID owner,Instant now){
        tx.player(owner);
        tx.lock("push-subscriptions");
        reconcile(tx,owner);
        tx.execute("INSERT INTO "+schema+".social_push_cursors(owner,last_event) VALUES(?,0) ON CONFLICT DO NOTHING",owner);
        long cursor=tx.query("SELECT last_event FROM "+schema+".social_push_cursors WHERE owner=? FOR UPDATE",r->r.getLong(1),owner).getFirst();
        var events=tx.query("SELECT id,type,event_order FROM "+schema+".push_events WHERE owner=? AND event_order>? ORDER BY event_order LIMIT 100",r->new Event(r.getObject(1,UUID.class),r.getString(2),r.getLong(3)),owner,cursor);
        var deliveries=new ArrayList<Delivery>();
        for(var event:events){
            var subscriptions=tx.query("SELECT s.owner,s.endpoint,s.proof,s.binding,s.device_revision,s.p256dh,s.auth,s.preferences::text,s.active FROM "+schema+".push_subscriptions s JOIN "+schema+".players p ON p.id=s.owner JOIN "+schema+".social_push_devices d ON d.endpoint=s.endpoint AND d.owner=s.owner AND d.binding=s.binding WHERE s.owner=? AND p.active AND s.active AND d.type=? AND d.baseline<? AND s.preferences @> ?::jsonb ORDER BY s.endpoint",r->new PushStore.Subscription(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getString(4),r.getLong(5),r.getString(6),r.getString(7),r.getString(8),r.getBoolean(9)),owner,event.type(),event.order(),consent(key(event.type())));
            for(var subscription:subscriptions){
                UUID id=UUID.randomUUID();
                var inserted=tx.query("INSERT INTO "+schema+".social_push_deliveries(id,event_id,owner,type,endpoint,binding,device_revision,status,reserved_at) VALUES(?,?,?,?,?,?,?,'pending',?) ON CONFLICT(event_id,endpoint,binding) DO NOTHING RETURNING id",r->r.getObject(1,UUID.class),id,event.id(),owner,event.type(),subscription.endpoint(),subscription.binding(),subscription.revision(),Timestamp.from(now));
                if(!inserted.isEmpty())deliveries.add(new Delivery(id,event.id(),owner,event.type(),subscription));
            }
            cursor=event.order();
        }
        tx.execute("UPDATE "+schema+".social_push_cursors SET last_event=? WHERE owner=?",cursor,owner);
        return deliveries;
    }
    boolean current(JdbcOnlineStore.Tx tx,Delivery delivery){
        var s=delivery.subscription();var current=new PushStore(schema).find(tx,s.endpoint());var owner=tx.player(delivery.owner());
        return owner!=null&&owner.active()&&current!=null&&current.active()&&current.owner().equals(delivery.owner())&&current.binding().equals(s.binding())&&current.revision()==s.revision()&&current.preferenceEnabled(key(delivery.type()));
    }
    private List<PushStore.Subscription> subscriptions(JdbcOnlineStore.Tx tx,UUID owner){
        return tx.query("SELECT s.owner,s.endpoint,s.proof,s.binding,s.device_revision,s.p256dh,s.auth,s.preferences::text,s.active FROM "+schema+".push_subscriptions s JOIN "+schema+".players p ON p.id=s.owner WHERE s.owner=? AND p.active AND s.active ORDER BY s.endpoint",r->new PushStore.Subscription(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getString(4),r.getLong(5),r.getString(6),r.getString(7),r.getString(8),r.getBoolean(9)),owner);
    }
    private static String key(String type){return switch(type){case "THEFT"->"thefts";case "PEST"->"pests";default->throw new IllegalArgumentException("Unsupported social event");};}
    private static String consent(String key){return "{\""+key+"\":true,\""+key+"Confirmed\":true}";}
}
