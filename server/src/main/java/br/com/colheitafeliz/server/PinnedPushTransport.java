package br.com.colheitafeliz.server;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.SSLContext;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.SchemePortResolver;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.DefaultSchemePortResolver;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.DefaultHttpClientConnectionOperator;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.impl.routing.DefaultRoutePlanner;
import org.apache.hc.client5.http.io.DetachedSocketFactory;
import org.apache.hc.client5.http.io.HttpClientConnectionOperator;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.http.ssl.DefaultHostnameVerifier;
import org.apache.hc.client5.http.ssl.HostnameVerificationPolicy;
import org.apache.hc.client5.http.ssl.TlsSocketStrategy;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.Timeout;

/** Connect only to an immutable, validated DNS snapshot; retain the original HTTPS identity. */
final class PinnedPushTransport {
    private PinnedPushTransport() {}
    record Response(int status,String retryAfter) {}
    static Response execute(nl.martijndwars.webpush.HttpRequest prepared,InetAddress[] addresses)throws IOException {
        URI uri=URI.create(prepared.getUrl());
        try(var client=client(uri,addresses,proxy->new Socket(Proxy.NO_PROXY),SSLContexts.createDefault())){
            var post=new HttpPost(uri);prepared.getHeaders().forEach(post::setHeader);
            post.setEntity(new ByteArrayEntity(prepared.getBody(),ContentType.APPLICATION_OCTET_STREAM));
            return execute(client,post,Duration.ofSeconds(5));
        }
    }
    static Response execute(CloseableHttpClient client,HttpPost post,Duration deadline)throws IOException{
        var timer=new ScheduledThreadPoolExecutor(1,task->{var thread=new Thread(task,"push-request-deadline");thread.setDaemon(true);return thread;});
        var expired=new AtomicBoolean();
        var abort=timer.schedule(()->{expired.set(true);post.cancel();client.close(CloseMode.IMMEDIATE);},deadline.toMillis(),TimeUnit.MILLISECONDS);
        try{
            try(var response=client.executeOpen(null,post,null)){
                try{
                    if(expired.get())throw new java.net.SocketTimeoutException("Push request timed out");
                    var retry=response.getFirstHeader("Retry-After");
                    return new Response(response.getCode(),retry==null?"60":retry.getValue());
                }finally{
                    // Close before the response resource: do not drain or reuse its body/socket.
                    client.close(CloseMode.IMMEDIATE);
                }
            }
        }finally{abort.cancel(false);timer.shutdownNow();}
    }
    static CloseableHttpClient client(URI uri,InetAddress[] answers,DetachedSocketFactory sockets,SSLContext trust){
        PushValidation.validateEndpoint(uri.toString());
        var snapshot=answers.clone();PushValidation.validateAddresses(snapshot);
        // Bound connection failover; every retained address has passed the same validation.
        var pinned=Arrays.copyOf(snapshot,Math.min(2,snapshot.length));String expectedHost=uri.getHost();
        DnsResolver resolver=new DnsResolver(){
            @Override public InetAddress[] resolve(String host)throws UnknownHostException{check(host);return pinned.clone();}
            @Override public String resolveCanonicalHostname(String host)throws UnknownHostException{check(host);return expectedHost;}
            private void check(String host)throws UnknownHostException{if(!expectedHost.equals(host))throw new UnknownHostException("Unexpected push host");}
        };
        var builder=new PoolingHttpClientConnectionManagerBuilder(){
            @Override protected HttpClientConnectionOperator createConnectionOperator(SchemePortResolver ports,DnsResolver dns,TlsSocketStrategy tls){
                return new DefaultHttpClientConnectionOperator(sockets,ports,dns,RegistryBuilder.<TlsSocketStrategy>create().register("https",tls).build());
            }
        };
        var manager=builder.setDnsResolver(resolver)
            .setTlsSocketStrategy(new DefaultClientTlsStrategy(trust,HostnameVerificationPolicy.BOTH,new DefaultHostnameVerifier()))
            .setMaxConnTotal(1).setMaxConnPerRoute(1)
            .setDefaultConnectionConfig(ConnectionConfig.custom().setConnectTimeout(Timeout.ofSeconds(2)).setSocketTimeout(Timeout.ofSeconds(3)).build())
            .setDefaultTlsConfig(TlsConfig.custom().setHandshakeTimeout(Timeout.ofSeconds(3)).build()).build();
        return HttpClients.custom().setConnectionManager(manager)
            .setRoutePlanner(new DefaultRoutePlanner(DefaultSchemePortResolver.INSTANCE))
            .setDefaultRequestConfig(RequestConfig.custom().setConnectionRequestTimeout(Timeout.ofSeconds(1)).setResponseTimeout(Timeout.ofSeconds(3)).build())
            .disableAutomaticRetries().disableRedirectHandling().disableCookieManagement().disableContentCompression().build();
    }
}
