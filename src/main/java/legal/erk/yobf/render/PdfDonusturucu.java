package legal.erk.yobf.render;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * .docx → .pdf. Windows'ta yüklü Microsoft Word ile (COM, PowerShell üzerinden);
 * Word yoksa LibreOffice (soffice) ile. Hiçbiri yoksa dönüştürme yapılamaz.
 * Dosya yolları komut satırına değil ortam değişkenleriyle verilir (tırnak/özel karakter sorunu olmaz).
 */
public final class PdfDonusturucu {

    static final long ZAMAN_ASIMI_SN = 120;

    /** Word ile dönüştürme betiği. 17 = wdFormatPDF. */
    static final String WORD_BETIGI = String.join("; ",
            "$ErrorActionPreference = 'Stop'",
            "$w = New-Object -ComObject Word.Application",
            "$w.Visible = $false",
            "$w.DisplayAlerts = 0",
            "try { $d = $w.Documents.Open($env:YOBF_DOCX, $false, $true); $d.SaveAs([ref]$env:YOBF_PDF, [ref]17); $d.Close([ref]0) }",
            "finally { $w.Quit() }");

    public enum Yontem { WORD, LIBREOFFICE }

    public record Sonuc(Path pdf, Yontem yontem) {}

    private PdfDonusturucu() {}

    public static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    /**
     * @return oluşturulan PDF; Word ve LibreOffice bulunamazsa boş
     * @throws IOException dönüştürücü bulundu ama hata verdi
     */
    public static Optional<Sonuc> donustur(Path docx) throws IOException, InterruptedException {
        Path pdf = pdfYolu(docx);
        IOException sonHata = null;
        if (windows()) {
            try {
                wordIle(docx, pdf);
                if (Files.isRegularFile(pdf)) return Optional.of(new Sonuc(pdf, Yontem.WORD));
            } catch (IOException e) {
                sonHata = e;
            }
        }
        Optional<Path> soffice = libreOffice();
        if (soffice.isPresent()) {
            libreOfficeIle(soffice.get(), docx);
            if (Files.isRegularFile(pdf)) return Optional.of(new Sonuc(pdf, Yontem.LIBREOFFICE));
        }
        if (sonHata != null) throw sonHata;
        return Optional.empty();
    }

    public static Path pdfYolu(Path docx) {
        String ad = docx.getFileName().toString().replaceFirst("(?i)\\.docx$", "") + ".pdf";
        return docx.toAbsolutePath().resolveSibling(ad);
    }

    static void wordIle(Path docx, Path pdf) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive",
                "-ExecutionPolicy", "Bypass", "-Command", WORD_BETIGI);
        pb.environment().put("YOBF_DOCX", docx.toAbsolutePath().toString());
        pb.environment().put("YOBF_PDF", pdf.toAbsolutePath().toString());
        calistir(pb, "Word");
    }

    static void libreOfficeIle(Path soffice, Path docx) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(soffice.toString(), "--headless", "--norestore",
                "--convert-to", "pdf", "--outdir", docx.toAbsolutePath().getParent().toString(),
                docx.toAbsolutePath().toString());
        calistir(pb, "LibreOffice");
    }

    private static void calistir(ProcessBuilder pb, String ad) throws IOException, InterruptedException {
        pb.redirectErrorStream(true);
        Path log = Files.createTempFile("yobf-pdf", ".log");
        pb.redirectOutput(log.toFile());
        try {
            Process p = pb.start();
            if (!p.waitFor(ZAMAN_ASIMI_SN, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IOException(ad + " " + ZAMAN_ASIMI_SN + " saniyede yanıt vermedi.");
            }
            if (p.exitValue() != 0) {
                String cikti = Files.readString(log).strip();
                throw new IOException(ad + " ile PDF oluşturulamadı"
                        + (cikti.isEmpty() ? "." : ": " + cikti.substring(0, Math.min(400, cikti.length()))));
            }
        } finally {
            Files.deleteIfExists(log);
        }
    }

    /** soffice'i PATH'te ve yaygın kurulum klasörlerinde arar. */
    static Optional<Path> libreOffice() {
        List<Path> adaylar = new ArrayList<>();
        String exe = windows() ? "soffice.exe" : "soffice";
        String path = System.getenv("PATH");
        if (path != null) {
            for (String d : path.split(java.io.File.pathSeparator)) {
                if (!d.isBlank()) adaylar.add(Paths.get(d, exe));
            }
        }
        if (windows()) {
            for (String pf : new String[]{System.getenv("ProgramFiles"), System.getenv("ProgramFiles(x86)")}) {
                if (pf != null) adaylar.add(Paths.get(pf, "LibreOffice", "program", exe));
            }
        } else {
            adaylar.add(Paths.get("/usr/bin/soffice"));
            adaylar.add(Paths.get("/Applications/LibreOffice.app/Contents/MacOS/soffice"));
        }
        return adaylar.stream().filter(Files::isExecutable).findFirst();
    }
}
