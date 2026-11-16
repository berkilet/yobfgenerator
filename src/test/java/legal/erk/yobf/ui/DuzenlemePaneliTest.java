package legal.erk.yobf.ui;

import legal.erk.yobf.TestVerisi;
import legal.erk.yobf.model.FormVerisi;
import legal.erk.yobf.model.SozlesmeGrubu;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Düzenleme ekranı ↔ FormVerisi gidiş-dönüş: grup türleri, başlık biçimi, aynı işlem birleştirme. */
class DuzenlemePaneliTest {

    @Test
    void grupTurleriVeBaslikKorunur() throws Exception {
        // Düzenleme paneli ekran gerektirir; varsayılan (headless) test koşusunda atlanır.
        // Elle: xvfb-run mvn test -Dtest=DuzenlemePaneliTest -Dtest.headless=false
        org.junit.jupiter.api.Assumptions.assumeFalse(java.awt.GraphicsEnvironment.isHeadless(), "ekran yok");
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            DuzenlemePaneli p = new DuzenlemePaneli(TestVerisi.yapilandirma());
            FormVerisi f = new FormVerisi();
            SozlesmeGrubu a = new SozlesmeGrubu(List.of("Kalyon Enerji Yatırımları A.Ş.", "Bir Ltd. Şti."), List.of());
            a.belgeTuru = "Sözleşme";
            SozlesmeGrubu b = new SozlesmeGrubu(List.of("Kalyon Enerji Yatırımları A.Ş.", "İki A.Ş."), List.of());
            b.belgeTuru = "Taahhütname";
            f.sozlesmeler.addAll(List.of(a, b));
            f.grupBasligi = "{no}. Evrak ({tur}) Taraflar:";
            p.yukle(f);

            FormVerisi o = p.oku();
            assertFalse(o.ayniIslem);
            assertEquals(2, o.sozlesmeler.size());
            assertEquals("Sözleşme", o.sozlesmeler.get(0).belgeTuru);
            assertEquals("Taahhütname", o.sozlesmeler.get(1).belgeTuru);
            assertEquals("{no}. Evrak ({tur}) Taraflar:", o.grupBasligi);

            assertTrue(p.cokluSecenekleriGorunur(), "iki grup varken seçenekler görünür");

            FormVerisi tek = new FormVerisi();
            tek.sozlesmeler.add(new SozlesmeGrubu(List.of("Kalyon Enerji Yatırımları A.Ş.", "Bir Ltd. Şti."), List.of()));
            p.yukle(tek);
            assertFalse(p.cokluSecenekleriGorunur(), "tek belgede gizli");
            tek.belgeSayisi = 2;
            p.yukle(tek);
            assertTrue(p.cokluSecenekleriGorunur(), "analize iki belge verildiyse görünür");
            assertTrue(p.oku().cokluBelge(), "iki belgede Konu sınırı 600");
            tek.belgeSayisi = 1;
            p.yukle(tek);
            assertFalse(p.oku().cokluBelge(), "tek belgede Konu sınırı 400");

            f.ayniIslem = true;
            p.yukle(f);
            FormVerisi t = p.oku();
            assertTrue(t.ayniIslem);
            assertEquals(1, t.sozlesmeler.size());
            assertEquals(List.of("Kalyon Enerji Yatırımları A.Ş.", "Bir Ltd. Şti.", "İki A.Ş."), t.sozlesmeler.get(0).taraflar);
        });
    }
}
