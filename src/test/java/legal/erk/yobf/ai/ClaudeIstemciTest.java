package legal.erk.yobf.ai;

import com.anthropic.models.beta.messages.MessageCreateParams;
import com.anthropic.models.beta.messages.StructuredMessageCreateParams;
import legal.erk.yobf.TestVerisi;
import legal.erk.yobf.config.Ayarlar;
import legal.erk.yobf.extract.BelgeOkuyucu;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Ağ çağrısı yapmaz: şema üretimi, istek oluşturma ve uzun belge seçimi. */
class ClaudeIstemciTest {

    private final Ayarlar a = TestVerisi.yapilandirma().ayarlar();

    @Test
    void semaYerelOlarakGecerliVeIstekOlusur() {
        StructuredMessageCreateParams<OzetSemasi> p = MessageCreateParams.builder()
                .model(a.model())
                .maxTokens(1000)
                .system("x")
                .addUserMessage("y")
                .addBeta("server-side-fallback-2026-07-01")
                .fallbacksDefault()
                .outputConfig(OzetSemasi.class)
                .build();
        String json = p.rawParams()._body().toString();
        assertTrue(json.contains("konu_tr"), json);
        assertTrue(json.contains("belge_no"), json);
        assertNotNull(MessageCreateParams.builder().model(a.model()).maxTokens(100).addUserMessage("k")
                .outputConfig(OzetSemasi.Kisaltma.class).build());
    }

    @Test
    void promptYerTutucuDoldurulur() throws Exception {
        try (ClaudeIstemci c = new ClaudeIstemci(a, Path.of("prompts/ozet_prompt.md"), "test-anahtar")) {
            String m = c.kullaniciMesaji(List.of(
                    new BelgeOkuyucu.Belge(Path.of("a.pdf"), List.of("birinci")),
                    new BelgeOkuyucu.Belge(Path.of("b.pdf"), List.of("ikinci"))), false);
            assertTrue(m.contains("=== BELGE 1: a.pdf ==="));
            assertTrue(m.contains("=== BELGE 2: b.pdf ==="));
            assertTrue(m.contains("belge_no"));
        }
    }

    @Test
    void uzunBelgedeSecilmisSayfalarGonderilir() {
        List<String> s = new ArrayList<>();
        for (int i = 1; i <= 300; i++) s.add("Sayfa içeriği " + i + (i == 120 ? " Sözleşme Bedeli 1.000.000 TL" : ""));
        String m = ClaudeIstemci.secilmisMetin(new BelgeOkuyucu.Belge(Path.of("uzun.pdf"), s));
        assertTrue(m.contains("Sayfa içeriği 1\n"));
        assertTrue(m.contains("Sözleşme Bedeli 1.000.000 TL"));
        assertTrue(m.contains("Sayfa içeriği 300"));
        assertFalse(m.contains("Sayfa içeriği 150\n"));
    }
}
