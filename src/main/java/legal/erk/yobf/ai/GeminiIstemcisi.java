package legal.erk.yobf.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import legal.erk.yobf.config.Ayarlar;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Google Gemini (generateContent). JSON şemasıyla (responseJsonSchema) dener; model desteklemiyorsa
 * yalnızca JSON çıktı + şema talimatıyla yeniden dener.
 */
public final class GeminiIstemcisi implements ModelIstemcisi {

    private final String tabanAdres;
    private final String apiAnahtari;
    private final String model;
    private final HttpYardim http;

    public GeminiIstemcisi(Ayarlar ayarlar, String tabanAdres, String apiAnahtari, String model) {
        this.tabanAdres = tabanAdres.replaceAll("/+$", "");
        this.apiAnahtari = apiAnahtari;
        this.model = model;
        this.http = new HttpYardim(ayarlar, "Google Gemini");
    }

    @Override public String etiket() { return "Google Gemini · " + model; }

    @Override
    public <T> T yapilandirilmis(Class<T> tur, String sistem, String kullanici, long maxTokens) throws ModelHatasi {
        HttpYardim.Yanit y = gonder(istek(tur, sistem, kullanici, maxTokens, true));
        if (y.durum() == 400 && y.ham() != null && y.ham().toLowerCase().contains("schema")) {
            y = gonder(istek(tur, sistem, kullanici, maxTokens, false));
        }
        if (y.durum() != 200) throw http.hata(y);
        JsonNode aday = y.govde().path("candidates").path(0);
        String bitis = aday.path("finishReason").asText("");
        if (aday.isMissingNode()) {
            String engel = y.govde().path("promptFeedback").path("blockReason").asText("");
            throw new ModelHatasi("Gemini yanıt vermedi" + (engel.isBlank() ? "." : " (engellendi: " + engel + ")."));
        }
        if ("MAX_TOKENS".equals(bitis)) throw new ModelHatasi("Model yanıtı token sınırında kesildi; belge çok uzun olabilir.");
        if ("SAFETY".equals(bitis) || "RECITATION".equals(bitis) || "PROHIBITED_CONTENT".equals(bitis)) {
            throw new ModelHatasi("Gemini isteği güvenlik nedeniyle yanıtlamadı (" + bitis + ").");
        }
        StringBuilder metin = new StringBuilder();
        for (JsonNode parca : aday.path("content").path("parts")) {
            if (!parca.path("thought").asBoolean(false)) metin.append(parca.path("text").asText(""));
        }
        return JsonSema.coz(metin.toString(), tur);
    }

    ObjectNode istek(Class<?> tur, String sistem, String kullanici, long maxTokens, boolean sema) {
        ObjectNode g = JsonSema.JSON.createObjectNode();
        String sis = sema ? sistem : sistem + "\n\nYanıtı YALNIZCA şu JSON şemasına uyan tek bir JSON nesnesi olarak ver:\n"
                + JsonSema.of(tur).toString();
        g.putObject("systemInstruction").putArray("parts").addObject().put("text", sis);
        ObjectNode icerik = g.putArray("contents").addObject();
        icerik.put("role", "user");
        icerik.putArray("parts").addObject().put("text", kullanici);
        ObjectNode gc = g.putObject("generationConfig");
        gc.put("responseMimeType", "application/json");
        gc.put("maxOutputTokens", maxTokens);
        if (sema) gc.set("responseJsonSchema", JsonSema.of(tur));
        return g;
    }

    private HttpYardim.Yanit gonder(ObjectNode govde) throws ModelHatasi {
        String adres = tabanAdres + "/models/" + URLEncoder.encode(model, StandardCharsets.UTF_8) + ":generateContent";
        return http.postJson(adres, Map.of("x-goog-api-key", apiAnahtari), govde);
    }
}
