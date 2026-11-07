package br.com.colheitafeliz.game;

import java.net.URI;
import java.util.Set;

/** Public presentation preference only; never stores authentication metadata. */
public record PlayerAvatar(String id, String photoUrl) {
    private static final Set<String> IDS=Set.of("classic","male-2","male-3","female-1","female-2","female-3","google");
    public PlayerAvatar {
        if(id==null||!IDS.contains(id))throw new IllegalArgumentException("Unknown avatar");
        if(id.equals("google")){
            if(photoUrl==null||photoUrl.length()>2048)throw new IllegalArgumentException("Missing Google photo");
            var uri=URI.create(photoUrl);
            if(!"https".equals(uri.getScheme())||!"lh3.googleusercontent.com".equals(uri.getHost())
                    ||uri.getRawUserInfo()!=null||uri.getPort()!=-1||uri.getRawQuery()!=null||uri.getRawFragment()!=null
                    ||uri.getPath()==null||uri.getPath().isBlank()||uri.getPath().equals("/"))
                throw new IllegalArgumentException("Invalid Google photo URL");
        }else if(photoUrl!=null)throw new IllegalArgumentException("Classic avatar cannot store a photo");
    }
}
