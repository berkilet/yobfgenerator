package legal.erk.yobf.config;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Uygulama klasörünü bulur. config/, prompts/, assets/ bu klasörün altındadır
 * ve derleme dışında, kullanıcı tarafından düzenlenebilir.
 *
 * Arama sırası: -Dyobf.home → jar'ın bulunduğu klasör ve üst klasörleri → çalışma dizini.
 */
public final class UygulamaYolu {

    private static Path kok;

    private UygulamaYolu() {}

    public static synchronized Path kok() {
        if (kok == null) kok = bul();
        return kok;
    }

    /** Testler için. */
    public static synchronized void ayarla(Path p) {
        kok = p;
    }

    private static Path bul() {
        String prop = System.getProperty("yobf.home");
        if (prop != null && !prop.isBlank()) return Paths.get(prop).toAbsolutePath();
        try {
            Path jar = Paths.get(UygulamaYolu.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path d = Files.isDirectory(jar) ? jar : jar.getParent();
            for (int i = 0; i < 4 && d != null; i++, d = d.getParent()) {
                if (Files.isDirectory(d.resolve("config"))) return d;
            }
        } catch (URISyntaxException | SecurityException | NullPointerException ignored) {
            // çalışma dizinine düş
        }
        return Paths.get("").toAbsolutePath();
    }

    public static Path config(String dosya) { return kok().resolve("config").resolve(dosya); }
    public static Path prompt(String dosya) { return kok().resolve("prompts").resolve(dosya); }
    public static Path logolar() { return kok().resolve("assets").resolve("logos"); }

    /** Kullanıcı profili klasörü: %APPDATA%\YOBF (Windows) veya ~/.yobf */
    public static Path kullaniciKlasoru() {
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isBlank()) return Paths.get(appData, "YOBF");
        return Paths.get(System.getProperty("user.home"), ".yobf");
    }
}
