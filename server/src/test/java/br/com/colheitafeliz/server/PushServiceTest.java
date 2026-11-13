package br.com.colheitafeliz.server;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PushServiceTest {
 @Test void payloadContainsOnlyGenericMessageAndBinding(){
  JsonObject payload=PushService.payload("opaque-binding");
  assertEquals(4,payload.size()); assertEquals("TEST",payload.get("type").getAsString());
  assertEquals("opaque-binding",payload.get("bindingId").getAsString());
  assertEquals("Colheita Feliz",payload.get("title").getAsString());
  assertEquals("As notificações deste dispositivo estão funcionando.",payload.get("body").getAsString());
 }
 @Test void providerResultsAreSanitized(){
  assertEquals("accepted",PushService.deliveryStatus(201));
  assertEquals("expired",PushService.deliveryStatus(410));
  assertEquals("expired",PushService.deliveryStatus(404));
  assertEquals("unavailable",PushService.deliveryStatus(429));
 }
}

