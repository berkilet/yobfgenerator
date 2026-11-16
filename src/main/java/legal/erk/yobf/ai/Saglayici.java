package legal.erk.yobf.ai;

import java.util.List;

/** Desteklenen yapay zekâ sağlayıcıları. Anahtarlar %APPDATA%\YOBF altında ayrı dosyalarda veya ortam değişkeninde. */
public enum Saglayici {
    ANTHROPIC("anthropic", "Anthropic Claude", "anahtar.txt", List.of("ANTHROPIC_API_KEY"), null,
            "https://console.anthropic.com"),
    OPENAI("openai", "OpenAI", "anahtar-openai.txt", List.of("OPENAI_API_KEY"), "https://api.openai.com/v1",
            "https://platform.openai.com/api-keys"),
    GEMINI("gemini", "Google Gemini", "anahtar-gemini.txt", List.of("GEMINI_API_KEY", "GOOGLE_API_KEY"),
            "https://generativelanguage.googleapis.com/v1beta", "https://aistudio.google.com/apikey"),
    UYUMLU("uyumlu", "OpenAI uyumlu servis", "anahtar-uyumlu.txt", List.of(), null, null);

    public final String kod;
    public final String ad;
    public final String anahtarDosyasi;
    public final List<String> ortamDegiskenleri;
    /** Varsayılan API adresi (UYUMLU için kullanıcı girer). */
    public final String tabanAdres;
    public final String anahtarSayfasi;

    Saglayici(String kod, String ad, String anahtarDosyasi, List<String> ortam, String tabanAdres, String sayfa) {
        this.kod = kod;
        this.ad = ad;
        this.anahtarDosyasi = anahtarDosyasi;
        this.ortamDegiskenleri = ortam;
        this.tabanAdres = tabanAdres;
        this.anahtarSayfasi = sayfa;
    }

    public static Saglayici kodla(String kod) {
        for (Saglayici s : values()) if (s.kod.equalsIgnoreCase(kod)) return s;
        return ANTHROPIC;
    }

    @Override public String toString() { return ad; }
}
