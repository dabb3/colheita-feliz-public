package br.com.colheitafeliz.ui;

import br.com.colheitafeliz.game.GameState;
import br.com.colheitafeliz.render.UiSkin;
import br.com.colheitafeliz.render.Palette;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Align;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Paged inbox reusing the HUD window; all rows remain reachable with at most 100 entries. */
public final class NotificationPanel {
    public static final int PAGE_SIZE=5;
    private static final float W=520,H=455;
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault());
    private int page;
    public void reset(){page=0;}
    public static Rectangle bounds(){var anchor=Hud.shortcutBounds(Hud.Shortcut.ALERTS);return new Rectangle(Math.min(anchor.x,1588-W),anchor.y-H-8,W,H);}
    public static Rectangle row(int index){var b=bounds();return new Rectangle(b.x+12,b.y+H-82-(index+1)*61,W-24,58);}
    public static Rectangle readAll(){var b=bounds();return new Rectangle(b.x+12,b.y+H-74,W/2-24,32);}
    public static Rectangle clearRead(){var b=bounds();return new Rectangle(b.x+W/2,b.y+H-74,W/2-12,32);}
    public static Rectangle dismiss(int index){var r=row(index);return new Rectangle(r.x+r.width-60,r.y+19,56,36);}
    public static Rectangle previous(){var b=bounds();return new Rectangle(b.x+12,b.y+12,140,32);}
    public static Rectangle next(){var b=bounds();return new Rectangle(b.x+W-152,b.y+12,140,32);}
    public boolean click(float x,float y,GameState state){
        if(!bounds().contains(x,y))return false;
        int pages=Math.max(1,(state.notifications().entries().size()+PAGE_SIZE-1)/PAGE_SIZE);
        page=Math.min(page,pages-1);
        if(readAll().contains(x,y))state.markAllNotificationsRead();
        else if(clearRead().contains(x,y))state.dismissReadNotifications();
        else if(previous().contains(x,y))page=Math.max(0,page-1);
        else if(next().contains(x,y))page=Math.min(pages-1,page+1);
        else for(int i=0;i<PAGE_SIZE;i++)if(row(i).contains(x,y)){
            int index=page*PAGE_SIZE+i;var entries=state.notifications().entries();
            if(index<entries.size()){
                var entry=entries.get(index);
                if(entry.read()&&dismiss(i).contains(x,y))state.dismissNotification(entry.id());
                else state.markNotificationRead(entry.id());
            }break;
        }
        return true;
    }
    public void draw(SpriteBatch batch,UiSkin skin,GameState state){
        var b=bounds();var entries=state.notifications().entries();int pages=Math.max(1,(entries.size()+PAGE_SIZE-1)/PAGE_SIZE);page=Math.min(page,pages-1);
        skin.dropShadow(batch,b.x,b.y,W,H,4);skin.draw(batch,skin.window,b.x,b.y,W,H);
        skin.text(batch,skin.label,"Notificações · "+state.notifications().unread()+" não lidas",b.x+16,b.y+H-18,Palette.INK,Align.left);
        skin.text(batch,skin.body,"Marcar todas como lidas",readAll().x+10,readAll().y+22,Palette.SKY_INK,Align.left);
        skin.text(batch,skin.body,"Limpar lidas",clearRead().x+10,clearRead().y+22,Palette.SKY_INK,Align.left);
        if(entries.isEmpty())skin.text(batch,skin.body,"Nenhuma notificação por enquanto.",b.x+16,b.y+H-115,Palette.INK_MUTED,Align.left);
        for(int i=0;i<PAGE_SIZE&&page*PAGE_SIZE+i<entries.size();i++){
            var entry=entries.get(page*PAGE_SIZE+i);var r=row(i);
            if(!entry.read())skin.draw(batch,skin.itemHover,r.x,r.y,r.width,r.height);
            if(entry.read())skin.text(batch,skin.micro,"X Excluir",dismiss(i).x+2,dismiss(i).y+24,Palette.SKY_INK,Align.left);
            String text=entry.message();int split=Math.min(text.length(),50);if(split<text.length()){int space=text.lastIndexOf(' ',split);if(space>20)split=space;}
            skin.text(batch,skin.caption,text.substring(0,split),r.x+8,r.y+45,entry.read()?Palette.INK_MUTED:Palette.INK,Align.left);
            if(split<text.length())skin.text(batch,skin.caption,text.substring(split).strip(),r.x+8,r.y+29,Palette.INK,Align.left);
            skin.text(batch,skin.micro,DATE.format(Instant.parse(entry.createdAt()))+(entry.read()?" · Lida":" · Clique para marcar lida"),r.x+8,r.y+12,Palette.SKY_INK,Align.left);
        }
        skin.text(batch,skin.body,"< Anterior",previous().x+8,previous().y+22,Palette.INK,Align.left);
        skin.text(batch,skin.body,(page+1)+" / "+pages,b.x+W/2,b.y+33,Palette.INK_MUTED,Align.center);
        skin.text(batch,skin.body,"Próxima >",next().x+8,next().y+22,Palette.INK,Align.left);
    }
}
