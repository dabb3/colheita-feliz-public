import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Run from the repository root with Java 21: java web/scripts/WebAssets.java. */
class WebAssets {
    public static void main(String[] args) throws Exception {
        resize("buildings/main-house.png", 600);
        resize("buildings/dog-house.png", 320);
        for (String id : new String[]{"avatar_masculino", "avatar_masculino2", "avatar_masculino3", "avatar_feminino", "avatar_feminino2", "avatar_feminino3", "amigos", "moeda-comum", "moeda-verde", "icon-shop", "deposit-icon"})
            resize("icons/" + id + ".png", 128);
        for (String id : new String[]{"comum", "rapido", "super", "amor"}) resize("icons/adubo-" + id + ".png", 96);
        for (String id : new String[]{"pa-ui", "saco-ui", "regador-ui", "mao-restaurada", "herbicida-ui", "pesticida-ui"})
            resize("icons/hud/" + id + ".png", 96);
        cursor();
    }
    private static void cursor() throws Exception {
        var source = ImageIO.read(Path.of("assets/classic/icons/cursor-action.png").toFile());
        int left = source.getWidth(), top = source.getHeight(), right = -1, bottom = -1;
        for (int y = 0; y < source.getHeight(); y++) for (int x = 0; x < source.getWidth(); x++) {
            if ((source.getRGB(x, y) >>> 24) > 20) {
                left = Math.min(left, x); top = Math.min(top, y);
                right = Math.max(right, x); bottom = Math.max(bottom, y);
            }
        }
        if (right < left) throw new IllegalStateException("Cursor artwork is empty");
        int side = Math.max(right - left + 1, bottom - top + 1);
        var square = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
        var graphics = square.createGraphics();
        try { graphics.drawImage(source, -left, -top, null); } finally { graphics.dispose(); }
        while (square.getWidth() > 32) square = scaled(square, Math.max(32, square.getWidth() / 2));
        write("icons/cursor-action.png", square);
    }
    private static void resize(String name, int side) throws Exception {
        var source = ImageIO.read(Path.of("assets/classic", name).toFile());
        write(name, scaled(source, side));
    }
    private static BufferedImage scaled(BufferedImage source, int side) {
        double scale = Math.min(1, (double) side / Math.max(source.getWidth(), source.getHeight()));
        var output = new BufferedImage(Math.max(1, (int) Math.round(source.getWidth() * scale)),
                Math.max(1, (int) Math.round(source.getHeight() * scale)), BufferedImage.TYPE_INT_ARGB);
        var graphics = output.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(source, 0, 0, output.getWidth(), output.getHeight(), null);
        } finally { graphics.dispose(); }
        return output;
    }
    private static void write(String name, BufferedImage output) throws Exception {
        Path path = Path.of("web/public/assets/classic", name);
        Files.createDirectories(path.getParent());
        if (!ImageIO.write(output, "png", path.toFile())) throw new IllegalStateException("PNG encoder unavailable");
    }
}
