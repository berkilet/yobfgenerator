package legal.erk.yobf.rules;

import legal.erk.yobf.config.SirketKaydi;
import legal.erk.yobf.config.Sirketler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LogoCozumleyiciTest {

    @TempDir Path logolar;
    Sirketler sirketler;

    @BeforeEach
    void kur() throws IOException {
        sirketler = new Sirketler("varsayilan.png", List.of(
                new SirketKaydi("a", "Alfa Enerji A.Ş.", List.of("Alfa"), "alfa.png", "varsayilan.png"),
                new SirketKaydi("b", "Beta Güneş A.Ş.", List.of(), "beta.png", "varsayilan.png"),
                new SirketKaydi("c", "Gama Rüzgar A.Ş.", List.of(), "alfa.png", "varsayilan.png"),
                new SirketKaydi("d", "Delta Elektrik A.Ş.", List.of(), "delta.png", "alfa.png"),
                new SirketKaydi("e", "Epsilon Enerji A.Ş.", List.of(), "eksik.png", "eksik2.png")));
        for (String f : List.of("varsayilan.png", "alfa.png", "beta.png")) Files.writeString(logolar.resolve(f), "x");
    }

    LogoCozumleyici c() { return new LogoCozumleyici(sirketler, logolar); }

    @Test
    void tekGrupSirketi() {
        var s = c().coz(List.of("Beta Güneş Anonim Şirketi", "Karşı Taraf Ltd. Şti."), null);
        assertEquals(logolar.resolve("beta.png"), s.logo());
        assertFalse(s.kullaniciyaSor());
        assertTrue(s.uyari().isEmpty());
    }

    @Test
    void cokluGrupFarkliLogoKullaniciyaSorulur() {
        var s = c().coz(List.of("Beta Güneş A.Ş.", "Alfa Enerji A.Ş."), null);
        assertTrue(s.kullaniciyaSor());
        assertEquals(2, s.adaylar().size());
        assertEquals(logolar.resolve("beta.png"), s.logo(), "ilk eşleşen ön seçili");
    }

    @Test
    void cokluGrupAyniLogoSorulmaz() {
        var s = c().coz(List.of("Alfa Enerji A.Ş.", "Gama Rüzgar A.Ş."), null);
        assertFalse(s.kullaniciyaSor());
        assertEquals(1, s.adaylar().size());
        assertEquals(2, s.adaylar().get(0).sirketler().size());
        assertEquals(logolar.resolve("alfa.png"), s.logo());
    }

    @Test
    void grupSirketiYokVarsayilanVeUyari() {
        var s = c().coz(List.of("Bilinmeyen A.Ş."), null);
        assertEquals(logolar.resolve("varsayilan.png"), s.logo());
        assertTrue(s.uyari().orElseThrow().contains("grup şirketi tespit edilemedi"));
    }

    @Test
    void logoDosyasiEksikYedekZinciri() {
        // delta.png yok → logo_yedek alfa.png
        assertEquals(logolar.resolve("alfa.png"), c().coz(List.of("Delta Elektrik A.Ş."), null).logo());
        // eksik.png ve eksik2.png yok → varsayılan
        assertEquals(logolar.resolve("varsayilan.png"), c().coz(List.of("Epsilon Enerji A.Ş."), null).logo());
    }

    @Test
    void varsayilanDaYoksaNullVeUyari() throws IOException {
        Files.delete(logolar.resolve("varsayilan.png"));
        var s = c().coz(List.of("Epsilon Enerji A.Ş."), null);
        assertNull(s.logo());
        assertTrue(s.uyari().isPresent());
    }

    @Test
    void kullaniciSecimiOnceliklidir() {
        Path elle = logolar.resolve("beta.png");
        var s = c().coz(List.of("Alfa Enerji A.Ş."), elle);
        assertEquals(elle, s.logo());
        assertFalse(s.kullaniciyaSor());
    }
}
