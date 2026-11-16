package legal.erk.yobf.ai;

import com.anthropic.core.ObjectMappers;
import com.anthropic.models.messages.MessageCreateParams;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bir Java kaydının (OzetSemasi vb.) katı JSON şeması. Anthropic SDK'sının ürettiği şema kullanılır
 * (tüm alanlar zorunlu, additionalProperties=false) — böylece üç sağlayıcı da aynı şemayla çalışır.
 */
public final class JsonSema {

    static final ObjectMapper JSON = new ObjectMapper()
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final Map<Class<?>, ObjectNode> ONBELLEK = new ConcurrentHashMap<>();

    private JsonSema() {}

    public static ObjectNode of(Class<?> tur) {
        return ONBELLEK.computeIfAbsent(tur, t -> {
            MessageCreateParams p = MessageCreateParams.builder().model("x").maxTokens(1).addUserMessage("x")
                    .outputConfig(t).build().rawParams();
            JsonNode govde = ObjectMappers.jsonMapper().valueToTree(p._body());
            ObjectNode sema = (ObjectNode) govde.path("output_config").path("format").path("schema");
            ObjectNode kopya = JSON.valueToTree(sema);
            kopya.remove("$schema");
            return kopya;
        }).deepCopy();
    }

    /** Model yanıtındaki JSON metnini kayda çevirir (```json çitlerini de temizler). */
    public static <T> T coz(String metin, Class<T> tur) throws ModelHatasi {
        if (metin == null || metin.isBlank()) throw new ModelHatasi("Modelden boş yanıt geldi.");
        String s = metin.strip();
        if (s.startsWith("```")) {
            s = s.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        }
        int bas = s.indexOf('{'), son = s.lastIndexOf('}');
        if (bas > 0 && son > bas) s = s.substring(bas, son + 1);
        try {
            return JSON.readValue(s, tur);
        } catch (Exception e) {
            throw new ModelHatasi("Model yanıtı beklenen biçimde değil (JSON okunamadı).", e);
        }
    }
}
