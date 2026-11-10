package legal.erk.yobf.extract;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** PDF / DOCX dosyasından düz metin çıkarır. */
public final class BelgeOkuyucu {

    /** DOCX'te sayfa bilgisi olmadığından yaklaşık bu uzunlukta parçalara bölünür. */
    static final int DOCX_SAYFA_KARAKTER = 3000;

    public record Belge(Path dosya, List<String> sayfalar) {
        public String ad() { return dosya.getFileName().toString(); }
        public String metin() { return String.join("\n", sayfalar); }
        public int sayfaSayisi() { return sayfalar.size(); }
    }

    private BelgeOkuyucu() {}

    public static boolean destekleniyor(Path p) {
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        return n.endsWith(".pdf") || n.endsWith(".docx");
    }

    public static Belge oku(Path p) throws IOException {
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        if (n.endsWith(".pdf")) return new Belge(p, PdfMetin.sayfalar(p));
        if (n.endsWith(".docx")) return new Belge(p, parcala(DocxMetin.metin(p)));
        throw new IOException("Desteklenmeyen dosya türü: " + p.getFileName() + " (yalnızca PDF ve DOCX)");
    }

    static List<String> parcala(String metin) {
        List<String> r = new ArrayList<>();
        StringBuilder b = new StringBuilder();
        for (String satir : metin.split("\n", -1)) {
            if (b.length() + satir.length() > DOCX_SAYFA_KARAKTER && b.length() > 0) {
                r.add(b.toString());
                b.setLength(0);
            }
            b.append(satir).append('\n');
        }
        if (b.length() > 0 || r.isEmpty()) r.add(b.toString());
        return r;
    }
}
