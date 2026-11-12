package legal.erk.yobf.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.errors.BadRequestException;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.anthropic.models.beta.messages.StructuredMessage;
import com.anthropic.models.beta.messages.StructuredMessageCreateParams;
import legal.erk.yobf.config.Ayarlar;
import legal.erk.yobf.extract.BelgeOkuyucu.Belge;
import legal.erk.yobf.rules.KarakterSayaci;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Claude ile yapılandırılmış özet çıkarımı (CLAUDE.md §7).
 * Model adı ayarlar.json'dan, sistem talimatı prompts/ozet_prompt.md'den okunur.
 * Yapılandırılmış çıktı (output_config.format / JSON şeması) kullanılır; dönen nesne şemaya uyar.
 */
public final class ClaudeIstemci implements AutoCloseable {

    /** Uzun belge eşiği (§7): bunun üzerindeki belgelerden yalnızca ilgili sayfalar gönderilir. */
    static final int UZUN_BELGE_SAYFA = 150;
    private static final int BAS_SAYFA = 25;
    private static final int SON_SAYFA = 10;
    private static final int ANAHTAR_SAYFA_MAX = 40;
    private static final String YEDEK_MODEL_BETA = "server-side-fallback-2026-07-01";

    private final Ayarlar ayarlar;
    private final Ayarlar.ModelSecenegi model;
    private final String sistemTalimati;
    private final AnthropicClient client;

    public ClaudeIstemci(Ayarlar ayarlar, Path promptDosyasi, String apiAnahtari) throws IOException {
        this(ayarlar, ayarlar.model(), promptDosyasi, apiAnahtari);
    }

    /** @param modelId arayüzde seçilen model (ayarlar.json → modeller) */
    public ClaudeIstemci(Ayarlar ayarlar, String modelId, Path promptDosyasi, String apiAnahtari) throws IOException {
        this.ayarlar = ayarlar;
        this.model = ayarlar.modelSecenegi(modelId == null || modelId.isBlank() ? ayarlar.model() : modelId);
        this.sistemTalimati = Files.readString(promptDosyasi, StandardCharsets.UTF_8)
                .replace("{{KONU_MAX}}", String.valueOf(ayarlar.konuMaxKarakter()));
        AnthropicOkHttpClient.Builder b = AnthropicOkHttpClient.builder()
                .apiKey(apiAnahtari)
                .timeout(Duration.ofMinutes(5))
                .maxRetries(3);
        if (ayarlar.proxy().tanimli()) {
            b.proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(ayarlar.proxy().host(), ayarlar.proxy().port())));
        }
        this.client = b.build();
    }

    public static class ModelHatasi extends Exception {
        public ModelHatasi(String m, Throwable t) { super(m, t); }
        public ModelHatasi(String m) { super(m); }
    }

    /** Özet + kısaltma denemeleri. ilerleme: arayüzde durum çubuğu için. */
    public OzetSemasi ozetCikar(List<Belge> maskeliBelgeler, boolean ikiDilli, Consumer<String> ilerleme) throws ModelHatasi {
        ilerleme.accept("Model çağrılıyor (" + model + ")…");
        OzetSemasi o = cagir(OzetSemasi.class, kullaniciMesaji(maskeliBelgeler, ikiDilli), 16000);

        int sinir = ayarlar.konuMaxKarakter();
        for (int deneme = 1; deneme <= ayarlar.kisaltmaDenemeSayisi() && KarakterSayaci.say(o.konuTr()) > sinir; deneme++) {
            ilerleme.accept("Konu " + KarakterSayaci.say(o.konuTr()) + " karakter; kısaltılıyor (" + deneme + "/"
                    + ayarlar.kisaltmaDenemeSayisi() + ")…");
            OzetSemasi.Kisaltma k = cagir(OzetSemasi.Kisaltma.class, kisaltmaMesaji(o, ikiDilli), 4000);
            o = new OzetSemasi(o.belgeTuru(), o.formTuruOnerisi(), o.taraflar(),
                    k.konuTr() == null || k.konuTr().isBlank() ? o.konuTr() : k.konuTr(),
                    ikiDilli && k.konuEn() != null && !k.konuEn().isBlank() ? k.konuEn() : o.konuEn(),
                    o.bedel(), o.kisiler(), o.sonaErmeTarihi(), o.dikkatHususlari(), o.guven());
        }
        return o;
    }

    private <T> T cagir(Class<T> tur, String kullaniciMesaji, long maxTokens) throws ModelHatasi {
        StructuredMessageCreateParams<T> params = istek(tur, kullaniciMesaji, maxTokens);
        try {
            StructuredMessage<T> m = client.beta().messages().create(params);
            BetaStopReason sr = m.stopReason().orElse(null);
            if (BetaStopReason.REFUSAL.equals(sr)) {
                throw new ModelHatasi("Model isteği reddetti (refusal). Belge içeriğini kontrol edip tekrar deneyin.");
            }
            if (BetaStopReason.MAX_TOKENS.equals(sr)) {
                throw new ModelHatasi("Model yanıtı token sınırında kesildi; belge çok uzun olabilir.");
            }
            return m.content().stream()
                    .flatMap(c -> c.text().stream())
                    .map(t -> t.text())
                    .findFirst()
                    .orElseThrow(() -> new ModelHatasi("Modelden yapılandırılmış yanıt alınamadı."));
        } catch (UnauthorizedException e) {
            throw new ModelHatasi("API anahtarı geçersiz veya yetkisiz (401). Ayarlar > API Anahtarı.", e);
        } catch (RateLimitException e) {
            throw new ModelHatasi("Hız sınırına takıldı (429). Bir süre sonra tekrar deneyin.", e);
        } catch (BadRequestException e) {
            throw new ModelHatasi("İstek reddedildi (400): " + e.getMessage(), e);
        } catch (AnthropicIoException e) {
            throw new ModelHatasi("Ağ bağlantısı kurulamadı. İnternet/proxy ayarlarını kontrol edin: " + e.getMessage(), e);
        } catch (AnthropicServiceException e) {
            throw new ModelHatasi("API hatası (" + e.statusCode() + "): " + e.getMessage(), e);
        } catch (ModelHatasi e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ModelHatasi("Bağlantı/yanıt hatası: " + e.getMessage(), e);
        }
    }

    /** İstek gövdesi. Sunucu tarafı yedek model yalnızca destekleyen modellerde istenir (Haiku 5.5'te yok). */
    <T> StructuredMessageCreateParams<T> istek(Class<T> tur, String kullaniciMesaji, long maxTokens) {
        MessageCreateParams.Builder b = MessageCreateParams.builder()
                .model(model.id())
                .maxTokens(maxTokens)
                .system(sistemTalimati)
                .addUserMessage(kullaniciMesaji);
        if (ayarlar.sunucuTarafiYedekModel() && model.yedekModel()) {
            b.addBeta(YEDEK_MODEL_BETA).fallbacksDefault();
        }
        return b.outputConfig(tur).build();
    }

    String kullaniciMesaji(List<Belge> belgeler, boolean ikiDilli) {
        StringBuilder b = new StringBuilder();
        if (belgeler.size() > 1) {
            b.append("Aşağıda tek bir forma bağlanacak ").append(belgeler.size())
                    .append(" ayrı belge var. Her taraf ve bedel için belge_no alanına belgenin sıra numarasını yaz. ")
                    .append("konu_tr tek ve ortak olmalıdır.\n\n");
        }
        b.append(ikiDilli
                ? "Form iki dilli (TR/EN) hazırlanacak: konu_en alanına konu_tr'nin resmi İngilizce karşılığını yaz.\n\n"
                : "konu_en alanını boş bırak.\n\n");
        for (int i = 0; i < belgeler.size(); i++) {
            Belge d = belgeler.get(i);
            b.append("=== BELGE ").append(i + 1).append(": ").append(d.ad()).append(" ===\n");
            b.append(secilmisMetin(d)).append("\n\n");
        }
        return b.toString();
    }

    /** Belge çok uzunsa ilk sayfalar, tanımlar, bedel ve imza bölümleri gönderilir (§7). */
    static String secilmisMetin(Belge d) {
        List<String> s = d.sayfalar();
        if (s.size() <= UZUN_BELGE_SAYFA) return d.metin();
        Set<Integer> secim = new LinkedHashSet<>();
        for (int i = 0; i < Math.min(BAS_SAYFA, s.size()); i++) secim.add(i);
        String[] anahtarlar = {"tanımlar", "tanimlar", "definitions", "bedel", "ücret", "fiyat", "price",
                "consideration", "contract sum", "contract price", "ödeme", "payment", "imza", "signature",
                "in witness whereof", "taraflar", "parties"};
        int eklenen = 0;
        for (int i = 0; i < s.size() && eklenen < ANAHTAR_SAYFA_MAX; i++) {
            if (secim.contains(i)) continue;
            String k = s.get(i).toLowerCase(Locale.forLanguageTag("tr-TR"));
            for (String a : anahtarlar) {
                if (k.contains(a)) { secim.add(i); eklenen++; break; }
            }
        }
        for (int i = Math.max(0, s.size() - SON_SAYFA); i < s.size(); i++) secim.add(i);
        List<Integer> sirali = new ArrayList<>(secim);
        sirali.sort(Integer::compare);
        StringBuilder b = new StringBuilder("[Not: belge " + s.size() + " sayfa; yalnızca seçilmiş sayfalar gönderildi]\n");
        for (int i : sirali) b.append("--- Sayfa ").append(i + 1).append(" ---\n").append(s.get(i)).append('\n');
        return b.toString();
    }

    private String kisaltmaMesaji(OzetSemasi o, boolean ikiDilli) {
        int sinir = ayarlar.konuMaxKarakter();
        return "Aşağıdaki konu metni " + KarakterSayaci.say(o.konuTr()) + " karakter (yalnızca harf ve rakam sayılır). "
                + "Anlamı, üslubu ve kapanış kalıbını koruyarak en fazla " + sinir + " karaktere kısalt. "
                + "Yeni bilgi ekleme. "
                + (ikiDilli ? "konu_en alanına kısaltılmış metnin İngilizcesini yaz." : "konu_en alanını boş bırak.")
                + "\n\nkonu_tr:\n" + o.konuTr()
                + (ikiDilli && o.konuEn() != null ? "\n\nkonu_en:\n" + o.konuEn() : "");
    }

    public Optional<String> model() { return Optional.ofNullable(model.id()); }

    @Override
    public void close() {
        client.close();
    }
}
