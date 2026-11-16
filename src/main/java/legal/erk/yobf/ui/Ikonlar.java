package legal.erk.yobf.ui;

import javax.imageio.ImageIO;
import java.awt.Image;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Uygulama simgesi (pencere başlığı, görev çubuğu); /ikon/yobf-*.png kaynaklarından. */
public final class Ikonlar {

    static final int[] BOYUTLAR = {16, 24, 32, 48, 64, 128, 256};
    private static List<Image> liste;

    private Ikonlar() {}

    public static synchronized List<Image> liste() {
        if (liste == null) {
            List<Image> l = new ArrayList<>();
            for (int n : BOYUTLAR) {
                try (InputStream in = Ikonlar.class.getResourceAsStream("/ikon/yobf-" + n + ".png")) {
                    if (in != null) l.add(ImageIO.read(in));
                } catch (Exception ignored) {
                    // simge yüklenemezse varsayılan Java simgesi kalır
                }
            }
            liste = List.copyOf(l);
        }
        return liste;
    }
}
