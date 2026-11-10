package legal.erk.yobf.rules;

import legal.erk.yobf.TestVerisi;
import legal.erk.yobf.config.SirketKaydi;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UnvanNormalizerTest {

    private final List<SirketKaydi> kayitlar = TestVerisi.yapilandirma().sirketler().sirketler();

    @Test
    void ticVeTicaretAyniKaydaEslesir() {
        var a = UnvanNormalizer.esle("Kalen Elektrik Toptan Satış ve Tic. A.Ş.", kayitlar);
        var b = UnvanNormalizer.esle("Kalen Elektrik Toptan Satış ve Ticaret A.Ş.", kayitlar);
        assertTrue(a.isPresent());
        assertEquals(a, b);
        assertEquals("kalen-elektrik-toptan", a.get().id());
        assertTrue(UnvanNormalizer.ayniMi("Kalen Elektrik Toptan Satış ve Tic. A.Ş.", "Kalen Elektrik Toptan Satış ve Ticaret A.Ş."));
    }

    @Test
    void varyasyonlar() {
        assertTrue(UnvanNormalizer.ayniMi("KALEN ELEKTRİK TOPTAN SATIŞ VE TİCARET ANONİM ŞİRKETİ", "Kalen Elektrik Toptan Satış ve Tic. A.Ş."));
        assertTrue(UnvanNormalizer.ayniMi("Melet Enerji Elektrik Üretim ve Tic.  A. Ş.", "Melet Enerji Elektrik Üretim ve Ticaret A.Ş."));
        assertTrue(UnvanNormalizer.ayniMi("Kasar Yapı İnş. San. Tic. Ltd. Şti.", "KASAR YAPI İNŞAAT SANAYİ TİCARET LİMİTED ŞİRKETİ"));
        assertTrue(UnvanNormalizer.ayniMi("Venüs Danışmanlık Ltd.Şti.", "Venus Danismanlik Limited Sirketi"));
        assertFalse(UnvanNormalizer.ayniMi("Kalen Enerji Elektrik Üretim A.Ş.", "Kalen Elektrik Toptan Satış ve Ticaret A.Ş."));
    }

    @Test
    void grupSirketleriEslesir() {
        assertEquals("kalyon-yeka-ges-5", UnvanNormalizer.esle("KALYON YEKA GES 5 ELEKTRİK ÜRETİM ANONİM ŞİRKETİ", kayitlar).orElseThrow().id());
        assertEquals("kalyon-isletme", UnvanNormalizer.esle("Kalyon İşletme ve Yönetim Danışmanlığı A.Ş.", kayitlar).orElseThrow().id());
        assertEquals("kalen-enerji-uretim", UnvanNormalizer.esle("Kalen Enerji Elektrik Üretim A.Ş.", kayitlar).orElseThrow().id());
        assertEquals("kalyon-yeka-res-3", UnvanNormalizer.esle("Kalyon YEKA RES 3 Rüzgar Enerjisi Yatırımları A.Ş. (\"Şirket\")", kayitlar).orElseThrow().id());
    }

    @Test
    void grupDisiTarafEslesmez() {
        assertTrue(UnvanNormalizer.esle("UK Grid Solutions Limited (GE UK)", kayitlar).isEmpty());
        assertTrue(UnvanNormalizer.esle("Av. Hamdi Bildirici", kayitlar).isEmpty());
        assertTrue(UnvanNormalizer.esle("Kivi Stratejik Planlama A.Ş.", kayitlar).isEmpty());
    }

    @Test
    void parantezliKisaltmaAtilir() {
        assertEquals("UK Grid Solutions Limited", UnvanNormalizer.parantezsiz("UK Grid Solutions Limited (GE UK)"));
    }
}
