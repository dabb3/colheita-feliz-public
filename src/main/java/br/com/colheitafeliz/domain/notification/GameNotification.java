package br.com.colheitafeliz.domain.notification;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record GameNotification(String id, Type type, String message, String createdAt, boolean read, Map<String,String> metadata) {
    public enum Type { THEFT, CROP_READY, ANIMAL_READY, VIP_EXPIRED, CHRISTMAS_GIFT }
    public GameNotification {
        if(id==null||type==null||message==null||message.isBlank()||message.length()>300||createdAt==null)
            throw new IllegalArgumentException("Invalid notification");
        if(!UUID.fromString(id).toString().equals(id))throw new IllegalArgumentException("Invalid notification id");
        Instant.parse(createdAt);
        metadata=metadata==null?Map.of():Map.copyOf(metadata);
        if(metadata.size()>8||metadata.entrySet().stream().anyMatch(e->e.getKey().isBlank()||e.getKey().length()>40||e.getValue().length()>160))
            throw new IllegalArgumentException("Invalid notification metadata");
    }
    public GameNotification asRead(){return new GameNotification(id,type,message,createdAt,true,metadata);}
}
