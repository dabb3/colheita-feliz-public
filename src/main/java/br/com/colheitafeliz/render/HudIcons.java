package br.com.colheitafeliz.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;

/**
 * Ícones da HUD que ainda não têm arte: desenhados com formas simples e contorno escuro, em 128 × 128.
 * shortcut: formas geométricas, trocar por arte quando houver.
 */
final class HudIcons {
    private static final int S = 128;
    private static final Color OUTLINE = Color.valueOf("3b2a14");

    private HudIcons() {}

    static Pixmap bell() {
        Pixmap p = blank();
        p.setColor(Color.valueOf("f5c242"));
        p.fillCircle(64, 20, 9);
        p.fillCircle(64, 60, 34);
        p.fillRectangle(30, 60, 69, 30);
        p.fillRectangle(20, 86, 89, 12);
        p.setColor(Color.valueOf("c98a1c"));
        p.fillCircle(64, 106, 11);
        return outlined(p);
    }

    static Pixmap music() {
        Pixmap p = blank();
        p.setColor(Color.valueOf("8a63e8"));
        p.fillCircle(40, 98, 16);
        p.fillCircle(92, 86, 16);
        p.fillRectangle(48, 30, 9, 68);
        p.fillRectangle(100, 18, 9, 68);
        p.fillTriangle(48, 30, 109, 18, 109, 34);
        p.fillTriangle(48, 30, 48, 46, 109, 34);
        return outlined(p);
    }

    static Pixmap sound() {
        Pixmap p = blank();
        p.setColor(Color.valueOf("3a8ad8"));
        ring(p, 76, 64, 50, 40);
        ring(p, 76, 64, 32, 23);
        clear(p, 0, 0, 84, S);
        p.setColor(Color.valueOf("3a8ad8"));
        p.fillRectangle(16, 46, 30, 36);
        p.fillTriangle(44, 46, 80, 16, 80, 112);
        p.fillTriangle(44, 46, 44, 82, 80, 112);
        return outlined(p);
    }

    static Pixmap gear() {
        Pixmap p = blank();
        p.setColor(Color.valueOf("9aa3ad"));
        p.fillCircle(64, 64, 38);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4, w = .2;
            int x1 = (int) (64 + 36 * Math.cos(a - w)), y1 = (int) (64 + 36 * Math.sin(a - w));
            int x2 = (int) (64 + 36 * Math.cos(a + w)), y2 = (int) (64 + 36 * Math.sin(a + w));
            int x3 = (int) (64 + 54 * Math.cos(a + w * .7)), y3 = (int) (64 + 54 * Math.sin(a + w * .7));
            int x4 = (int) (64 + 54 * Math.cos(a - w * .7)), y4 = (int) (64 + 54 * Math.sin(a - w * .7));
            p.fillTriangle(x1, y1, x2, y2, x3, y3);
            p.fillTriangle(x1, y1, x3, y3, x4, y4);
        }
        p.setBlending(Pixmap.Blending.None);
        p.setColor(0, 0, 0, 0);
        p.fillCircle(64, 64, 15);
        p.setBlending(Pixmap.Blending.SourceOver);
        return outlined(p);
    }

    static Pixmap clipboard() {
        Pixmap p = blank();
        p.setColor(Color.valueOf("b0773a"));
        p.fillRectangle(22, 14, 84, 106);
        p.setColor(Color.WHITE);
        p.fillRectangle(32, 28, 64, 84);
        p.setColor(Color.valueOf("8d939a"));
        p.fillRectangle(46, 6, 36, 20);
        for (int i = 0; i < 3; i++) {
            int y = 44 + i * 22;
            p.setColor(Color.valueOf("3a9a2a"));
            p.fillRectangle(40, y, 12, 12);
            p.setColor(Color.valueOf("b9bec4"));
            p.fillRectangle(58, y + 3, 30, 6);
        }
        return outlined(p);
    }

    private static Pixmap blank() {
        Pixmap p = new Pixmap(S, S, Pixmap.Format.RGBA8888);
        p.setColor(0, 0, 0, 0);
        p.fill();
        return p;
    }

    private static void ring(Pixmap p, int x, int y, int outer, int inner) {
        p.fillCircle(x, y, outer);
        p.setBlending(Pixmap.Blending.None);
        p.setColor(0, 0, 0, 0);
        p.fillCircle(x, y, inner);
        p.setBlending(Pixmap.Blending.SourceOver);
    }

    private static void clear(Pixmap p, int x, int y, int w, int h) {
        p.setBlending(Pixmap.Blending.None);
        p.setColor(0, 0, 0, 0);
        p.fillRectangle(x, y, w, h);
        p.setBlending(Pixmap.Blending.SourceOver);
    }

    /** Contorno escuro de 4 px em volta de tudo que foi desenhado, como nos ícones clássicos. */
    private static Pixmap outlined(Pixmap p) {
        Pixmap out = blank();
        int line = Color.rgba8888(OUTLINE);
        for (int y = 0; y < S; y++)
            for (int x = 0; x < S; x++) {
                if ((p.getPixel(x, y) & 0xff) != 0) { out.drawPixel(x, y, p.getPixel(x, y)); continue; }
                search:
                for (int dy = -4; dy <= 4; dy++)
                    for (int dx = -4; dx <= 4; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if (dx * dx + dy * dy > 16 || nx < 0 || ny < 0 || nx >= S || ny >= S) continue;
                        if ((p.getPixel(nx, ny) & 0xff) != 0) { out.drawPixel(x, y, line); break search; }
                    }
            }
        p.dispose();
        return out;
    }
}
