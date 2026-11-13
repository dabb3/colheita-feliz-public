package br.com.colheitafeliz.server;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URI;
import java.security.KeyStore;
import java.util.ArrayList;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.concurrent.Executors;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.TrustManagerFactory;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.io.DetachedSocketFactory;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.ContentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PinnedPushTransportTest {
    @TempDir Path temporary;
    @Test void actualSocketOnlyReceivesTheValidatedSnapshotEvenIfAnswersChange() throws Exception {
        var publicAddress=InetAddress.getByAddress(new byte[]{93,(byte)184,(byte)216,34});
        var answers=new InetAddress[]{publicAddress};var dialed=new ArrayList<SocketAddress>();
        DetachedSocketFactory sockets=proxy->new Socket(Proxy.NO_PROXY){
            @Override public void connect(SocketAddress destination,int timeout)throws IOException{dialed.add(destination);throw new IOException("Test stops before network");}
        };
        var uri=URI.create("https://fcm.googleapis.com/wp/test");
        try(var client=PinnedPushTransport.client(uri,answers,sockets,SSLContext.getDefault())){
            // Simulates a changed DNS answer after validation; it must not affect the dial.
            answers[0]=InetAddress.getByAddress(new byte[]{127,0,0,1});
            assertThrows(IOException.class,()->client.execute(new HttpPost(uri),response->response.getCode()));
        }
        assertEquals(1,dialed.size());assertEquals(new InetSocketAddress(publicAddress,443),dialed.getFirst());
    }
    @Test void privateOrMixedDnsAnswersAreRejectedBeforeAnySocketIsCreated()throws Exception{
        var publicAddress=InetAddress.getByAddress(new byte[]{93,(byte)184,(byte)216,34});
        var privateAddress=InetAddress.getByAddress(new byte[]{127,0,0,1});
        DetachedSocketFactory sockets=proxy->{throw new AssertionError("No socket allowed");};
        assertThrows(ApiFailure.class,()->PinnedPushTransport.client(URI.create("https://fcm.googleapis.com/wp/test"),new InetAddress[]{publicAddress,privateAddress},sockets,SSLContext.getDefault()));
    }
    @Test void pinnedConnectionStillUsesOriginalTlsHostnameAndRejectsAnotherHostname() throws Exception {
        var contexts=testContexts();
        assertEquals(204,localTlsRequest("https://fcm.googleapis.com/wp/test",contexts[0],contexts[1]));
        assertThrows(SSLException.class,()->localTlsRequest("https://web.push.apple.com/test",contexts[0],contexts[1]));
    }
    @Test void slowHeadersCannotExtendTheAbsoluteRequestDeadline()throws Exception{
        var contexts=testContexts();
        try(var server=(SSLServerSocket)contexts[0].getServerSocketFactory().createServerSocket(0,1,InetAddress.getLoopbackAddress());var threads=Executors.newSingleThreadExecutor()){
            server.setSoTimeout(3000);
            var serving=threads.submit(()->{try(var socket=server.accept()){
                socket.setSoTimeout(3000);var input=socket.getInputStream();int tail=0;
                while(tail!=0x0d0a0d0a){int value=input.read();if(value<0)return;tail=(tail<<8)|value;}
                var output=socket.getOutputStream();output.write("HTTP/1.1 204 No Content\r\nX-Slow: ".getBytes(java.nio.charset.StandardCharsets.US_ASCII));output.flush();
                for(int i=0;i<60;i++){output.write('x');output.flush();try{Thread.sleep(50);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();return;}}
            }catch(IOException expected){/* Deadline closes the local socket. */}});
            DetachedSocketFactory routed=proxy->new Socket(Proxy.NO_PROXY){@Override public void connect(SocketAddress destination,int timeout)throws IOException{super.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(),server.getLocalPort()),timeout);}};
            var uri=URI.create("https://fcm.googleapis.com/wp/test");
            try(var client=PinnedPushTransport.client(uri,new InetAddress[]{InetAddress.getByAddress(new byte[]{93,(byte)184,(byte)216,34})},routed,contexts[1])){
                long started=System.nanoTime();
                assertThrows(IOException.class,()->PinnedPushTransport.execute(client,new HttpPost(uri),java.time.Duration.ofMillis(300)));
                assertTrue(java.time.Duration.ofNanos(System.nanoTime()-started).toMillis()<2000,"Slow headers escaped the deadline");
            }finally{serving.get(4,java.util.concurrent.TimeUnit.SECONDS);}
        }
    }
    private int localTlsRequest(String endpoint,SSLContext serverContext,SSLContext clientContext)throws Exception{
        var pinned=InetAddress.getByAddress(new byte[]{93,(byte)184,(byte)216,34});var captured=new ArrayList<SocketAddress>();
        try(var server=(SSLServerSocket)serverContext.getServerSocketFactory().createServerSocket(0,1,InetAddress.getLoopbackAddress());var threads=Executors.newSingleThreadExecutor()){
            server.setSoTimeout(3000);
            var serving=threads.submit(()->{try(var socket=server.accept()){
                socket.setSoTimeout(3000);var input=socket.getInputStream();int tail=0;
                while(tail!=0x0d0a0d0a){int value=input.read();if(value<0)return;tail=(tail<<8)|value;}
                socket.getOutputStream().write("HTTP/1.1 204 No Content\r\nConnection: close\r\n\r\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            }catch(IOException expected){/* Hostname mismatch closes the test TLS socket. */}});
            DetachedSocketFactory routed=proxy->new Socket(Proxy.NO_PROXY){
                @Override public void connect(SocketAddress destination,int timeout)throws IOException{
                    captured.add(destination);super.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(),server.getLocalPort()),timeout);
                }
            };
            var uri=URI.create(endpoint);
            try(var client=PinnedPushTransport.client(uri,new InetAddress[]{pinned},routed,clientContext)){
                var post=new HttpPost(uri);post.setEntity(new ByteArrayEntity(new byte[0],ContentType.APPLICATION_OCTET_STREAM));
                try{return client.execute(post,response->response.getCode());}
                finally{assertEquals(new InetSocketAddress(pinned,443),captured.getFirst());serving.get(4,java.util.concurrent.TimeUnit.SECONDS);}
            }
        }
    }
    private SSLContext[] testContexts()throws Exception{
        Path keys=temporary.resolve("fixture.p12");String executable=System.getProperty("os.name").startsWith("Windows")?"keytool.exe":"keytool";
        // Ephemeral test-only keys, never written to the repository or used by a real provider.
        var process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin",executable).toString(),"-genkeypair","-alias","fixture","-keyalg","EC","-groupname","secp256r1","-dname","CN=fcm.googleapis.com","-ext","SAN=dns:fcm.googleapis.com","-validity","1","-keystore",keys.toString(),"-storetype","PKCS12","-storepass","fixture-only","-keypass","fixture-only","-noprompt").redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        assertTrue(process.waitFor(10,java.util.concurrent.TimeUnit.SECONDS));assertEquals(0,process.exitValue());
        var store=KeyStore.getInstance("PKCS12");try(var input=Files.newInputStream(keys)){store.load(input,"fixture-only".toCharArray());}
        var managers=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());managers.init(store,"fixture-only".toCharArray());
        var trust=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());trust.init(store);
        var server=SSLContext.getInstance("TLS");server.init(managers.getKeyManagers(),null,null);
        var client=SSLContext.getInstance("TLS");client.init(null,trust.getTrustManagers(),null);
        return new SSLContext[]{server,client};
    }
}
