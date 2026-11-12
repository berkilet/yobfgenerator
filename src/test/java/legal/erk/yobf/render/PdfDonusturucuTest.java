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
        assertTrue(PdfDonusturucu.WORD_BETIGI.contains("$env:YOBF_DOCX"));
        assertTrue(PdfDonusturucu.WORD_BETIGI.contains("[ref]17"));
        assertTrue(PdfDonusturucu.WORD_BETIGI.contains("Quit()"));
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
