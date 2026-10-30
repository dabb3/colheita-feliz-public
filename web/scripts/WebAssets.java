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
        for (String id : new String[]{"comum", "rapido", "super", "amor"}) resize("icons/adubo-" + id + ".png", 96);
        for (String id : new String[]{"pa-ui", "saco-ui", "regador-ui", "mao-restaurada", "herbicida-ui", "pesticida-ui"})
            resize("icons/hud/" + id + ".png", 96);
    }
    private static void resize(String name, int side) throws Exception {
        var source = ImageIO.read(Path.of("assets/classic", name).toFile());
        double scale = Math.min(1, (double) side / Math.max(source.getWidth(), source.getHeight()));
        var output = new BufferedImage(Math.max(1, (int) Math.round(source.getWidth() * scale)),
                Math.max(1, (int) Math.round(source.getHeight() * scale)), BufferedImage.TYPE_INT_ARGB);
        var graphics = output.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(source, 0, 0, output.getWidth(), output.getHeight(), null);
        } finally { graphics.dispose(); }
        Path path = Path.of("web/public/assets/classic", name);
        Files.createDirectories(path.getParent());
        if (!ImageIO.write(output, "png", path.toFile())) throw new IllegalStateException("PNG encoder unavailable");
    }
}
