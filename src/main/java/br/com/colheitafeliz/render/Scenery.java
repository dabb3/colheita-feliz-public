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
 * Cenário da fazenda: somente animais comprados, distribuídos pela referência visual. No cercado ficam
 * porco, ovelha, vaca, galinha e pato; os demais ficam fora. Cada animal tem um lugar e
 * um ponto de produto, visível só com o produto pronto: ovos no ninho da galinha e da pata, balde da vaca e cesto
 * da ovelha; mel e pena usam o próprio animal, pois o acervo não tem ícone específico.
 */
final class Scenery {
    /** Camada de desenho: atrás dos canteiros (o padrão) ou na frente deles. */
    enum Layer { BACK, FRONT }

    /**
     * Um objeto do cenário. Se {@code walk} não for nulo, ele passeia: fica parado alguns segundos, escolhe
     * um ponto perto de casa e anda até lá, alternando os quadros conforme a distância percorrida.
     */
    private static final class Thing {
        Texture idle;
        Texture walk, sleep;
        boolean sleeping, hungry;
        float width;
        final float fullWidth, homeX, homeY, radius, speed;
        final Layer layer;
        final boolean shadow;
        final boolean insidePen;
        float x, y, targetX, targetY, wait;
        boolean hidden;
        boolean walking, facingRight;
        float phase, walkDistance;
        String typeId;

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
        for (var type : br.com.colheitafeliz.game.AnimalCatalogLoader.load()) {
            AnimalLayout layout = AnimalLayout.forType(type.id());
            boolean hive = type.id().equals("abelha"), mobile = roams(type.id());
            looks.put(type.id(), new Look(hive ? a.beehive : a.animal(type.id()), null, layout.width(),
                    !mobile ? 0 : type.id().equals("cavalo") ? 36 : 20, mobile ? 18 : 0, a.product(type.id()), null, 28,
                    new float[][]{{layout.x(), layout.y(), layout.x() + 38, layout.y() - 12}}));
        }
        // Casas no alto, a casinha do cachorro à esquerda da casa.
        add(a.dogHouse, null, 410, 556, 214, 0, 0, Layer.BACK, false, false, false);
        add(a.house, null, 735, 540, 420, 0, 0, Layer.BACK, false, false, false);
        for (Thing t : things) {
            t.wait = random.nextFloat() * 3;
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
            if (p.typeId().equals("abelha")) {
                p.animal().hungry = animal.statusAt(now) == Animal.Status.HUNGRY;
                p.product().hidden = true; // A própria colmeia recebe a coleta; não desenhar uma segunda colmeia como mel.
                continue;
            }
            Animal.Stage stage = animal.stageAt(now);
            p.animal().idle = assets.animal(p.typeId(), stage, GameAssets.AnimalPose.IDLE);
            p.animal().walk = assets.animal(p.typeId(), stage, GameAssets.AnimalPose.WALKING);
            p.animal().sleep = assets.animal(p.typeId(), stage, GameAssets.AnimalPose.SLEEPING);
            p.animal().sleeping = night;
            p.animal().hungry = animal.statusAt(now) == Animal.Status.HUNGRY;
            p.animal().width = p.animal().fullWidth * (stage == Animal.Stage.BABY ? .65f : stage == Animal.Stage.YOUNG ? .85f : 1);
            p.product().hidden = animal.type().kind() != br.com.colheitafeliz.domain.animal.AnimalType.Kind.PRODUCER
                    || p.typeId().equals("pavao")
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
            boolean insidePen = AnimalLayout.forType(id).insidePen();
            Layer layer = insidePen || s[1] > 450 ? Layer.BACK : Layer.FRONT;
            Thing product = add(look.empty() != null ? look.empty() : look.ready(), null, s[2], s[3],
                    look.productWidth(), 0, 0, layer, look.empty() == null, insidePen, false);
            Thing animal = add(look.idle(), look.walk(), s[0], s[1], look.width(), look.radius(), look.speed(),
                    layer, true, insidePen, false);
            animal.typeId = id;
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

    private boolean covers(Thing t, float x, float y) {
        Texture texture = pose(t);
        SpriteFootprint foot = assets.footprint(texture);
        float w = drawWidth(t, texture), h = w * texture.getHeight() / texture.getWidth();
        boolean flipped = flipSprite(t.typeId, t.facingRight);
        float anchor = flipped ? 1 - foot.centerX() : foot.centerX();
        float left = flipped ? 1 - foot.left() - foot.width() : foot.left();
        float minX = t.x + w * (left - anchor);
        return x >= minX && x <= minX + w * foot.width()
                && y >= t.y - 6 && y <= t.y + h * foot.height();
    }

    /** Ponto acima do animal, para o balão de dica. */
    float[] animalAnchor(int index) {
        Thing animal = placed.get(index).animal();
        Texture texture = pose(animal);
        return new float[]{animal.x, animal.y + drawWidth(animal, texture) * texture.getHeight() / texture.getWidth()
                * assets.footprint(texture).height() - 40};
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
                t.walkDistance += dist;
                t.x = t.targetX;
                t.y = t.targetY;
                t.walking = false;
                t.wait = 2 + random.nextFloat() * 4;
            } else {
                t.walkDistance += step;
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
            if (Math.hypot(x - t.x, y - t.y) < t.radius * .6f) continue;
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
        Texture tex = pose(t);
        SpriteFootprint foot = assets.footprint(tex);
        float w = drawWidth(t, tex), h = w * tex.getHeight() / tex.getWidth();
        if (t.shadow) {
            Color previous = batch.getColor().cpy();
            batch.setColor(0, 0, 0, .26f);
            float shadowW = w * foot.width() * .65f, shadowH = shadowW * .16f;
            batch.draw(soft, t.x - shadowW / 2f, t.y - shadowH / 2f, shadowW, shadowH);
            batch.setColor(previous);
        }
        // Espelha em torno do apoio: a sombra permanece em (x, y) ao virar.
        boolean flipped = flipSprite(t.typeId, t.facingRight);
        float anchor = flipped ? 1 - foot.centerX() : foot.centerX();
        batch.draw(tex, t.x - w * anchor, t.y - h * foot.bottom(), w, h,
                0, 0, tex.getWidth(), tex.getHeight(), flipped, false);
        if (t.idle == assets.beehive) {
            for (int bee = 0; bee < 4; bee++) {
                BeeOffset offset = beeOffset(bee, t.phase);
                float bw = 16, bh = bw * assets.bee.getHeight() / assets.bee.getWidth();
                batch.draw(assets.bee, t.x + offset.x() - bw / 2, t.y + h - 8 + offset.y(), bw, bh);
            }
        }
        if (t.hungry) batch.draw(assets.feed, t.x - 9, t.y + h * foot.height() + 2, 18, 20);
    }

    private static Texture pose(Thing t) {
        if ("pavao".equals(t.typeId)) return alternatePose(t.typeId, false, 0, t.phase) ? t.walk : t.idle;
        return t.sleeping && t.sleep != null ? t.sleep
                : t.walk != null && alternatePose(t.typeId, t.walking, t.walkDistance, t.phase) ? t.walk : t.idle;
    }

    /** A cauda aberta ocupa mais largura, preservando a altura visível e a base do pavão. */
    private float drawWidth(Thing t, Texture texture) {
        if (!"pavao".equals(t.typeId) || texture == t.idle) return t.width;
        float height = t.width * t.idle.getHeight() / t.idle.getWidth() * assets.footprint(t.idle).height();
        return height * texture.getWidth() / texture.getHeight() / assets.footprint(texture).height();
    }

    static boolean roams(String id) {
        return !"pombo".equals(id) && !"pavao".equals(id) && !"abelha".equals(id);
    }

    static boolean flipSprite(String id, boolean facingRight) {
        boolean originalRight = id != null && switch (id) {
            case "cavalo", "cachorro-robo", "cachorro-mastiff", "cabra", "burro", "avestruz", "coelho" -> true;
            default -> false;
        };
        return facingRight != originalRight;
    }

    static boolean alternatePose(String id, boolean walking, float distance, float seconds) {
        if ("pavao".equals(id)) return (int) (seconds / 4) % 2 != 0;
        return roams(id) && walking && (int) (distance / 6) % 2 != 0;
    }

    record BeeOffset(float x, float y) {}

    /** Voo curto, com fases diferentes; nunca desloca a estrutura da colmeia. */
    static BeeOffset beeOffset(int bee, float seconds) {
        float phase = seconds * 1.4f + bee * 1.7f;
        return new BeeOffset((float) Math.sin(phase) * 28, (float) Math.cos(phase * 1.3f) * 12);
    }
}
