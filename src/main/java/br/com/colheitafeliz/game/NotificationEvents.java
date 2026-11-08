package br.com.colheitafeliz.game;

import br.com.colheitafeliz.domain.animal.Animal;
import br.com.colheitafeliz.domain.animal.AnimalType;
import br.com.colheitafeliz.domain.notification.GameNotification.Type;
import java.util.Map;

final class NotificationEvents {
    private NotificationEvents() {}
    static boolean observe(GameState game){
        boolean changed=false;var inbox=game.notifications();var now=game.realNow();
        for(int row=0;row<GameState.ROWS;row++)for(int col=0;col<GameState.COLUMNS;col++){
            var tile=game.farm().tile(col,row);var crop=tile.crop();
            if(crop!=null&&crop.matureAt(game.now())){
                var maturedAt=crop.maturedAt(game.now());
                var ownerExclusiveUntil=maturedAt.plus(java.time.Duration.ofMinutes(5));
                String plot=Integer.toString(row*GameState.COLUMNS+col);
                changed|=inbox.record("crop:"+plot,Long.toString(tile.harvestRevision()),Type.CROP_READY,
                        crop.type().name()+": planta pronta para colher.",now,Map.of("cropId",crop.type().id(),"plot",plot,"season",Integer.toString(crop.season()),"maturedAt",maturedAt.toString(),"ownerExclusiveUntil",ownerExclusiveUntil.toString()));
            }
        }
        for(var animal:game.barn().animals())if(animal.type().kind()!=AnimalType.Kind.PROTECTOR&&animal.statusAt(game.now())==Animal.Status.READY){
            var type=animal.type();
            changed|=inbox.record("animal:"+type.id(),animal.acquiredAt()+":"+animal.growthAnchorAt(),Type.ANIMAL_READY,
                    type.name()+(type.kind()==AnimalType.Kind.PRODUCER?": produto pronto para coletar.":": adulto pronto para vender."),now,Map.of("animalId",type.id()));
        }
        if(game.vip().expiresAt()!=null&&!game.vipActive())changed|=inbox.record("vip",game.vip().expiresAt().toString(),Type.VIP_EXPIRED,
                "Seu cartão VIP expirou.",now,Map.of("expiredAt",game.vip().expiresAt().toString()));
        return changed;
    }
}
