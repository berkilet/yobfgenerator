package legal.erk.yobf.ai;

import legal.erk.yobf.config.Ayarlar;
import legal.erk.yobf.extract.BelgeOkuyucu.Belge;
import legal.erk.yobf.rules.KarakterSayaci;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Belgelerden yapılandırılmış özet çıkarımı (CLAUDE.md §7) — sağlayıcıdan bağımsız.
 * Sistem talimatı prompts/ozet_prompt.md'den okunur; istek seçili ModelIstemcisi ile gönderilir
 * (Anthropic, OpenAI, Gemini veya OpenAI uyumlu servis).
 */
public final class OzetCikarici implements AutoCloseable {

    /** Uzun belge eşiği (§7): bunun üzerindeki belgelerden yalnızca ilgili sayfalar gönderilir. */
    static final int UZUN_BELGE_SAYFA = 150;
    private static final int BAS_SAYFA = 25;
    private static final int SON_SAYFA = 10;
    private static final int ANAHTAR_SAYFA_MAX = 40;

    private final Ayarlar ayarlar;
    private final String sistemTalimati;
    private final ModelIstemcisi istemci;

    public OzetCikarici(Ayarlar ayarlar, Path promptDosyasi, ModelIstemcisi istemci) throws IOException {
        this.ayarlar = ayarlar;
        this.istemci = istemci;
        this.sistemTalimati = Files.readString(promptDosyasi, StandardCharsets.UTF_8)
                .replace("{{KONU_MAX}}", String.valueOf(ayarlar.konuMaxKarakter()));
    }

    /** Özet + kısaltma denemeleri. ilerleme: arayüzde durum çubuğu için. */
    public OzetSemasi ozetCikar(List<Belge> maskeliBelgeler, boolean ikiDilli, Consumer<String> ilerleme) throws ModelHatasi {
        ilerleme.accept("Model çağrılıyor (" + istemci.etiket() + ")…");
        OzetSemasi o = istemci.yapilandirilmis(OzetSemasi.class, sistemTalimati, kullaniciMesaji(maskeliBelgeler, ikiDilli), 16000);

        int sinir = ayarlar.konuMaxKarakter();
        for (int deneme = 1; deneme <= ayarlar.kisaltmaDenemeSayisi() && KarakterSayaci.say(o.konuTr()) > sinir; deneme++) {
            ilerleme.accept("Konu " + KarakterSayaci.say(o.konuTr()) + " karakter; kısaltılıyor (" + deneme + "/"
                    + ayarlar.kisaltmaDenemeSayisi() + ")…");
            OzetSemasi.Kisaltma k = istemci.yapilandirilmis(OzetSemasi.Kisaltma.class, sistemTalimati,
                    kisaltmaMesaji(o, ikiDilli), 4000);
            o = new OzetSemasi(o.belgeTuru(), o.formTuruOnerisi(), o.taraflar(),
                    k.konuTr() == null || k.konuTr().isBlank() ? o.konuTr() : k.konuTr(),
                    ikiDilli && k.konuEn() != null && !k.konuEn().isBlank() ? k.konuEn() : o.konuEn(),
                    o.bedel(), o.kisiler(), o.sonaErmeTarihi(), o.dikkatHususlari(), o.guven(),
                    o.belgeler(), o.cokluBelgeIliskisi());
        }
        return o;
    }

    String kullaniciMesaji(List<Belge> belgeler, boolean ikiDilli) {
        StringBuilder b = new StringBuilder();
        if (belgeler.size() > 1) {
            b.append("Aşağıda tek bir forma bağlanacak ").append(belgeler.size()).append(" belge var.\n")
                    .append("- belgeler listesine her belgenin sıra numarasını ve türünü yaz (belgenin kendi başlığına göre: ")
                    .append("Sözleşme, Protokol, Zeyilname, Taahhütname, Dilekçe, Yönetim Kurulu Kararı vb.).\n")
                    .append("- coklu_belge_iliskisi: belgeler ayrı ayrı imzalanacak bağımsız belgelerse (ör. aynı projede iki ")
                    .append("farklı yükleniciyle iki sözleşme) AYRI_BELGELER; aynı işin/başvurunun parçalarıysa ")
                    .append("(ör. dilekçe + taahhütname + yönetim kurulu kararı) AYNI_ISLEM yaz.\n")
                    .append("- Her taraf ve bedel için belge_no alanına ait olduğu belgenin sıra numarasını yaz.\n")
                    .append("- konu_tr tek ve ortak olmalıdır; AYNI_ISLEM ise belgeleri birlikte an ")
                    .append("(ör. \"… hazırlanan dilekçe, taahhütname ve yönetim kurulu kararı onay ve imzanıza sunulmaktadır.\").\n\n");
        } else {
            b.append("Tek belge var: belgeler listesine tek eleman (belge_no 1 ve türü) yaz, coklu_belge_iliskisi boş kalsın.\n\n");
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

    public String etiket() { return istemci.etiket(); }

    @Override
    public void close() {
        istemci.close();
    }
}
