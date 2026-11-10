package legal.erk.yobf.log;

import legal.erk.yobf.config.UygulamaYolu;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * kayit/islem_gunlugu.csv — her işlem bir satır:
 * tarih, belge adı, form türü, taraflar, bedel, kullanıcı.
 * Uygulama klasörü yazılamıyorsa %APPDATA%\YOBF\kayit altına yazılır.
 */
public final class IslemGunlugu {

    static final String BASLIK = "tarih;belge_adi;form_turu;taraflar;bedel;kullanici";
    private static final DateTimeFormatter ZAMAN = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    private final Path dosya;

    public IslemGunlugu(Path dosya) {
        this.dosya = dosya;
    }

    public static IslemGunlugu varsayilan() {
        Path ana = UygulamaYolu.kok().resolve("kayit").resolve("islem_gunlugu.csv");
        try {
            Files.createDirectories(ana.getParent());
            if (Files.isWritable(ana.getParent())) return new IslemGunlugu(ana);
        } catch (IOException | SecurityException ignored) {
            // yedek klasöre düş
        }
        return new IslemGunlugu(UygulamaYolu.kullaniciKlasoru().resolve("kayit").resolve("islem_gunlugu.csv"));
    }

    public Path dosya() { return dosya; }

    public synchronized void ekle(List<String> belgeAdlari, String formTuru, List<String> taraflar, List<String> bedeller)
            throws IOException {
        Files.createDirectories(dosya.getParent());
        boolean yeni = !Files.exists(dosya);
        String satir = String.join(";",
                LocalDateTime.now().format(ZAMAN),
                csv(String.join(" | ", belgeAdlari)),
                csv(formTuru),
                csv(String.join(" | ", taraflar)),
                csv(String.join(" | ", bedeller)),
                csv(System.getProperty("user.name", ""))) + "\r\n";
        // Excel'in Türkçe karakterleri doğru açması için UTF-8 BOM
        String icerik = (yeni ? "﻿" + BASLIK + "\r\n" : "") + satir;
        Files.writeString(dosya, icerik, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    static String csv(String s) {
        if (s == null) return "";
        String t = s.replace("\r", " ").replace("\n", " ");
        if (t.contains(";") || t.contains("\"")) t = "\"" + t.replace("\"", "\"\"") + "\"";
        return t;
    }
}
