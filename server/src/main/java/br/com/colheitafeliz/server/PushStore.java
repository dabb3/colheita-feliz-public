package br.com.colheitafeliz.server;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Private transport state; never touches farm snapshots or economic revisions. */
final class PushStore {
    record Subscription(UUID owner,String endpoint,String proof,String binding,long revision,String p256dh,String auth,String preferences,boolean active) {
        boolean plantsEnabled(){var prefs=com.google.gson.JsonParser.parseString(preferences).getAsJsonObject();return plantsConfirmed(prefs)&&isBooleanTrue(prefs,"plants");}
        boolean preferenceEnabled(String key){var prefs=com.google.gson.JsonParser.parseString(preferences).getAsJsonObject();return isBooleanTrue(prefs,key)&&isBooleanTrue(prefs,key+"Confirmed");}
    }
    static boolean plantsConfirmed(com.google.gson.JsonObject preferences){return isConfirmed(preferences,"plants");}
    static boolean isConfirmed(com.google.gson.JsonObject preferences,String key){return isBooleanTrue(preferences,key)&&isBooleanTrue(preferences,key+"Confirmed");}
    private static boolean isBooleanTrue(com.google.gson.JsonObject preferences,String name){var value=preferences.get(name);return value!=null&&value.isJsonPrimitive()&&value.getAsJsonPrimitive().isBoolean()&&value.getAsBoolean();}
    record Attempt(UUID actor,String endpoint,String binding,long revision,String requestHash,String status,Instant reservedAt) {}
    private final String schema;
    PushStore(String schema){this.schema=schema;}
    void initialize(JdbcOnlineStore.Tx tx){
        tx.execute("CREATE TABLE IF NOT EXISTS "+schema+".push_subscriptions (endpoint text PRIMARY KEY, owner uuid NOT NULL REFERENCES "+schema+".players(id), proof text NOT NULL, binding text NOT NULL, device_revision bigint NOT NULL CHECK(device_revision BETWEEN 1 AND 9007199254740991), p256dh text NOT NULL, auth text NOT NULL, preferences jsonb NOT NULL, active boolean NOT NULL, created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL)");
        tx.execute("CREATE INDEX IF NOT EXISTS push_owner_idx ON "+schema+".push_subscriptions(owner) WHERE active");
        tx.execute("CREATE INDEX IF NOT EXISTS push_owner_created_idx ON "+schema+".push_subscriptions(owner,created_at)");
        tx.execute("CREATE TABLE IF NOT EXISTS "+schema+".push_tests (id uuid PRIMARY KEY, actor uuid NOT NULL REFERENCES "+schema+".players(id), endpoint text NOT NULL, binding text NOT NULL, device_revision bigint NOT NULL, request_hash text NOT NULL, status text NOT NULL, reserved_at timestamptz NOT NULL)");
        tx.execute("CREATE INDEX IF NOT EXISTS push_tests_actor_time_idx ON "+schema+".push_tests(actor,reserved_at)");
        tx.execute("CREATE INDEX IF NOT EXISTS push_tests_endpoint_time_idx ON "+schema+".push_tests(endpoint,reserved_at)");
        tx.execute("CREATE TABLE IF NOT EXISTS "+schema+".push_events (id uuid NOT NULL UNIQUE, owner uuid NOT NULL REFERENCES "+schema+".players(id), actor uuid NOT NULL REFERENCES "+schema+".players(id), request_id uuid NOT NULL, type text NOT NULL CHECK(type IN ('THEFT','PEST')), metadata jsonb NOT NULL, created_at timestamptz NOT NULL, PRIMARY KEY(actor,request_id))");
        tx.execute("ALTER TABLE "+schema+".push_events ADD COLUMN IF NOT EXISTS request_id uuid");
        tx.execute("UPDATE "+schema+".push_events SET request_id=id WHERE request_id IS NULL");
        tx.execute("ALTER TABLE "+schema+".push_events ALTER COLUMN request_id SET NOT NULL");
        tx.execute("CREATE UNIQUE INDEX IF NOT EXISTS push_events_id_idx ON "+schema+".push_events(id)");
        tx.execute("DO $$ BEGIN IF EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='"+schema+".push_events'::regclass AND conname='push_events_pkey' AND pg_get_constraintdef(oid) NOT LIKE '%actor, request_id%') THEN ALTER TABLE "+schema+".push_events DROP CONSTRAINT push_events_pkey; END IF; IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='"+schema+".push_events'::regclass AND conname='push_events_actor_request_key') THEN ALTER TABLE "+schema+".push_events ADD CONSTRAINT push_events_actor_request_key PRIMARY KEY(actor,request_id); END IF; END $$");
        tx.execute("CREATE INDEX IF NOT EXISTS push_events_owner_time_idx ON "+schema+".push_events(owner,created_at)");
    }
    Subscription find(JdbcOnlineStore.Tx tx,String endpoint){
        tx.lock("push-endpoint:"+endpoint);
        var rows=tx.query("SELECT owner,endpoint,proof,binding,device_revision,p256dh,auth,preferences::text,active FROM "+schema+".push_subscriptions WHERE endpoint=?",r->new Subscription(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getString(4),r.getLong(5),r.getString(6),r.getString(7),r.getString(8),r.getBoolean(9)),endpoint);
        return rows.isEmpty()?null:rows.getFirst();
    }
    int totalCount(JdbcOnlineStore.Tx tx,UUID actor,Instant now){return tx.query("SELECT count(*) FROM "+schema+".push_subscriptions WHERE owner=? AND created_at>?",r->r.getInt(1),actor,Timestamp.from(now.minusSeconds(30L*24*60*60))).getFirst();}
    int activeCount(JdbcOnlineStore.Tx tx,UUID actor){return tx.query("SELECT count(*) FROM "+schema+".push_subscriptions WHERE owner=? AND active",r->r.getInt(1),actor).getFirst();}
    void save(JdbcOnlineStore.Tx tx,Subscription s,Instant now){
        tx.execute("INSERT INTO "+schema+".push_subscriptions(endpoint,owner,proof,binding,device_revision,p256dh,auth,preferences,active,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?::jsonb,?,?,?) ON CONFLICT(endpoint) DO UPDATE SET owner=EXCLUDED.owner,proof=EXCLUDED.proof,binding=EXCLUDED.binding,device_revision=EXCLUDED.device_revision,p256dh=EXCLUDED.p256dh,auth=EXCLUDED.auth,preferences=EXCLUDED.preferences,active=EXCLUDED.active,updated_at=EXCLUDED.updated_at",s.endpoint(),s.owner(),s.proof(),s.binding(),s.revision(),s.p256dh(),s.auth(),s.preferences(),s.active(),Timestamp.from(now),Timestamp.from(now));
    }
    void deactivate(JdbcOnlineStore.Tx tx,String endpoint,String binding,long revision,Instant now){tx.execute("UPDATE "+schema+".push_subscriptions SET active=false,device_revision=?,updated_at=? WHERE endpoint=? AND binding=?",revision,Timestamp.from(now),endpoint,binding);}
    Attempt attempt(JdbcOnlineStore.Tx tx,UUID id){
        tx.lock("push-test:"+id);
        List<Attempt> rows=tx.query("SELECT actor,endpoint,binding,device_revision,request_hash,status,reserved_at FROM "+schema+".push_tests WHERE id=?",r->new Attempt(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getLong(4),r.getString(5),r.getString(6),r.getTimestamp(7).toInstant()),id);
        return rows.isEmpty()?null:rows.getFirst();
    }
    boolean coolingDown(JdbcOnlineStore.Tx tx,UUID actor,String endpoint,Instant now){return !tx.query("SELECT 1 FROM "+schema+".push_tests WHERE (actor=? OR endpoint=?) AND reserved_at>? LIMIT 1",r->r.getInt(1),actor,endpoint,Timestamp.from(now.minusSeconds(60))).isEmpty();}
    void reserve(JdbcOnlineStore.Tx tx,UUID id,Attempt a,Instant now){tx.execute("INSERT INTO "+schema+".push_tests(id,actor,endpoint,binding,device_revision,request_hash,status,reserved_at) VALUES(?,?,?,?,?,?,?,?)",id,a.actor(),a.endpoint(),a.binding(),a.revision(),a.requestHash(),"pending",Timestamp.from(now));}
    void finish(JdbcOnlineStore.Tx tx,UUID id,String status){tx.execute("UPDATE "+schema+".push_tests SET status=? WHERE id=? AND status='pending'",status,id);}
    void recordEvent(JdbcOnlineStore.Tx tx,UUID requestId,UUID owner,UUID actor,String type,String metadata,Instant now){tx.execute("INSERT INTO "+schema+".push_events(id,owner,actor,request_id,type,metadata,created_at) VALUES(?,?,?,?,?,?::jsonb,?) ON CONFLICT(actor,request_id) DO NOTHING",UUID.randomUUID(),owner,actor,requestId,type,metadata,Timestamp.from(now));}
}

