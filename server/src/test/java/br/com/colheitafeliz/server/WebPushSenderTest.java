package br.com.colheitafeliz.server;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WebPushSenderTest {
    @Test void providerRetryAfterIsRespectedWithoutAutomaticRetries(){
        var now=java.time.Instant.parse("2026-11-13T12:00:00Z");
        assertEquals(now.plusSeconds(600),WebPushSender.retryDeadline("600",now));
        assertEquals(now.plusSeconds(600),WebPushSender.retryDeadline("Fri, 13 Nov 2026 12:10:00 GMT",now));
        assertEquals(now.plusSeconds(60),WebPushSender.retryDeadline("invalid",now));
    }
    @Test void missingOrInvalidKeysDoNotPreventApiStartup() {
        assertNull(WebPushSender.fromEnvironment(Map.of()));
        assertNull(WebPushSender.fromEnvironment(Map.of("VAPID_PUBLIC_KEY","invalid")));
        assertNull(WebPushSender.fromEnvironment(Map.of("VAPID_PUBLIC_KEY","invalid","VAPID_PRIVATE_KEY","invalid","VAPID_SUBJECT","mailto:test@example.com")));
    }
    @Test void publishedLibraryBuildsEncryptedVapidRequestWithoutNetwork() throws Exception {
        var generator=KeyPairGenerator.getInstance("EC");generator.initialize(new ECGenParameterSpec("secp256r1"));
        var pair=generator.generateKeyPair();var client=generator.generateKeyPair();
        String publicKey=encodePublic((ECPublicKey)pair.getPublic());
        byte[] scalar=unsigned(((ECPrivateKey)pair.getPrivate()).getS().toByteArray());
        var env=Map.of("VAPID_PUBLIC_KEY",publicKey,"VAPID_PRIVATE_KEY",encode(scalar),"VAPID_SUBJECT","mailto:test@example.com");
        try(var sender=WebPushSender.fromEnvironment(env)){
            assertNotNull(sender);assertEquals(publicKey,sender.publicKey());
            var request=sender.prepare("https://fcm.googleapis.com/wp/test",encodePublic((ECPublicKey)client.getPublic()),encode(new byte[16]),"generic test");
            assertEquals("aes128gcm",request.getHeaders().get("Content-Encoding"));
            assertTrue(request.getHeaders().get("Authorization").startsWith("vapid t="));
            assertEquals("60",request.getHeaders().get("TTL"));
            assertFalse(new String(request.getBody(),StandardCharsets.UTF_8).contains("generic test"));
            assertTrue(request.getBody().length>"generic test".length());
            assertThrows(ApiFailure.class,()->sender.prepare("https://127.0.0.1/test",encodePublic((ECPublicKey)client.getPublic()),encode(new byte[16]),"test"));
        }
    }
    private static String encodePublic(ECPublicKey key){byte[] result=new byte[65];result[0]=4;System.arraycopy(unsigned(key.getW().getAffineX().toByteArray()),0,result,1,32);System.arraycopy(unsigned(key.getW().getAffineY().toByteArray()),0,result,33,32);return encode(result);}
    private static byte[] unsigned(byte[] value){byte[] result=new byte[32];int length=Math.min(32,value.length);System.arraycopy(value,value.length-length,result,32-length,length);return result;}
    private static String encode(byte[] value){return Base64.getUrlEncoder().withoutPadding().encodeToString(value);}
}
