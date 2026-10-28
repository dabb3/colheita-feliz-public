package br.com.colheitafeliz.render;

import br.com.colheitafeliz.domain.crop.CropCatalog;
import br.com.colheitafeliz.domain.crop.CropType;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GameAssets implements AutoCloseable {
    /** Maior lado da arte de animal na GPU; os quadros antigos tinham ~460 px e na tela não passam de ~160. */
    private static final int ANIMAL_SIDE = 512;
    static final int WEED_STAGES = 4;
    static final int INSECT_STAGES = 3;
    private final List<Texture> textures = new ArrayList<>();
    private final Map<String, Texture[]> crops = new HashMap<>();
    private final Map<String, Texture> seeds = new HashMap<>();
    private final Map<Texture, Float> footCenters = new HashMap<>();
    public final Texture background = load("classic/background/background.png");
    public final Texture house = load("classic/buildings/main-house.png");
    public final Texture dogHouse = load("classic/buildings/dog-house.png");
    /** Mourão e tábua da cerca do cercado (gerados por tools/art/fence.py). */
    public final Texture fencePost = load("classic/fence/mourao.png");
    public final Texture fenceRail = load("classic/fence/trilho.png");
    private final Texture[] cowFrames = animalFrames("classic/animals/vaca/vaca.png", "classic/animals/vaca/vaca-andando.png");
    public final Texture cow = cowFrames[0];
    public final Texture cowWalk = cowFrames[1];
    private final Texture[] sheepFrames = animalFrames("classic/animals/ovelha/ovelha.png", "classic/animals/ovelha/ovelha-andando.png");
    public final Texture sheep = sheepFrames[0];
    public final Texture sheepWalk = sheepFrames[1];
    private final Texture[] chickenFrames = animalFrames("classic/animals/galinha/galinha.png", "classic/animals/galinha/galinha-andando.png");
    public final Texture chicken = chickenFrames[0];
    public final Texture chickenWalk = chickenFrames[1];
    public final Texture duck = animalSprite("classic/animals/pato/pato.png");
    private final Texture[] dogFrames = animalFrames("classic/animals/cachorro/cachorro.png", "classic/animals/cachorro/cachorro-andando.png");
    public final Texture dog = dogFrames[0];
    public final Texture dogWalk = dogFrames[1];
    public final Texture donkeySleeping = animalSprite("classic/animals/burrinho/burrinho-dormindo.png");
    public final Texture rabbit = animalSprite("classic/animals/coelho/coelho.png");
    public final Texture carrot = animalSprite("classic/animals/coelho/cenoura-coelho.png");
    public final Texture nestEggs = animalSprite("classic/animals/ninho-geral/ninho-com-ovos.png");
    public final Texture nestEmpty = animalSprite("classic/animals/ninho-geral/ninho-vazio.png");
    public final Texture bucket = animalSprite("classic/animals/vaca/balde-vaca.png");
    public final Texture woolBasket = animalSprite("classic/animals/ovelha/la-ovelha.png");
    public final Texture bowl = animalSprite("classic/animals/cachorro/potinho-cachorro-com-comida.png");
    public final Texture[] weeds = new Texture[WEED_STAGES];
    /** Minhocas por quantidade: insects[0] = 1 minhoca ... insects[2] = 3. */
    public final Texture[] insects = new Texture[INSECT_STAGES];
    public final Texture tileGrass = load("classic/terrain/grama-default.png");
    public final Texture tilePlowed = load("classic/terrain/terra-arada.png");
    public final Texture tileWet = load("classic/terrain/terra-molhada.png");
    public final Texture tileDry = load("classic/terrain/terra-seca.png");
    public final Texture tileHighlight = load("classic/terrain/terra-destaque.png");
    public final Texture cursor = load("classic/icons/hud/cursor-ui.png");
    public final Texture hoe = load("classic/icons/hud/pa-ui.png");
    public final Texture bag = load("classic/icons/hud/saco-ui.png");
    public final Texture hand = load("classic/icons/hud/mao-restaurada.png");
    public final Texture water = load("classic/icons/hud/regador-ui.png");
    /** Herbicida (frasco verde): remove ervas daninhas. */
    public final Texture weeder = load("classic/icons/hud/herbicida-ui.png");
    /** Pesticida (frasco vermelho): remove insetos. */
    public final Texture pesticide = load("classic/icons/hud/pesticida-ui.png");
    public final Texture coin = load("classic/icons/moeda-comum.png");
    public final Texture greenCoin = load("classic/icons/moeda-verde.png");
    private final Map<String, Texture> fertilizers = Map.of(
            "comum", load("classic/icons/adubo-comum.png"), "rapido", load("classic/icons/adubo-rapido.png"),
            "super", load("classic/icons/adubo-super.png"), "amor", load("classic/icons/adubo-amor.png"));
    public final Texture vipCard = load("classic/icons/cartao-vip.png");
    public final Texture bell = generated(HudIcons.bell());
    public final Texture gear = generated(HudIcons.gear());
    public final Texture clipboard = generated(HudIcons.clipboard());
    public final Texture star = load("classic/icons/xp.png");
    public final Texture gift = load("classic/icons/asset_presente.jpg");
    public final Texture shop = load("classic/icons/icon-shop.png");
    public final Texture deposit = load("classic/icons/deposit-icon.png");
    public final Texture avatar = load("classic/icons/avatar_masculino.png");
    public final BitmapFont font;

    public GameAssets(CropCatalog catalog) {
        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal("fonts/Lato-Regular.ttf"));
        FreeTypeFontGenerator.FreeTypeFontParameter fontParameters = new FreeTypeFontGenerator.FreeTypeFontParameter();
        fontParameters.size = 20;
        fontParameters.characters = FreeTypeFontGenerator.DEFAULT_CHARS
                + "áàâãéêíóôõúçÁÀÂÃÉÊÍÓÔÕÚÇ";
        fontParameters.minFilter = Texture.TextureFilter.Linear;
        fontParameters.magFilter = Texture.TextureFilter.Linear;
        font = generator.generateFont(fontParameters);
        generator.dispose();
        for (int i = 0; i < weeds.length; i++)
            weeds[i] = load(weedPath(i + 1));
        for (int i = 0; i < insects.length; i++)
            insects[i] = load(insectPath(i + 1));
        for (CropType type : catalog.all()) {
            Texture[] stages = new Texture[type.stages()];
            for (int i = 0; i < stages.length; i++)
                stages[i] = load(cropPath(type.spriteStem(), i + 1));
            crops.put(type.id(), stages);
            seeds.put(type.id(), load(seedPath(type.spriteStem())));
        }
    }

    static String weedPath(int count) { return "classic/effects/erva_daninha_" + count + ".png"; }
    static String insectPath(int count) { return "classic/effects/verme_" + count + ".png"; }
    static String cropPath(String stem, int stage) { return "classic/crops/plants_phase_one_to_five/" + stem + "_" + stage + ".png"; }
    static String seedPath(String stem) { return "classic/crops/seeds/semente_" + stem + ".png"; }

    public Texture crop(CropType type, int stage) { return crops.get(type.id())[stage - 1]; }

    /** Ícone de um animal produtivo pelo id do catálogo de animais. */
    public Texture animal(String id) {
        return switch (id) { case "pato" -> duck; case "ovelha" -> sheep; case "vaca" -> cow; default -> chicken; };
    }

    /** Ícone do produto de um animal: ovos no ninho, lã no cesto, leite no balde. */
    public Texture product(String id) {
        return switch (id) { case "ovelha" -> woolBasket; case "vaca" -> bucket; default -> nestEggs; };
    }
    public Texture seed(CropType type) { return seeds.get(type.id()); }

    /** Ícone de um fertilizante pelo id do catálogo. */
    public Texture fertilizer(String id) {
        Texture icon = fertilizers.get(id);
        if (icon == null) throw new IllegalArgumentException("No icon for fertilizer " + id);
        return icon;
    }

    public Texture tool(br.com.colheitafeliz.game.Tool tool) {
        return switch (tool) {
            case CURSOR -> cursor;
            case HAND -> hand;
            case HOE -> hoe;
            case WATER -> water;
            case SEED -> bag;
            case WEEDER -> weeder;
            case PESTICIDE -> pesticide;
            case FERTILIZER -> fertilizers.get("comum");
        };
    }

    private Texture load(String path) {
        return register(new Texture(Gdx.files.internal(path), true));
    }

    private Texture animalSprite(String path) { return animalFrames(path)[0]; }

    /**
     * Arte de animal vem em telas grandes com margem. Recorta todas pela mesma caixa (a união da arte),
     * para o quadro parado e o andando manterem escala e apoio ao trocar, e reduz para caber em ANIMAL_SIDE.
     */
    private Texture[] animalFrames(String... paths) {
        Pixmap[] frames = new Pixmap[paths.length];
        int left = Integer.MAX_VALUE, top = Integer.MAX_VALUE, right = -1, bottom = -1;
        for (int i = 0; i < paths.length; i++) {
            Pixmap pixels = frames[i] = new Pixmap(Gdx.files.internal(paths[i]));
            // Ignora resíduos quase invisíveis ao localizar a base real da arte.
            for (int y = 0; y < pixels.getHeight(); y++)
                for (int x = 0; x < pixels.getWidth(); x++)
                    if ((pixels.getPixel(x, y) & 0xff) > 12) {
                        left = Math.min(left, x);
                        right = Math.max(right, x);
                        top = Math.min(top, y);
                        bottom = Math.max(bottom, y);
                    }
        }
        Texture[] textures = new Texture[paths.length];
        for (int i = 0; i < paths.length; i++) {
            Pixmap pixels = frames[i];
            if (right >= left) {
                Pixmap cropped = new Pixmap(right - left + 1, bottom - top + 1, Pixmap.Format.RGBA8888);
                cropped.setBlending(Pixmap.Blending.None);
                cropped.drawPixmap(pixels, 0, 0, left, top, cropped.getWidth(), cropped.getHeight());
                pixels.dispose();
                pixels = cropped;
            }
            pixels = shrink(pixels);
            Texture texture = textures[i] = register(new Texture(pixels, true));
            // Cabeça e cauda deslocam o centro da imagem; o apoio vem da faixa inferior.
            int footLeft = pixels.getWidth(), footRight = -1;
            int band = Math.max(1, Math.round(pixels.getHeight() * .12f));
            for (int y = pixels.getHeight() - band; y < pixels.getHeight(); y++)
                for (int x = 0; x < pixels.getWidth(); x++)
                    if ((pixels.getPixel(x, y) & 0xff) > 12) {
                        footLeft = Math.min(footLeft, x);
                        footRight = Math.max(footRight, x);
                    }
            footCenters.put(texture, footRight >= footLeft ? (footLeft + footRight + 1f) / (2f * pixels.getWidth()) : .5f);
            pixels.dispose();
        }
        return textures;
    }

    /** Reduz pela metade até o maior lado caber em ANIMAL_SIDE; passos de 2x evitam o serrilhado do bilinear. */
    private static Pixmap shrink(Pixmap pixels) {
        while (Math.max(pixels.getWidth(), pixels.getHeight()) > ANIMAL_SIDE) {
            float scale = Math.max(.5f, (float) ANIMAL_SIDE / Math.max(pixels.getWidth(), pixels.getHeight()));
            Pixmap smaller = new Pixmap(Math.max(1, Math.round(pixels.getWidth() * scale)),
                    Math.max(1, Math.round(pixels.getHeight() * scale)), Pixmap.Format.RGBA8888);
            smaller.setBlending(Pixmap.Blending.None);
            smaller.setFilter(Pixmap.Filter.BiLinear);
            smaller.drawPixmap(pixels, 0, 0, pixels.getWidth(), pixels.getHeight(), 0, 0, smaller.getWidth(), smaller.getHeight());
            pixels.dispose();
            pixels = smaller;
        }
        return pixels;
    }

    private Texture register(Texture texture) {
        texture.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        textures.add(texture);
        return texture;
    }

    private Texture generated(Pixmap pixels) {
        Texture texture = register(new Texture(pixels, true));
        pixels.dispose();
        return texture;
    }

    float footCenter(Texture texture) { return footCenters.getOrDefault(texture, .5f); }

    @Override public void close() {
        for (Texture texture : textures) texture.dispose();
        font.dispose();
    }
}
