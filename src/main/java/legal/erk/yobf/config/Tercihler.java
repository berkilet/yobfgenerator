package legal.erk.yobf.config;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Kullanıcıya özel küçük tercihler: %APPDATA%\YOBF\tercihler.json (seçili model, PDF de oluştur). */
public final class Tercihler {

    private final Path dosya;
    private final ObjectNode veri;

    public Tercihler(Path dosya) {
        this.dosya = dosya;
        ObjectNode v;
        try {
            v = Files.isRegularFile(dosya)
                    ? (ObjectNode) Yapilandirma.JSON.readTree(dosya.toFile())
                    : Yapilandirma.JSON.createObjectNode();
        } catch (IOException | ClassCastException e) {
            v = Yapilandirma.JSON.createObjectNode();
        }
        this.veri = v;
    }

    public static Tercihler varsayilan() {
        return new Tercihler(UygulamaYolu.kullaniciKlasoru().resolve("tercihler.json"));
    }

    public String model(String varsayilan) {
        return veri.hasNonNull("model") ? veri.get("model").asText() : varsayilan;
    }

    public void modelAyarla(String model) { veri.put("model", model); kaydet(); }

    public boolean pdfDeOlustur() {
        return veri.path("pdf_de_olustur").asBoolean(false);
    }

    public void pdfDeOlusturAyarla(boolean b) { veri.put("pdf_de_olustur", b); kaydet(); }

    private void kaydet() {
        try {
            Files.createDirectories(dosya.getParent());
            Yapilandirma.JSON.writerWithDefaultPrettyPrinter().writeValue(dosya.toFile(), veri);
        } catch (IOException ignored) {
            // tercih kaydedilemezse uygulama yine çalışır
        }
    }
}
