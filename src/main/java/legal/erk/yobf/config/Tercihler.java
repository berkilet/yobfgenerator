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

    /** PDF oluştururken Word (.docx) dosyası da yanına kaydedilsin mi? */
    public boolean wordDeSakla() {
        return veri.path("word_de_sakla").asBoolean(false);
    }

    public void wordDeSaklaAyarla(boolean b) { veri.put("word_de_sakla", b); kaydet(); }

    /** Seçili yapay zekâ sağlayıcısı (anthropic, openai, gemini, uyumlu). */
    public String saglayici() { return veri.path("saglayici").asText("anthropic"); }

    public void saglayiciAyarla(String kod) { veri.put("saglayici", kod); kaydet(); }

    /** Sağlayıcıya göre son seçilen model; Anthropic için eski "model" anahtarı. */
    public String model(String saglayiciKodu, String varsayilan) {
        String k = "anthropic".equals(saglayiciKodu) ? "model" : "model_" + saglayiciKodu;
        return veri.hasNonNull(k) && !veri.get(k).asText().isBlank() ? veri.get(k).asText() : varsayilan;
    }

    public void modelAyarla(String saglayiciKodu, String model) {
        veri.put("anthropic".equals(saglayiciKodu) ? "model" : "model_" + saglayiciKodu, model);
        kaydet();
    }

    /** OpenAI uyumlu servis adresi. */
    public String tabanAdres(String varsayilan) {
        return veri.hasNonNull("taban_adres") && !veri.get("taban_adres").asText().isBlank()
                ? veri.get("taban_adres").asText() : varsayilan;
    }

    public void tabanAdresAyarla(String adres) { veri.put("taban_adres", adres); kaydet(); }

    public boolean koyuTema() { return veri.path("koyu_tema").asBoolean(false); }

    public void koyuTemaAyarla(boolean b) { veri.put("koyu_tema", b); kaydet(); }

    private void kaydet() {
        try {
            Files.createDirectories(dosya.getParent());
            Yapilandirma.JSON.writerWithDefaultPrettyPrinter().writeValue(dosya.toFile(), veri);
        } catch (IOException ignored) {
            // tercih kaydedilemezse uygulama yine çalışır
        }
    }
}
