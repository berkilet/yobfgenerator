package legal.erk.yobf.ai;

import legal.erk.yobf.config.Ayarlar;

/** Seçili sağlayıcıya göre istemci oluşturur. */
public final class IstemciFabrikasi {

    private IstemciFabrikasi() {}

    /**
     * @param tabanAdres yalnızca OpenAI uyumlu servis için (ör. https://api.mistral.ai/v1, http://localhost:11434/v1)
     */
    public static ModelIstemcisi olustur(Ayarlar ayarlar, Saglayici s, String model, String anahtar, String tabanAdres)
            throws ModelHatasi {
        if (model == null || model.isBlank()) throw new ModelHatasi(s.ad + " için model adı girilmemiş. ☰ › Yapay zekâ ayarları.");
        boolean anahtarGerekli = s != Saglayici.UYUMLU;
        if (anahtarGerekli && (anahtar == null || anahtar.isBlank())) {
            throw new ModelHatasi(s.ad + " API anahtarı girilmemiş. ☰ › Yapay zekâ ayarları.");
        }
        return switch (s) {
            case ANTHROPIC -> new AnthropicIstemcisi(ayarlar, model, anahtar);
            case OPENAI -> new OpenAiIstemcisi(ayarlar, Saglayici.OPENAI.tabanAdres, anahtar, model, true);
            case GEMINI -> new GeminiIstemcisi(ayarlar, Saglayici.GEMINI.tabanAdres, anahtar, model);
            case UYUMLU -> {
                if (tabanAdres == null || tabanAdres.isBlank()) {
                    throw new ModelHatasi("OpenAI uyumlu servis için API adresi girilmemiş. ☰ › Yapay zekâ ayarları.");
                }
                yield new OpenAiIstemcisi(ayarlar, tabanAdres, anahtar, model, false);
            }
        };
    }
}
