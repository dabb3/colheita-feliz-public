package br.com.colheitafeliz.ui;

import br.com.colheitafeliz.game.*;
import br.com.colheitafeliz.domain.notification.GameNotification;
import br.com.colheitafeliz.testsupport.ManualClock;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NotificationPanelTest {
    @Test void bellIsImmediatelyLeftOfShopAndAllHundredRowsAreReachableAndReadable(){
        var bell=Hud.shortcutBounds(Hud.Shortcut.ALERTS);var shop=Hud.shortcutBounds(Hud.Shortcut.SHOP);
        assertTrue(bell.x+bell.width<shop.x);assertEquals(Hud.Shortcut.ALERTS.ordinal()+1,Hud.Shortcut.SHOP.ordinal());
        var clock=new ManualClock();var g=new GameState(clock::instant,clock,CropCatalogLoader.load(),bound->99);
        for(int i=0;i<100;i++)g.notifications().record("crop:0",""+i,GameNotification.Type.CROP_READY,"Pronta",clock.instant(),Map.of());
        var hud=new Hud();hud.toggleAlerts();var next=NotificationPanel.next();for(int i=0;i<30;i++)assertTrue(hud.notificationClick(next.x+2,next.y+2,g));
        var last=NotificationPanel.row(4);assertTrue(hud.notificationClick(last.x+2,last.y+2,g));
        assertTrue(g.notifications().entries().getLast().read());assertEquals(99,g.notifications().unread());
        var all=NotificationPanel.readAll();hud.notificationClick(all.x+2,all.y+2,g);assertEquals(0,g.notifications().unread());
        assertFalse(hud.notificationClick(0,0,g));
    }
}
