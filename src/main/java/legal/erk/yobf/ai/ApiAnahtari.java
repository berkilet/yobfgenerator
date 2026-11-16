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

    /** Anthropic anahtar dosyası (geriye uyumluluk). */
    public static Path dosya() { return dosya(Saglayici.ANTHROPIC); }

    public static Path dosya(Saglayici s) {
        return UygulamaYolu.kullaniciKlasoru().resolve(s.anahtarDosyasi);
    }

    public static Optional<String> oku() { return oku(Saglayici.ANTHROPIC); }

    /** Önce ortam değişkeni, sonra kullanıcı klasöründeki dosya. */
    public static Optional<String> oku(Saglayici s) {
        for (String ad : s.ortamDegiskenleri) {
            String env = System.getenv(ad);
            if (env != null && !env.isBlank()) return Optional.of(env.trim());
        }
        Path d = dosya(s);
        if (Files.isRegularFile(d)) {
            try {
                String k = Files.readString(d, StandardCharsets.UTF_8).trim();
                if (!k.isEmpty()) return Optional.of(k);
            } catch (IOException ignored) {
                // anahtar yok say
            }
        }
        return Optional.empty();
    }

    /** Anahtar ortam değişkeninden mi geliyor (arayüzde bilgi için)? */
    public static Optional<String> ortamDegiskeni(Saglayici s) {
        return s.ortamDegiskenleri.stream().filter(a -> {
            String e = System.getenv(a);
            return e != null && !e.isBlank();
        }).findFirst();
    }

    public static void kaydet(String anahtar) throws IOException { kaydet(Saglayici.ANTHROPIC, anahtar); }

    public static void kaydet(Saglayici s, String anahtar) throws IOException {
        Path d = dosya(s);
        Files.createDirectories(d.getParent());
        Files.writeString(d, anahtar.trim(), StandardCharsets.UTF_8);
    }

    public static void sil(Saglayici s) throws IOException {
        Files.deleteIfExists(dosya(s));
    }
}
