package br.com.colheitafeliz.server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.List;
import java.util.Map;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Activation is explicit deployment metadata, never inferred from an open pull request. */
record PublishedNotes(String version,int prNumber,List<String> items) {
    static final ZoneId ZONE=ZoneId.of("America/Sao_Paulo");
    PublishedNotes {
        if(version==null||!version.matches("[a-z0-9-]{1,80}")||prNumber<=0||items==null||items.isEmpty()||items.size()>30)
            throw new IllegalArgumentException("Invalid published update notes");
        items=List.copyOf(items);
        if(items.stream().anyMatch(item->item.isBlank()||item.length()>600))throw new IllegalArgumentException("Invalid update copy");
    }
    static PublishedNotes fromEnvironment(Map<String,String> env){
        var version=env.get("PUBLISHED_UPDATE_NOTES_VERSION");var pr=env.get("PUBLISHED_UPDATE_NOTES_PR");
        if(version==null&&pr==null)return null;
        if(version==null||pr==null)return null;
        try(var stream=PublishedNotes.class.getClassLoader().getResourceAsStream("data/update-notes.json")){
            if(stream==null)return null;
            var json=JsonParser.parseString(new String(stream.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
            var manifestPr=json.get("prNumber");
            if(!version.equals(json.get("version").getAsString())||manifestPr==null||manifestPr.isJsonNull()||manifestPr.getAsInt()!=Integer.parseInt(pr))
                return null;
            var lines=new java.util.ArrayList<String>();for(var item:json.getAsJsonArray("items"))lines.add(item.getAsString());
            return new PublishedNotes(version,Integer.parseInt(pr),lines);
        }catch(IOException|RuntimeException e){
            System.err.println("Update notes unavailable; game remains available.");return null;
        }
    }
    JsonObject view(Instant now){return OnlineViews.object("version",version,"prNumber",prNumber,"date",day(now).toString(),"items",items);}
    LocalDate day(Instant now){return LocalDate.ofInstant(now,ZONE);}
}
