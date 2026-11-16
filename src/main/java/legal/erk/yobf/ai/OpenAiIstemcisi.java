package legal.erk.yobf.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import legal.erk.yobf.config.Ayarlar;

import java.util.Map;

/**
 * OpenAI Chat Completions — ve aynı arayüzü sunan servisler (Mistral, DeepSeek, Groq, Azure,
 * yerel Ollama/LM Studio …). Önce katı JSON şemasıyla (response_format json_schema) dener;
 * servis desteklemiyorsa json_object + şema talimatıyla yeniden dener.
 */
public final class OpenAiIstemcisi implements ModelIstemcisi {

    private final String tabanAdres;
    private final String apiAnahtari;
    private final String model;
    private final boolean resmiOpenAi;
    private final String ad;
    private final HttpYardim http;

    public OpenAiIstemcisi(Ayarlar ayarlar, String tabanAdres, String apiAnahtari, String model, boolean resmiOpenAi) {
        this.tabanAdres = tabanAdres.replaceAll("/+$", "");
        this.apiAnahtari = apiAnahtari;
        this.model = model;
        this.resmiOpenAi = resmiOpenAi;
        this.ad = resmiOpenAi ? "OpenAI" : "OpenAI uyumlu servis";
        this.http = new HttpYardim(ayarlar, ad);
    }

    @Override public String etiket() { return ad + " · " + model; }

    @Override
    public <T> T yapilandirilmis(Class<T> tur, String sistem, String kullanici, long maxTokens) throws ModelHatasi {
        HttpYardim.Yanit y = gonder(istek(tur, sistem, kullanici, maxTokens, true));
        if (y.durum() == 400 && semaDesteklenmiyor(y)) {
            y = gonder(istek(tur, sistem, kullanici, maxTokens, false));
        }
        if (y.durum() != 200) throw http.hata(y);
        JsonNode secim = y.govde().path("choices").path(0);
        JsonNode mesaj = secim.path("message");
        if (mesaj.hasNonNull("refusal") && !mesaj.path("refusal").asText().isBlank()) {
            throw new ModelHatasi("Model isteği reddetti: " + mesaj.path("refusal").asText());
        }
        if ("length".equals(secim.path("finish_reason").asText())) {
            throw new ModelHatasi("Model yanıtı token sınırında kesildi; belge çok uzun olabilir.");
        }
        return JsonSema.coz(mesaj.path("content").asText(null), tur);
    }

    ObjectNode istek(Class<?> tur, String sistem, String kullanici, long maxTokens, boolean katiSema) {
        ObjectNode g = JsonSema.JSON.createObjectNode();
        g.put("model", model);
        // Yeni OpenAI modelleri max_completion_tokens bekler; uyumlu servislerin çoğu max_tokens.
        g.put(resmiOpenAi ? "max_completion_tokens" : "max_tokens", maxTokens);
        ArrayNode m = g.putArray("messages");
        String sis = katiSema ? sistem : sistem + "\n\nYanıtı YALNIZCA şu JSON şemasına uyan tek bir JSON nesnesi olarak ver:\n"
                + JsonSema.of(tur).toString();
        m.addObject().put("role", "system").put("content", sis);
        m.addObject().put("role", "user").put("content", kullanici);
        ObjectNode rf = g.putObject("response_format");
        if (katiSema) {
            rf.put("type", "json_schema");
            ObjectNode js = rf.putObject("json_schema");
            js.put("name", tur.getSimpleName());
            js.put("strict", true);
            js.set("schema", JsonSema.of(tur));
        } else {
            rf.put("type", "json_object");
        }
        return g;
    }

    private HttpYardim.Yanit gonder(ObjectNode govde) throws ModelHatasi {
        return http.postJson(tabanAdres + "/chat/completions",
                apiAnahtari == null || apiAnahtari.isBlank() ? Map.of() : Map.of("Authorization", "Bearer " + apiAnahtari),
                govde);
    }

    private static boolean semaDesteklenmiyor(HttpYardim.Yanit y) {
        String m = y.ham() == null ? "" : y.ham().toLowerCase();
        return m.contains("response_format") || m.contains("json_schema") || m.contains("schema");
    }
}
