package legal.erk.yobf.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import legal.erk.yobf.TestVerisi;
import legal.erk.yobf.config.Ayarlar;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OpenAI / OpenAI uyumlu / Gemini istemcileri, yerel sahte sunucuya karşı (ağ ve gerçek anahtar gerekmez):
 * istek biçimi, yanıt çözme, hata mesajları ve "şema desteklenmiyor" yedek yolu.
 */
class SaglayiciIstemcileriTest {

    private final Ayarlar a = TestVerisi.yapilandirma().ayarlar();
    private HttpServer sunucu;
    private final List<JsonNode> istekler = new ArrayList<>();
    private final List<String> yollar = new ArrayList<>();
    private final List<String> basliklar = new ArrayList<>();
    private Function<JsonNode, Object[]> cevap; // istek → {durum, gövde}

    @BeforeEach
    void kur() throws Exception {
        sunucu = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        sunucu.createContext("/", ex -> {
            JsonNode govde = JsonSema.JSON.readTree(ex.getRequestBody());
            istekler.add(govde);
            yollar.add(ex.getRequestURI().getPath());
            basliklar.add(ex.getRequestHeaders().getFirst("Authorization") + "|" + ex.getRequestHeaders().getFirst("x-goog-api-key"));
            Object[] c = cevap.apply(govde);
            byte[] b = ((String) c[1]).getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders((int) c[0], b.length);
            try (OutputStream o = ex.getResponseBody()) { o.write(b); }
        });
        sunucu.start();
    }

    @AfterEach
    void kapat() { sunucu.stop(0); }

    private String adres() { return "http://127.0.0.1:" + sunucu.getAddress().getPort() + "/v1"; }

    private static String openAiYanit(String icerik) throws Exception {
        ObjectNode n = JsonSema.JSON.createObjectNode();
        ObjectNode secim = n.putArray("choices").addObject();
        secim.put("finish_reason", "stop");
        secim.putObject("message").put("role", "assistant").put("content", icerik);
        return n.toString();
    }

    private static final String KISALTMA_JSON = "{\"konu_tr\":\"tamam\",\"konu_en\":\"ok\"}";

    @Test
    void openAiKatiSemaIleIsterVeCozer() throws Exception {
        cevap = g -> new Object[]{200, uncheck(() -> openAiYanit(KISALTMA_JSON))};
        try (OpenAiIstemcisi c = new OpenAiIstemcisi(a, adres(), "sk-test", "gpt-5-mini", true)) {
            OzetSemasi.Kisaltma k = c.yapilandirilmis(OzetSemasi.Kisaltma.class, "sistem", "kullanıcı", 300);
            assertEquals("tamam", k.konuTr());
        }
        JsonNode g = istekler.get(0);
        assertEquals("/v1/chat/completions", yollar.get(0));
        assertTrue(basliklar.get(0).startsWith("Bearer sk-test"));
        assertEquals("gpt-5-mini", g.path("model").asText());
        assertEquals(300, g.path("max_completion_tokens").asInt(), "resmi OpenAI: max_completion_tokens");
        assertEquals("json_schema", g.path("response_format").path("type").asText());
        assertTrue(g.path("response_format").path("json_schema").path("strict").asBoolean());
        JsonNode sema = g.path("response_format").path("json_schema").path("schema");
        assertFalse(sema.path("additionalProperties").asBoolean(true));
        assertTrue(sema.path("properties").has("konu_tr"));
    }

    @Test
    void ozetSemasiTamSemayla() throws Exception {
        JsonNode sema = JsonSema.of(OzetSemasi.class);
        for (String alan : List.of("belge_turu", "taraflar", "konu_tr", "bedel", "belgeler", "coklu_belge_iliskisi", "guven")) {
            assertTrue(sema.path("properties").has(alan), alan);
        }
        assertFalse(sema.has("$schema"));
    }

    @Test
    void uyumluServisSemaDesteklemezseJsonObjectIleTekrarDener() throws Exception {
        cevap = g -> "json_schema".equals(g.path("response_format").path("type").asText())
                ? new Object[]{400, "{\"error\":{\"message\":\"response_format json_schema is not supported\"}}"}
                : new Object[]{200, uncheck(() -> openAiYanit("```json\n" + KISALTMA_JSON + "\n```"))};
        try (OpenAiIstemcisi c = new OpenAiIstemcisi(a, adres(), "", "mistral-small-latest", false)) {
            assertEquals("ok", c.yapilandirilmis(OzetSemasi.Kisaltma.class, "sistem", "k", 200).konuEn());
        }
        assertEquals(2, istekler.size());
        assertEquals("json_object", istekler.get(1).path("response_format").path("type").asText());
        assertEquals(200, istekler.get(1).path("max_tokens").asInt(), "uyumlu servis: max_tokens");
        assertTrue(istekler.get(1).path("messages").path(0).path("content").asText().contains("konu_tr"),
                "şema talimatı sistem mesajında");
        assertEquals("null|null", basliklar.get(0), "anahtarsız yerel servis: Authorization gönderilmez");
    }

    @Test
    void yetkisizAnahtarAnlasilirHata() {
        cevap = g -> new Object[]{401, "{\"error\":{\"message\":\"Incorrect API key provided\"}}"};
        try (OpenAiIstemcisi c = new OpenAiIstemcisi(a, adres(), "yanlis", "gpt-5-mini", true)) {
            ModelHatasi h = assertThrows(ModelHatasi.class,
                    () -> c.yapilandirilmis(OzetSemasi.Kisaltma.class, "s", "k", 100));
            assertTrue(h.getMessage().contains("API anahtarı geçersiz"), h.getMessage());
            assertTrue(h.getMessage().contains("Yapay zekâ ayarları"));
        }
    }

    @Test
    void geminiSemaIleIsterVeCozer() throws Exception {
        cevap = g -> new Object[]{200, "{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":["
                + "{\"text\":\"düşünce\",\"thought\":true},{\"text\":" + uncheck(() -> JsonSema.JSON.writeValueAsString(KISALTMA_JSON)) + "}]}}]}"};
        try (GeminiIstemcisi c = new GeminiIstemcisi(a, adres(), "AIza-test", "gemini-2.5-flash")) {
            assertEquals("tamam", c.yapilandirilmis(OzetSemasi.Kisaltma.class, "sistem", "k", 500).konuTr());
        }
        assertEquals("/v1/models/gemini-2.5-flash:generateContent", yollar.get(0));
        assertEquals("null|AIza-test", basliklar.get(0));
        JsonNode gc = istekler.get(0).path("generationConfig");
        assertEquals("application/json", gc.path("responseMimeType").asText());
        assertTrue(gc.path("responseJsonSchema").path("properties").has("konu_tr"));
        assertEquals("sistem", istekler.get(0).path("systemInstruction").path("parts").path(0).path("text").asText());
    }

    @Test
    void geminiSemaDesteklenmezseYenidenDenerVeGuvenlikEngeliniBildirir() throws Exception {
        cevap = g -> g.path("generationConfig").has("responseJsonSchema")
                ? new Object[]{400, "{\"error\":{\"message\":\"Unknown name responseJsonSchema\"}}"}
                : new Object[]{200, "{\"candidates\":[{\"finishReason\":\"SAFETY\",\"content\":{\"parts\":[]}}]}"};
        try (GeminiIstemcisi c = new GeminiIstemcisi(a, adres(), "k", "gemini-x")) {
            ModelHatasi h = assertThrows(ModelHatasi.class, () -> c.yapilandirilmis(OzetSemasi.Kisaltma.class, "s", "k", 100));
            assertTrue(h.getMessage().contains("güvenlik"), h.getMessage());
        }
        assertEquals(2, istekler.size());
        assertFalse(istekler.get(1).path("generationConfig").has("responseJsonSchema"));
    }

    @Test
    void fabrikaEksikBilgideAnlasilirHata() {
        ModelHatasi h = assertThrows(ModelHatasi.class, () -> IstemciFabrikasi.olustur(a, Saglayici.OPENAI, "gpt-5-mini", "", null));
        assertTrue(h.getMessage().contains("OpenAI API anahtarı girilmemiş"));
        h = assertThrows(ModelHatasi.class, () -> IstemciFabrikasi.olustur(a, Saglayici.UYUMLU, "m", "", " "));
        assertTrue(h.getMessage().contains("API adresi"));
    }

    @Test
    void ayarlardaSaglayiciModelleri() {
        assertFalse(a.modeller("openai").isEmpty());
        assertFalse(a.modeller("gemini").isEmpty());
        assertEquals("claude-haiku-5-5", a.modeller("anthropic").get(0).id());
        assertTrue(a.tabanAdres("uyumlu").startsWith("https://"));
    }

    interface Uretici { String al() throws Exception; }

    private static String uncheck(Uretici u) {
        try { return u.al(); } catch (Exception e) { throw new RuntimeException(e); }
    }
}
