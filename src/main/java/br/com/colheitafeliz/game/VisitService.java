package br.com.colheitafeliz.game;

import br.com.colheitafeliz.domain.farm.FarmTile;
import br.com.colheitafeliz.domain.farm.TileState;

/**
 * Ensaio interno sobre dois estados autorizados pelo chamador local controlado.
 * O monitor é compartilhado com GameState.act; mutações diretas do domínio exigem serialização do chamador.
 * Identidade, amizade, autoridade de tempo e transação entre processos pertencem ao serviço online chamador.
 */
public final class VisitService {
    /** A pest blocked by protection spends the online cooldown like a successful spread. */
    public record PestAttempt(ActionResult result, boolean consumesCooldown) {}

    /** Online caller validates friendship, owner window and cooldown before this attempt. */
    public TheftAttempt attemptSteal(GameState owner,GameState visitor,int col,int row,long revision,
                                    String visitorName,br.com.colheitafeliz.domain.farm.RandomSource random) {
        synchronized (GameState.class) {
            var tile=eligibleTile(owner,visitor,col,row,revision,true);
            if (tile != null && tile.crop() != null && tile.crop().type().acquisitionSource()
                    == br.com.colheitafeliz.domain.inventory.AcquisitionSource.LOGIN_REWARD)
                return TheftAttempt.blocked("Culturas do calendário não podem ser furtadas.");
            if(tile==null||tile.crop()==null||!tile.crop().matureAt(owner.now())||tile.harvestRevision()==Long.MAX_VALUE)
                return TheftAttempt.blocked("Temporada indisponível para furto.");
            if(!visitor.inventory().canAddProduceLot(tile.crop().origin()))
                return TheftAttempt.blocked("Destino ou revisão sem capacidade.");
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
            if (tile != null && tile.crop() != null && tile.crop().type().acquisitionSource()
                    == br.com.colheitafeliz.domain.inventory.AcquisitionSource.LOGIN_REWARD)
                return ActionResult.fail("Culturas do calendário não podem ser furtadas.");
            if (tile == null || tile.crop() == null || !tile.crop().matureAt(owner.now()))
                return ActionResult.fail("Temporada indisponível para furto.");
            var origin = tile.crop().origin();
            var cropType=tile.crop().type();
            if (!visitor.inventory().canAddProduceLot(origin) || tile.harvestRevision() == Long.MAX_VALUE)
                return ActionResult.fail("Destino ou revisão sem capacidade.");
            visitor.inventory().addProduceLot(tile.harvest(owner.now()));
            String name=actorName(visitorName);
            owner.notifications().record("theft",col+":"+row+":"+expectedRevision,
                    br.com.colheitafeliz.domain.notification.GameNotification.Type.THEFT,
                    name+" furtou 1 "+cropType.name()+" da sua fazenda.",owner.realNow(),
                    java.util.Map.of("actorName",name,"cropId",cropType.id(),"quantity","1"));
            return ActionResult.ok("Uma unidade transferida ao visitante.");
        }
    }

    public static final int MAX_GIFT_SEEDS = 5;

    /** Online caller validates friendship, quantity bounds and the 24h limit; lots move unchanged, without XP or missions. */
    public ActionResult giftSeeds(GameState sender, GameState recipient, String cropId, int quantity, String senderName, String giftId) {
        synchronized (GameState.class) {
            if (sender == null || recipient == null || sender == recipient || quantity < 1 || quantity > MAX_GIFT_SEEDS)
                throw new IllegalArgumentException("Invalid gift");
            var type = sender.catalog().get(cropId);
            if (type.acquisitionSource() == br.com.colheitafeliz.domain.inventory.AcquisitionSource.LOGIN_REWARD)
                return ActionResult.fail("Sementes do calendário não podem ser enviadas como presente.");
            if (sender.inventory().seeds(type) < quantity)
                return ActionResult.fail("Você não tem " + quantity + " semente(s) de " + type.name() + ".");
            if (!recipient.unlocked(type))
                return ActionResult.fail("Seu amigo precisa do nível " + type.requiredLevel() + " para plantar " + type.name() + ".");
            if (type.vipOnly() && !recipient.vipActive())
                return ActionResult.fail(type.name() + " exige cartão VIP ativo do seu amigo para plantar.");
            if (recipient.inventory().seeds(type) > Integer.MAX_VALUE - quantity)
                return ActionResult.fail("O estoque do seu amigo não comporta este presente.");
            for (int i = 0; i < quantity; i++) recipient.inventory().addSeedLot(sender.inventory().takeSeed(type.id()));
            String name = actorName(senderName);
            // shortcut: reuses the theft cursor and CHRISTMAS_GIFT type so v20 snapshots stay readable by older releases;
            // a dedicated GIFT type needs a save version bump.
            recipient.notifications().record("theft", "gift:" + giftId,
                    br.com.colheitafeliz.domain.notification.GameNotification.Type.CHRISTMAS_GIFT,
                    name + " enviou " + quantity + " semente(s) de " + type.name() + " de presente.", recipient.realNow(),
                    java.util.Map.of("actorName", name, "cropId", type.id(), "quantity", Integer.toString(quantity)));
            return ActionResult.ok("Presente enviado: " + quantity + " semente(s) de " + type.name() + ".");
        }
    }

    private static String actorName(String value) {
        String name = value == null || value.isBlank() ? "Um visitante" : value.strip().replaceAll("[\\p{Cntrl}]", "");
        return name.substring(0, Math.min(80, name.length()));
    }

    public ActionResult fertilizeVisit(GameState owner, GameState visitor, int col, int row,
                                       String fertilizerId, long expectedRevision) {
        synchronized (GameState.class) {
            // Love only helps, so protection never blocks it nor changes its answer.
            FarmTile tile = eligibleTile(owner, visitor, col, row, expectedRevision, true);
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

    public PestAttempt spreadInsects(GameState owner, GameState visitor, int col, int row, long expectedRevision) {
        return spreadPest(owner, visitor, col, row, expectedRevision, true);
    }

    public PestAttempt spreadWeeds(GameState owner, GameState visitor, int col, int row, long expectedRevision) {
        return spreadPest(owner, visitor, col, row, expectedRevision, false);
    }

    private static PestAttempt spreadPest(GameState owner, GameState visitor, int col, int row, long revision, boolean insects) {
        synchronized (GameState.class) {
            // Protection is checked last so earlier refusals never reveal it without spending the attempt.
            FarmTile tile = eligibleTile(owner, visitor, col, row, revision, true);
            if (tile == null || tile.state() != TileState.GROWING || tile.crop() == null
                    || !tile.crop().watered() || tile.crop().matureAt(owner.now()))
                return new PestAttempt(ActionResult.fail("Praga indisponível: escolha uma planta em crescimento e já regada."), false);
            if (insects ? tile.insects() > 0 : tile.pest() != br.com.colheitafeliz.domain.farm.PestState.NONE)
                return new PestAttempt(ActionResult.fail("Esta planta já tem esse tipo de praga."), false);
            if (owner.protectedPlots().contains(owner.land().at(col, row).id()))
                return new PestAttempt(ActionResult.fail("Este terreno está protegido pelos animais. Sua praga foi bloqueada; aguarde 6 horas para lançar esse tipo novamente."), true);
            if (insects) tile.checkInsects(owner.now(), true);
            else tile.checkPest(owner.now(), true);
            return new PestAttempt(ActionResult.ok(insects ? "Uma minhoca lançada. O crescimento foi pausado." : "Uma erva daninha lançada. O crescimento foi pausado."), true);
        }
    }

    /**
     * Visitante online remove ervas ou insetos com a mesma lógica de domínio do dono, sem gastar itens de ninguém.
     * Terreno protegido também aceita a limpeza, e as recusas não revelam proteção.
     */
    public ActionResult cleanPest(GameState owner, GameState visitor, int col, int row, long revision, boolean insects, int xp) {
        return cleanPest(owner, visitor, col, row, revision, insects, xp, true);
    }

    public ActionResult cleanPest(GameState owner, GameState visitor, int col, int row, long revision,
                                 boolean insects, int xp, boolean reward) {
        synchronized (GameState.class) {
            FarmTile tile = eligibleTile(owner, visitor, col, row, revision, true);
            var now = owner.now();
            if (tile != null) tile.refresh(now);
            if (tile == null || (insects ? tile.insects() == 0 : tile.pest() != br.com.colheitafeliz.domain.farm.PestState.WEEDS))
                return ActionResult.fail(insects ? "Não há insetos para remover nesta planta." : "Não há ervas daninhas para remover nesta planta.");
            if (insects) tile.removeInsects(now);
            else tile.removePest(now);
            if (!reward) return ActionResult.ok((insects ? "Insetos removidos" : "Ervas removidas") + " da fazenda do amigo. A limpeza da praga lançada por você não concede recompensa.");
            String missionReward = visitor.rewardFriendHelp("friend-pest", xp);
            return ActionResult.ok((insects ? "Insetos removidos" : "Ervas removidas") + " da fazenda do amigo. "
                    + (xp > 0 ? "+" + xp + " XP." : "Limite diário de XP cooperativo atingido: limpeza sem XP.")
                    + (missionReward.isEmpty() ? "" : " " + missionReward));
        }
    }

    /**
     * Visitante online alimenta um animal faminto do dono pela regra do dono, pagando a ração do próprio estoque.
     * O dono não recebe XP nem missão; recusas não mencionam terrenos ou proteção.
     */
    public ActionResult feedAnimal(GameState owner, GameState visitor, String typeId, String feedId, int xp) {
        synchronized (GameState.class) {
            if (owner == null || visitor == null || owner == visitor) return ActionResult.fail("Nenhum animal deste tipo precisa de ração agora.");
            int index = owner.friendFeedableAnimal(typeId);
            if (index < 0) return ActionResult.fail("Nenhum animal deste tipo precisa de ração agora.");
            String refusal = owner.feedWith(index, visitor.inventory(), feedId);
            if (refusal != null) return ActionResult.fail(refusal);
            String reward = visitor.rewardFriendHelp("friend-feed", xp);
            return ActionResult.ok(owner.barn().get(index).type().name() + " do amigo alimentado. "
                    + (xp > 0 ? "+" + xp + " XP." : "Limite diário de XP cooperativo atingido: alimentação sem XP.")
                    + (reward.isEmpty() ? "" : " " + reward));
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
