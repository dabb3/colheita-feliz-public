package br.com.colheitafeliz.server;

import com.google.gson.JsonParser;
import java.net.InetAddress;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PushValidationTest {
    @Test void endpointsOnlyAllowKnownHttpsPushProviders() {
        for (String endpoint : new String[]{"https://fcm.googleapis.com/wp/test", "https://updates.push.services.mozilla.com/wpush/v2/test", "https://web.push.apple.com/test", "https://wns2-test.notify.windows.com/w/?token=example"})
            assertDoesNotThrow(() -> PushValidation.validateEndpoint(endpoint));
        for (String endpoint : new String[]{"http://fcm.googleapis.com/wp/test", "https://localhost/test", "https://127.0.0.1/test", "https://[::1]/test", "https://fcm.googleapis.com.evil.test/wp/test", "https://evil@fcm.googleapis.com/wp/test", "https://fcm.googleapis.com:444/wp/test", "https://fcm.googleapis.com/wp/test#fragment", "https://fcm.googleapis.com/other-api", "https://evil.test/path"})
            assertThrows(ApiFailure.class, () -> PushValidation.validateEndpoint(endpoint), endpoint);
    }
    @Test void canonicalSecretsAndRevisionRejectMalformedOrOversizedInputs() {
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
        assertEquals(token,PushValidation.token(token));
        assertThrows(ApiFailure.class,()->PushValidation.token("known-id"));
        assertThrows(ApiFailure.class,()->PushValidation.binding(token+"="));
        assertEquals(1,PushValidation.revision(JsonParser.parseString("{\"deviceRevision\":1}").getAsJsonObject()));
        for(String raw:new String[]{"0","-1","1.5","1e2","9007199254740992","\"1\"","null"})
            assertThrows(ApiFailure.class,()->PushValidation.revision(JsonParser.parseString("{\"deviceRevision\":"+raw+"}").getAsJsonObject()));
    }
    @Test void dnsResultsMustBePublicIncludingIpv6() throws Exception {
        for(String ip:new String[]{"127.0.0.1","10.0.0.1","192.168.1.1","169.254.169.254","100.64.0.1","::1","fd00::1","fe80::1","0.0.0.0","224.0.0.1"}){
            var address=InetAddress.getByName(ip);
            assertThrows(ApiFailure.class,()->PushValidation.validateAddresses(new InetAddress[]{address}));
        }
        PushValidation.validateAddresses(new InetAddress[]{InetAddress.getByName("8.8.8.8"),InetAddress.getByName("2606:4700:4700::1111")});
    }
}
