package br.com.colheitafeliz.server;

import br.com.colheitafeliz.domain.crop.Crop;
import br.com.colheitafeliz.domain.farm.TileState;
import br.com.colheitafeliz.game.CropCatalogLoader;
import br.com.colheitafeliz.game.GameSnapshot;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Private bounded per-tile cursors, separate from the player's notification inbox. */
final class CropPushStore {
    record Job(boolean enabled, Instant due, Instant lastBatch, JsonObject cursors) {}
    record Delivery(UUID id, UUID owner, PushStore.Subscription subscription) {}
    private final String schema;
    private final br.com.colheitafeliz.domain.crop.CropCatalog catalog=CropCatalogLoader.load();
    CropPushStore(String schema){this.schema=schema;}
    void initialize(JdbcOnlineStore.Tx tx){
        tx.execute("CREATE TABLE IF NOT EXISTS "+schema+".crop_push_jobs (owner uuid PRIMARY KEY REFERENCES "+schema+".players(id), enabled boolean NOT NULL, next_at timestamptz, last_batch_at timestamptz, cursors jsonb NOT NULL)");
        tx.execute("CREATE INDEX IF NOT EXISTS crop_push_due_idx ON "+schema+".crop_push_jobs(next_at) WHERE enabled AND next_at IS NOT NULL");
        tx.execute("CREATE TABLE IF NOT EXISTS "+schema+".crop_push_deliveries (id uuid PRIMARY KEY, owner uuid NOT NULL REFERENCES "+schema+".players(id), endpoint text NOT NULL, binding text NOT NULL, device_revision bigint NOT NULL, status text NOT NULL, reserved_at timestamptz NOT NULL)");
        tx.execute("CREATE INDEX IF NOT EXISTS crop_push_pending_idx ON "+schema+".crop_push_deliveries(reserved_at) WHERE status='pending'");
    }
    boolean eligible(JdbcOnlineStore.Tx tx,UUID owner){return !tx.query("SELECT 1 FROM "+schema+".push_subscriptions s JOIN "+schema+".players p ON p.id=s.owner WHERE s.owner=? AND p.active AND s.active AND s.preferences @> '{\"plants\":true,\"plantsConfirmed\":true}'::jsonb LIMIT 1",r->1,owner).isEmpty();}
    void suspendIfIneligible(JdbcOnlineStore.Tx tx,UUID owner){if(!eligible(tx,owner)){var job=job(tx,owner);if(job!=null)save(tx,owner,new Job(false,null,job.lastBatch(),job.cursors()));}}
    Job job(JdbcOnlineStore.Tx tx,UUID owner){
        var rows=tx.query("SELECT enabled,next_at,last_batch_at,cursors::text FROM "+schema+".crop_push_jobs WHERE owner=? FOR UPDATE",r->new Job(r.getBoolean(1),r.getTimestamp(2)==null?null:r.getTimestamp(2).toInstant(),r.getTimestamp(3)==null?null:r.getTimestamp(3).toInstant(),JsonParser.parseString(r.getString(4)).getAsJsonObject()),owner);
        return rows.isEmpty()?null:rows.getFirst();
    }
    void save(JdbcOnlineStore.Tx tx,UUID owner,Job job){tx.execute("INSERT INTO "+schema+".crop_push_jobs(owner,enabled,next_at,last_batch_at,cursors) VALUES(?,?,?,?,?::jsonb) ON CONFLICT(owner) DO UPDATE SET enabled=EXCLUDED.enabled,next_at=EXCLUDED.next_at,last_batch_at=EXCLUDED.last_batch_at,cursors=EXCLUDED.cursors",owner,job.enabled(),stamp(job.due()),stamp(job.lastBatch()),job.cursors().toString());}
    void reconcile(JdbcOnlineStore.Tx tx,UUID owner,GameSnapshot snapshot,Instant now){
        boolean enabled=eligible(tx,owner);
        Job previous=job(tx,owner);
        if(!enabled){if(previous!=null)save(tx,owner,new Job(false,null,previous.lastBatch(),previous.cursors()));return;}
        var cursors=previous==null?new JsonObject():previous.cursors();
        boolean baseline=previous==null||!previous.enabled();
        var current=new JsonObject();
        Instant baselineAt=baseline?now:cursors.has("baseline")?Instant.parse(cursors.get("baseline").getAsString()):null;
        if(baselineAt!=null)current.addProperty("baseline",baselineAt.toString());
        for(int i=0;i<snapshot.tiles().size();i++){
            var tile=snapshot.tiles().get(i);String key=Integer.toString(i);
            if(!growing(tile))continue;
            String identity=identity(tile);
            Instant matured=crop(tile).maturedAt(now);
            if((matured!=null&&baselineAt!=null&&!matured.isAfter(baselineAt))||(cursors.has(key)&&cursors.get(key).getAsString().equals(identity)))current.addProperty(key,identity);
        }
        Instant last=previous==null?null:previous.lastBatch();
        save(tx,owner,new Job(true,next(snapshot,current,now,last),last,current));
    }
    void reconcileSubscription(JdbcOnlineStore.Tx tx,UUID owner,Instant now){
        tx.lockFarms(owner);
        var farm=tx.farm(owner);
        if(farm!=null)reconcile(tx,owner,farm.snapshot(),now.isBefore(farm.time())?farm.time():now);
    }
    /** Fast path for ordinary saves: only eligible owners need a job upsert. */
    void reconcileIfEligible(JdbcOnlineStore.Tx tx,UUID owner,GameSnapshot snapshot,Instant now){
        if(eligible(tx,owner))reconcile(tx,owner,snapshot,now);
    }
    List<UUID> uninitialized(JdbcOnlineStore.Tx tx){return tx.query("SELECT DISTINCT s.owner FROM "+schema+".push_subscriptions s JOIN "+schema+".players p ON p.id=s.owner JOIN "+schema+".farms f ON f.owner=s.owner LEFT JOIN "+schema+".crop_push_jobs j ON j.owner=s.owner WHERE p.active AND s.active AND s.preferences @> '{\"plants\":true,\"plantsConfirmed\":true}'::jsonb AND j.owner IS NULL ORDER BY s.owner LIMIT 10",r->r.getObject(1,UUID.class));}
    List<UUID> due(JdbcOnlineStore.Tx tx,Instant now,int limit){return tx.query("SELECT owner FROM "+schema+".crop_push_jobs WHERE enabled AND next_at<=? ORDER BY next_at,owner LIMIT ?",r->r.getObject(1,UUID.class),stamp(now),limit);}
    boolean ready(GameSnapshot snapshot,JsonObject cursors,Instant now){
        for(int i=0;i<snapshot.tiles().size();i++){
            var tile=snapshot.tiles().get(i);String key=Integer.toString(i);
            if(growing(tile)&&crop(tile).matureAt(now)&&(!cursors.has(key)||!cursors.get(key).getAsString().equals(identity(tile))))return true;
        }return false;
    }
    void observeReady(GameSnapshot snapshot,JsonObject cursors,Instant now){for(int i=0;i<snapshot.tiles().size();i++){var tile=snapshot.tiles().get(i);if(growing(tile)&&crop(tile).matureAt(now))cursors.addProperty(Integer.toString(i),identity(tile));}}
    Instant next(GameSnapshot snapshot,JsonObject cursors,Instant now,Instant last){
        Instant next=null;
        for(int i=0;i<snapshot.tiles().size();i++){
            var tile=snapshot.tiles().get(i);if(!growing(tile))continue;
            String key=Integer.toString(i);if(cursors.has(key)&&cursors.get(key).getAsString().equals(identity(tile)))continue;
            var crop=crop(tile);Instant candidate=null;
            if(crop.matureAt(now))candidate=now;
            else if(crop.paused()){
                Instant expiry=expiry(snapshot,"pests");
                if(expiry!=null&&expiry.isAfter(now)){
                    if(("WEEDS".equals(tile.pest())&&!snapshot.automations().weedsSince().containsKey(key))||(tile.insects()>0&&!snapshot.automations().insectsSince().containsKey(key)))candidate=now;
                    for(var arrivals:List.of(snapshot.automations().weedsSince(),snapshot.automations().insectsSince()))if(arrivals.containsKey(key)){
                        Instant resume=Instant.parse(arrivals.get(key)).plusSeconds(900);
                        if(resume.isBefore(expiry))candidate=earlier(candidate,resume.isAfter(now)?resume:now);
                    }
                }
            }else if(crop.watered()){
                candidate=now.plus(crop.remainingAt(now));
                Instant water=expiry(snapshot,"water");
                if(water!=null&&water.isAfter(now)){
                    // Full-speed maturity is an early hypothesis; the engine replays care and expiry.
                    candidate=earlier(candidate,now.plusMillis(crop.currentDuration().toMillis()-crop.grownMillisAt(now)));
                }
            }else{Instant water=expiry(snapshot,"water");if(water!=null&&water.isAfter(now))candidate=now;}
            // Expired care may have resumed growth before now; only the normal engine can prove it.
            if(needsAutomationReplay(snapshot,tile,now))candidate=now;
            next=earlier(next,candidate);
        }
        if(next!=null&&last!=null&&next.isBefore(last.plusSeconds(60)))next=last.plusSeconds(60);
        return next;
    }
    List<Delivery> reserve(JdbcOnlineStore.Tx tx,UUID owner,Instant now){
        var subscriptions=tx.query("SELECT owner,endpoint,proof,binding,device_revision,p256dh,auth,preferences::text,active FROM "+schema+".push_subscriptions WHERE owner=? AND active AND preferences @> '{\"plants\":true,\"plantsConfirmed\":true}'::jsonb ORDER BY endpoint",r->new PushStore.Subscription(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getString(4),r.getLong(5),r.getString(6),r.getString(7),r.getString(8),r.getBoolean(9)),owner);
        return subscriptions.stream().map(s->{UUID id=UUID.randomUUID();tx.execute("INSERT INTO "+schema+".crop_push_deliveries(id,owner,endpoint,binding,device_revision,status,reserved_at) VALUES(?,?,?,?,?,'pending',?)",id,owner,s.endpoint(),s.binding(),s.revision(),stamp(now));return new Delivery(id,owner,s);}).toList();
    }
    boolean current(JdbcOnlineStore.Tx tx,Delivery delivery){
        var s=delivery.subscription();var current=new PushStore(schema).find(tx,s.endpoint());var owner=tx.player(delivery.owner());
        return owner!=null&&owner.active()&&current!=null&&current.active()&&current.owner().equals(delivery.owner())&&current.binding().equals(s.binding())&&current.revision()==s.revision()&&current.plantsEnabled();
    }
    void finish(JdbcOnlineStore.Tx tx,Delivery d,String status,Instant now){
        if(status.equals("expired"))tx.lock("push-subscriptions");
        boolean pending=!tx.query("UPDATE "+schema+".crop_push_deliveries SET status=? WHERE id=? AND status='pending' AND reserved_at>? RETURNING id",r->r.getObject(1,UUID.class),status,d.id(),stamp(now.minusSeconds(60))).isEmpty();
        if(pending&&status.equals("expired")&&current(tx,d)){new PushStore(schema).deactivate(tx,d.subscription().endpoint(),d.subscription().binding(),d.subscription().revision(),now);suspendIfIneligible(tx,d.owner());}
    }
    void expire(JdbcOnlineStore.Tx tx,Instant now){tx.execute("UPDATE "+schema+".crop_push_deliveries SET status='unavailable' WHERE id IN (SELECT id FROM "+schema+".crop_push_deliveries WHERE status='pending' AND reserved_at<=? ORDER BY reserved_at LIMIT 100 FOR UPDATE SKIP LOCKED)",stamp(now.minusSeconds(60)));}
    private Crop crop(GameSnapshot.TileSnapshot t){return new Crop(catalog.get(t.cropId()),Instant.parse(t.plantedAt()),parse(t.wateredAt()),parse(t.pausedAt()),parse(t.growthAnchorAt()),t.grownAtAnchorMillis(),t.origin(),t.season());}
    private static String identity(GameSnapshot.TileSnapshot t){return t.harvestRevision()+":"+t.plantedAt()+":"+t.season();}
    private static boolean growing(GameSnapshot.TileSnapshot t){return t.state()==TileState.GROWING||t.state()==TileState.READY;}
    private static boolean needsAutomationReplay(GameSnapshot snapshot,GameSnapshot.TileSnapshot tile,Instant now){
        if(snapshot.automations()==null)return false;
        Instant cursor=Instant.parse(snapshot.automations().cursor());
        Instant planted=Instant.parse(tile.plantedAt());
        if(planted.isAfter(cursor))cursor=planted;
        if(!cursor.isBefore(now))return false;
        for(String service:List.of("water","pests")){Instant expiry=expiry(snapshot,service);if(expiry!=null&&cursor.isBefore(expiry))return true;}
        return false;
    }
    private static Instant expiry(GameSnapshot s,String id){return s.automations()==null||s.vipExpiresAt()==null||!s.automations().expiresAt().containsKey(id)?null:earlier(parse(s.automations().expiresAt().get(id)),parse(s.vipExpiresAt()));}
    private static Instant parse(String value){return value==null?null:Instant.parse(value);}
    private static Instant earlier(Instant a,Instant b){return a==null?b:b==null?a:a.isBefore(b)?a:b;}
    private static Timestamp stamp(Instant value){return value==null?null:Timestamp.from(value);}
}
