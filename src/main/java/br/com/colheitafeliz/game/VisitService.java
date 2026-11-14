package br.com.colheitafeliz.game;

import br.com.colheitafeliz.domain.farm.FarmTile;
import br.com.colheitafeliz.domain.farm.TileState;

/**
 * Ensaio interno sobre dois estados autorizados pelo chamador local controlado.
 * O monitor é compartilhado com GameState.act; mutações diretas do domínio exigem serialização do chamador.
 * Identidade, amizade, autoridade de tempo e transação entre processos pertencem ao serviço online chamador.
 */
public final class VisitService {
    /** Online caller validates friendship, owner window and cooldown before this attempt. */
    public TheftAttempt attemptSteal(GameState owner,GameState visitor,int col,int row,long revision,
                                    String visitorName,br.com.colheitafeliz.domain.farm.RandomSource random) {
        synchronized (GameState.class) {
            var tile=eligibleTile(owner,visitor,col,row,revision,true);
            if(tile==null||tile.crop()==null||!tile.crop().matureAt(owner.now())||tile.harvestRevision()==Long.MAX_VALUE)
                return TheftAttempt.blocked("Temporada indisponível para furto.");
            if(!owner.protectedPlots().contains(owner.land().at(col,row).id()))
                return TheftAttempt.from(steal(owner,visitor,col,row,revision,visitorName));
            int roll=random.nextInt(100);
            if(roll<0||roll>=100)throw new IllegalStateException("Invalid bite roll");
            if(roll>=30)return new TheftAttempt(ActionResult.fail("Este terreno está protegido pelos animais. Sua tentativa foi bloqueada; aguarde 6 horas para tentar novamente."),
                TheftAttempt.Kind.PROTECTED,java.math.BigDecimal.ZERO);
            int fine=random.nextInt(41);
            if(fine<0||fine>=41)throw new IllegalStateException("Invalid bite fine");
            var amount=visitor.wallet().coins().min(java.math.BigDecimal.valueOf(10L+fine));
            if(amount.signum()>0)visitor.wallet().debit(br.com.colheitafeliz.domain.economy.Currency.GOLD,amount);
            return new TheftAttempt(ActionResult.fail("Você foi mordido por um animal protetor e perdeu "+amount.toPlainString()+" moedas de ouro. Aguarde 6 horas para tentar novamente."),
                TheftAttempt.Kind.BITTEN,amount);
        }
    }
    public ActionResult steal(GameState owner, GameState visitor, int col, int row, long expectedRevision) {
        return steal(owner,visitor,col,row,expectedRevision,"Um visitante");
    }
    public ActionResult steal(GameState owner, GameState visitor, int col, int row, long expectedRevision, String visitorName) {
        synchronized (GameState.class) {
            FarmTile tile = eligibleTile(owner, visitor, col, row, expectedRevision);
            if (tile == null || tile.crop() == null || !tile.crop().matureAt(owner.now()))
                return ActionResult.fail("Temporada indisponível para furto.");
            var origin = tile.crop().origin();
            var cropType=tile.crop().type();
            if (!visitor.inventory().canAddProduceLot(origin) || tile.harvestRevision() == Long.MAX_VALUE)
                return ActionResult.fail("Destino ou revisão sem capacidade.");
            visitor.inventory().addProduceLot(tile.harvest(owner.now()));
            String name=visitorName==null||visitorName.isBlank()?"Um visitante":visitorName.strip().replaceAll("[\\p{Cntrl}]", "");
            name=name.substring(0,Math.min(80,name.length()));
            owner.notifications().record("theft",col+":"+row+":"+expectedRevision,
                    br.com.colheitafeliz.domain.notification.GameNotification.Type.THEFT,
                    name+" furtou 1 "+cropType.name()+" da sua fazenda.",owner.realNow(),
                    java.util.Map.of("actorName",name,"cropId",cropType.id(),"quantity","1"));
            return ActionResult.ok("Uma unidade transferida ao visitante.");
        }
    }

    public ActionResult fertilizeVisit(GameState owner, GameState visitor, int col, int row,
                                       String fertilizerId, long expectedRevision) {
        synchronized (GameState.class) {
            FarmTile tile = eligibleTile(owner, visitor, col, row, expectedRevision);
            if (tile == null || !"amor".equals(fertilizerId)) return ActionResult.fail("Ajuda indisponível.");
            var type = visitor.fertilizer(fertilizerId);
            var now = owner.now();
            if (visitor.progress().level() < type.requiredLevel() || visitor.inventory().fertilizer(fertilizerId) == 0
                    || tile.state() != TileState.GROWING || tile.fertilized() || tile.crop().dryAt(now)
                    || tile.crop().paused() || tile.crop().matureAt(now)) return ActionResult.fail("A planta não pode receber amor.");
            tile.fertilize(now, type.reduction());
            visitor.inventory().useFertilizer(fertilizerId);
            tile.refresh(now);
            return ActionResult.ok("Fertilizante do amor aplicado.");
        }
    }

    public ActionResult spreadInsects(GameState owner, GameState visitor, int col, int row, long expectedRevision) {
        return spreadPest(owner, visitor, col, row, expectedRevision, true);
    }

    public ActionResult spreadWeeds(GameState owner, GameState visitor, int col, int row, long expectedRevision) {
        return spreadPest(owner, visitor, col, row, expectedRevision, false);
    }

    private static ActionResult spreadPest(GameState owner, GameState visitor, int col, int row, long revision, boolean insects) {
        synchronized (GameState.class) {
            FarmTile tile = eligibleTile(owner, visitor, col, row, revision);
            if (tile == null || tile.state() != TileState.GROWING || tile.crop() == null
                    || !tile.crop().watered() || tile.crop().matureAt(owner.now()))
                return ActionResult.fail("Praga indisponível: escolha uma planta em crescimento, já regada e sem proteção.");
            if (insects ? tile.insects() > 0 : tile.pest() != br.com.colheitafeliz.domain.farm.PestState.NONE)
                return ActionResult.fail("Esta planta já tem esse tipo de praga.");
            if (insects) tile.checkInsects(owner.now(), true);
            else tile.checkPest(owner.now(), true);
            return ActionResult.ok(insects ? "Uma minhoca lançada. O crescimento foi pausado." : "Uma erva daninha lançada. O crescimento foi pausado.");
        }
    }

    private static FarmTile eligibleTile(GameState owner, GameState visitor, int col, int row, long revision) {
        return eligibleTile(owner,visitor,col,row,revision,false);
    }
    private static FarmTile eligibleTile(GameState owner, GameState visitor, int col, int row, long revision,boolean allowProtected) {
        if (owner == null || visitor == null || owner == visitor || !owner.farm().contains(col, row)
                || !owner.land().isUnlocked(col, row)) return null;
        FarmTile tile = owner.farm().tile(col, row);
        if (tile.harvestRevision() != revision || (!allowProtected&&owner.protectedPlots().contains(owner.land().at(col, row).id()))) return null;
        return tile;
    }
}
