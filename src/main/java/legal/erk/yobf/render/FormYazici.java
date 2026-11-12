package legal.erk.yobf.render;

import legal.erk.yobf.config.Ayarlar;
import legal.erk.yobf.config.Imzaci;
import legal.erk.yobf.model.Bedel;
import legal.erk.yobf.model.EkSatir;
import legal.erk.yobf.model.Satirlar;
import legal.erk.yobf.model.FormVerisi;
import legal.erk.yobf.model.SozlesmeGrubu;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.TableRowAlign;
import org.apache.poi.xwpf.usermodel.TableWidthType;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageBorders;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyles;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblBorders;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblGrid;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageBorderOffset;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblLayoutType;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * CLAUDE.md §9 — Apache POI ile .docx üretimi.
 * A4, dar kenar boşlukları, kalın siyah çerçeve (sayfa kenarlığı), Cambria;
 * iki sütunlu (etiket : değer) tablo ve UYGUNDUR imza bloğu (1–5 + 1).
 */
public final class FormYazici {

    // Ölçüler (twip = 1/20 pt)
    public static final int A4_GEN = 11906, A4_YUK = 16838;
    public static final int KENAR_YAN = 1000, KENAR_UST = 850, KENAR_ALT = 850;
    public static final int ICERIK = A4_GEN - 2 * KENAR_YAN;
    public static final int ETIKET_SUTUN = Math.round(ICERIK * 0.28f);
    public static final int AYRAC_SUTUN = 280;
    public static final int DEGER_SUTUN = ICERIK - ETIKET_SUTUN - AYRAC_SUTUN;
    public static final double LOGO_YUKSEKLIK_CM = 2.2;

    public static final String FONT = "Cambria";
    public static final int GOVDE = 12, ACIKLAMA = 10, BASLIK = 16;
    /** İmza bloğu: örnek formlardaki gibi ad büyük ve kalın, unvan okunur büyüklükte italik. */
    public static final int IMZACI_AD = 14, IMZACI_UNVAN = 11, IMZA_BASLIK = 15;
    /** İmza için unvan ile imza çizgisi arasında bırakılan boşluk (twip; ≈ 2 cm). */
    public static final int IMZA_BOSLUGU = 1150;
    /** Tahmini içerik bunu aşarsa form kademeli olarak sıkıştırılır (yazılar küçülür, boşluklar daralır). */
    public static final double SAYFA_YUKSEKLIGI_PT = 700;
    /** Bundan kısa formlarda satır içi boşluklar ve imza alanı büyütülerek sayfa daha dolu kullanılır. */
    public static final double KISA_FORM_PT = 660;
    /** Her sıkıştırma kademesinin karşıladığı yaklaşık yükseklik (pt). */
    public static final double KADEME_PT = 150;

    private static final Map<String, String> BELGE_TURU_EN = Map.ofEntries(
            Map.entry("Sözleşme", "Agreement"),
            Map.entry("Zeyilname", "Amendment"),
            Map.entry("Protokol", "Protocol"),
            Map.entry("Gizlilik Sözleşmesi", "Non-Disclosure Agreement"),
            Map.entry("Variation Order", "Variation Order"),
            Map.entry("Yönetim Kurulu Kararı", "Board of Directors Resolution"),
            Map.entry("Avukatlık Ücret Sözleşmesi", "Attorney Fee Agreement"),
            Map.entry("Vekaletname", "Power of Attorney"),
            Map.entry("Azilnameler", "Revocation of Power of Attorney"),
            Map.entry("Azilname", "Revocation of Power of Attorney"),
            Map.entry("Diğer", "Other"));

    public static final Map<String, String> BEDEL_ETIKETI_EN = Map.of(
            "Bedel", "Price",
            "Sözleşme Bedeli", "Contract Price",
            "Zeyilnameye Konu Tutar", "Amendment Amount");

    private final Ayarlar ayarlar;

    public FormYazici(Ayarlar ayarlar) {
        this.ayarlar = ayarlar;
    }

    // ------------------------------------------------------------------ giriş

    public void yaz(FormVerisi f, Path cikti) throws IOException {
        Files.createDirectories(cikti.toAbsolutePath().getParent());
        try (OutputStream out = Files.newOutputStream(cikti)) {
            yaz(f, out);
        }
    }

    public void yaz(FormVerisi f, OutputStream out) throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            varsayilanYaziTipi(doc);
            sayfaDuzeni(doc);
            logo(doc, f.logo);
            baslik(doc, f);
            anaTablo(doc, f);
            imzaBlogu(doc, f);
            double tahmin = tahminiYukseklik(f);
            int kademe = sikistirmaKademesi(tahmin);
            if (kademe > 0) sikistir(doc, kademe);
            else if (tahmin < KISA_FORM_PT) genislet(doc);
            doc.write(out);
        }
    }

    /** YOBF_<BelgeTürü>_<KarşıTaraf>_<GGAAYYYY>.docx */
    public static String dosyaAdi(FormVerisi f, Predicate<String> grupSirketiMi, java.time.LocalDate tarih) {
        String karsi = f.ilkTaraf(grupSirketiMi);
        String[] kelimeler = legal.erk.yobf.rules.UnvanNormalizer.parantezsiz(karsi).split("\\s+");
        StringBuilder k = new StringBuilder();
        for (int i = 0; i < Math.min(3, kelimeler.length); i++) k.append(kelimeler[i]);
        return "YOBF_" + temizle(f.belgeTuru) + "_" + temizle(k.toString()) + "_"
                + legal.erk.yobf.rules.TarihKontrol.dosyaAdiTarihi(tarih) + ".docx";
    }

    static String temizle(String s) {
        if (s == null || s.isBlank()) return "Belge";
        String t = s.replaceAll("[\\\\/:*?\"<>|.,;'’()&]", "").replaceAll("\\s+", "");
        return t.isEmpty() ? "Belge" : t;
    }

    // ------------------------------------------------------------------ belge düzeni

    private static void varsayilanYaziTipi(XWPFDocument doc) {
        XWPFStyles styles = doc.createStyles();
        CTStyles ct = CTStyles.Factory.newInstance();
        CTRPr rpr = ct.addNewDocDefaults().addNewRPrDefault().addNewRPr();
        CTFonts fonts = rpr.addNewRFonts();
        fonts.setAscii(FONT);
        fonts.setHAnsi(FONT);
        fonts.setCs(FONT);
        fonts.setEastAsia(FONT);
        rpr.addNewSz().setVal(BigInteger.valueOf(GOVDE * 2L));
        rpr.addNewSzCs().setVal(BigInteger.valueOf(GOVDE * 2L));
        rpr.addNewLang().setVal("tr-TR");
        var ppr = ct.getDocDefaults().addNewPPrDefault().addNewPPr();
        var sp = ppr.addNewSpacing();
        sp.setAfter(BigInteger.ZERO);
        sp.setBefore(BigInteger.ZERO);
        sp.setLine(BigInteger.valueOf(264)); // 1,1 satır aralığı: sayfa daha dolu ve okunaklı
        sp.setLineRule(org.openxmlformats.schemas.wordprocessingml.x2006.main.STLineSpacingRule.AUTO);
        styles.setStyles(ct);
    }

    private static void sayfaDuzeni(XWPFDocument doc) {
        CTSectPr s = doc.getDocument().getBody().isSetSectPr()
                ? doc.getDocument().getBody().getSectPr()
                : doc.getDocument().getBody().addNewSectPr();
        CTPageSz sz = s.isSetPgSz() ? s.getPgSz() : s.addNewPgSz();
        sz.setW(BigInteger.valueOf(A4_GEN));
        sz.setH(BigInteger.valueOf(A4_YUK));
        CTPageMar m = s.isSetPgMar() ? s.getPgMar() : s.addNewPgMar();
        m.setTop(BigInteger.valueOf(KENAR_UST));
        m.setBottom(BigInteger.valueOf(KENAR_ALT));
        m.setLeft(BigInteger.valueOf(KENAR_YAN));
        m.setRight(BigInteger.valueOf(KENAR_YAN));
        m.setHeader(BigInteger.valueOf(400));
        m.setFooter(BigInteger.valueOf(400));
        // Kalın siyah çerçeve: sayfa kenarlığı
        CTPageBorders pb = s.addNewPgBorders();
        pb.setOffsetFrom(STPageBorderOffset.PAGE);
        for (CTBorder b : new CTBorder[]{pb.addNewTop(), pb.addNewLeft(), pb.addNewBottom(), pb.addNewRight()}) {
            b.setVal(STBorder.SINGLE);
            b.setSz(BigInteger.valueOf(18)); // 2,25 pt
            b.setSpace(BigInteger.valueOf(24));
            b.setColor("000000");
        }
    }

    private static void logo(XWPFDocument doc, Path logo) throws IOException {
        if (logo == null || !Files.isRegularFile(logo)) return;
        BufferedImage img;
        try (InputStream in = Files.newInputStream(logo)) {
            img = ImageIO.read(in);
        }
        if (img == null) return;
        double yukPt = LOGO_YUKSEKLIK_CM / 2.54 * 72.0;
        double genPt = yukPt * img.getWidth() / img.getHeight();
        double maxGen = ICERIK / 20.0;
        if (genPt > maxGen) { // çok geniş logolarda en-boy oranı korunarak daraltılır
            yukPt = yukPt * maxGen / genPt;
            genPt = maxGen;
        }
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        p.setSpacingBefore(200);
        p.setSpacingAfter(200);
        int tur = logo.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png")
                ? Document.PICTURE_TYPE_PNG : Document.PICTURE_TYPE_JPEG;
        try (InputStream in = Files.newInputStream(logo)) {
            p.createRun().addPicture(in, tur, logo.getFileName().toString(),
                    Units.toEMU(genPt), Units.toEMU(yukPt));
        } catch (org.apache.poi.openxml4j.exceptions.InvalidFormatException e) {
            throw new IOException("Logo eklenemedi: " + e.getMessage(), e);
        }
    }

    private void baslik(XWPFDocument doc, FormVerisi f) {
        Ayarlar.FormTuru ft = ayarlar.formTuru(f.formTuru).orElse(new Ayarlar.FormTuru(f.formTuru, f.formTuru, null));
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        p.setSpacingAfter(f.ikiDilli() ? 80 : 200);
        run(p, ft.baslik(), true, false, false, BASLIK);
        if (f.ikiDilli() && ft.baslikEn() != null && !ft.baslikEn().isBlank()) {
            XWPFParagraph e = doc.createParagraph();
            e.setAlignment(ParagraphAlignment.CENTER);
            e.setSpacingAfter(200);
            run(e, ft.baslikEn(), true, false, false, BASLIK);
        }
    }

    // ------------------------------------------------------------------ ana tablo

    /** Ana tablo yazımı sırasında durum: ilk satır kullanıldı mı, hangi konumlar yazıldı. */
    private static final class Tablo {
        final XWPFTable t;
        final FormVerisi f;
        boolean ilkKullanildi;
        final java.util.Set<String> yazilanKonumlar = new java.util.HashSet<>();
        Tablo(XWPFTable t, FormVerisi f) { this.t = t; this.f = f; }
    }

    private void anaTablo(XWPFDocument doc, FormVerisi f) {
        XWPFTable t = doc.createTable(1, 3);
        sabitTablo(t, new int[]{ETIKET_SUTUN, AYRAC_SUTUN, DEGER_SUTUN});
        kenarliklar(t, true);
        t.setCellMargins(110, 100, 110, 100);
        boolean en = f.ikiDilli();
        Tablo tb = new Tablo(t, f);

        satir(tb, Satirlar.TARIH, en ? "Tarih / Date" : "Tarih", c -> tekSatir(c, f.tarih));
        satir(tb, Satirlar.BELGE_TURU, en ? "Belge Türü / Document Type" : "Belge Türü", c -> tekSatir(c, belgeTuru(f)));
        satir(tb, Satirlar.TALEP_EDEN, en ? "Belgeyi Talep Eden Departman / Requesting Department" : "Belgeyi Talep Eden Departman",
                c -> cokSatir(c, f.talepEdenDepartman));
        satir(tb, Satirlar.IMZAYA_SUNAN, en ? "Belgeyi İmzaya Sunan Departman / Department Submitting for Signature"
                : "Belgeyi İmzaya Sunan Departman", c -> cokSatir(c, f.imzayaSunanDepartman));

        if (f.azilname()) {
            satir(tb, Satirlar.KISILER, "Azledilecek Kişiler", c -> tekSatir(c, String.join(", ", f.kisiler)));
            satir(tb, Satirlar.SIRKETLER, "Azleden Şirketler", c -> liste(c, f.sirketler));
            satir(tb, Satirlar.KONU, "Konu", c -> konuVeNot(c, f));
        } else if (f.vekaletname()) {
            satir(tb, Satirlar.KISILER, "Vekaletname Verilecek Kişiler", c -> tekSatir(c, String.join(", ", f.kisiler)));
            satir(tb, Satirlar.SIRKETLER, "Vekaletname Veren Şirket", c -> liste(c, f.sirketler));
            satir(tb, Satirlar.SONA_ERME, "Sona Erme Tarihi", c -> tekSatir(c, f.sonaErmeTarihi));
            satir(tb, Satirlar.KONU, "Konu", c -> konuVeNot(c, f));
        } else {
            boolean icerikVar = f.dahil(Satirlar.TARAFLAR) || f.dahil(Satirlar.KONU) || f.dahil(Satirlar.BEDEL)
                    || f.ekSatirlar.stream().anyMatch(e -> e.alt() && Satirlar.KONU.equals(e.konum()) && !e.bos());
            if (icerikVar) {
                satir(tb, Satirlar.KONU, en ? "Belgenin Konusu/Özeti / Subject/Summary of the Document" : "Belgenin Konusu/Özeti",
                        c -> konuBlogu(c, f));
            } else {
                ekSatirlariYaz(tb, Satirlar.KONU);
            }
        }

        List<String> yetkili = f.imzasinaSunulacak.stream().filter(x -> x != null && !x.isBlank()).toList();
        if (!yetkili.isEmpty()) {
            satir(tb, Satirlar.IMZASINA_SUNULACAK,
                    en ? "İmzasına Sunulacak Yetkili / Authorized Signatory" : "İmzasına Sunulacak Yetkili",
                    c -> {
                        XWPFParagraph p = ilkParagraf(c);
                        for (int i = 0; i < yetkili.size(); i++) {
                            if (i > 0) p = c.addParagraph();
                            aciklamali(p, yetkili.get(i));
                        }
                    });
        } else {
            ekSatirlariYaz(tb, Satirlar.IMZASINA_SUNULACAK);
        }

        // Konumu bu formda bulunmayan ek satırlar tablonun sonuna
        for (EkSatir e : f.ekSatirlar) {
            if (!e.bos() && !tb.yazilanKonumlar.contains(e.konum())) {
                tekEkSatir(tb, e);
            }
        }
    }

    /** Konumdan sonra gelen (alt olmayan) ek satırları yazar. */
    private void ekSatirlariYaz(Tablo tb, String konum) {
        tb.yazilanKonumlar.add(konum);
        for (EkSatir e : tb.f.ekSatirlar) {
            if (!e.alt() && konum.equals(e.konum()) && !e.bos()) tekEkSatir(tb, e);
        }
    }

    private void tekEkSatir(Tablo tb, EkSatir e) {
        satirYaz(tb, e.etiket(), c -> cokSatir(c, e.deger()));
    }

    /** Hücrenin sonuna konuma ait alt satırları ekler. */
    private static void altSatirlariYaz(XWPFTableCell c, FormVerisi f, String konum) {
        for (EkSatir e : f.ekSatirlar) {
            if (!e.alt() || !konum.equals(e.konum()) || e.bos()) continue;
            ayirici(c);
            String[] satirlar = e.deger().isEmpty() ? new String[]{""} : e.deger().split("\\R");
            for (int i = 0; i < satirlar.length; i++) {
                XWPFParagraph p = yeniParagraf(c);
                p.setAlignment(ParagraphAlignment.BOTH);
                if (i == 0 && !e.etiket().isEmpty()) {
                    run(p, e.etiket().endsWith(":") ? e.etiket() : e.etiket() + ":", true, false, true, GOVDE);
                    if (!satirlar[0].isEmpty()) run(p, " ", false, false, false, GOVDE);
                }
                if (!satirlar[i].isEmpty()) run(p, satirlar[i], false, false, false, GOVDE);
            }
        }
    }

    public static String belgeTuru(FormVerisi f) {
        String b = f.belgeTuru == null ? "" : f.belgeTuru.trim();
        if (f.ikiDilli() && !b.contains("/") && BELGE_TURU_EN.containsKey(b)) {
            return b + " / " + BELGE_TURU_EN.get(b);
        }
        return b;
    }

    /** "Sn. Murathan KALYONCU (Kalyon İşletme için)" → parantezli kısım italik. */
    private static void aciklamali(XWPFParagraph p, String satir) {
        int i = satir.indexOf('(');
        if (i > 0 && satir.trim().endsWith(")")) {
            run(p, satir.substring(0, i).stripTrailing() + " ", false, false, false, GOVDE);
            run(p, satir.substring(i).trim(), false, true, false, GOVDE);
        } else {
            run(p, satir, false, false, false, GOVDE);
        }
    }

    /** Satır dahilse yazar (alt satırlarıyla), ardından konumuna eklenen ek satırları yazar. */
    private void satir(Tablo tb, String anahtar, String etiket, Consumer<XWPFTableCell> deger) {
        if (tb.f.dahil(anahtar)) {
            satirYaz(tb, etiket, c -> {
                deger.accept(c);
                altSatirlariYaz(c, tb.f, anahtar);
            });
        }
        ekSatirlariYaz(tb, anahtar);
    }

    private void satirYaz(Tablo tb, String etiket, Consumer<XWPFTableCell> deger) {
        XWPFTableRow r = tb.ilkKullanildi ? tb.t.createRow() : tb.t.getRow(0);
        tb.ilkKullanildi = true;
        r.setCantSplitRow(true);
        XWPFTableCell e = r.getCell(0);
        e.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
        run(ilkParagraf(e), etiket, true, false, false, GOVDE);
        XWPFTableCell a = r.getCell(1);
        a.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
        XWPFParagraph ap = ilkParagraf(a);
        ap.setAlignment(ParagraphAlignment.CENTER);
        run(ap, ":", false, false, false, GOVDE);
        XWPFTableCell d = r.getCell(2);
        d.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
        deger.accept(d);
        hucreGenislikleri(r, new int[]{ETIKET_SUTUN, AYRAC_SUTUN, DEGER_SUTUN});
    }

    private static void tekSatir(XWPFTableCell c, String s) {
        run(ilkParagraf(c), s == null ? "" : s, false, false, false, GOVDE);
    }

    private static void cokSatir(XWPFTableCell c, String s) {
        liste(c, s == null ? List.of() : List.of(s.split("\\R")));
    }

    private static void liste(XWPFTableCell c, List<String> satirlar) {
        XWPFParagraph p = ilkParagraf(c);
        boolean ilk = true;
        for (String s : satirlar) {
            if (s == null || s.isBlank()) continue;
            if (!ilk) p = c.addParagraph();
            run(p, s.trim(), false, false, false, GOVDE);
            ilk = false;
        }
    }

    /** YOBF / YOBF_TR_EN: Taraflar + Konu + (Bedel) + (Not). */
    /** Hücre henüz boşsa ilk paragrafı, değilse yeni paragrafı döndürür. */
    private static XWPFParagraph yeniParagraf(XWPFTableCell c) {
        return hucreBos(c) ? ilkParagraf(c) : c.addParagraph();
    }

    private static boolean hucreBos(XWPFTableCell c) {
        return c.getParagraphs().isEmpty()
                || (c.getParagraphs().size() == 1 && c.getParagraphs().get(0).getRuns().isEmpty());
    }

    /** Bölümler arasına boş satır (hücre boşsa eklenmez). */
    private static void ayirici(XWPFTableCell c) {
        if (!hucreBos(c)) bosParagraf(c);
    }

    /** YOBF / YOBF_TR_EN: Taraflar + Konu + (Bedel) + (Not); her bölüm ayrı ayrı dahil edilebilir. */
    private void konuBlogu(XWPFTableCell c, FormVerisi f) {
        boolean en = f.ikiDilli();
        boolean coklu = f.sozlesmeler.size() > 1;

        if (f.dahil(Satirlar.TARAFLAR)) {
            for (int g = 0; g < f.sozlesmeler.size(); g++) {
                SozlesmeGrubu sg = f.sozlesmeler.get(g);
                List<String> taraflar = sg.taraflar.stream().filter(x -> x != null && !x.isBlank()).toList();
                if (taraflar.isEmpty()) continue;
                XWPFParagraph p = yeniParagraf(c);
                String etiket = (coklu ? (g + 1) + ". Sözleşme " : "") + (en ? "Taraflar / Parties:" : "Taraflar:");
                run(p, etiket, true, false, true, GOVDE);
                for (int i = 0; i < taraflar.size(); i++) {
                    XWPFParagraph tp = c.addParagraph();
                    tp.setIndentationLeft(coklu ? 500 : 360);
                    tp.setIndentationHanging(0);
                    run(tp, (i + 1) + ") " + taraflar.get(i).trim(), false, false, false, GOVDE);
                }
            }
        }

        if (f.dahil(Satirlar.KONU)) {
            ayirici(c);
            XWPFParagraph kp = yeniParagraf(c);
            kp.setAlignment(ParagraphAlignment.BOTH);
            run(kp, en ? "Konu / Subject:" : "Konu:", true, false, true, GOVDE);
            run(kp, " " + nz(f.konuTr).trim(), false, false, false, GOVDE);
            if (en && !nz(f.konuEn).isBlank()) {
                bosParagraf(c);
                XWPFParagraph ep = c.addParagraph();
                ep.setAlignment(ParagraphAlignment.BOTH);
                run(ep, f.konuEn.trim(), false, false, false, GOVDE);
            }
        }

        boolean bedelVar = f.sozlesmeler.stream().anyMatch(sg -> sg.bedeller.stream().anyMatch(b -> !b.bos()));
        if (f.dahil(Satirlar.BEDEL) && bedelVar) {
            ayirici(c);
            for (int g = 0; g < f.sozlesmeler.size(); g++) {
                List<Bedel> bl = f.sozlesmeler.get(g).bedeller.stream().filter(b -> !b.bos()).toList();
                for (int i = 0; i < bl.size(); i++) {
                    XWPFParagraph bp = yeniParagraf(c);
                    String etiket = coklu ? (g + 1) + ". Sözleşme Bedeli" : nz(f.bedelEtiketi, "Bedel");
                    if (en) etiket += " / " + BEDEL_ETIKETI_EN.getOrDefault(coklu ? "Sözleşme Bedeli" : etiket, "Price");
                    if (i == 0) {
                        run(bp, etiket + ":", true, false, true, GOVDE);
                        run(bp, " " + bl.get(i).metin(), false, false, false, GOVDE);
                    } else {
                        bp.setIndentationLeft(360);
                        run(bp, bl.get(i).metin(), false, false, false, GOVDE);
                    }
                }
            }
        }
        not(c, f);
    }

    /** Azilname / Vekaletname: Konu (+ Not). */
    private void konuVeNot(XWPFTableCell c, FormVerisi f) {
        XWPFParagraph p = ilkParagraf(c);
        p.setAlignment(ParagraphAlignment.BOTH);
        run(p, nz(f.konuTr).trim(), false, false, false, GOVDE);
        not(c, f);
    }

    private static void not(XWPFTableCell c, FormVerisi f) {
        if (!f.dahil(Satirlar.NOT) || nz(f.notMetni).isBlank()) return;
        ayirici(c);
        String[] satirlar = f.notMetni.trim().split("\\R");
        for (int i = 0; i < satirlar.length; i++) {
            XWPFParagraph np = yeniParagraf(c);
            np.setAlignment(ParagraphAlignment.BOTH);
            if (i == 0) run(np, nz(f.notEtiketi, "Not:") + " ", true, true, false, ACIKLAMA);
            run(np, satirlar[i], false, true, false, ACIKLAMA);
        }
    }

    // ------------------------------------------------------------------ imza bloğu

    private void imzaBlogu(XWPFDocument doc, FormVerisi f) {
        Ayarlar.ImzaBlogu ib = ayarlar.imzaBlogu();
        boolean en = f.ikiDilli();
        List<Imzaci> imzacilar = new ArrayList<>(f.anaImzacilar.stream().filter(i -> i != null && !i.ad().isBlank()).toList());
        if (imzacilar.size() > ib.anaImzaciMax()) imzacilar = imzacilar.subList(0, ib.anaImzaciMax());

        baslikParagrafi(doc, en ? ib.anaBaslik() + " / " + ib.anaBaslikEn() : ib.anaBaslik(), 560);

        if (!imzacilar.isEmpty()) {
            List<Integer> satirlar = ib.satirlar(imzacilar.size());
            int enCok = satirlar.stream().mapToInt(Integer::intValue).max().orElse(1);
            int hucre = ICERIK / Math.max(2, enCok);
            int idx = 0;
            for (int s = 0; s < satirlar.size(); s++) {
                if (s > 0) aralik(doc, 360);
                imzaSatiri(doc, imzacilar.subList(idx, idx + satirlar.get(s)), hucre, en);
                idx += satirlar.get(s);
            }
        }

        if (f.ekOnayAcik && f.ekOnayImzaci != null && !f.ekOnayImzaci.ad().isBlank()) {
            String b = nz(f.ekOnayBaslik, ib.ekOnayBaslik());
            baslikParagrafi(doc, en ? b + " / " + ib.ekOnayBaslikEn() : b, 480);
            imzaSatiri(doc, List.of(f.ekOnayImzaci), ICERIK / 2, en);
        }
    }

    /** "UYGUNDUR" / "ONAY" başlığı: kalın, altı çizili, ortalı; önünde verilen boşluk. */
    private static void baslikParagrafi(XWPFDocument doc, String metin, int onceBosluk) {
        XWPFParagraph h = doc.createParagraph();
        h.setAlignment(ParagraphAlignment.CENTER);
        h.setSpacingBefore(onceBosluk);
        h.setSpacingAfter(300);
        birlikte(h);
        run(h, metin, true, false, true, IMZA_BASLIK);
    }

    private static void aralik(XWPFDocument doc, int twip) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(twip);
        birlikte(p);
    }

    /**
     * İki satırlı kenarlıksız tablo: üst satırda Ad SOYAD (kalın) + unvan (italik), alt satırda
     * imza boşluğu ve imzanın üzerine atılacağı düz çizgi (_____). Çizgiler ayrı satırda olduğu için
     * unvanı uzun/kısa olan imzacılarda da aynı hizada durur.
     */
    private static void imzaSatiri(XWPFDocument doc, List<Imzaci> kisiler, int hucre, boolean en) {
        int n = kisiler.size();
        XWPFTable t = doc.createTable(2, n);
        int[] g = new int[n];
        java.util.Arrays.fill(g, hucre);
        sabitTablo(t, g);
        kenarliklar(t, false);
        t.setTableAlignment(TableRowAlign.CENTER);
        t.setCellMargins(0, 80, 0, 80);
        XWPFTableRow adSatiri = t.getRow(0);
        XWPFTableRow cizgiSatiri = t.getRow(1);
        adSatiri.setCantSplitRow(true);
        cizgiSatiri.setCantSplitRow(true);
        String cizgi = "_".repeat(imzaCizgisiUzunlugu(hucre));
        for (int i = 0; i < n; i++) {
            Imzaci k = kisiler.get(i);
            XWPFTableCell c = adSatiri.getCell(i);
            c.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.TOP);
            XWPFParagraph ad = ilkParagraf(c);
            ad.setAlignment(ParagraphAlignment.CENTER);
            birlikte(ad);
            run(ad, k.ad(), true, false, false, IMZACI_AD);
            if (!k.unvan().isBlank()) {
                XWPFParagraph u = c.addParagraph();
                u.setAlignment(ParagraphAlignment.CENTER);
                birlikte(u);
                run(u, k.unvan(), false, true, false, IMZACI_UNVAN);
            }
            if (en && !k.unvanEn().isBlank() && !k.unvanEn().equals(k.unvan())) {
                XWPFParagraph u = c.addParagraph();
                u.setAlignment(ParagraphAlignment.CENTER);
                birlikte(u);
                run(u, k.unvanEn(), false, true, false, IMZACI_UNVAN);
            }
            XWPFParagraph cp = ilkParagraf(cizgiSatiri.getCell(i));
            cp.setAlignment(ParagraphAlignment.CENTER);
            cp.setSpacingBefore(IMZA_BOSLUGU);
            run(cp, cizgi, false, false, false, GOVDE);
        }
        hucreGenislikleri(adSatiri, g);
        hucreGenislikleri(cizgiSatiri, g);
    }

    // ------------------------------------------------------------------ tek sayfaya sığdırma

    /** Formun yaklaşık yüksekliği (pt), varsayılan yazı boyutlarıyla. Kaba ama kararlı bir tahmin. */
    public double tahminiYukseklik(FormVerisi f) {
        final double satir = GOVDE * 1.1 * 1.2;          // bir metin satırı
        final double hucreBasina = 2 * satir + 11;        // etiketi iki satıra kırılan tablo satırı
        final int kolonKarakter = 80;                     // değer sütununa sığan karakter
        double h = 62 + 2 * BASLIK + 30;                   // logo + başlık
        if (f.ikiDilli()) h += BASLIK * 1.4;

        int tabloSatiri = 4 + (f.azilname() ? 3 : f.vekaletname() ? 4 : 1);
        if (!f.imzasinaSunulacak.isEmpty()) tabloSatiri++;
        tabloSatiri += (int) f.ekSatirlar.stream().filter(e -> !e.alt()).count();
        h += tabloSatiri * hucreBasina;

        int metinSatiri = 0;
        for (SozlesmeGrubu g : f.sozlesmeler) metinSatiri += 1 + g.taraflar.size() + g.bedeller.size() + 1;
        metinSatiri += satirSayisi(f.konuTr, kolonKarakter) + 1;
        if (f.ikiDilli()) metinSatiri += satirSayisi(f.konuEn, kolonKarakter) + 1;
        metinSatiri += (int) Math.ceil(satirSayisi(f.notMetni, (int) (kolonKarakter * 1.2)) * 0.85);
        metinSatiri += f.sirketler.size() + f.imzasinaSunulacak.size();
        for (EkSatir e : f.ekSatirlar) metinSatiri += satirSayisi(e.deger(), kolonKarakter) + (e.alt() ? 1 : 0);
        h += Math.max(0, metinSatiri - 2) * satir;

        int imzaci = (int) f.anaImzacilar.stream().filter(i -> i != null && !i.ad().isBlank()).count();
        int imzaSatiri = imzaci == 0 ? 0 : ayarlar.imzaBlogu().satirlar(Math.min(imzaci, 5)).size();
        if (f.ekOnayAcik) imzaSatiri++;
        double imzaBlogu = IMZACI_AD * 1.3 + 2 * IMZACI_UNVAN * 1.3 + IMZA_BOSLUGU / 20.0 + satir;
        h += 28 + IMZA_BASLIK * 2 + imzaSatiri * imzaBlogu + (f.ekOnayAcik ? 30 : 0);
        return h;
    }

    private static int satirSayisi(String metin, int kolon) {
        if (metin == null || metin.isBlank()) return 0;
        int n = 0;
        for (String s : metin.split("\\R")) n += Math.max(1, (int) Math.ceil(s.length() / (double) kolon));
        return n;
    }

    /** 0: sıkıştırma yok; 1–3: yazılar o kadar pt küçülür, boşluklar orantılı daralır. */
    public static int sikistirmaKademesi(double tahmin) {
        if (tahmin <= SAYFA_YUKSEKLIGI_PT) return 0;
        return (int) Math.min(3, Math.ceil((tahmin - SAYFA_YUKSEKLIGI_PT) / KADEME_PT));
    }

    /** Kısa formlar: tablo satırları daha ferah, imza alanı daha geniş. */
    static void genislet(XWPFDocument doc) {
        for (XWPFParagraph p : doc.getParagraphs()) {
            if (p.getSpacingBefore() > 0) p.setSpacingBefore((int) (p.getSpacingBefore() * 1.4));
        }
        for (XWPFTable t : doc.getTables()) {
            if (t.getRow(0).getTableCells().size() == 3 && t.getRows().size() > 2) {
                t.setCellMargins(180, 100, 180, 100);
            }
            for (XWPFTableRow r : t.getRows()) {
                for (XWPFTableCell c : r.getTableCells()) {
                    for (XWPFParagraph p : c.getParagraphs()) {
                        if (p.getSpacingBefore() > 0) p.setSpacingBefore((int) (p.getSpacingBefore() * 1.35));
                    }
                }
            }
        }
    }

    static void sikistir(XWPFDocument doc, int kademe) {
        double oran = Math.pow(0.72, kademe);
        for (XWPFParagraph p : doc.getParagraphs()) sikistir(p, kademe, oran);
        for (XWPFTable t : doc.getTables()) {
            if (t.getRow(0).getTableCells().size() == 3 && t.getRows().size() > 2) {
                int dikey = Math.max(30, (int) (110 * oran));
                t.setCellMargins(dikey, 100, dikey, 100);
            }
            for (XWPFTableRow r : t.getRows()) {
                for (XWPFTableCell c : r.getTableCells()) for (XWPFParagraph p : c.getParagraphs()) sikistir(p, kademe, oran);
            }
        }
    }

    private static void sikistir(XWPFParagraph p, int kademe, double oran) {
        for (XWPFRun r : p.getRuns()) {
            Double fs = r.getFontSizeAsDouble();
            if (fs != null) r.setFontSize(Math.max(fs >= 11 ? 9 : 8, fs - kademe));
        }
        if (p.getSpacingBefore() > 0) p.setSpacingBefore((int) (p.getSpacingBefore() * oran));
        if (p.getSpacingAfter() > 0) p.setSpacingAfter((int) (p.getSpacingAfter() * oran));
    }

    /** Hücre genişliğine sığan alt çizgi (_) sayısı (11 pt Cambria'da ≈ 112 twip/karakter). */
    public static int imzaCizgisiUzunlugu(int hucre) {
        return Math.max(12, Math.min(40, (hucre - 700) / 112));
    }

    // ------------------------------------------------------------------ yardımcılar

    private static void sabitTablo(XWPFTable t, int[] genislikler) {
        int toplam = 0;
        for (int w : genislikler) toplam += w;
        t.setWidthType(TableWidthType.DXA);
        t.setWidth(String.valueOf(toplam));
        CTTblPr pr = t.getCTTbl().getTblPr() != null ? t.getCTTbl().getTblPr() : t.getCTTbl().addNewTblPr();
        (pr.isSetTblLayout() ? pr.getTblLayout() : pr.addNewTblLayout()).setType(STTblLayoutType.FIXED);
        CTTblGrid grid = t.getCTTbl().getTblGrid() != null ? t.getCTTbl().getTblGrid() : t.getCTTbl().addNewTblGrid();
        while (grid.sizeOfGridColArray() > 0) grid.removeGridCol(0);
        for (int w : genislikler) grid.addNewGridCol().setW(BigInteger.valueOf(w));
        for (XWPFTableRow r : t.getRows()) hucreGenislikleri(r, genislikler);
    }

    private static void hucreGenislikleri(XWPFTableRow r, int[] genislikler) {
        for (int i = 0; i < genislikler.length && i < r.getTableCells().size(); i++) {
            XWPFTableCell c = r.getCell(i);
            c.setWidthType(TableWidthType.DXA);
            c.setWidth(String.valueOf(genislikler[i]));
        }
    }

    /** cizgili=true: yalnızca üst/alt ve iç yatay ince çizgiler; false: hiç kenarlık yok. */
    private static void kenarliklar(XWPFTable t, boolean cizgili) {
        CTTblPr pr = t.getCTTbl().getTblPr();
        CTTblBorders b = pr.isSetTblBorders() ? pr.getTblBorders() : pr.addNewTblBorders();
        kenar(b.isSetTop() ? b.getTop() : b.addNewTop(), cizgili);
        kenar(b.isSetBottom() ? b.getBottom() : b.addNewBottom(), cizgili);
        kenar(b.isSetInsideH() ? b.getInsideH() : b.addNewInsideH(), cizgili);
        kenar(b.isSetLeft() ? b.getLeft() : b.addNewLeft(), false);
        kenar(b.isSetRight() ? b.getRight() : b.addNewRight(), false);
        kenar(b.isSetInsideV() ? b.getInsideV() : b.addNewInsideV(), false);
    }

    private static void kenar(CTBorder b, boolean var) {
        b.setVal(var ? STBorder.SINGLE : STBorder.NONE);
        b.setSz(BigInteger.valueOf(var ? 4 : 0));
        b.setSpace(BigInteger.ZERO);
        b.setColor("000000");
    }

    /** Paragrafı bir sonrakiyle aynı sayfada tutar (imza bloğunun bölünmemesi için). */
    private static void birlikte(XWPFParagraph p) {
        if (p.getCTP().getPPr() == null) p.getCTP().addNewPPr();
        p.setKeepNext(true);
    }

    private static XWPFParagraph ilkParagraf(XWPFTableCell c) {
        return c.getParagraphs().isEmpty() ? c.addParagraph() : c.getParagraphs().get(0);
    }

    private static void bosParagraf(XWPFTableCell c) {
        c.addParagraph();
    }

    private static void bosParagraf(XWPFDocument doc, int adet) {
        for (int i = 0; i < adet; i++) doc.createParagraph();
    }

    static XWPFRun run(XWPFParagraph p, String metin, boolean kalin, boolean italik, boolean altiCizili, int punto) {
        XWPFRun r = p.createRun();
        r.setFontFamily(FONT);
        r.setFontSize(punto);
        r.setBold(kalin);
        r.setItalic(italik);
        if (altiCizili) r.setUnderline(UnderlinePatterns.SINGLE);
        r.setText(metin == null ? "" : metin);
        return r;
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private static String nz(String s, String varsayilan) { return s == null || s.isBlank() ? varsayilan : s; }
}
