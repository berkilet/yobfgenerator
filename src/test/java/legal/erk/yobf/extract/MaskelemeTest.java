package legal.erk.yobf.extract;

import legal.erk.yobf.config.Ayarlar;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MaskelemeTest {

    private final Maskeleme m = new Maskeleme(new Ayarlar.Maskeleme(true, true, true, true));

    @Test
    void kisiselVerilerMaskelenir() {
        String metin = "TCKN: 10000000146, IBAN: TR33 0006 1005 1978 6457 8413 26, "
                + "Tel: 0532 123 45 67 / +90 (212) 555 12 34, e-posta: ad.soyad@ornek.com.tr";
        Maskeleme.Sonuc s = m.uygula(metin);
        assertFalse(s.metin().contains("10000000146"), s.metin());
        assertFalse(s.metin().contains("TR33"), s.metin());
        assertFalse(s.metin().contains("532"), s.metin());
        assertFalse(s.metin().contains("@"), s.metin());
        assertTrue(s.metin().contains("[TCKN]"));
        assertTrue(s.metin().contains("[IBAN]"));
        assertTrue(s.metin().contains("[TELEFON]"));
        assertTrue(s.metin().contains("[EPOSTA]"));
        assertEquals(1, s.sayi("TCKN"));
        assertEquals(2, s.sayi("TELEFON"));
    }

    @Test
    void tutarVeTarihlerMaskelenmez() {
        String metin = "Bedel 3.570.000 TL + KDV, 497.746,00 USD; 19.06.2026 tarihinde; Ticaret Sicil No 12345678901";
        Maskeleme.Sonuc s = m.uygula(metin);
        assertEquals(metin, s.metin(), "geçersiz TCKN algoritmalı 11 haneli sayı ve tutarlar korunur");
    }

    @Test
    void algoritmalar() {
        assertTrue(Maskeleme.tcknGecerli("10000000146"));
        assertFalse(Maskeleme.tcknGecerli("12345678901"));
        assertTrue(Maskeleme.ibanGecerli("TR330006100519786457841326"));
        assertFalse(Maskeleme.ibanGecerli("TR330006100519786457841327"));
    }

    @Test
    void ayarKapaliysaDokunulmaz() {
        Maskeleme kapali = new Maskeleme(new Ayarlar.Maskeleme(false, false, false, false));
        String metin = "TCKN 10000000146 ad@ornek.com";
        assertEquals(metin, kapali.uygula(metin).metin());
    }
}
