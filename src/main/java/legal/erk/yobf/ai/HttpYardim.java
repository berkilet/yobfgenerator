package legal.erk.yobf.ai;

import com.fasterxml.jackson.databind.JsonNode;
import legal.erk.yobf.config.Ayarlar;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/** OpenAI ve Gemini için ortak HTTP: proxy, zaman aşımı, 429/5xx'te yeniden deneme, anlaşılır hata. */
final class HttpYardim {

    record Yanit(int durum, JsonNode govde, String ham) {}

    private final HttpClient http;
    private final String saglayiciAdi;

    HttpYardim(Ayarlar ayarlar, String saglayiciAdi) {
        this.saglayiciAdi = saglayiciAdi;
        HttpClient.Builder b = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20));
        if (ayarlar.proxy().tanimli()) {
            b.proxy(ProxySelector.of(new InetSocketAddress(ayarlar.proxy().host(), ayarlar.proxy().port())));
        }
        this.http = b.build();
    }

    Yanit postJson(String adres, Map<String, String> basliklar, JsonNode govde) throws ModelHatasi {
        String metin;
        try {
            metin = JsonSema.JSON.writeValueAsString(govde);
        } catch (IOException e) {
            throw new ModelHatasi("İstek hazırlanamadı: " + e.getMessage(), e);
        }
        int[] beklemeler = {0, 1500, 4000};
        Yanit son = null;
        for (int bekle : beklemeler) {
            if (bekle > 0) {
                try {
                    Thread.sleep(bekle);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new ModelHatasi("İşlem iptal edildi.", e);
                }
            }
            HttpRequest.Builder r = HttpRequest.newBuilder(URI.create(adres))
                    .timeout(Duration.ofMinutes(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(metin, StandardCharsets.UTF_8));
            basliklar.forEach(r::header);
            try {
                HttpResponse<String> h = http.send(r.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                JsonNode j;
                try {
                    j = JsonSema.JSON.readTree(h.body());
                } catch (IOException e) {
                    j = JsonSema.JSON.createObjectNode();
                }
                son = new Yanit(h.statusCode(), j, h.body());
                if (h.statusCode() != 429 && h.statusCode() < 500) return son;
            } catch (IOException e) {
                throw new ModelHatasi(saglayiciAdi + " sunucusuna bağlanılamadı. İnternet/proxy ayarlarını kontrol edin: "
                        + e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ModelHatasi("İşlem iptal edildi.", e);
            } catch (IllegalArgumentException e) {
                throw new ModelHatasi("Geçersiz API adresi: " + adres, e);
            }
        }
        return son;
    }

    /** HTTP hata kodunu kullanıcıya anlaşılır mesaja çevirir. */
    ModelHatasi hata(Yanit y) {
        String mesaj = y.govde().path("error").path("message").asText("");
        if (mesaj.isBlank()) mesaj = y.ham() == null ? "" : y.ham().substring(0, Math.min(300, y.ham().length()));
        return switch (y.durum()) {
            case 401, 403 -> new ModelHatasi(saglayiciAdi + " API anahtarı geçersiz veya yetkisiz (" + y.durum()
                    + "). ☰ › Yapay zekâ ayarları. " + mesaj);
            case 404 -> new ModelHatasi(saglayiciAdi + ": model veya adres bulunamadı (404). Model adını kontrol edin. " + mesaj);
            case 429 -> new ModelHatasi(saglayiciAdi + ": hız sınırı veya kota doldu (429). " + mesaj);
            default -> new ModelHatasi(saglayiciAdi + " API hatası (" + y.durum() + "): " + mesaj);
        };
    }
}
