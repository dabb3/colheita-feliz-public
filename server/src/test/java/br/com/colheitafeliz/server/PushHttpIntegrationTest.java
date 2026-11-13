package br.com.colheitafeliz.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
class PushHttpIntegrationTest {
    @Test void migrationAndDeviceWritesWorkWithANonAdministratorDatabaseRole() {
        String url=System.getenv("TEST_DATABASE_URL");assertNotNull(url);assertTrue(url.startsWith("jdbc:postgresql://127.0.0.1:")&&url.contains("/colheita_test"));
        String suffix=UUID.randomUUID().toString().replace("-",""),role="push_role_"+suffix,schema="test_api_"+suffix;UUID actor=UUID.randomUUID();
        try(var admin=new JdbcOnlineStore(url,"postgres","","test_admin_"+suffix)){
            admin.transaction(actor,tx->{tx.execute("CREATE ROLE "+role+" LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE");tx.execute("GRANT CONNECT,CREATE ON DATABASE colheita_test TO "+role);return null;});
            try(var api=new JdbcOnlineStore(url,role,"",schema)){
                api.initialize(actor);api.initialize(actor);
                assertFalse(api.transaction(actor,tx->tx.query("SELECT rolsuper FROM pg_roles WHERE rolname=current_user",r->r.getBoolean(1)).getFirst()).booleanValue());
                var push=new PushService(api,Clock.systemUTC(),(e,k,a,p)->201,"public-key");var request=PushStoreIntegrationTest.subscription(1,1);
                push.subscribe(actor,request);var revoke=request.deepCopy();revoke.addProperty("deviceRevision",2);push.revoke(actor,revoke);
                assertEquals(1,api.transaction(actor,tx->tx.query("SELECT count(*) FROM "+schema+".push_subscriptions WHERE NOT active",r->r.getInt(1)).getFirst()).intValue());
            }
        }
    }
    @Test void routesAuthorizeAndRateLimitWithoutEconomicCommands() throws Exception {
        String url=System.getenv("TEST_DATABASE_URL");assertNotNull(url);assertTrue(url.startsWith("jdbc:postgresql://127.0.0.1:")&&url.contains("/colheita_test"));
        UUID owner=UUID.randomUUID(),other=UUID.randomUUID();String schema="test_"+UUID.randomUUID().toString().replace("-","");
        try(var store=new JdbcOnlineStore(url,"postgres","",schema);var client=HttpClient.newHttpClient()){
            store.initialize(owner);store.initialize(other);var sends=new AtomicInteger();
            var push=new PushService(store,Clock.systemUTC(),(endpoint,key,auth,payload)->{sends.incrementAndGet();return 201;},"public-key");
            Authenticator auth=token->{if("Bearer owner".equals(token))return new Authenticator.Identity(owner,"Private Name");if("Bearer other".equals(token))return new Authenticator.Identity(other,"Other Name");throw new ApiFailure(401,"Sessão inválida.");};
            try(var server=new GameHttpServer(new InetSocketAddress("127.0.0.1",0),null,auth,Set.of("https://game.example"),false,Clock.systemUTC(),false,push)){
                server.start();String base="http://127.0.0.1:"+server.port();
                var config=send(client,base,"/v1/push/config","owner",null);assertEquals(200,config.statusCode());assertFalse(config.body().contains("Private"));assertFalse(config.body().contains(owner.toString()));
                assertEquals(401,send(client,base,"/v1/push/config",null,null).statusCode());
                var subscription=PushStoreIntegrationTest.subscription(1,1);
                assertEquals(200,send(client,base,"/v1/push/subscribe","owner",subscription).statusCode());
                var attempt=PushStoreIntegrationTest.test(subscription);
                var accepted=send(client,base,"/v1/push/test","owner",attempt);assertEquals(200,accepted.statusCode());assertEquals("accepted",JsonParser.parseString(accepted.body()).getAsJsonObject().get("status").getAsString());
                assertEquals(200,send(client,base,"/v1/push/test","owner",attempt).statusCode());assertEquals(1,sends.get());
                assertEquals(403,send(client,base,"/v1/push/test","other",attempt).statusCode());
                var limited=send(client,base,"/v1/push/test","owner",PushStoreIntegrationTest.test(subscription));assertEquals(429,limited.statusCode());assertEquals("60",limited.headers().firstValue("Retry-After").orElseThrow());
                var malicious=attempt.deepCopy();malicious.addProperty("amount",100);assertEquals(400,send(client,base,"/v1/push/test","owner",malicious).statusCode());
                var blocked=client.send(HttpRequest.newBuilder(URI.create(base+"/v1/push/config")).header("Authorization","Bearer owner").header("Origin","https://bad.example").GET().build(),HttpResponse.BodyHandlers.ofString());assertEquals(403,blocked.statusCode());
                assertEquals(0,store.transaction(owner,tx->tx.query("SELECT count(*) FROM "+schema+".farms",r->r.getInt(1)).getFirst()).intValue());
                assertEquals(0,store.transaction(owner,tx->tx.query("SELECT count(*) FROM "+schema+".receipts",r->r.getInt(1)).getFirst()).intValue());
            }
        }
    }
    private static HttpResponse<String> send(HttpClient client,String base,String path,String actor,JsonObject body)throws Exception{
        var request=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(5)).header("Origin","https://game.example");
        if(actor!=null)request.header("Authorization","Bearer "+actor);
        if(body==null)request.GET();else request.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        return client.send(request.build(),HttpResponse.BodyHandlers.ofString());
    }
}
