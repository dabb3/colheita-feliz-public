package br.com.colheitafeliz.server;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Security;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import nl.martijndwars.webpush.AbstractPushService;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.Utils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

/** Uses the published library only for encryption/VAPID, never its old HTTP clients. */
final class WebPushSender implements PushService.Sender, AutoCloseable {
    private final Encoder encoder;
    private final String publicKey;
    private final HttpClient client;
    private final Semaphore concurrent=new Semaphore(2);
    private final AtomicReference<Instant> retryAfter=new AtomicReference<>(Instant.EPOCH);
    private final ThreadPoolExecutor dns;
    private WebPushSender(String publicKey,String privateKey,String subject) throws GeneralSecurityException {
        this.publicKey=publicKey;this.encoder=new Encoder(publicKey,privateKey,subject);
        if(!Utils.verifyKeyPair(encoder.getPrivateKey(),encoder.getPublicKey()))throw new IllegalArgumentException("Invalid VAPID key pair");
        this.client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
        this.dns=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(2),task->{var thread=new Thread(task,"push-dns");thread.setDaemon(true);return thread;},new ThreadPoolExecutor.AbortPolicy());
    }
    static WebPushSender fromEnvironment(Map<String,String> env){
        String publicKey=env.get("VAPID_PUBLIC_KEY"),privateKey=env.get("VAPID_PRIVATE_KEY"),subject=env.get("VAPID_SUBJECT");
        if(publicKey==null&&privateKey==null&&subject==null)return null;
        try{
            PushValidation.publicKey(publicKey);PushValidation.encoded(privateKey,32);
            if(subject==null||subject.length()>200||!subject.matches("mailto:[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9.-]+"))throw new IllegalArgumentException("Invalid VAPID subject");
            if(Security.getProvider("BC")==null)Security.addProvider(new BouncyCastleProvider());
            return new WebPushSender(publicKey,privateKey,subject);
        }catch(GeneralSecurityException|IllegalArgumentException|ApiFailure failure){
            System.err.println("Web Push disabled: invalid or incomplete configuration");return null;
        }
    }
    String publicKey(){return publicKey;}
    nl.martijndwars.webpush.HttpRequest prepare(String endpoint,String p256dh,String auth,String payload) throws Exception {
        PushValidation.validateEndpoint(endpoint);PushValidation.publicKey(p256dh);PushValidation.encoded(auth,16);
        if(payload==null||payload.getBytes(StandardCharsets.UTF_8).length>1024)throw new ApiFailure(400,"Mensagem inválida.");
        var request=encoder.encode(new Notification(endpoint,p256dh,auth,payload.getBytes(StandardCharsets.UTF_8),60));
        PushValidation.validateEndpoint(request.getUrl());return request;
    }
    @Override public int send(String endpoint,String p256dh,String auth,String payload) throws Exception {
        if(Instant.now().isBefore(retryAfter.get())||!concurrent.tryAcquire())throw new ApiFailure(503,"Envio temporariamente indisponível.");
        try{
            var prepared=prepare(endpoint,p256dh,auth,payload);var uri=URI.create(prepared.getUrl());
            var resolution=dns.submit(()->InetAddress.getAllByName(uri.getHost()));
            try{PushValidation.validateAddresses(resolution.get(2,TimeUnit.SECONDS));}finally{resolution.cancel(true);}
            var builder=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).POST(HttpRequest.BodyPublishers.ofByteArray(prepared.getBody()));
            prepared.getHeaders().forEach(builder::header);
            var response=client.send(builder.build(),HttpResponse.BodyHandlers.discarding());
            if(response.statusCode()==429||response.statusCode()==503){
                var until=retryDeadline(response.headers().firstValue("Retry-After").orElse("60"),Instant.now());
                retryAfter.accumulateAndGet(until,(a,b)->a.isAfter(b)?a:b);
            }
            return response.statusCode();
        }finally{concurrent.release();}
    }
    static Instant retryDeadline(String value,Instant now){
        try{long seconds=Long.parseLong(value);return now.plusSeconds(Math.max(60,Math.min(86400,seconds)));}
        catch(NumberFormatException ignored){
            try{var deadline=ZonedDateTime.parse(value,DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();return deadline.isAfter(now)?deadline:now.plusSeconds(60);}
            catch(java.time.format.DateTimeParseException invalid){return now.plusSeconds(60);}
        }
    }
    @Override public void close(){dns.shutdownNow();client.shutdownNow();}
    private static final class Encoder extends AbstractPushService<Encoder>{
        Encoder(String publicKey,String privateKey,String subject)throws GeneralSecurityException{super(publicKey,privateKey,subject);}
        nl.martijndwars.webpush.HttpRequest encode(Notification notification)throws Exception{return prepareRequest(notification,Encoding.AES128GCM);}
    }
}
