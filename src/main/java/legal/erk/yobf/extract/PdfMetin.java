package legal.erk.yobf.extract;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class PdfMetin {

    /** Sayfa başına bu kadar harf/rakamdan azsa sayfa "metinsiz" kabul edilir. */
    private static final int MIN_KARAKTER_SAYFA = 20;

    private PdfMetin() {}

    /** Sayfa sayfa metin döndürür. Metin katmanı yoksa TaranmisBelgeException. */
    public static List<String> sayfalar(Path pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            PDFTextStripper s = new PDFTextStripper();
            s.setSortByPosition(true);
            List<String> r = new ArrayList<>();
            int toplam = 0;
            for (int i = 1; i <= doc.getNumberOfPages(); i++) {
                s.setStartPage(i);
                s.setEndPage(i);
                String t = s.getText(doc);
                r.add(t);
                toplam += t.replaceAll("[^\\p{L}\\p{N}]", "").length();
            }
            if (doc.getNumberOfPages() > 0 && toplam < MIN_KARAKTER_SAYFA * Math.max(1, doc.getNumberOfPages() / 4)) {
                throw new TaranmisBelgeException(pdf.getFileName().toString());
            }
            return r;
        }
    }

    public static String metin(Path pdf) throws IOException {
        return String.join("\n", sayfalar(pdf));
    }
}
