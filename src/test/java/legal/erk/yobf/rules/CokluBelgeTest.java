package legal.erk.yobf.rules;

import legal.erk.yobf.TestVerisi;
import legal.erk.yobf.ai.OzetSemasi;
import legal.erk.yobf.config.Yapilandirma;
import legal.erk.yobf.model.FormVerisi;
import legal.erk.yobf.render.FormYazici;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Birden fazla belge: ayrı belgeler (türlerine göre numaralı gruplar) ve aynı işleme ait belgeler (tek liste). */
class CokluBelgeTest {

    private final Yapilandirma y = TestVerisi.yapilandirma();
    private final FormHazirlayici h = new FormHazirlayici(y);

    private static OzetSemasi ozet(String iliski, List<OzetSemasi.BelgeBilgisi> belgeler, List<OzetSemasi.Taraf> taraflar,
                                  List<OzetSemasi.BedelOge> bedel) {
        return new OzetSemasi("Sözleşme", "YOBF", taraflar, "Konu.", "", bedel, List.of(), "", List.of(),
                new OzetSemasi.Guven(1, 1), belgeler, iliski);
    }

    private static OzetSemasi.Taraf t(String u, int no) { return new OzetSemasi.Taraf(u, "", u, no); }

    @Test
    void ayriBelgelerTurleriyleGruplanir() {
        OzetSemasi o = ozet("AYRI_BELGELER",
                List.of(new OzetSemasi.BelgeBilgisi(1, "Sözleşme"), new OzetSemasi.BelgeBilgisi(2, "Protokol")),
                List.of(t("Kasar Yapı Ltd. Şti.", 1), t("Kalyon YEKA GES 3 ve 4 Güneş Enerjisi Yatırımları A.Ş.", 1),
                        t("Kalyon YEKA GES 3 ve 4 Güneş Enerjisi Yatırımları A.Ş.", 2), t("Çerçi Mühendislik Ltd. Şti.", 2)),
                List.of(new OzetSemasi.BedelOge("Sözleşme Bedeli", "3.570.000", "TL", "+ KDV", 1),
                        new OzetSemasi.BedelOge("Bedel", "145.000", "EUR", "+ KDV", 2)));
        FormVerisi f = h.hazirla(o, 2);
        assertFalse(f.ayniIslem);
        assertEquals(2, f.sozlesmeler.size());
        assertEquals("Sözleşme", f.sozlesmeler.get(0).belgeTuru);
        assertEquals("Protokol", f.sozlesmeler.get(1).belgeTuru);
        assertEquals("Sözleşme ve Protokol", f.belgeTuru);
        // grup şirketi önce
        assertTrue(f.sozlesmeler.get(0).taraflar.get(0).startsWith("Kalyon"));
        assertEquals("1. Sözleşme Taraflar", FormYazici.grupTarafBasligi(f, 0));
        assertEquals("2. Protokol Taraflar", FormYazici.grupTarafBasligi(f, 1));
        assertEquals("2. Protokol Bedeli", FormYazici.grupBedelBasligi(f, 1));

        f.grupBasligi = "{no}. Evrak ({tur}) Taraflar:";
        assertEquals("1. Evrak (Sözleşme) Taraflar", FormYazici.grupTarafBasligi(f, 0));
        assertEquals("2. Evrak (Protokol) Bedeli", FormYazici.grupBedelBasligi(f, 1));
        f.grupBasligi = "{no}. Belge Taraflar:";
        assertEquals("2. Belge Taraflar", FormYazici.grupTarafBasligi(f, 1));
    }

    @Test
    void ayniIslemeAitBelgelerTekListe() {
        OzetSemasi o = ozet("AYNI_ISLEM",
                List.of(new OzetSemasi.BelgeBilgisi(1, "Dilekçe"), new OzetSemasi.BelgeBilgisi(2, "Taahhütname"),
                        new OzetSemasi.BelgeBilgisi(3, "Yönetim Kurulu Kararı")),
                List.of(t("Kalyon Enerji Yatırımları A.Ş.", 1), t("T.C. Enerji Piyasası Düzenleme Kurumu", 1),
                        t("KALYON ENERJİ YATIRIMLARI ANONİM ŞİRKETİ", 2), t("Kalyon Enerji Yatırımları A.Ş.", 3)),
                List.of());
        FormVerisi f = h.hazirla(o, 3);
        assertTrue(f.ayniIslem);
        assertEquals(1, f.sozlesmeler.size());
        assertEquals(List.of("Kalyon Enerji Yatırımları A.Ş.", "T.C. Enerji Piyasası Düzenleme Kurumu"), f.sozlesmeler.get(0).taraflar);
        assertEquals("Dilekçe, Taahhütname ve Yönetim Kurulu Kararı", f.belgeTuru);
        assertFalse(FormYazici.numaraliGruplar(f));
    }

    @Test
    void ayniTurdekiBelgelerTekTurYazilir() {
        assertEquals("Sözleşme", FormHazirlayici.birlestir(List.of("Sözleşme", "Sözleşme")));
        assertEquals("A, B ve C", FormHazirlayici.birlestir(List.of("A", "B", "C", "b")));
    }

    @Test
    void grupTuruBossaFormTuruVeyaBelge() {
        FormVerisi f = new FormVerisi();
        f.belgeTuru = "Sözleşme";
        f.sozlesmeler.add(new legal.erk.yobf.model.SozlesmeGrubu());
        f.sozlesmeler.add(new legal.erk.yobf.model.SozlesmeGrubu());
        assertEquals("2. Sözleşme Taraflar", FormYazici.grupTarafBasligi(f, 1));
        f.belgeTuru = "Sözleşme ve Protokol";
        assertEquals("2. Belge Taraflar", FormYazici.grupTarafBasligi(f, 1));
    }
}
