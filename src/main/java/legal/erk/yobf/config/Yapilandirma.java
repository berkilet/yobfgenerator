package legal.erk.yobf.config;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

/** config/ altındaki üç JSON dosyasını okur. Kanonik veriler koda gömülmez. */
public record Yapilandirma(Ayarlar ayarlar, Sirketler sirketler, Imzacilar imzacilar) {

    public static final ObjectMapper JSON = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
            .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public static Yapilandirma yukle() {
        return yukle(UygulamaYolu.kok().resolve("config"));
    }

    public static Yapilandirma yukle(Path configKlasoru) {
        try {
            return new Yapilandirma(
                    JSON.readValue(configKlasoru.resolve("ayarlar.json").toFile(), Ayarlar.class),
                    JSON.readValue(configKlasoru.resolve("sirketler.json").toFile(), Sirketler.class),
                    JSON.readValue(configKlasoru.resolve("imzacilar.json").toFile(), Imzacilar.class));
        } catch (IOException e) {
            throw new UncheckedIOException("Yapılandırma okunamadı: " + configKlasoru + " — " + e.getMessage(), e);
        }
    }
}
