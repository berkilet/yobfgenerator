package legal.erk.yobf.render;

import legal.erk.yobf.TestVerisi;
import legal.erk.yobf.model.FormVerisi;
import legal.erk.yobf.model.SozlesmeGrubu;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PdfDonusturucuTest {

    @Test
    void pdfYolu() {
        assertEquals("YOBF_Zeyilname_X_01012026.pdf",
                PdfDonusturucu.pdfYolu(Path.of("a", "YOBF_Zeyilname_X_01012026.docx")).getFileName().toString());
    }

    @Test
    void wordBetigiYollariOrtamDegiskenindenAlir() {
        String b = PdfDonusturucu.WORD_BETIGI;
        assertTrue(b.contains("$env:YOBF_DOCX"));
        assertTrue(b.contains("ExportAsFixedFormat($env:YOBF_PDF, 17)"));
        assertTrue(b.contains("Quit(0)"));
        // try ... } catch ... } finally — arada ';' olmamalı (PowerShell ayrıştırma hatası)
        assertFalse(b.matches("(?s).*}\\s*;\\s*(catch|finally).*"), b);
        // -EncodedCommand: UTF-16LE Base64 gidiş-dönüş
        String geri = new String(java.util.Base64.getDecoder().decode(PdfDonusturucu.kodla(b)),
                java.nio.charset.StandardCharsets.UTF_16LE);
        assertEquals(b, geri);
    }

    @Test
    void powershellGurultusuTemizlenir() {
        assertEquals("Hata oldu.", PdfDonusturucu.temizle("#< CLIXML\r\n<Objs Version=\"1.1.0.1\">…</Objs>\r\nHata oldu.\r\n"));
    }

    /**
     * Yalnızca Windows'ta (CI: windows-latest): betik PowerShell'de gerçekten çalıştırılır.
     * Word kuruluysa PDF oluşmalı; kurulu değilse (CI) hata COM ile ilgili olmalı, ayrıştırma hatası değil.
     */
    @Test
    void windowsdaBetikAyristirilir(@TempDir Path d) throws Exception {
        assumeTrue(PdfDonusturucu.windows(), "yalnızca Windows");
        FormVerisi f = new FormVerisi();
        f.tarih = "01.01.2026";
        f.konuTr = "Deneme.";
        f.sozlesmeler.add(new SozlesmeGrubu(List.of("Kalyon Enerji Yatırımları A.Ş."), List.of()));
        Path klasor = Files.createDirectories(d.resolve("Kalyon Yatırım Holding A.Ş"));
        Path docx = klasor.resolve("YOBF_Sözleşme_Deneme.docx");
        new FormYazici(TestVerisi.yapilandirma().ayarlar()).yaz(f, docx);
        Path pdf = PdfDonusturucu.pdfYolu(docx);
        try {
            PdfDonusturucu.wordIle(docx, pdf);
            assertTrue(Files.size(pdf) > 1000, "Word ile PDF oluşmalı");
        } catch (java.io.IOException e) {
            String m = e.getMessage();
            System.out.println("Word yok / COM hatası (beklenen, CI): " + m);
            assertFalse(m.contains("ParserError") || m.contains("MissingCatchOrFinally")
                    || m.contains("ParseException") || m.contains("Unexpected token"), m);
        }
    }

    /** Bu makinede LibreOffice varsa gerçek dönüştürme (CI'da Word/LibreOffice yoksa atlanır). */
    @Test
    void libreOfficeIleDonusturur(@TempDir Path d) throws Exception {
        assumeTrue(!PdfDonusturucu.windows() && PdfDonusturucu.libreOffice().isPresent(), "LibreOffice yok");
        FormVerisi f = new FormVerisi();
        f.tarih = "01.01.2026";
        f.belgeTuru = "Sözleşme";
        f.konuTr = "Deneme konusu onay ve imzanıza sunulmaktadır.";
        f.sozlesmeler.add(new SozlesmeGrubu(List.of("Kalyon Enerji Yatırımları A.Ş.", "Deneme Ltd. Şti."), List.of()));
        Path docx = d.resolve("deneme form.docx");
        new FormYazici(TestVerisi.yapilandirma().ayarlar()).yaz(f, docx);
        var sonuc = PdfDonusturucu.donustur(docx).orElseThrow();
        assertEquals(PdfDonusturucu.Yontem.LIBREOFFICE, sonuc.yontem());
        assertTrue(Files.size(sonuc.pdf()) > 1000);
        assertTrue(Files.readString(sonuc.pdf(), java.nio.charset.StandardCharsets.ISO_8859_1).startsWith("%PDF"));
    }
}
