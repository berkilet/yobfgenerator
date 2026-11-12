package legal.erk.yobf.ui;

import legal.erk.yobf.TestVerisi;
import legal.erk.yobf.config.Yapilandirma;
import legal.erk.yobf.model.Bedel;
import legal.erk.yobf.model.EkSatir;
import legal.erk.yobf.model.FormVerisi;
import legal.erk.yobf.model.Satirlar;
import legal.erk.yobf.model.SozlesmeGrubu;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Önizleme, headless ortamda resme çizilir; farklı form türleri ve imzacı sayıları hata vermeden çizilmeli. */
class OnizlemePaneliTest {

    private final Yapilandirma y = TestVerisi.yapilandirma();

    @ParameterizedTest(name = "{0}, {1} imzacı, ek onay={2}")
    @CsvSource({"YOBF,1,false", "YOBF,2,true", "YOBF_TR_EN,3,false", "YOBF,4,true", "YOBF,5,true",
            "AZILNAME,2,false", "VEKALETNAME,2,true"})
    void cizer(String tur, int imzaci, boolean ekOnay) throws Exception {
        FormVerisi f = new FormVerisi();
        f.formTuru = tur;
        f.tarih = "20.10.2026";
        f.belgeTuru = "Zeyilname";
        f.talepEdenDepartman = "Kalyon Enerji HESS-Q Departmanı";
        f.sozlesmeler.add(new SozlesmeGrubu(List.of("Kalyon YEKA GES 5 Elektrik Üretim A.Ş.", "Örnek Ltd. Şti."),
                List.of(new Bedel("650.000", "TL", "+ KDV"))));
        f.kisiler.addAll(List.of("Ad Bir", "Ad İki"));
        f.sirketler.add("Kalyon Enerji Yatırımları A.Ş.");
        f.sonaErmeTarihi = "31.12.2027";
        f.konuTr = "Karapınar 2 projemiz kapsamında ilave işlerin sözleşme kapsamına dahil edilmesine ilişkin olarak "
                + "hazırlanan zeyilname, onay ve imzanıza sunulmaktadır.";
        f.konuEn = "The amendment is hereby submitted for your approval and signature.";
        f.notMetni = "Örnek not.";
        f.imzasinaSunulacak.add("Sn. Ad SOYAD (X için)");
        f.ekSatirlar.add(new EkSatir(false, Satirlar.BELGE_TURU, "Proje", "Karapınar-2"));
        f.ekSatirlar.add(new EkSatir(true, Satirlar.KONU, "Süre", "12 ay"));
        f.gizliSatirlar.add(Satirlar.TALEP_EDEN);
        f.anaImzacilar.addAll(y.imzacilar().imzacilar().subList(0, imzaci));
        f.ekOnayAcik = ekOnay;
        if (ekOnay) f.ekOnayImzaci = y.imzacilar().bul("fkolmek").orElseThrow();
        f.logo = Path.of("assets/logos/kalyon-enerji.png");

        OnizlemePaneli p = new OnizlemePaneli(y.ayarlar());
        p.goster(f);
        BufferedImage im = new BufferedImage(595, 842, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        p.ciz(g);
        g.dispose();
        // sayfanın alt yarısında da çizim olmalı (imza bloğu)
        int koyu = 0;
        for (int yy = 421; yy < 800; yy++) for (int xx = 60; xx < 535; xx++) if ((im.getRGB(xx, yy) & 0xFF) < 128) koyu++;
        assertTrue(koyu > 200, "imza bloğu çizilmemiş görünüyor");
        Path cikti = Path.of("target", "ornek-cikti");
        Files.createDirectories(cikti);
        ImageIO.write(im, "png", cikti.resolve("onizleme_" + tur + "_" + imzaci + (ekOnay ? "_ek" : "") + ".png").toFile());
    }
}
