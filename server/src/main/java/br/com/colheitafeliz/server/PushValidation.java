package br.com.colheitafeliz.server;

import com.google.gson.JsonObject;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.URI;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.spec.ECFieldFp;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;

/** Strict boundary for untrusted browser subscriptions. No DNS or network during registration. */
final class PushValidation {
    private PushValidation() {}
    static void validateSubscription(JsonObject request) {
        fields(request,Set.of("endpoint","keys","deviceToken","bindingId","deviceRevision","preferences"));
        validateEndpoint(text(request,"endpoint"));token(text(request,"deviceToken"));binding(text(request,"bindingId"));revision(request);
        var keys=object(request,"keys");fields(keys,Set.of("p256dh","auth"));
        publicKey(text(keys,"p256dh"));encoded(text(keys,"auth"),16);
        var preferences=object(request,"preferences");fields(preferences,Set.of("plants","animals","thefts","pests"));
        for(String key:preferences.keySet())if(!preferences.get(key).isJsonPrimitive()||!preferences.getAsJsonPrimitive(key).isBoolean())throw invalid();
    }
    static void validateEndpoint(String endpoint) {
        if(endpoint==null||endpoint.length()>4096)throw invalid();
        final URI uri;
        try{uri=URI.create(endpoint);}catch(IllegalArgumentException e){throw invalid();}
        String host=uri.getHost(),path=uri.getRawPath();
        if(!"https".equals(uri.getScheme())||host==null||uri.getUserInfo()!=null||uri.getFragment()!=null||uri.getPort()!=-1||path==null||path.length()<2||endpoint.contains("\\")||path.contains(".."))throw invalid();
        boolean google=host.equals("fcm.googleapis.com")&&(path.startsWith("/wp/")||path.startsWith("/fcm/send/"));
        boolean mozilla=(host.equals("updates.push.services.mozilla.com")||host.matches("[a-z0-9-]+\\.push\\.services\\.mozilla\\.com"))&&path.startsWith("/wpush/v2/");
        boolean apple=host.equals("web.push.apple.com");
        boolean windows=host.matches("[a-z0-9-]+\\.notify\\.windows\\.com")&&path.equals("/w/");
        if(!(google||mozilla||apple||windows))throw invalid();
    }
    static String token(String value){encoded(value,32);return value;}
    static String binding(String value){return token(value);}
    static long revision(JsonObject request){
        var value=request.get("deviceRevision");
        if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber()||!value.toString().matches("[1-9][0-9]{0,15}"))throw invalid();
        try{long number=value.getAsLong();if(number>9_007_199_254_740_991L)throw invalid();return number;}catch(NumberFormatException e){throw invalid();}
    }
    static void publicKey(String value){
        byte[] key=encoded(value,65);if(key[0]!=4)throw invalid();
        // Validate the uncompressed point on the JDK's named curve; no custom encryption.
        try{
            var parameters=AlgorithmParameters.getInstance("EC");parameters.init(new ECGenParameterSpec("secp256r1"));
            var curve=parameters.getParameterSpec(ECParameterSpec.class).getCurve();var prime=((ECFieldFp)curve.getField()).getP();
            var x=new BigInteger(1,Arrays.copyOfRange(key,1,33));var y=new BigInteger(1,Arrays.copyOfRange(key,33,65));
            if(x.compareTo(prime)>=0||y.compareTo(prime)>=0||!y.multiply(y).mod(prime).equals(x.pow(3).add(curve.getA().multiply(x)).add(curve.getB()).mod(prime)))throw invalid();
        }catch(GeneralSecurityException e){throw new IllegalStateException("P-256 unavailable");}
    }
    static byte[] encoded(String value,int length){
        if(value==null||value.length()>100||!value.matches("[A-Za-z0-9_-]+"))throw invalid();
        try{byte[] bytes=Base64.getUrlDecoder().decode(value);if(bytes.length!=length||!Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(value))throw invalid();return bytes;}
        catch(IllegalArgumentException e){throw invalid();}
    }
    static String text(JsonObject object,String key){var value=object.get(key);if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString())throw invalid();return value.getAsString();}
    private static JsonObject object(JsonObject object,String key){var value=object.get(key);if(value==null||!value.isJsonObject())throw invalid();return value.getAsJsonObject();}
    private static void fields(JsonObject object,Set<String> fields){if(!object.keySet().equals(fields))throw invalid();}
    static void validateAddresses(InetAddress[] addresses){
        if(addresses.length==0)throw invalid();
        for(var address:addresses){
            byte[] b=address.getAddress();int a=b[0]&255,second=b[1]&255;
            if(address.isAnyLocalAddress()||address.isLoopbackAddress()||address.isLinkLocalAddress()||address.isSiteLocalAddress()||address.isMulticastAddress()
                ||(b.length==16&&(a&254)==252)||(b.length==4&&(a==0||a>=224||(a==100&&second>=64&&second<=127)||(a==198&&(second==18||second==19)))))throw invalid();
        }
    }
    private static ApiFailure invalid(){return new ApiFailure(400,"Inscrição de notificações inválida.");}
}
