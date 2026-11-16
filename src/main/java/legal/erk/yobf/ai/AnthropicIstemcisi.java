package legal.erk.yobf.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.BadRequestException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.anthropic.models.beta.messages.StructuredMessage;
import com.anthropic.models.beta.messages.StructuredMessageCreateParams;
import legal.erk.yobf.config.Ayarlar;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.Duration;

/** Anthropic Claude — resmi Java SDK, yapılandırılmış çıktı (output_config.format). */
public final class AnthropicIstemcisi implements ModelIstemcisi {

    private static final String YEDEK_MODEL_BETA = "server-side-fallback-2026-07-01";

    private final Ayarlar ayarlar;
    private final Ayarlar.ModelSecenegi model;
    private final AnthropicClient client;

    public AnthropicIstemcisi(Ayarlar ayarlar, String modelId, String apiAnahtari) {
        this.ayarlar = ayarlar;
        this.model = ayarlar.modelSecenegi(modelId == null || modelId.isBlank() ? ayarlar.model() : modelId);
        AnthropicOkHttpClient.Builder b = AnthropicOkHttpClient.builder()
                .apiKey(apiAnahtari)
                .timeout(Duration.ofMinutes(5))
                .maxRetries(3);
        if (ayarlar.proxy().tanimli()) {
            b.proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(ayarlar.proxy().host(), ayarlar.proxy().port())));
        }
        this.client = b.build();
    }

    @Override public String etiket() { return "Anthropic · " + model; }

    @Override
    public <T> T yapilandirilmis(Class<T> tur, String sistem, String kullanici, long maxTokens) throws ModelHatasi {
        StructuredMessageCreateParams<T> params = istek(tur, sistem, kullanici, maxTokens);
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
            throw new ModelHatasi("Anthropic API anahtarı geçersiz veya yetkisiz (401). ☰ › Yapay zekâ ayarları.", e);
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
    <T> StructuredMessageCreateParams<T> istek(Class<T> tur, String sistem, String kullanici, long maxTokens) {
        MessageCreateParams.Builder b = MessageCreateParams.builder()
                .model(model.id())
                .maxTokens(maxTokens)
                .system(sistem)
                .addUserMessage(kullanici);
        if (ayarlar.sunucuTarafiYedekModel() && model.yedekModel()) {
            b.addBeta(YEDEK_MODEL_BETA).fallbacksDefault();
        }
        return b.outputConfig(tur).build();
    }

    @Override
    public void close() {
        client.close();
    }
}
