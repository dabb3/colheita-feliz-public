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
    private final List<Texture> textures = new ArrayList<>();
    private final Map<String, Texture[]> crops = new HashMap<>();
    private final Map<String, Texture> seeds = new HashMap<>();
    private final Map<Texture, Float> footCenters = new HashMap<>();
    public final Texture background = load("classic/background/background.png");
    public final Texture grass = load("sprites/tiles/tile_grama.png");
    public final Texture darkGrass = load("sprites/tiles/tile_grama_escuro.png");
    public final Texture soil = load("sprites/tiles/tile_terra.png");
    public final Texture drySoil = load("sprites/tiles/tile_terra_seca.png");
    public final Texture house = load("classic/buildings/main-house.png");
    public final Texture dogHouse = load("classic/buildings/dog-house.png");
    /** Mourão e tábua da cerca do cercado (gerados por tools/art/fence.py). */
    public final Texture fencePost = load("sprites/fence/mourao.png");
    public final Texture fenceRail = load("sprites/fence/trilho.png");
    public final Texture cow = load("sprites/animals/normalized/vaca.png");
    public final Texture cowWalk = load("sprites/animals/normalized/vaca_andando.png");
    public final Texture sheep = load("sprites/animals/normalized/ovelha.png");
    public final Texture sheepWalk = load("sprites/animals/normalized/ovelha_andando.png");
    public final Texture chicken = load("sprites/animals/normalized/galinha.png");
    public final Texture chickenWalk = load("sprites/animals/normalized/galinha_andando.png");
    public final Texture duck = load("sprites/animals/pato.png");
    public final Texture dog = load("sprites/animals/normalized/cachorro.png");
    public final Texture dogWalk = load("sprites/animals/normalized/cachorro_andando.png");
    public final Texture donkeySleeping = load("sprites/animals/burrinho_dormindo.png");
    public final Texture rabbit = load("sprites/animals/coelho.png");
    public final Texture carrot = load("sprites/animals/cenoura.png");
    public final Texture nestEggs = load("sprites/animals/ninho_com_ovos.png");
    public final Texture nestEmpty = load("sprites/animals/ninho_vazio.png");
    public final Texture bucket = load("sprites/animals/balde.png");
    public final Texture woolBasket = load("sprites/animals/cesto_de_la.png");
    public final Texture bowl = load("sprites/animals/potinho.png");
    public final Texture[] weeds = new Texture[4];
    /** Minhocas por quantidade: insects[0] = 1 minhoca ... insects[2] = 3. */
    public final Texture[] insects = new Texture[3];
    public final Texture tileGrass = load("classic/terrain/grama-default.png");
    public final Texture tilePlowed = load("classic/terrain/terra-arada.png");
    public final Texture tileWet = load("classic/terrain/terra-molhada.png");
    public final Texture tileDry = load("classic/terrain/terrain_dry.png");
    public final Texture tileHighlight = load("classic/terrain/terrain_highlight.png");
    public final Texture hoe = load("classic/hud/pa-ui.png");
    public final Texture bag = load("classic/hud/saco-ui.png");
    public final Texture hand = load("classic/hud/mao-restaurada.png");
    public final Texture water = load("classic/hud/regador-ui.png");
    /** Herbicida (frasco verde): remove ervas daninhas. */
    public final Texture weeder = load("classic/hud/herbicida-ui.png");
    /** Pesticida (frasco vermelho): remove insetos. */
    public final Texture pesticide = load("classic/hud/pesticida-ui.png");
    public final Texture coin = load("classic/avatar-ui/moeda-comum.png");
    public final Texture star = load("classic/ui/icon_xp.png");
    public final Texture gift = load("classic/ui/icon_gift.png");
    public final Texture shop = load("classic/others-ui/icon-shop.png");
    public final Texture deposit = load("classic/others-ui/deposit-icon.png");
    public final Texture statusPanel = load("sprites/icons/hud/esquerdo/painel_status.png");
    public final Texture coinIcon = load("sprites/icons/hud/esquerdo/ouro.png");
    public final Texture xpIcon = load("sprites/icons/hud/esquerdo/xp.png");
    public final Texture avatar = load("sprites/icons/hud/esquerdo/avatar_masculino.png");
    public final Texture shopIcon = load("sprites/icons/hud/direito/loja.png");
    public final Texture inventoryIcon = load("sprites/icons/hud/direito/armazen.png");
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
            weeds[i] = load("sprites/effects/erva_daninha_" + (i + 1) + ".png");
        for (int i = 0; i < insects.length; i++)
            insects[i] = load("sprites/effects/verme_" + (i + 1) + ".png");
        for (CropType type : catalog.all()) {
            Texture[] stages = new Texture[type.stages()];
            for (int i = 0; i < stages.length; i++)
                stages[i] = load("sprites/crops/" + type.spriteStem() + "_" + (i + 1) + ".png");
            crops.put(type.id(), stages);
            seeds.put(type.id(), load("sprites/icons/seeds/semente_" + type.spriteStem() + ".png"));
        }
    }

    public Texture crop(CropType type, int stage) { return crops.get(type.id())[stage - 1]; }
    public Texture seed(CropType type) { return seeds.get(type.id()); }

    public Texture tool(br.com.colheitafeliz.game.Tool tool) {
        return switch (tool) {
            case HAND -> hand;
            case HOE -> hoe;
            case WATER -> water;
            case SEED -> bag;
            case WEEDER -> weeder;
            case PESTICIDE -> pesticide;
        };
    }

    private Texture load(String path) {
        Texture texture;
        if (path.startsWith("sprites/animals/")) {
            Pixmap pixels = new Pixmap(Gdx.files.internal(path));
            if (!path.startsWith("sprites/animals/normalized/")) {
                // Ignora resíduos quase invisíveis ao localizar a base real da arte.
                int left = pixels.getWidth(), top = pixels.getHeight(), right = -1, bottom = -1;
                for (int y = 0; y < pixels.getHeight(); y++)
                    for (int x = 0; x < pixels.getWidth(); x++)
                        if ((pixels.getPixel(x, y) & 0xff) > 12) {
                            left = Math.min(left, x);
                            right = Math.max(right, x);
                            top = Math.min(top, y);
                            bottom = Math.max(bottom, y);
                        }
                if (right >= left) {
                    Pixmap cropped = new Pixmap(right - left + 1, bottom - top + 1, Pixmap.Format.RGBA8888);
                    cropped.setBlending(Pixmap.Blending.None);
                    cropped.drawPixmap(pixels, 0, 0, left, top, cropped.getWidth(), cropped.getHeight());
                    pixels.dispose();
                    pixels = cropped;
                }
            }
            texture = new Texture(pixels, true);
            // Cabeça e cauda deslocam o centro da imagem; o apoio vem da faixa inferior.
            int left = pixels.getWidth(), right = -1;
            int band = Math.max(1, Math.round(pixels.getHeight() * .12f));
            for (int y = pixels.getHeight() - band; y < pixels.getHeight(); y++)
                for (int x = 0; x < pixels.getWidth(); x++)
                    if ((pixels.getPixel(x, y) & 0xff) > 12) {
                        left = Math.min(left, x);
                        right = Math.max(right, x);
                    }
            footCenters.put(texture, right >= left ? (left + right + 1f) / (2f * pixels.getWidth()) : .5f);
            pixels.dispose();
        } else {
            texture = new Texture(Gdx.files.internal(path), true);
        }
        texture.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        textures.add(texture);
        return texture;
    }

    float footCenter(Texture texture) { return footCenters.getOrDefault(texture, .5f); }

    @Override public void close() {
        for (Texture texture : textures) texture.dispose();
        font.dispose();
    }
}
