package br.com.colheitafeliz.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.UUID;

/** Authenticated device operations isolated from game actions. */
final class PushService {
    @FunctionalInterface interface Sender { int send(String endpoint,String p256dh,String auth,String payload) throws Exception; }
    private final JdbcOnlineStore store;
    private final PushStore push;
    private final Clock clock;
    private final Sender sender;
    private final String publicKey;
    PushService(JdbcOnlineStore store,Clock clock,Sender sender,String publicKey){this.store=store;this.push=store.pushStore();this.clock=clock;this.sender=sender;this.publicKey=publicKey;}
    JsonObject config(UUID actor){return store.transaction(actor,tx->{active(tx,actor);var result=new JsonObject();result.addProperty("enabled",publicKey!=null);if(publicKey==null)result.add("publicKey",com.google.gson.JsonNull.INSTANCE);else result.addProperty("publicKey",publicKey);for(String key:new String[]{"automaticEventsEnabled","plantEventsEnabled","animalEventsEnabled","theftEventsEnabled","pestEventsEnabled"})result.addProperty(key,publicKey!=null);return result;});}
    JsonObject subscribe(UUID actor,JsonObject request){
        PushValidation.validateSubscription(request);
        String endpoint=text(request,"endpoint"),proof=hash(PushValidation.token(text(request,"deviceToken"))),binding=PushValidation.binding(text(request,"bindingId"));
        long revision=PushValidation.revision(request);
        var keys=request.getAsJsonObject("keys");var preferences=new JsonObject();
        for(String key:new String[]{"plants","animals","thefts","pests"})preferences.addProperty(key,request.getAsJsonObject("preferences").get(key).getAsBoolean());
        var candidate=new PushStore.Subscription(actor,endpoint,proof,binding,revision,text(keys,"p256dh"),text(keys,"auth"),preferences.toString(),true);
        return store.transaction(actor,tx->{
            active(tx,actor);enabled();tx.lock("push-subscriptions");var old=push.find(tx,endpoint);
            if(old!=null){
                possession(old,proof);if(revision<old.revision())throw conflict();
                if(revision==old.revision()){
                    if(!same(old,candidate))throw conflict();return status("subscribed");
                }
            }
            if((old==null||!old.owner().equals(actor))&&push.totalCount(tx,actor,clock.instant())>=50)throw new ApiFailure(409,"Limite de registros de dispositivos atingido.");
            if((old==null||!old.active()||!old.owner().equals(actor))&&push.activeCount(tx,actor)>=5)throw new ApiFailure(409,"Limite de dispositivos atingido.");
            tx.lockFarms(actor,old==null?actor:old.owner());
            push.save(tx,candidate,clock.instant());store.cropPushStore().reconcileSubscription(tx,actor,clock.instant());
            store.socialPushStore().reconcile(tx,actor);
            if(old!=null&&!old.owner().equals(actor))disableCropJob(tx,old.owner());
            return status("subscribed");
        });
    }
    JsonObject revoke(UUID actor,JsonObject request){
        String endpoint=text(request,"endpoint");PushValidation.validateEndpoint(endpoint);String proof=hash(PushValidation.token(text(request,"deviceToken")));long revision=PushValidation.revision(request);
        return store.transaction(actor,tx->{
            active(tx,actor);tx.lock("push-subscriptions");var old=push.find(tx,endpoint);
            if(old==null){if(push.totalCount(tx,actor,clock.instant())>=50)throw new ApiFailure(409,"Limite de registros de dispositivos atingido.");push.save(tx,new PushStore.Subscription(actor,endpoint,proof,"",revision,"","","{}",false),clock.instant());return status("revoked");}
            possession(old,proof);if(revision<old.revision()||(revision==old.revision()&&old.active()))throw conflict();
            push.deactivate(tx,endpoint,old.binding(),revision,clock.instant());disableCropJob(tx,old.owner());return status("revoked");
        });
    }
    private void disableCropJob(JdbcOnlineStore.Tx tx,UUID owner){
        store.cropPushStore().suspendIfIneligible(tx,owner);
        store.socialPushStore().reconcile(tx,owner);
    }
    JsonObject preferences(UUID actor,JsonObject request){
        String endpoint=text(request,"endpoint");PushValidation.validateEndpoint(endpoint);
        String proof=hash(PushValidation.token(text(request,"deviceToken"))),binding=PushValidation.binding(text(request,"bindingId"));
        long revision=PushValidation.revision(request);
        String writeKey=null;for(String key:new String[]{"plants","animals","thefts","pests"})if(request.has(key)){if(writeKey!=null)throw new ApiFailure(400,"Escolha apenas uma preferência.");writeKey=key;if(!request.get(key).isJsonPrimitive()||!request.get(key).getAsJsonPrimitive().isBoolean())throw new ApiFailure(400,"Preferência inválida.");}
        final String preferenceKey=writeKey;
        return store.transaction(actor,tx->{
            active(tx,actor);tx.lock("push-subscriptions");tx.lockFarms(actor);var old=push.find(tx,endpoint);
            if(old==null)throw new ApiFailure(403,"Dispositivo não autorizado.");possession(old,proof);
            if(!old.active()||!old.owner().equals(actor)||!old.binding().equals(binding))throw new ApiFailure(403,"Dispositivo não autorizado.");
            var prefs=JsonParser.parseString(old.preferences()).getAsJsonObject();
            if(preferenceKey==null){if(revision!=old.revision())throw conflict();}
            else{
                boolean desired=request.get(preferenceKey).getAsBoolean();boolean current=old.preferenceEnabled(preferenceKey);boolean confirmed=PushStore.hasConfirmation(prefs,preferenceKey);
                if(revision<old.revision()||revision>old.revision()+1||(revision==old.revision()&&(!confirmed||current!=desired)))throw conflict();
                if(revision>old.revision()){
                    store.cropPushStore().suspendIfIneligible(tx,actor);
                    prefs.addProperty(preferenceKey,desired);prefs.addProperty(preferenceKey+"Confirmed",true);
                    push.save(tx,new PushStore.Subscription(actor,endpoint,old.proof(),binding,revision,old.p256dh(),old.auth(),prefs.toString(),true),clock.instant());
                    store.socialPushStore().reconcile(tx,actor);
                    store.cropPushStore().reconcileSubscription(tx,actor,clock.instant());
                }
            }
            var result=new JsonObject();for(String key:new String[]{"plants","animals","thefts","pests"})result.addProperty(key,PushStore.isConfirmed(prefs,key));result.addProperty("deviceRevision",revision);result.addProperty("bindingId",binding);return result;
        });
    }
    private record Reservation(PushStore.Subscription subscription,String result) {}
    JsonObject test(UUID actor,JsonObject request){
        String endpoint=text(request,"endpoint");PushValidation.validateEndpoint(endpoint);
        String proof=hash(PushValidation.token(text(request,"deviceToken"))),binding=PushValidation.binding(text(request,"bindingId"));long revision=PushValidation.revision(request);
        UUID testId;try{String raw=text(request,"testId");testId=UUID.fromString(raw);if(!testId.toString().equals(raw))throw new IllegalArgumentException();}catch(IllegalArgumentException e){throw new ApiFailure(400,"Identificador de teste inválido.");}
        String requestHash=hash(actor+"\n"+endpoint+"\n"+proof+"\n"+binding+"\n"+revision);
        Reservation reserved=store.transaction(actor,tx->{
            active(tx,actor);enabled();var current=push.find(tx,endpoint);
            if(current==null)throw new ApiFailure(403,"Dispositivo não autorizado.");possession(current,proof);
            if(!current.owner().equals(actor)||!current.binding().equals(binding))throw new ApiFailure(403,"Dispositivo não autorizado.");
            if(current.revision()!=revision)throw conflict();
            var previous=push.attempt(tx,testId);
            if(previous!=null){if(!previous.requestHash().equals(requestHash))throw conflict();if(previous.status().equals("pending")&&!clock.instant().isBefore(previous.reservedAt().plusSeconds(60)))push.finish(tx,testId,"unavailable");return new Reservation(null,push.attempt(tx,testId).status());}
            if(!current.active())throw new ApiFailure(409,"Inscrição desativada.");
            if(push.coolingDown(tx,actor,endpoint,clock.instant()))throw new ApiFailure(429,"Aguarde antes de testar novamente.");
            push.reserve(tx,testId,new PushStore.Attempt(actor,endpoint,binding,revision,requestHash,"pending",clock.instant()),clock.instant());return new Reservation(current,null);
        });
        if(reserved.result()!=null)return status(reserved.result());
        String result;
        try{var s=reserved.subscription();result=deliveryStatus(sender.send(s.endpoint(),s.p256dh(),s.auth(),payload(s.binding()).toString()));}
        catch(Exception failure){if(failure instanceof InterruptedException)Thread.currentThread().interrupt();result="unavailable";}
        String finalResult=result;
        String persistedResult=store.transaction(actor,tx->{
            tx.lock("push-subscriptions");
            var current=push.find(tx,endpoint);
            var attempt=push.attempt(tx,testId);
            if(!attempt.status().equals("pending"))return attempt.status();
            if(finalResult.equals("expired")&&current!=null&&current.binding().equals(binding)&&current.revision()==revision){push.deactivate(tx,endpoint,binding,revision,clock.instant());disableCropJob(tx,current.owner());}
            push.finish(tx,testId,finalResult);return push.attempt(tx,testId).status();
        });
        return status(persistedResult);
    }
    private static boolean same(PushStore.Subscription a,PushStore.Subscription b){
        var preferences=JsonParser.parseString(a.preferences()).getAsJsonObject();
        // A registration replay cannot grant or erase the later, explicit preference consent.
        preferences.remove("plantsConfirmed");
        preferences.remove("animalsConfirmed");
        preferences.remove("theftsConfirmed");
        preferences.remove("pestsConfirmed");
        return a.active()&&a.owner().equals(b.owner())&&a.binding().equals(b.binding())&&a.p256dh().equals(b.p256dh())&&a.auth().equals(b.auth())&&preferences.equals(JsonParser.parseString(b.preferences()));
    }
    private static void possession(PushStore.Subscription old,String proof){if(!MessageDigest.isEqual(old.proof().getBytes(StandardCharsets.US_ASCII),proof.getBytes(StandardCharsets.US_ASCII)))throw new ApiFailure(403,"Dispositivo não autorizado.");}
    private static void active(JdbcOnlineStore.Tx tx,UUID actor){var player=tx.player(actor);if(player==null||!player.active())throw new ApiFailure(403,"Jogador indisponível.");}
    private void enabled(){if(publicKey==null)throw new ApiFailure(503,"Notificações indisponíveis.");}
    private static ApiFailure conflict(){return new ApiFailure(409,"Vinculação do dispositivo desatualizada.");}
    private static String text(JsonObject object,String field){if(object==null||!object.has(field)||!object.get(field).isJsonPrimitive()||!object.get(field).getAsJsonPrimitive().isString())throw new ApiFailure(400,"Dados do dispositivo inválidos.");return object.get(field).getAsString();}
    static String hash(String input){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException("SHA-256 unavailable",e);}}
    static JsonObject payload(String binding){var result=new JsonObject();result.addProperty("type","TEST");result.addProperty("bindingId",binding);result.addProperty("title","Colheita Feliz");result.addProperty("body","As notificações deste dispositivo estão funcionando.");return result;}
    static String deliveryStatus(int code){return code>=200&&code<300?"accepted":code==404||code==410?"expired":"unavailable";}
    private static JsonObject status(String value){var result=new JsonObject();result.addProperty("status",value);return result;}
}



