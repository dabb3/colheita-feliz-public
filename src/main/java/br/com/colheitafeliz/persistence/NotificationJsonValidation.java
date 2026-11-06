package br.com.colheitafeliz.persistence;

import com.google.gson.JsonElement;
import java.util.Set;

/** Optional only for historical snapshots; new inbox fields must not be coerced by the JSON reader. */
public final class NotificationJsonValidation {
    private NotificationJsonValidation() {}
    public static void validate(JsonElement snapshot){
        if(snapshot!=null&&snapshot.isJsonObject()&&snapshot.getAsJsonObject().has("christmas2026")){
            var christmas=snapshot.getAsJsonObject().get("christmas2026");
            if(!christmas.isJsonObject()||!christmas.getAsJsonObject().keySet().equals(Set.of("registeredAt","giftGranted")))throw new IllegalArgumentException("Invalid Christmas state");
            string(christmas.getAsJsonObject().get("registeredAt"));
            var granted=christmas.getAsJsonObject().get("giftGranted");
            if(!granted.isJsonPrimitive()||!granted.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("Invalid gift receipt");
        }
        if(snapshot==null||!snapshot.isJsonObject()||!snapshot.getAsJsonObject().has("notifications"))return;
        var state=snapshot.getAsJsonObject().get("notifications");
        if(!state.isJsonObject()||!state.getAsJsonObject().keySet().equals(Set.of("version","entries","observed")))throw new IllegalArgumentException("Invalid inbox schema");
        var object=state.getAsJsonObject();var version=object.get("version");
        if(!version.isJsonPrimitive()||!version.getAsJsonPrimitive().isNumber()||version.getAsBigDecimal().intValueExact()!=1)
            throw new IllegalArgumentException("Unknown inbox version");
        if(!object.get("entries").isJsonArray()||!object.get("observed").isJsonObject())throw new IllegalArgumentException("Invalid inbox data");
        for(var value:object.getAsJsonObject("observed").entrySet())string(value.getValue());
        for(var entry:object.getAsJsonArray("entries")){
            if(!entry.isJsonObject())throw new IllegalArgumentException("Invalid notification entry");
            var note=entry.getAsJsonObject();
            for(String key:new String[]{"id","type","message","createdAt"})string(note.get(key));
            var read=note.get("read");
            if(read==null||!read.isJsonPrimitive()||!read.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("Invalid notification read flag");
            var metadata=note.get("metadata");
            if(metadata!=null&&!metadata.isJsonNull()){
                if(!metadata.isJsonObject())throw new IllegalArgumentException("Invalid notification metadata");
                for(var value:metadata.getAsJsonObject().entrySet())string(value.getValue());
            }
        }
    }
    private static void string(JsonElement value){if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Expected notification text");}
}
