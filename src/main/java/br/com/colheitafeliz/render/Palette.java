package br.com.colheitafeliz.render;

import com.badlogic.gdx.graphics.Color;

/**
 * Cores do design system (docs/design-system/tokens.json), mesmos nomes em UPPER_SNAKE.
 * Renderers e painéis pegam cor daqui; nada de {@code new Color(...)} solto.
 */
public final class Palette {
    private Palette() {}

    public static final Color WINDOW = rgb("ffffff");
    public static final Color WINDOW_2 = rgb("eef1f3");
    public static final Color CHROME_HI = rgb("f8f9fa");
    public static final Color CHROME = rgb("e3e7ea");
    public static final Color CHROME_LO = rgb("c8ced3");
    public static final Color CHROME_LINE = rgb("a7b0b7");
    public static final Color INK = rgb("2f3a42");
    public static final Color INK_MUTED = rgb("56616a");

    public static final Color LEAF_HI = rgb("b5ec6a");
    public static final Color LEAF = rgb("6cc22e");
    public static final Color LEAF_LO = rgb("3a8a14");
    public static final Color LEAF_INK = rgb("2b6e0e");
    public static final Color SUN_HI = rgb("ffe97a");
    public static final Color SUN = rgb("ffc21a");
    public static final Color SUN_LO = rgb("f29c00");
    public static final Color SUN_INK = rgb("a65f00");
    public static final Color INK_WARM = rgb("4a2a00");
    public static final Color SKY_HI = rgb("9fe0ff");
    public static final Color SKY = rgb("2fa8e8");
    public static final Color SKY_LO = rgb("1680c9");
    public static final Color SKY_INK = rgb("0c5a94");
    public static final Color BERRY = rgb("e8352b");
    public static final Color BERRY_HI = rgb("ff8a7f");
    public static final Color BERRY_LO = rgb("c62a20");
    public static final Color BERRY_INK = rgb("8f1610");
    public static final Color COIN = rgb("f7c600");
    public static final Color COIN_INK = rgb("9b5a00");

    public static final Color TOOLTIP = rgb("fff6c2");
    public static final Color TOOLTIP_LINE = rgb("d9c46a");
    public static final Color REWARD_HI = rgb("fffbe3");
    public static final Color REWARD = rgb("fff1b3");
    public static final Color REWARD_LINE = rgb("e0bf4a");
    public static final Color TRAY = rgba("181c14", .52f);
    public static final Color TRAY_LINE = rgba("ffffff", .22f);
    public static final Color GLOW = rgba("ffec96", .90f);
    public static final Color HUD_CARD = rgba("ffffff", .82f);
    public static final Color ON_COLOR = rgb("ffffff");
    public static final Color SCRIM = rgba("14181c", .45f);
    public static final Color SHADOW = rgba("000000", .28f);

    public static final Color WOOD_HI = rgb("d8a866");
    public static final Color WOOD = rgb("b07a3a");
    public static final Color WOOD_INK = rgb("6e4520");
    public static final Color WEED = rgb("4f7d1f");
    /** Tinta da planta em terra seca: amarelada, sem chegar a parecer morta. */
    public static final Color DRY_PLANT = rgb("e8d79a");
    public static final Color DEAD_PLANT = rgb("b5894f");

    private static Color rgb(String hex) { return Color.valueOf(hex); }

    private static Color rgba(String hex, float alpha) {
        Color color = Color.valueOf(hex);
        color.a = alpha;
        return color;
    }
}
