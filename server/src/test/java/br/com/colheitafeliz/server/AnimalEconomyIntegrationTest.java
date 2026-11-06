package br.com.colheitafeliz.server;

import br.com.colheitafeliz.game.*;
import br.com.colheitafeliz.domain.animal.*;
import br.com.colheitafeliz.domain.economy.Currency;
import br.com.colheitafeliz.domain.inventory.ItemLot;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
class AnimalEconomyIntegrationTest {
    private final UUID actor=UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final Clock clock=Clock.fixed(Instant.parse("2026-11-06T12:00:00Z"),ZoneOffset.UTC);
    private JdbcOnlineStore store;
    private OnlineGameService service;
    private String schema;
    @BeforeEach void setup() {
        var url=System.getenv("TEST_DATABASE_URL");
        assertNotNull(url,"Disposable TEST_DATABASE_URL required");
        assertTrue((url.startsWith("jdbc:postgresql://127.0.0.1:")||url.startsWith("jdbc:postgresql://localhost:"))&&url.contains("/colheita_test"));
        schema="test_"+UUID.randomUUID().toString().replace("-","");
        store=new JdbcOnlineStore(url,"postgres","",schema);store.initialize(actor);
        service=new OnlineGameService(store,clock,bound->99,5);service.farm(actor);
    }
    @AfterEach void close(){if(store!=null)store.close();}
    private UUID request(){return RequestIds.issue(clock.instant());}
    private Command command(String action,String id){return new Command(request(),service.farm(actor).get("revision").getAsString(),action,id,null,null,null,null);}
    private void seed(String id,Currency currency,boolean vip,boolean legacy,boolean product) {
        store.transaction(actor,tx->{
            tx.lockFarms(actor);var saved=tx.farm(actor);
            var g=new GameState(clock::instant,clock,CropCatalogLoader.load(),bound->99);
            while(g.progress().level()<60)g.progress().award(g.progress().xpToNext());
            g.wallet().credit(Currency.GOLD,new BigDecimal("10000000"));g.wallet().credit(Currency.GREEN,new BigDecimal("10000"));
            if(vip)assertTrue(g.buyVip().success());
            var type=g.animalType(id);
            assertTrue(g.buyAnimal(type,currency).success()); // Actual acquisition captures v2.
            var origin=g.barn().remove(0).origin();
            if(legacy)origin=new ItemLot(id,currency,type.prices().get(currency),1,false);
            g.barn().add(new Animal(type,origin,clock.instant(),clock.instant(),type.requiredMillis(),clock.instant()));
            if(product)assertTrue(g.tendAnimal(id).success());
            g.dailyMissions().restore("2026-11-06",java.util.Map.of("care",1,"trade",1),java.util.List.of("care","trade"),LocalDate.of(2026,11,6));
            tx.save(actor,g.snapshot(),saved.revision()+1,clock.instant());return null;
        });
    }
    @ParameterizedTest
    @CsvSource({"galinha,8480,8600,9.2,9.4", "abelha,29200,29470,28.4,28.8",
            "pombo,40480,41560,37.4,38.6", "coelho,67440,69240,61.4,62.6",
            "pato,73000,73720,70.0,70.6", "pavao,106240,107710,101.0,102.0",
            "porco,164720,168320,104.8,107.2", "burro,169840,174700,106.4,109.6",
            "ovelha,186240,187740,122.0,123.0", "vaca,228160,230140,182.6,184.0"})
    void approvedServerPricesPersistAndReplayDoesNotPayTwice(String id,String gold,String vipGold,String green,String vipGreen) {
        for(var currency:Currency.values())for(boolean vip:new boolean[]{false,true}){
            seed(id,currency,vip,false,false);
            var farm=service.farm(actor);var field=currency==Currency.GOLD?"coins":"greenCoins";
            var expected=new BigDecimal(currency==Currency.GOLD?(vip?vipGold:gold):(vip?vipGreen:green));
            assertEquals(0,expected.compareTo(farm.getAsJsonArray("animals").get(0).getAsJsonObject().get("saleQuote").getAsBigDecimal()));
            var type=AnimalCatalogLoader.load().stream().filter(t->t.id().equals(id)).findFirst().orElseThrow();
            if(type.kind()==AnimalType.Kind.PRODUCER){
                assertTrue(service.command(actor,command("TEND_ANIMAL",id)).getAsJsonObject("result").get("success").getAsBoolean());
                farm=service.farm(actor);
                assertEquals(2,farm.getAsJsonArray("produceLots").get(0).getAsJsonObject().get("economyVersion").getAsInt());
            }
            var before=service.farm(actor).get(field).getAsBigDecimal();
            var sale=command(type.kind()==AnimalType.Kind.PRODUCER?"SELL_ITEM":"SELL_ANIMAL",type.kind()==AnimalType.Kind.PRODUCER?type.productId():id);
            var reply=service.command(actor,sale);assertTrue(reply.getAsJsonObject("result").get("success").getAsBoolean());
            assertEquals(reply,service.command(actor,sale));
            assertEquals(0,expected.compareTo(service.farm(actor).get(field).getAsBigDecimal().subtract(before)));
        }
    }
    @Test void concurrentSalesAndReplayAfterReopenPayOnlyOnce() throws Exception {
        seed("galinha",Currency.GREEN,false,false,true);
        var before=service.farm(actor).get("greenCoins").getAsBigDecimal();
        var one=command("SELL_ITEM","ovo");var two=command("SELL_ALL",null);
        var start=new java.util.concurrent.CyclicBarrier(2);
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var first=executor.submit(()->{start.await();return success(one);});
            var second=executor.submit(()->{start.await();return success(two);});
            assertEquals(1,(first.get()?1:0)+(second.get()?1:0));
        }
        assertEquals(0,new BigDecimal("9.2").compareTo(service.farm(actor).get("greenCoins").getAsBigDecimal().subtract(before)));
        var paid=service.farm(actor).get("greenCoins");
        store.close();store=new JdbcOnlineStore(System.getenv("TEST_DATABASE_URL"),"postgres","",schema);
        service=new OnlineGameService(store,clock,bound->99,5);
        try{service.command(actor,one);}catch(ApiFailure e){assertEquals(409,e.status());}
        try{service.command(actor,two);}catch(ApiFailure e){assertEquals(409,e.status());}
        assertEquals(paid,service.farm(actor).get("greenCoins"));
    }
    private boolean success(Command command){try{return service.command(actor,command).getAsJsonObject("result").get("success").getAsBoolean();}catch(ApiFailure e){assertEquals(409,e.status());return false;}}
    @Test void httpRejectsClientPricesAndCurrencyCannotChangeAnimalProceeds() throws Exception {
        seed("galinha",Currency.GREEN,false,false,true);
        var before=service.farm(actor);
        var sale=new Command(request(),before.get("revision").getAsString(),"SELL_ITEM","ovo","GOLD",null,null,null);
        try(var http=new GameHttpServer(new java.net.InetSocketAddress("127.0.0.1",0),service,
                header->new Authenticator.Identity(actor,"Test"),java.util.Set.of("http://localhost:5173"),true,clock);
            var client=java.net.http.HttpClient.newHttpClient()){
            http.start();var uri=java.net.URI.create("http://127.0.0.1:"+http.port()+"/v1/me/actions");
            for(String field:java.util.List.of("unitSale","saleQuote","animalSale","economyVersion")){
                var forged=OnlineViews.JSON.toJsonTree(sale).getAsJsonObject();forged.addProperty(field,999999);
                var request=java.net.http.HttpRequest.newBuilder(uri).header("Content-Type","application/json")
                        .POST(java.net.http.HttpRequest.BodyPublishers.ofString(forged.toString())).build();
                assertEquals(400,client.send(request,java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode());
            }
            assertEquals(before.get("greenCoins"),service.farm(actor).get("greenCoins"));
            assertTrue(service.command(actor,sale).getAsJsonObject("result").get("success").getAsBoolean());
            var after=service.farm(actor);assertEquals(before.get("coins"),after.get("coins"));
            assertEquals(0,new BigDecimal("9.2").compareTo(after.get("greenCoins").getAsBigDecimal().subtract(before.get("greenCoins").getAsBigDecimal())));
        }
    }
    @Test void rollbackRestoresProductMoneyAndReceiptThenRetryPaysOnce() {
        seed("vaca",Currency.GREEN,true,false,true);
        var before=service.farm(actor);var sale=command("SELL_ALL",null);
        assertThrows(IllegalStateException.class,()->store.transaction(actor,tx->{service.command(tx,actor,sale);throw new IllegalStateException("rollback animal sale");}));
        var after=service.farm(actor);assertEquals(before.get("greenCoins"),after.get("greenCoins"));assertEquals(before.get("produceLots"),after.get("produceLots"));
        var reply=service.command(actor,sale);assertEquals(reply,service.command(actor,sale));
        assertEquals(0,new BigDecimal("184.0").compareTo(service.farm(actor).get("greenCoins").getAsBigDecimal().subtract(before.get("greenCoins").getAsBigDecimal())));
    }
    @Test void previousRulesetAnimalAndProductRemainV0AfterNewBinaryLoadsAndCollects() {
        seed("galinha",Currency.GREEN,true,true,false);
        store.transaction(actor,tx->{tx.execute("UPDATE "+schema+".farms SET ruleset=? WHERE owner=?","economy-2026-11-plants-v1",actor);return null;});
        assertEquals("11.3",service.farm(actor).getAsJsonArray("animals").get(0).getAsJsonObject().get("saleQuote").getAsString());
        assertTrue(service.command(actor,command("TEND_ANIMAL","galinha")).getAsJsonObject("result").get("success").getAsBoolean());
        var before=service.farm(actor).get("greenCoins").getAsBigDecimal();service.command(actor,command("SELL_ALL",null));
        assertEquals(0,new BigDecimal("11.3").compareTo(service.farm(actor).get("greenCoins").getAsBigDecimal().subtract(before)));
        assertEquals("economy-2026-11-animals-v2",store.transaction(actor,tx->tx.query("SELECT ruleset FROM "+schema+".farms WHERE owner=?",r->r.getString(1),actor).getFirst()));
    }
    @Test void corruptJdbcTermsAreRejectedBeforeGsonCoercionAndLeaveDatabaseUntouched() {
        for(String corruption:java.util.List.of("fraction","string","missing","marker","price","currency")){
            seed("galinha",Currency.GOLD,false,false,true);
            String damaged=store.transaction(actor,tx->{
                var data=OnlineViews.JSON.toJsonTree(tx.farm(actor).snapshot()).getAsJsonObject();
                var lot=data.getAsJsonArray("produce").get(0).getAsJsonObject();var terms=lot.getAsJsonObject("animalSale");
                switch(corruption){
                    case "fraction" -> terms.addProperty("commonRevenueTenths",new BigDecimal("84800.5"));
                    case "string" -> terms.addProperty("vipRevenueTenths","86000");
                    case "missing" -> terms.remove("vipRevenueTenths");
                    case "marker" -> lot.remove("animalSale");
                    case "price" -> terms.addProperty("commonRevenueTenths",84801);
                    case "currency" -> lot.addProperty("currency","GREEN");
                    default -> throw new AssertionError();
                }
                tx.execute("UPDATE "+schema+".farms SET snapshot=?::jsonb WHERE owner=?",data.toString(),actor);return data.toString();
            });
            assertThrows(RuntimeException.class,()->service.farm(actor),corruption);
            var stored=store.transaction(actor,tx->tx.query("SELECT snapshot::text FROM "+schema+".farms WHERE owner=?",r->r.getString(1),actor).getFirst());
            assertEquals(com.google.gson.JsonParser.parseString(damaged),com.google.gson.JsonParser.parseString(stored));
            // Restore a valid fixture without reading the deliberately corrupt farm.
            store.transaction(actor,tx->{tx.execute("DELETE FROM "+schema+".farms WHERE owner=?",actor);return null;});service.farm(actor);
        }
    }
}
