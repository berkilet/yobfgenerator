package legal.erk.yobf.rules;

import legal.erk.yobf.TestVerisi;
import legal.erk.yobf.config.Imzaci;
import legal.erk.yobf.config.Yapilandirma;
import legal.erk.yobf.model.Bedel;
import legal.erk.yobf.model.FormVerisi;
import legal.erk.yobf.model.SozlesmeGrubu;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DogrulayiciTest {

    private final Yapilandirma y = TestVerisi.yapilandirma();
    private final Dogrulayici d = new Dogrulayici(y.ayarlar(), y.sirketler());

    private static final String KAYNAK = """
            İşbu Sözleşme, KALYON YEKA GES 5 ELEKTRİK ÜRETİM ANONİM ŞİRKETİ ("İşveren") ile
            Venüs Danışmanlık Proje ve Müh. Hizm. Ltd. Şti. ("Yüklenici") arasında akdedilmiştir.
            Madde 5 - Bedel: 650.000,00 TL (altıyüzellibin) + KDV.
            """;

    private FormVerisi gecerliForm() {
        FormVerisi f = new FormVerisi();
        f.tarih = "20.10.2026";
        f.belgeTuru = "Zeyilname";
        f.sozlesmeler.add(new SozlesmeGrubu(
                List.of("Kalyon YEKA GES 5 Elektrik Üretim A.Ş.", "Venüs Danışmanlık Proje ve Müh. Hizm. Ltd. Şti."),
                List.of(new Bedel("650.000", "TL", "+ KDV"))));
        f.konuTr = "Kısa bir konu metni onay ve imzanıza sunulmaktadır.";
        f.anaImzacilar.add(y.imzacilar().bul("gsumer").orElseThrow());
        return f;
    }

    @Test
    void gecerliFormdaKirmiziYok() {
        List<Uyari> u = d.dogrula(gecerliForm(), KAYNAK);
        assertFalse(Dogrulayici.engelleyiciVar(u), u.toString());
    }

    @Test
    void hataliTarihYakalanir() {
        FormVerisi f = gecerliForm();
        f.tarih = "0110.2026";
        assertTrue(d.dogrula(f, KAYNAK).stream().anyMatch(x -> x.alan().equals("Tarih") && x.seviye() == Uyari.Seviye.KIRMIZI));
        f.tarih = "31.02.2026";
        assertTrue(Dogrulayici.engelleyiciVar(d.dogrula(f, KAYNAK)));
    }

    @Test
    void kaynaktaOlmayanTarafVeBedel() {
        FormVerisi f = gecerliForm();
        f.sozlesmeler.get(0).taraflar.add("Uydurma Firma A.Ş.");
        f.sozlesmeler.get(0).bedeller.add(new Bedel("1.234.567", "USD", ""));
        List<Uyari> u = d.dogrula(f, KAYNAK);
        assertTrue(u.stream().anyMatch(x -> x.mesaj().contains("Uydurma Firma")));
        assertTrue(u.stream().anyMatch(x -> x.alan().equals("Bedel") && x.mesaj().contains("1.234.567")));
    }

    @Test
    void ingilizBicimiBedelDeBulunur() {
        FormVerisi f = gecerliForm();
        f.sozlesmeler.get(0).bedeller.set(0, new Bedel("497.746,00", "USD", ""));
        List<Uyari> u = d.dogrula(f, KAYNAK + "\nContract Price: USD 497,746.00");
        assertTrue(u.stream().noneMatch(x -> x.alan().equals("Bedel")), u.toString());
    }

    @Test
    void konuSiniriVeImzaci() {
        FormVerisi f = gecerliForm();
        f.konuTr = "a".repeat(401);
        f.anaImzacilar.clear();
        List<Uyari> u = d.dogrula(f, KAYNAK);
        assertTrue(u.stream().anyMatch(x -> x.alan().equals("Konu") && x.seviye() == Uyari.Seviye.KIRMIZI));
        assertTrue(u.stream().anyMatch(x -> x.alan().equals("İmzacılar")));
    }

    @Test
    void grupSirketiYoksaUyari() {
        FormVerisi f = gecerliForm();
        f.sozlesmeler.get(0).taraflar.remove(0);
        assertTrue(d.dogrula(f, KAYNAK).stream().anyMatch(x -> x.mesaj().contains("grup şirketi")));
    }

    @Test
    void gecmisSonaErmeTarihi() {
        FormVerisi f = gecerliForm();
        f.formTuru = "VEKALETNAME";
        f.sirketler.add("Kalyon Enerji Yatırımları A.Ş.");
        f.kisiler.add("Ad Soyad");
        f.sonaErmeTarihi = "01.01.2020";
        f.ekOnayAcik = true;
        f.ekOnayImzaci = new Imzaci("", "", "", "");
        List<Uyari> u = d.dogrula(f, null);
        assertTrue(u.stream().anyMatch(x -> x.alan().equals("Sona Erme Tarihi") && x.mesaj().contains("geçmişte")));
        assertTrue(u.stream().anyMatch(x -> x.alan().equals("Ek Onay")));
    }
}
