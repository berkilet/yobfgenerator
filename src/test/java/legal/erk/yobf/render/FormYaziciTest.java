package legal.erk.yobf.render;

import legal.erk.yobf.TestVerisi;
import legal.erk.yobf.config.Imzaci;
import legal.erk.yobf.config.Yapilandirma;
import legal.erk.yobf.model.Bedel;
import legal.erk.yobf.model.EkSatir;
import legal.erk.yobf.model.Satirlar;
import legal.erk.yobf.model.FormVerisi;
import legal.erk.yobf.model.SozlesmeGrubu;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 1–5 imzacı ve +1 ek onay kombinasyonlarıyla .docx üretimi.
 * Çıktılar görsel karşılaştırma için target/ornek-cikti altına yazılır.
 */
class FormYaziciTest {

    static final Path CIKTI = Path.of("target", "ornek-cikti");
    private final Yapilandirma y = TestVerisi.yapilandirma();
    private final FormYazici yazici = new FormYazici(y.ayarlar());

    private List<Imzaci> imzacilar(int n) {
        return y.imzacilar().imzacilar().subList(0, n);
    }

    private FormVerisi yobf() {
        FormVerisi f = new FormVerisi();
        f.tarih = "20.10.2026";
        f.belgeTuru = "Zeyilname";
        f.talepEdenDepartman = "Kalyon Enerji HESS-Q Departmanı";
        f.imzayaSunanDepartman = "Kalyon Enerji Hukuk ve Uyum Müşavirliği";
        f.sozlesmeler.add(new SozlesmeGrubu(
                List.of("Kalyon YEKA GES 5 Elektrik Üretim A.Ş.", "Örnek Danışmanlık Ltd. Şti."),
                List.of(new Bedel("650.000", "TL", "+ KDV"))));
        f.bedelEtiketi = "Zeyilnameye Konu Tutar";
        f.konuTr = "Karapınar 2 projemiz kapsamında ÇSED sonrası biyoçeşitlilik ek izlemeleri hizmet sözleşmesi kapsamında "
                + "ilave işlerin sözleşme kapsamına dahil edilmesine ilişkin olarak hazırlanan zeyilname, onay ve imzanıza sunulmaktadır.";
        f.logo = Path.of("assets/logos/kalyon-enerji.png");
        return f;
    }

    private String yazVeOku(FormVerisi f, String ad) throws Exception {
        Files.createDirectories(CIKTI);
        Path p = CIKTI.resolve(ad);
        yazici.yaz(f, p);
        assertTrue(Files.size(p) > 1000);
        try (InputStream in = Files.newInputStream(p); XWPFDocument d = new XWPFDocument(in);
             XWPFWordExtractor ex = new XWPFWordExtractor(d)) {
            assertEquals(1, d.getAllPictures().size(), "logo");
            return ex.getText();
        }
    }

    @ParameterizedTest(name = "{0} imzacı, ek onay={1}")
    @CsvSource({"1,false", "2,false", "3,false", "4,false", "5,false", "1,true", "2,true", "3,true", "4,true", "5,true"})
    void imzaciKombinasyonlari(int n, boolean ekOnay) throws Exception {
        FormVerisi f = yobf();
        f.anaImzacilar.addAll(imzacilar(n));
        f.ekOnayAcik = ekOnay;
        if (ekOnay) f.ekOnayImzaci = y.imzacilar().bul("fkolmek").orElseThrow();
        String metin = yazVeOku(f, "imzaci_" + n + (ekOnay ? "_ekonay" : "") + ".docx");
        for (Imzaci i : imzacilar(n)) {
            assertTrue(metin.contains(i.ad()), i.ad());
            assertTrue(metin.contains(i.unvan()), i.unvan());
        }
        assertTrue(metin.contains("UYGUNDUR"));
        assertEquals(ekOnay, metin.contains("\nONAY"), "ek onay başlığı");
        assertTrue(metin.contains("Zeyilnameye Konu Tutar: 650.000 TL + KDV"));
        assertTrue(metin.contains("1) Kalyon YEKA GES 5 Elektrik Üretim A.Ş."));

        // Yerleşim: 1–3 → tek satır; 4 → 2+2; 5 → 3+2 (+1 ek onay tablosu)
        try (InputStream in = Files.newInputStream(CIKTI.resolve("imzaci_" + n + (ekOnay ? "_ekonay" : "") + ".docx"));
             XWPFDocument d = new XWPFDocument(in)) {
            List<XWPFTable> t = d.getTables();
            List<Integer> beklenen = y.ayarlar().imzaBlogu().satirlar(n);
            assertEquals(1 + beklenen.size() + (ekOnay ? 1 : 0), t.size());
            for (int i = 0; i < beklenen.size(); i++) {
                assertEquals(beklenen.get(i), t.get(1 + i).getRow(0).getTableCells().size());
            }
        }
    }

    @Test
    void cokluSozlesmeVeNot() throws Exception {
        FormVerisi f = yobf();
        f.belgeTuru = "Sözleşme";
        f.sozlesmeler.clear();
        f.sozlesmeler.add(new SozlesmeGrubu(List.of("Kalyon YEKA GES 3 ve 4 Güneş Enerjisi Yatırımları A.Ş.", "Birinci Yüklenici Ltd. Şti."),
                List.of(new Bedel("3.570.000", "TL", "+ KDV"))));
        f.sozlesmeler.add(new SozlesmeGrubu(List.of("Kalyon YEKA GES 3 ve 4 Güneş Enerjisi Yatırımları A.Ş.", "İkinci Yüklenici A.Ş."),
                List.of(new Bedel("145.000", "EUR", "+ KDV"))));
        f.notEtiketi = "Müşavirliğimizin Notu:";
        f.notMetni = "Birinci paragraf.\nİkinci paragraf.";
        f.imzasinaSunulacak.add("Sn. Murathan KALYONCU (Kalyon İşletme için)");
        f.anaImzacilar.addAll(imzacilar(2));
        String m = yazVeOku(f, "coklu_sozlesme.docx");
        assertTrue(m.contains("1. Sözleşme Taraflar:"));
        assertTrue(m.contains("2. Sözleşme Taraflar:"));
        assertTrue(m.contains("1. Sözleşme Bedeli: 3.570.000 TL + KDV"));
        assertTrue(m.contains("2. Sözleşme Bedeli: 145.000 EUR + KDV"));
        assertTrue(m.contains("Müşavirliğimizin Notu: Birinci paragraf."));
        assertTrue(m.contains("Sn. Murathan KALYONCU (Kalyon İşletme için)"));
    }

    @Test
    void ikiDilliForm() throws Exception {
        FormVerisi f = yobf();
        f.formTuru = "YOBF_TR_EN";
        f.belgeTuru = "Yönetim Kurulu Kararı";
        f.sozlesmeler.get(0).bedeller.clear();
        f.konuEn = "The Board of Directors' resolution is hereby submitted for your approval and signature.";
        f.anaImzacilar.addAll(imzacilar(3));
        String m = yazVeOku(f, "iki_dilli.docx");
        assertTrue(m.contains("EXECUTIVE SUMMARY INFORMATION FORM"));
        assertTrue(m.contains("UYGUNDUR / APPROVED"));
        assertTrue(m.contains("Yönetim Kurulu Kararı / Board of Directors Resolution"));
        assertTrue(m.contains("Tarih / Date"));
        assertTrue(m.contains(f.konuEn));
        assertTrue(m.contains("Kalyon Holding Chief Legal Counsel"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"AZILNAME", "VEKALETNAME"})
    void azilnameVekaletname(String tur) throws Exception {
        FormVerisi f = yobf();
        f.formTuru = tur;
        f.belgeTuru = tur.equals("AZILNAME") ? "Azilnameler" : "Vekaletname";
        f.sozlesmeler.clear();
        f.kisiler.addAll(List.of("Ad Bir", "Ad İki", "Ad Üç"));
        f.sirketler.addAll(List.of("Kalen Elektrik Toptan Satış ve Ticaret A.Ş.", "Melet Enerji Elektrik Üretim ve Ticaret A.Ş."));
        f.sonaErmeTarihi = "31.12.2027";
        f.notMetni = "Örnek not.";
        f.anaImzacilar.addAll(imzacilar(2));
        String m = yazVeOku(f, tur.toLowerCase() + ".docx");
        assertTrue(m.contains(tur.equals("AZILNAME") ? "AZİLNAME ONAY FORMU" : "VEKALETNAME ONAY FORMU"));
        assertTrue(m.contains("Ad Bir, Ad İki, Ad Üç"));
        assertTrue(m.contains("Melet Enerji Elektrik Üretim ve Ticaret A.Ş."));
        assertEquals(tur.equals("VEKALETNAME"), m.contains("Sona Erme Tarihi"));
        assertTrue(m.contains("Not: Örnek not."));
    }

    @Test
    void satirlarDisaridaBirakilabilir() throws Exception {
        FormVerisi f = yobf();
        f.imzasinaSunulacak.add("Sn. Haluk KALYONCU");
        f.notMetni = "Gizli not.";
        f.anaImzacilar.addAll(imzacilar(2));
        f.gizliSatirlar.addAll(List.of(Satirlar.IMZASINA_SUNULACAK, Satirlar.BEDEL, Satirlar.NOT, Satirlar.TALEP_EDEN));
        String m = yazVeOku(f, "gizli_satirlar.docx");
        assertFalse(m.contains("İmzasına Sunulacak Yetkili"));
        assertFalse(m.contains("Haluk"));
        assertFalse(m.contains("Zeyilnameye Konu Tutar"));
        assertFalse(m.contains("Gizli not."));
        assertFalse(m.contains("Belgeyi Talep Eden"));
        assertTrue(m.contains("Taraflar:"));
        assertTrue(m.contains("Konu:"));
    }

    @Test
    void satirVeAltSatirEklenebilir() throws Exception {
        FormVerisi f = yobf();
        f.anaImzacilar.addAll(imzacilar(2));
        f.ekSatirlar.add(new EkSatir(false, Satirlar.BELGE_TURU, "Proje", "Karapınar-2 GES"));
        f.ekSatirlar.add(new EkSatir(false, Satirlar.SON, "Ekler", "1) Sözleşme\n2) Teklif"));
        f.ekSatirlar.add(new EkSatir(true, Satirlar.KONU, "Süre", "12 ay"));
        f.ekSatirlar.add(new EkSatir(true, Satirlar.TARIH, "", "(imza tarihi)"));
        String m = yazVeOku(f, "ek_satirlar.docx");
        assertTrue(m.contains("Süre: 12 ay"));
        assertTrue(m.indexOf("Belge Türü") < m.indexOf("Proje") && m.indexOf("Proje") < m.indexOf("Belgeyi Talep Eden"));
        assertTrue(m.contains("(imza tarihi)"));
        assertTrue(m.indexOf("Ekler") > m.indexOf("Belgenin Konusu"));
        try (InputStream in = Files.newInputStream(CIKTI.resolve("ek_satirlar.docx")); XWPFDocument d = new XWPFDocument(in)) {
            // Tarih, Proje, Belge Türü, Talep Eden, İmzaya Sunan, Konu, Ekler = 7 satır
            assertEquals(7, d.getTables().get(0).getRows().size());
        }
    }

    @Test
    void dosyaAdi() {
        FormVerisi f = yobf();
        String ad = FormYazici.dosyaAdi(f, s -> s.startsWith("Kalyon"), LocalDate.of(2026, 10, 20));
        assertEquals("YOBF_Zeyilname_ÖrnekDanışmanlıkLtd_20102026.docx", ad);
    }
}
