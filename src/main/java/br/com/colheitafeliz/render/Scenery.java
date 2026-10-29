package br.com.colheitafeliz.render;

import br.com.colheitafeliz.domain.animal.Animal;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Cenário vivo, como na fazenda do Orkut: casa e casinha do cachorro no alto, cachorro com o potinho, burrinho
 * cochilando, coelho com a cenoura e, dentro do cercado, os animais do jogador. Cada animal tem um lugar e
 * um ponto de produto, visível só com o produto pronto: ovos no ninho da galinha e da pata, balde da vaca e cesto
 * da ovelha; mel e pena usam o próprio animal, pois o acervo não tem ícone específico.
 */
final class Scenery {
    /** Camada de desenho: atrás dos canteiros (o padrão) ou na frente deles. */
    enum Layer { BACK, FRONT }

    /**
     * Um objeto do cenário. Se {@code walk} não for nulo, ele passeia: fica parado alguns segundos, escolhe
     * um ponto perto de casa e anda até lá, alternando os quadros de passo. Os sprites olham para a esquerda.
     */
    private static final class Thing {
        Texture idle;
        Texture walk, sleep;
        boolean sleeping, hungry;
        float width;
        final float fullWidth, homeX, homeY, radius, speed;
        float footCenter = .5f;
        final Layer layer;
        final boolean shadow;
        final boolean insidePen;
        float x, y, targetX, targetY, wait;
        boolean hidden;
        boolean walking, facingRight;
        float phase;

        Thing(Texture idle, Texture walk, float x, float y, float width, float radius, float speed,
              Layer layer, boolean shadow, boolean insidePen, boolean facingRight) {
            this.idle = idle;
            this.walk = walk;
            this.width = width;
            this.fullWidth = width;
            this.homeX = x;
            this.homeY = y;
            this.radius = radius;
            this.speed = speed;
            this.layer = layer;
            this.shadow = shadow;
            this.insidePen = insidePen;
            this.x = x;
            this.y = y;
            this.facingRight = facingRight;
        }
    }

    /**
     * Aparência de um tipo de animal no cercado. Cada lugar é {casaX, casaY, produtoX, produtoY}, na ordem de
     * compra. {@code empty} nulo esconde o ponto de produto até ficar pronto.
     */
    private record Look(Texture idle, Texture walk, float width, float radius, float speed,
                        Texture ready, Texture empty, float productWidth, float[][] slots) {}

    /** Um animal desenhado e o ponto de produto dele. */
    private record Placed(String typeId, Thing animal, Thing product) {}

    private final Map<String, Look> looks = new HashMap<>();
    private final GameAssets assets;
    private final List<Thing> things = new ArrayList<>();
    private final List<Placed> placed = new ArrayList<>();
    private final Pen pen;
    private final Random random = new Random(7);
    private final Texture soft;
    private long lastNanos = -1;

    Scenery(GameAssets a, Pen pen, Texture softShadow) {
        this.pen = pen;
        this.soft = softShadow;
        this.assets = a;
        int position = 0;
        for (var type : br.com.colheitafeliz.game.AnimalCatalogLoader.load()) {
            int col = position % 5, row = position / 5;
            float x = row == 2 ? 1250 + col * 73 : 1160 + col * 95;
            float y = 458 - row * 54;
            float width = type.id().equals("vaca") ? 88 : type.id().equals("galinha") || type.id().equals("abelha") || type.id().equals("pombo") ? 52 : 72;
            looks.put(type.id(), new Look(a.animal(type.id()), null, width, 6, 8, a.product(type.id()), null, 28,
                    new float[][]{{x, y, x + 30, y - 16}}));
            position++;
        }
        // Casas no alto, a casinha do cachorro à esquerda da casa.
        add(a.dogHouse, null, 410, 556, 214, 0, 0, Layer.BACK, false, false, false);
        add(a.house, null, 735, 540, 420, 0, 0, Layer.BACK, false, false, false);
        // Cachorro, potinho e burrinho na frente das casas.
        add(a.bowl, null, 318, 544, 46, 0, 0, Layer.BACK, true, false, false);
        add(a.dog, a.dogWalk, 384, 530, 92, 40, 34, Layer.BACK, true, false, true);
        add(a.donkeySleeping, null, 222, 540, 96, 0, 0, Layer.BACK, true, false, false);
        // Dentro do cercado.
        // Coelho com a cenoura no canto de baixo.
        add(a.carrot, null, 168, 140, 54, 0, 0, Layer.FRONT, true, false, false);
        add(a.rabbit, null, 104, 150, 58, 0, 0, Layer.FRONT, true, false, false);
        for (Thing t : things) {
            t.wait = random.nextFloat() * 3;
            if (t.shadow) t.footCenter = a.footCenter(t.idle);
        }
    }

    private Thing add(Texture idle, Texture walk, float x, float y, float width, float radius, float speed,
                      Layer layer, boolean shadow, boolean insidePen, boolean facingRight) {
        Thing thing = new Thing(idle, walk, x, y, width, radius, speed, layer, shadow, insidePen, facingRight);
        things.add(thing);
        return thing;
    }

    /** Mantém um animal desenhado por animal do jogador, com o ponto de produto mostrando se está pronto. */
    void syncAnimals(List<Animal> animals, java.time.Instant now) {
        List<String> typeIds = animals.stream().map(a -> a.type().id()).toList();
        boolean same = placed.size() == typeIds.size();
        for (int i = 0; same && i < typeIds.size(); i++) same = placed.get(i).typeId().equals(typeIds.get(i));
        if (!same) rebuild(typeIds);
        int hour = now.atOffset(java.time.ZoneOffset.UTC).getHour();
        boolean night = hour < 6 || hour >= 22;
        for (int i = 0; i < placed.size(); i++) {
            Placed p = placed.get(i);
            Animal animal = animals.get(i);
            Animal.Stage stage = animal.stageAt(now);
            p.animal().idle = assets.animal(p.typeId(), stage, GameAssets.AnimalPose.IDLE);
            p.animal().walk = assets.animal(p.typeId(), stage, GameAssets.AnimalPose.WALKING);
            p.animal().sleep = assets.animal(p.typeId(), stage, GameAssets.AnimalPose.SLEEPING);
            p.animal().sleeping = night;
            p.animal().hungry = animal.statusAt(now) == Animal.Status.HUNGRY;
            p.animal().footCenter = assets.footCenter(p.animal().idle);
            p.animal().width = p.animal().fullWidth * (stage == Animal.Stage.BABY ? .65f : stage == Animal.Stage.YOUNG ? .85f : 1);
            p.product().hidden = animal.type().kind() != br.com.colheitafeliz.domain.animal.AnimalType.Kind.PRODUCER
                    || animal.statusAt(now) != Animal.Status.READY;
        }
    }

    private void rebuild(List<String> typeIds) {
        for (Placed p : placed) { things.remove(p.animal()); things.remove(p.product()); }
        placed.clear();
        Map<String, Integer> used = new HashMap<>();
        for (String id : typeIds) {
            Look look = looks.get(id);
            int slot = used.merge(id, 1, Integer::sum) - 1;
            if (look == null || slot >= look.slots().length) throw new IllegalStateException("No pen slot for " + id);
            float[] s = look.slots()[slot];
            Thing product = add(look.empty() != null ? look.empty() : look.ready(), null, s[2], s[3],
                    look.productWidth(), 0, 0, Layer.BACK, look.empty() == null, true, false);
            Thing animal = add(look.idle(), look.walk(), s[0], s[1], look.width(), look.radius(), look.speed(),
                    Layer.BACK, true, true, slot % 2 == 0);
            animal.footCenter = assets.footCenter(animal.idle);
            animal.wait = random.nextFloat() * 3;
            placed.add(new Placed(id, animal, product));
        }
    }

    String animalTypeAt(float x, float y) {
        int index = animalAt(x, y);
        return index < 0 ? null : placed.get(index).typeId();
    }

    /** Índice (na ordem do jogador) do animal ou do ponto de produto sob o ponto, ou -1. */
    int animalAt(float x, float y) {
        for (int i = placed.size() - 1; i >= 0; i--) {
            Thing product = placed.get(i).product(), animal = placed.get(i).animal();
            if (!product.hidden && covers(product, x, y)) return i;
            if (covers(animal, x, y)) return i;
        }
        return -1;
    }

    private static boolean covers(Thing t, float x, float y) {
        float h = t.width * t.idle.getHeight() / t.idle.getWidth();
        return Math.abs(x - t.x) <= t.width / 2f && y >= t.y - 6 && y <= t.y + h;
    }

    /** Ponto acima do animal, para o balão de dica. */
    float[] animalAnchor(int index) {
        Thing animal = placed.get(index).animal();
        return new float[]{animal.x, animal.y + animal.width * animal.idle.getHeight() / animal.idle.getWidth() - 40};
    }

    /** Desenha a camada pedida, de trás para a frente (maior y primeiro). {@code pen} vem no meio da camada BACK. */
    void draw(SpriteBatch batch, Layer layer) {
        if (layer == Layer.BACK) update();
        List<Thing> sorted = new ArrayList<>();
        for (Thing t : things) if (t.layer == layer) sorted.add(t);
        sorted.sort(Comparator.comparingDouble((Thing t) -> -t.y));
        if (layer == Layer.BACK) pen.drawBack(batch);
        for (Thing t : sorted) {
            if (t.insidePen || layer == Layer.FRONT) drawThing(batch, t);
        }
        if (layer == Layer.BACK) {
            pen.drawFront(batch);
            for (Thing t : sorted) if (!t.insidePen) drawThing(batch, t);
        }
    }

    private void update() {
        long now = System.nanoTime();
        float dt = lastNanos < 0 ? 0 : Math.min(.1f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        for (Thing t : things) {
            t.phase += dt;
            if (t.sleeping || t.walk == null || t.speed <= 0) continue;
            if (!t.walking) {
                t.wait -= dt;
                if (t.wait <= 0) pickTarget(t);
                continue;
            }
            float dx = t.targetX - t.x, dy = t.targetY - t.y;
            float dist = (float) Math.hypot(dx, dy);
            float step = t.speed * dt;
            if (dist <= step) {
                t.x = t.targetX;
                t.y = t.targetY;
                t.walking = false;
                t.wait = 2 + random.nextFloat() * 4;
            } else {
                t.x += dx / dist * step;
                t.y += dy / dist * step;
            }
        }
    }

    private void pickTarget(Thing t) {
        for (int attempt = 0; attempt < 12; attempt++) {
            float angle = random.nextFloat() * MathUtils.PI2;
            float r = t.radius * (.3f + .7f * random.nextFloat());
            float x = t.homeX + MathUtils.cos(angle) * r, y = t.homeY + MathUtils.sin(angle) * r * .55f;
            if (t.insidePen && !pen.inside(x, y, t.width * .45f)) continue;
            t.targetX = x;
            t.targetY = y;
            t.facingRight = x > t.x;
            t.walking = true;
            return;
        }
        t.wait = 1;
    }

    private void drawThing(SpriteBatch batch, Thing t) {
        if (t.hidden) return;
        Texture tex = t.sleeping && t.sleep != null ? t.sleep : t.walking && t.walk != null && ((int) (t.phase * 4) % 2 == 0) ? t.walk : t.idle;
        // Pares normalizados compartilham o canvas, a altura visível e a base dos pés.
        float w = t.width, h = w * t.idle.getHeight() / t.idle.getWidth();
        if (t.shadow) {
            Color previous = batch.getColor().cpy();
            batch.setColor(0, 0, 0, .26f);
            float shadowW = t.width * .7f, shadowH = t.width * .16f;
            batch.draw(soft, t.x - shadowW / 2f, t.y - shadowH / 2f, shadowW, shadowH);
            batch.setColor(previous);
        }
        // Espelha em torno do apoio: a sombra permanece em (x, y) ao virar.
        float anchor = t.facingRight ? 1 - t.footCenter : t.footCenter;
        batch.draw(tex, t.x - w * anchor, t.y, w, h, 0, 0, tex.getWidth(), tex.getHeight(), t.facingRight, false);
        if (t.hungry) batch.draw(assets.feed, t.x - 9, t.y + h + 2, 18, 20);
    }
}
