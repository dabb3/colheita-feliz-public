package br.com.colheitafeliz.render;

import br.com.colheitafeliz.domain.animal.Animal;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Cenário vivo, como na fazenda do Orkut: casa e casinha do cachorro no alto, cachorro com o potinho, burrinho
 * cochilando, coelho com a cenoura e os animais passeando dentro do cercado. As galinhas são as do jogador:
 * cada uma tem um ninho, que mostra os ovos quando estão prontos; vaca, ovelha e pato ainda são decoração.
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
        final Texture walk;
        final float width, homeX, homeY, radius, speed;
        float footCenter = .5f;
        final Layer layer;
        final boolean shadow;
        final boolean insidePen;
        float x, y, targetX, targetY, wait;
        boolean walking, facingRight;
        float phase;

        Thing(Texture idle, Texture walk, float x, float y, float width, float radius, float speed,
              Layer layer, boolean shadow, boolean insidePen, boolean facingRight) {
            this.idle = idle;
            this.walk = walk;
            this.width = width;
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

    /** Ninhos das galinhas do jogador, na ordem de compra; cada galinha passeia perto do seu. */
    static final float[][] NESTS = {{1296, 338}, {1496, 306}, {1186, 372}};
    private static final float NEST_W = 76;

    private final List<Thing> things = new ArrayList<>();
    private final List<Thing> nests = new ArrayList<>();
    private final List<Thing> hens = new ArrayList<>();
    private final GameAssets assets;
    private final Pen pen;
    private final Random random = new Random(7);
    private final Texture soft;
    private long lastNanos = -1;

    Scenery(GameAssets a, Pen pen, Texture softShadow) {
        this.pen = pen;
        this.soft = softShadow;
        this.assets = a;
        // Casas no alto, a casinha do cachorro à esquerda da casa.
        add(a.dogHouse, null, 410, 556, 214, 0, 0, Layer.BACK, false, false, false);
        add(a.house, null, 735, 540, 420, 0, 0, Layer.BACK, false, false, false);
        // Cachorro, potinho e burrinho na frente das casas.
        add(a.bowl, null, 318, 544, 46, 0, 0, Layer.BACK, true, false, false);
        add(a.dog, a.dogWalk, 384, 530, 92, 40, 34, Layer.BACK, true, false, true);
        add(a.donkeySleeping, null, 222, 540, 96, 0, 0, Layer.BACK, true, false, false);
        // Dentro do cercado.
        add(a.bucket, null, 1514, 424, 40, 0, 0, Layer.BACK, true, true, false);
        add(a.woolBasket, null, 1316, 470, 44, 0, 0, Layer.BACK, true, true, false);
        add(a.cow, a.cowWalk, 1436, 462, 158, 40, 16, Layer.BACK, true, true, false);
        add(a.sheep, a.sheepWalk, 1222, 460, 96, 32, 18, Layer.BACK, true, true, true);
        add(a.duck, null, 1540, 352, 48, 0, 0, Layer.BACK, true, true, false);
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

    /** Uma galinha e um ninho por animal do jogador; o ninho mostra ovos quando estão prontos. */
    void syncHens(List<Animal.Status> statuses) {
        int count = Math.min(statuses.size(), NESTS.length);
        while (hens.size() < count) {
            float[] nest = NESTS[hens.size()];
            nests.add(add(assets.nestEmpty, null, nest[0], nest[1], NEST_W, 0, 0, Layer.BACK, false, true, false));
            Thing hen = add(assets.chicken, assets.chickenWalk, nest[0] - 70, nest[1] + 40, 58, 45, 32,
                    Layer.BACK, true, true, hens.size() % 2 == 0);
            hen.footCenter = assets.footCenter(hen.idle);
            hen.wait = random.nextFloat() * 3;
            hens.add(hen);
        }
        while (hens.size() > count) {
            things.remove(hens.removeLast());
            things.remove(nests.removeLast());
        }
        for (int i = 0; i < count; i++)
            nests.get(i).idle = statuses.get(i) == Animal.Status.READY ? assets.nestEggs : assets.nestEmpty;
    }

    /** Índice da galinha (ou do ninho dela) sob o ponto, ou -1. */
    int henAt(float x, float y) {
        for (int i = 0; i < hens.size(); i++) {
            Thing nest = nests.get(i), hen = hens.get(i);
            if (Math.abs(x - nest.x) <= NEST_W / 2f && y >= nest.y - 6 && y <= nest.y + NEST_W * .7f) return i;
            float h = hen.width * hen.idle.getHeight() / hen.idle.getWidth();
            if (Math.abs(x - hen.x) <= hen.width / 2f && y >= hen.y - 6 && y <= hen.y + h) return i;
        }
        return -1;
    }

    /** Ponto acima do ninho, para o balão de dica. */
    float[] henAnchor(int index) {
        Thing nest = nests.get(index);
        return new float[]{nest.x, nest.y + 20};
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
            if (t.walk == null || t.speed <= 0) continue;
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
        Texture tex = t.walking && t.walk != null && ((int) (t.phase * 4) % 2 == 0) ? t.walk : t.idle;
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
    }
}
