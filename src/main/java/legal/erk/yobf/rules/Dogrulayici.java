package legal.erk.yobf.rules;

import legal.erk.yobf.config.Ayarlar;
import legal.erk.yobf.config.SirketKaydi;
import legal.erk.yobf.config.Sirketler;
import legal.erk.yobf.model.Bedel;
import legal.erk.yobf.model.FormVerisi;
import legal.erk.yobf.model.SozlesmeGrubu;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** CLAUDE.md §8 — modelden bağımsız doğrulama katmanı. */
public final class Dogrulayici {

    private final Ayarlar ayarlar;
    private final Sirketler sirketler;

    public Dogrulayici(Ayarlar ayarlar, Sirketler sirketler) {
        this.ayarlar = ayarlar;
        this.sirketler = sirketler;
    }

    /**
     * @param form       doğrulanacak form
     * @param kaynakMetin belgelerden çıkarılan (maskeli) metin; null ise kaynak kontrolleri atlanır
     */
    public List<Uyari> dogrula(FormVerisi form, String kaynakMetin) {
        List<Uyari> u = new ArrayList<>();
        String kaynakNorm = kaynakMetin == null ? null : UnvanNormalizer.normalize(kaynakMetin);
        Set<BigDecimal> kaynakSayilar = kaynakMetin == null ? Set.of() : TurkSayi.metindekiSayilar(kaynakMetin);

        // 3. Tarih
        if (form.tarih == null || form.tarih.isBlank()) {
            u.add(Uyari.kirmizi("Tarih", "Tarih boş."));
        } else if (!TarihKontrol.gecerliMi(form.tarih)) {
            u.add(Uyari.kirmizi("Tarih", "'" + form.tarih + "' GG.AA.YYYY biçiminde geçerli bir tarih değil."));
        }

        boolean ozelForm = form.azilname() || form.vekaletname();

        // 1. Taraf unvanları kaynakta geçiyor mu?
        if (kaynakNorm != null) {
            List<String> kontrolEdilecek = ozelForm ? form.sirketler : form.tumTaraflar();
            for (String t : kontrolEdilecek) {
                if (t == null || t.isBlank()) continue;
                if (!kaynaktaVar(t, kaynakNorm)) {
                    u.add(Uyari.kirmizi(ozelForm ? "Şirketler" : "Taraflar", "'" + t + "' kaynak metinde bulunamadı."));
                }
            }
        }

        // 2. Bedeldeki rakam kaynakta geçiyor mu?
        if (!ozelForm && kaynakMetin != null) {
            for (SozlesmeGrubu g : form.sozlesmeler) {
                for (Bedel b : g.bedeller) {
                    if (b.bos()) continue;
                    Set<BigDecimal> s = TurkSayi.tutardakiSayi(b.metin());
                    if (s.isEmpty()) {
                        u.add(Uyari.sari("Bedel", "'" + b.metin() + "' içinde rakam bulunamadı."));
                    } else if (Collections.disjoint(s, kaynakSayilar)) {
                        u.add(Uyari.kirmizi("Bedel", "'" + b.metin() + "' tutarı kaynak metinde bulunamadı."));
                    }
                }
            }
        }

        // 4. Konu karakter sayımı
        int sinir = ayarlar.konuMaxKarakter();
        int n = KarakterSayaci.say(form.konuTr);
        if (form.konuTr == null || form.konuTr.isBlank()) {
            u.add(Uyari.kirmizi("Konu", "Konu boş."));
        } else if (n > sinir) {
            String m = "Konu " + n + " / " + sinir + " karakter — sınır aşıldı.";
            u.add(ayarlar.konuSinirSert() ? Uyari.kirmizi("Konu", m) : Uyari.sari("Konu", m));
        }
        if (form.ikiDilli() && (form.konuEn == null || form.konuEn.isBlank())) {
            u.add(Uyari.kirmizi("Konu (EN)", "İki dilli formda İngilizce konu boş."));
        }

        // 5. En az bir grup şirketi taraf mı?
        List<String> sirketAdlari = form.logoIcinSirketler();
        boolean grupVar = sirketAdlari.stream()
                .anyMatch(t -> UnvanNormalizer.esle(t, sirketler.sirketler()).isPresent());
        if (!grupVar) {
            u.add(Uyari.sari(ozelForm ? "Şirketler" : "Taraflar", "Taraflarda grup şirketi tespit edilemedi."));
        }

        // Vekaletname sona erme tarihi
        if (form.vekaletname()) {
            if (form.sonaErmeTarihi == null || form.sonaErmeTarihi.isBlank()) {
                u.add(Uyari.sari("Sona Erme Tarihi", "Sona erme tarihi boş."));
            } else {
                Optional<LocalDate> d = TarihKontrol.coz(form.sonaErmeTarihi);
                if (d.isEmpty()) {
                    u.add(Uyari.kirmizi("Sona Erme Tarihi", "'" + form.sonaErmeTarihi + "' GG.AA.YYYY biçiminde geçerli bir tarih değil."));
                } else if (d.get().isBefore(LocalDate.now())) {
                    u.add(Uyari.sari("Sona Erme Tarihi", "Sona erme tarihi geçmişte: " + form.sonaErmeTarihi));
                }
            }
        }
        if (ozelForm && form.kisiler.isEmpty()) {
            u.add(Uyari.kirmizi("Kişiler", "Kişi listesi boş."));
        }

        // 6. En az 1 ana imzacı
        int imzaci = (int) form.anaImzacilar.stream().filter(i -> i != null && !i.ad().isBlank()).count();
        if (imzaci < ayarlar.imzaBlogu().anaImzaciMin()) {
            u.add(Uyari.kirmizi("İmzacılar", "En az " + ayarlar.imzaBlogu().anaImzaciMin() + " ana imzacı seçilmeli."));
        }
        if (imzaci > ayarlar.imzaBlogu().anaImzaciMax()) {
            u.add(Uyari.kirmizi("İmzacılar", "En fazla " + ayarlar.imzaBlogu().anaImzaciMax() + " ana imzacı seçilebilir."));
        }
        if (form.ekOnayAcik && (form.ekOnayImzaci == null || form.ekOnayImzaci.ad().isBlank())) {
            u.add(Uyari.kirmizi("Ek Onay", "Ek onay bloğu açık ama imzacı seçilmedi."));
        }
        return u;
    }

    private boolean kaynaktaVar(String taraf, String kaynakNorm) {
        if (UnvanNormalizer.metindeGeciyor(taraf, kaynakNorm)) return true;
        if (UnvanNormalizer.metindeGeciyor(UnvanNormalizer.parantezsiz(taraf), kaynakNorm)) return true;
        Optional<SirketKaydi> k = UnvanNormalizer.esle(taraf, sirketler.sirketler());
        if (k.isPresent()) {
            if (UnvanNormalizer.metindeGeciyor(k.get().unvan(), kaynakNorm)) return true;
            for (String t : k.get().takmaAdlar()) if (UnvanNormalizer.metindeGeciyor(t, kaynakNorm)) return true;
        }
        return false;
    }

    public static boolean engelleyiciVar(List<Uyari> uyarilar) {
        return uyarilar.stream().anyMatch(x -> x.seviye() == Uyari.Seviye.KIRMIZI);
    }
}
