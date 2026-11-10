package legal.erk.yobf.ai;

import legal.erk.yobf.config.UygulamaYolu;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * API anahtarı: ANTHROPIC_API_KEY ortam değişkeni veya %APPDATA%\YOBF\anahtar.txt.
 * Asla config/ veya repo içinde tutulmaz.
 */
public final class ApiAnahtari {

    private ApiAnahtari() {}

    public static Path dosya() {
        return UygulamaYolu.kullaniciKlasoru().resolve("anahtar.txt");
    }

    public static Optional<String> oku() {
        String env = System.getenv("ANTHROPIC_API_KEY");
        if (env != null && !env.isBlank()) return Optional.of(env.trim());
        Path d = dosya();
        if (Files.isRegularFile(d)) {
            try {
                String s = Files.readString(d, StandardCharsets.UTF_8).trim();
                if (!s.isEmpty()) return Optional.of(s);
            } catch (IOException ignored) {
                // anahtar yok say
            }
        }
        return Optional.empty();
    }

    public static void kaydet(String anahtar) throws IOException {
        Path d = dosya();
        Files.createDirectories(d.getParent());
        Files.writeString(d, anahtar.trim(), StandardCharsets.UTF_8);
    }
}
