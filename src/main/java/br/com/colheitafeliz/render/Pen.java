package br.com.colheitafeliz.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;

/**
 * Cercado dos animais, no desenho do Colheita Feliz do Orkut: a cerca da frente desce em diagonal, paralela
 * ao campo; a do fundo é curta, quase reta, e fica abaixo da mata. Entre o começo das duas fica a entrada,
 * virada para a casa.
 *
 * <p>Cada cerca é uma lista de mourões (posição da base, em coordenadas lógicas). Entre dois mourões vizinhos
 * vão duas tábuas ({@code trilho.png}) esticadas e giradas no ângulo certo; os mourões ({@code mourao.png})
 * são desenhados por cima das pontas. Assim a cerca pode fazer curvas leves e ter espaçamento irregular, sem
 * parecer uma grade.
 */
final class Pen {
    /** Cerca do fundo: começa à direita da entrada, sobe de leve e vai até a borda da tela. */
    private static final float[][] BACK = {
            {1108, 494}, {1180, 509}, {1262, 521}, {1350, 528}, {1440, 530}, {1530, 525}, {1620, 517}};
    /** Cerca da frente: começa ao lado da casa, desce em diagonal e vai se aplainando, espelhando a curva da cerca do fundo. */
    private static final float[][] FRONT = {
            {986, 410}, {1072, 370}, {1160, 334}, {1250, 304}, {1342, 280}, {1434, 262}, {1526, 250}, {1618, 244}};
    private static final float POST_W = 25, POST_H = 76, RAIL_H = 10;
    /** Alturas das duas tábuas, como fração da altura do mourão. */
    private static final float[] RAILS = {.34f, .64f};

    private final Texture post, rail, soft;

    Pen(Texture post, Texture rail, Texture softShadow) {
        this.post = post;
        this.rail = rail;
        this.soft = softShadow;
    }

    /** Cerca do fundo: desenhada antes dos animais. */
    void drawBack(SpriteBatch batch) { fence(batch, BACK, .9f); }

    /** Cerca da frente: desenhada depois dos animais. */
    void drawFront(SpriteBatch batch) { fence(batch, FRONT, 1f); }

    /** Se o ponto (pés de um animal) está dentro do cercado, com uma folga das cercas. */
    boolean inside(float x, float y, float margin) {
        if (x < BACK[0][0] + margin * .5f || x > 1600 - margin) return false;
        return y > lineY(FRONT, x) + margin * .6f + 6 && y < lineY(BACK, x) - 14;
    }

    private static float lineY(float[][] posts, float x) {
        for (int i = 0; i < posts.length - 1; i++) {
            if (x <= posts[i + 1][0]) {
                float t = (x - posts[i][0]) / (posts[i + 1][0] - posts[i][0]);
                return MathUtils.lerp(posts[i][1], posts[i + 1][1], MathUtils.clamp(t, 0, 1));
            }
        }
        return posts[posts.length - 1][1];
    }

    private void fence(SpriteBatch batch, float[][] posts, float scale) {
        float h = POST_H * scale, w = POST_W * scale;
        Color previous = batch.getColor().cpy();
        batch.setColor(0, 0, 0, .24f);
        for (float[] p : posts) batch.draw(soft, p[0] - w * 1.2f, p[1] - 5.5f * scale, w * 2.4f, 11 * scale);
        batch.setColor(previous);
        for (int i = 0; i < posts.length - 1; i++) {
            float[] a = posts[i], b = posts[i + 1];
            float dx = b[0] - a[0], dy = b[1] - a[1];
            float length = (float) Math.hypot(dx, dy);
            float angle = MathUtils.atan2(dy, dx) * MathUtils.radiansToDegrees;
            for (float level : RAILS) {
                float sag = level < .5f ? 0 : 1.5f;
                batch.draw(rail, a[0], a[1] + h * level - RAIL_H * scale / 2f - sag, 0, RAIL_H * scale / 2f,
                        length, RAIL_H * scale, 1, 1, angle, 0, 0, rail.getWidth(), rail.getHeight(), false, false);
            }
        }
        for (float[] p : posts) batch.draw(post, p[0] - w / 2f, p[1] - 3 * scale, w, h);
    }
}
