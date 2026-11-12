package legal.erk.yobf.ui;

import legal.erk.yobf.config.Ayarlar;
import legal.erk.yobf.config.Imzaci;
import legal.erk.yobf.model.Bedel;
import legal.erk.yobf.model.EkSatir;
import legal.erk.yobf.model.FormVerisi;
import legal.erk.yobf.model.Satirlar;
import legal.erk.yobf.model.SozlesmeGrubu;
import legal.erk.yobf.render.FormYazici;

import javax.imageio.ImageIO;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.font.TextAttribute;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Formun yaklaşık önizlemesi (Word açmadan). FormYazici ile aynı ölçüleri, yazı boyutlarını ve
 * sıkıştırma/genişletme kuralını kullanır; satır kırılımları Word'dekinden biraz farklı olabilir.
 * Çizim birimi punto (pt); sayfa A4 = 595 × 842 pt.
 */
public class OnizlemePaneli extends JPanel {

    static final double SAYFA_G = FormYazici.A4_GEN / 20.0, SAYFA_Y = FormYazici.A4_YUK / 20.0;
    static final double YAN = FormYazici.KENAR_YAN / 20.0, UST = FormYazici.KENAR_UST / 20.0;
    static final double ICERIK = FormYazici.ICERIK / 20.0;
    static final double ETIKET = FormYazici.ETIKET_SUTUN / 20.0, AYRAC = FormYazici.AYRAC_SUTUN / 20.0;
    static final double DEGER = FormYazici.DEGER_SUTUN / 20.0;
    static final String YAZI_TIPI = yaziTipi();

    private final Ayarlar ayarlar;
    private final FormYazici yazici;
    private FormVerisi form;
    private final Map<Path, BufferedImage> logoOnbellek = new HashMap<>();

    public OnizlemePaneli(Ayarlar ayarlar) {
        this.ayarlar = ayarlar;
        this.yazici = new FormYazici(ayarlar);
        setBackground(new Color(0x9E9E9E));
        setPreferredSize(new Dimension(360, 510));
        setToolTipText("Yaklaşık önizleme — kesin görünüm Word çıktısındadır. Büyütmek için tıklayın.");
    }

    public void goster(FormVerisi f) {
        this.form = f;
        repaint();
    }

    public FormVerisi form() { return form; }

    private static String yaziTipi() {
        try {
            for (String f : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
                if (f.equalsIgnoreCase(FormYazici.FONT)) return f;
            }
        } catch (Exception ignored) {
            // headless
        }
        return Font.SERIF;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        double olcek = Math.min((getWidth() - 16) / SAYFA_G, (getHeight() - 16) / SAYFA_Y);
        double x = (getWidth() - SAYFA_G * olcek) / 2, y = (getHeight() - SAYFA_Y * olcek) / 2;
        g.translate(x, y);
        g.scale(olcek, olcek);
        ciz(g);
        g.dispose();
    }

    /** Sayfayı (0,0)–(595,842) pt koordinatlarına çizer. Testlerde BufferedImage'a çizmek için de kullanılır. */
    public void ciz(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setColor(Color.WHITE);
        g.fill(new Rectangle2D.Double(0, 0, SAYFA_G, SAYFA_Y));
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(2.25f));
        g.draw(new Rectangle2D.Double(24, 24, SAYFA_G - 48, SAYFA_Y - 48));
        if (form == null) return;
        new Cizici(g, form).ciz();
    }

    // ------------------------------------------------------------------ metin parçaları

    /** Aynı biçimdeki metin parçası. */
    record Parca(String metin, boolean kalin, boolean italik, boolean altiCizili, double punto) {}

    /** Paragraf: parçalar + sol girinti + iki yana yaslı mı + ortalı mı. */
    record Paragraf(List<Parca> parcalar, double girinti, boolean ortali, double onceBosluk) {
        static Paragraf of(double girinti, Parca... p) { return new Paragraf(List.of(p), girinti, false, 0); }
        static Paragraf bos() { return new Paragraf(List.of(), 0, false, 0); }
    }

    /** Tablo satırı: etiket + değer paragrafları. */
    record Satir(String etiket, List<Paragraf> deger) {}

    private final class Cizici {
        final Graphics2D g;
        final FormVerisi f;
        final int kademe;
        final boolean genis;
        final double oran;

        Cizici(Graphics2D g, FormVerisi f) {
            this.g = g;
            this.f = f;
            double tahmin = yazici.tahminiYukseklik(f);
            this.kademe = FormYazici.sikistirmaKademesi(tahmin);
            this.genis = kademe == 0 && tahmin < FormYazici.KISA_FORM_PT;
            this.oran = kademe > 0 ? Math.pow(0.72, kademe) : genis ? 1.4 : 1.0;
        }

        /** FormYazici.sikistir ile aynı: her kademede 1 pt küçülür, alt sınır 9/8 pt. */
        double pt(double p) {
            return kademe == 0 ? p : Math.max(p >= 11 ? 9 : 8, p - kademe);
        }

        Parca p(String m, boolean b, boolean i, boolean u, double punto) { return new Parca(m, b, i, u, pt(punto)); }
        Parca duz(String m) { return p(m, false, false, false, FormYazici.GOVDE); }

        Font font(Parca p) {
            Map<TextAttribute, Object> a = new HashMap<>();
            a.put(TextAttribute.FAMILY, YAZI_TIPI);
            a.put(TextAttribute.SIZE, (float) p.punto());
            if (p.kalin()) a.put(TextAttribute.WEIGHT, TextAttribute.WEIGHT_BOLD);
            if (p.italik()) a.put(TextAttribute.POSTURE, TextAttribute.POSTURE_OBLIQUE);
            if (p.altiCizili()) a.put(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);
            return Font.getFont(a);
        }

        double satirYuksekligi(double punto) { return punto * 1.1 * 1.17; }

        // -------------------------------------------------------------- ana akış

        void ciz() {
            double y = UST + 10 * oran;
            y = logo(y);
            Ayarlar.FormTuru ft = ayarlar.formTuru(f.formTuru).orElse(new Ayarlar.FormTuru(f.formTuru, f.formTuru, null));
            y = ortaliYaz(p(ft.baslik(), true, false, false, FormYazici.BASLIK), y) + 4;
            if (f.ikiDilli() && ft.baslikEn() != null && !ft.baslikEn().isBlank()) {
                y = ortaliYaz(p(ft.baslikEn(), true, false, false, FormYazici.BASLIK), y) + 4;
            }
            y += 8;
            y = tablo(y);
            imzaBlogu(y);
        }

        double logo(double y) {
            if (f.logo == null || !Files.isRegularFile(f.logo)) return y;
            BufferedImage im = logoOnbellek.computeIfAbsent(f.logo, p -> {
                try { return ImageIO.read(p.toFile()); } catch (Exception e) { return null; }
            });
            if (im == null) return y;
            double h = FormYazici.LOGO_YUKSEKLIK_CM / 2.54 * 72, w = h * im.getWidth() / im.getHeight();
            if (w > ICERIK) { h *= ICERIK / w; w = ICERIK; }
            AffineTransform t = new AffineTransform();
            t.translate((SAYFA_G - w) / 2, y);
            t.scale(w / im.getWidth(), h / im.getHeight());
            g.drawImage(im, t, null);
            return y + h + 10;
        }

        double ortaliYaz(Parca p, double y) {
            Font fn = font(p);
            FontMetrics fm = g.getFontMetrics(fn);
            g.setFont(fn);
            g.setColor(Color.BLACK);
            double w = fm.getStringBounds(p.metin(), g).getWidth();
            g.drawString(p.metin(), (float) ((SAYFA_G - w) / 2), (float) (y + fm.getAscent()));
            return y + satirYuksekligi(p.punto());
        }

        // -------------------------------------------------------------- tablo

        double tablo(double y) {
            List<Satir> satirlar = satirlar();
            double pad = (kademe > 0 ? Math.max(1.5, 5.5 * oran) : genis ? 9 : 5.5);
            g.setStroke(new BasicStroke(0.5f));
            g.setColor(Color.BLACK);
            g.draw(new Line2D.Double(YAN, y, YAN + ICERIK, y));
            for (Satir s : satirlar) {
                Paragraf etiketP = Paragraf.of(0, p(s.etiket(), true, false, false, FormYazici.GOVDE));
                double he = paragraflar(List.of(etiketP), YAN + 5, ETIKET - 10, 0, false);
                double hd = paragraflar(s.deger(), YAN + ETIKET + AYRAC + 5, DEGER - 10, 0, false);
                double h = Math.max(he, hd) + 2 * pad;
                paragraflar(List.of(etiketP), YAN + 5, ETIKET - 10, y + (h - he) / 2, true);
                Parca iki = duz(":");
                double hi = satirYuksekligi(iki.punto());
                yaz(iki, YAN + ETIKET + AYRAC / 2 - 2, y + (h - hi) / 2);
                paragraflar(s.deger(), YAN + ETIKET + AYRAC + 5, DEGER - 10, y + (h - hd) / 2, true);
                y += h;
                g.setColor(Color.BLACK);
                g.setStroke(new BasicStroke(0.5f));
                g.draw(new Line2D.Double(YAN, y, YAN + ICERIK, y));
            }
            return y;
        }

        /** Paragrafları ölçer (ciz=false) veya çizer; toplam yüksekliği döndürür. */
        double paragraflar(List<Paragraf> ps, double x, double genislik, double y0, boolean cizilsin) {
            double y = y0;
            for (Paragraf p : ps) {
                y += p.onceBosluk();
                if (p.parcalar().isEmpty()) {
                    y += satirYuksekligi(pt(FormYazici.GOVDE));
                    continue;
                }
                y = paragraf(p, x, genislik, y, cizilsin);
            }
            return y - y0;
        }

        double paragraf(Paragraf p, double x0, double genislik, double y, boolean cizilsin) {
            // kelime kelime yerleştirme (parçalar arası biçim korunur)
            List<Object[]> satir = new ArrayList<>(); // {Parca, kelime, genişlik}
            double x = 0, maks = genislik - p.girinti();
            double satirPt = p.parcalar().stream().mapToDouble(Parca::punto).max().orElse(FormYazici.GOVDE);
            for (Parca pr : p.parcalar()) {
                Font fn = font(pr);
                FontMetrics fm = g.getFontMetrics(fn);
                String[] kelimeler = pr.metin().split("(?<= )");
                for (String k : kelimeler) {
                    if (k.isEmpty()) continue;
                    double w = fm.getStringBounds(k, g).getWidth();
                    if (x + w > maks && x > 0 && !k.isBlank()) {
                        if (cizilsin) satirCiz(satir, x0 + p.girinti(), y, p.ortali(), maks, x);
                        y += satirYuksekligi(satirPt);
                        satir.clear();
                        x = 0;
                        k = k.stripLeading();
                        w = fm.getStringBounds(k, g).getWidth();
                    }
                    satir.add(new Object[]{pr, k, w});
                    x += w;
                }
            }
            if (cizilsin) satirCiz(satir, x0 + p.girinti(), y, p.ortali(), maks, x);
            return y + satirYuksekligi(satirPt);
        }

        void satirCiz(List<Object[]> satir, double x0, double y, boolean ortali, double maks, double toplam) {
            double x = ortali ? x0 + (maks - toplam) / 2 : x0;
            double asc = 0;
            for (Object[] o : satir) asc = Math.max(asc, g.getFontMetrics(font((Parca) o[0])).getAscent());
            for (Object[] o : satir) {
                Parca pr = (Parca) o[0];
                g.setFont(font(pr));
                g.setColor(Color.BLACK);
                g.drawString((String) o[1], (float) x, (float) (y + asc));
                x += (double) o[2];
            }
        }

        void yaz(Parca p, double x, double y) {
            g.setFont(font(p));
            g.setColor(Color.BLACK);
            g.drawString(p.metin(), (float) x, (float) (y + g.getFontMetrics().getAscent()));
        }

        /** FormYazici.anaTablo ile aynı satırlar, aynı dahil/ek satır kuralları. */
        List<Satir> satirlar() {
            boolean en = f.ikiDilli();
            List<Satir> r = new ArrayList<>();
            ekle(r, Satirlar.TARIH, en ? "Tarih / Date" : "Tarih", List.of(Paragraf.of(0, duz(f.tarih))));
            ekle(r, Satirlar.BELGE_TURU, en ? "Belge Türü / Document Type" : "Belge Türü",
                    List.of(Paragraf.of(0, duz(FormYazici.belgeTuru(f)))));
            ekle(r, Satirlar.TALEP_EDEN, en ? "Belgeyi Talep Eden Departman / Requesting Department"
                    : "Belgeyi Talep Eden Departman", satirParagraflari(f.talepEdenDepartman));
            ekle(r, Satirlar.IMZAYA_SUNAN, en ? "Belgeyi İmzaya Sunan Departman / Department Submitting for Signature"
                    : "Belgeyi İmzaya Sunan Departman", satirParagraflari(f.imzayaSunanDepartman));
            if (f.azilname() || f.vekaletname()) {
                ekle(r, Satirlar.KISILER, f.azilname() ? "Azledilecek Kişiler" : "Vekaletname Verilecek Kişiler",
                        List.of(Paragraf.of(0, duz(String.join(", ", f.kisiler)))));
                ekle(r, Satirlar.SIRKETLER, f.azilname() ? "Azleden Şirketler" : "Vekaletname Veren Şirket",
                        f.sirketler.stream().map(s -> Paragraf.of(0, duz(s))).toList());
                if (f.vekaletname()) {
                    ekle(r, Satirlar.SONA_ERME, "Sona Erme Tarihi", List.of(Paragraf.of(0, duz(f.sonaErmeTarihi))));
                }
                List<Paragraf> k = new ArrayList<>(List.of(Paragraf.of(0, duz(f.konuTr))));
                not(k);
                ekle(r, Satirlar.KONU, "Konu", k);
            } else {
                ekle(r, Satirlar.KONU, en ? "Belgenin Konusu/Özeti / Subject/Summary of the Document"
                        : "Belgenin Konusu/Özeti", konuBlogu());
            }
            List<String> yetkili = f.imzasinaSunulacak.stream().filter(s -> s != null && !s.isBlank()).toList();
            if (!yetkili.isEmpty()) {
                ekle(r, Satirlar.IMZASINA_SUNULACAK, en ? "İmzasına Sunulacak Yetkili / Authorized Signatory"
                        : "İmzasına Sunulacak Yetkili", yetkili.stream().map(this::aciklamali).toList());
            } else {
                ekSatirlar(r, Satirlar.IMZASINA_SUNULACAK);
            }
            // konumu bu formda olmayan ek satırlar sona
            List<String> konumlar = new ArrayList<>(Satirlar.konumlar(f.formTuru).keySet());
            for (EkSatir e : f.ekSatirlar) {
                if (!e.alt() && !e.bos() && !konumlar.contains(e.konum())) {
                    r.add(new Satir(e.etiket(), satirParagraflari(e.deger())));
                }
            }
            return r;
        }

        void ekle(List<Satir> r, String anahtar, String etiket, List<Paragraf> deger) {
            if (f.dahil(anahtar)) {
                List<Paragraf> d = new ArrayList<>(deger);
                altSatirlar(d, anahtar);
                if (!d.isEmpty()) r.add(new Satir(etiket, d));
            }
            ekSatirlar(r, anahtar);
        }

        void ekSatirlar(List<Satir> r, String konum) {
            for (EkSatir e : f.ekSatirlar) {
                if (!e.alt() && konum.equals(e.konum()) && !e.bos()) {
                    r.add(new Satir(e.etiket(), satirParagraflari(e.deger())));
                }
            }
        }

        void altSatirlar(List<Paragraf> d, String konum) {
            for (EkSatir e : f.ekSatirlar) {
                if (!e.alt() || !konum.equals(e.konum()) || e.bos()) continue;
                if (!d.isEmpty()) d.add(Paragraf.bos());
                String[] s = e.deger().isEmpty() ? new String[]{""} : e.deger().split("\\R");
                for (int i = 0; i < s.length; i++) {
                    List<Parca> pp = new ArrayList<>();
                    if (i == 0 && !e.etiket().isEmpty()) {
                        pp.add(p(e.etiket().endsWith(":") ? e.etiket() : e.etiket() + ":", true, false, true, FormYazici.GOVDE));
                        pp.add(duz(" "));
                    }
                    if (!s[i].isEmpty()) pp.add(duz(s[i]));
                    d.add(new Paragraf(pp, 0, false, 0));
                }
            }
        }

        List<Paragraf> satirParagraflari(String metin) {
            if (metin == null || metin.isBlank()) return List.of(Paragraf.of(0, duz("")));
            return Arrays.stream(metin.split("\\R")).filter(s -> !s.isBlank()).map(s -> Paragraf.of(0, duz(s.trim()))).toList();
        }

        Paragraf aciklamali(String satir) {
            int i = satir.indexOf('(');
            if (i > 0 && satir.trim().endsWith(")")) {
                return Paragraf.of(0, duz(satir.substring(0, i).stripTrailing() + " "),
                        p(satir.substring(i).trim(), false, true, false, FormYazici.GOVDE));
            }
            return Paragraf.of(0, duz(satir));
        }

        List<Paragraf> konuBlogu() {
            boolean en = f.ikiDilli(), coklu = f.sozlesmeler.size() > 1;
            List<Paragraf> d = new ArrayList<>();
            if (f.dahil(Satirlar.TARAFLAR)) {
                for (int gi = 0; gi < f.sozlesmeler.size(); gi++) {
                    List<String> t = f.sozlesmeler.get(gi).taraflar.stream().filter(s -> s != null && !s.isBlank()).toList();
                    if (t.isEmpty()) continue;
                    d.add(Paragraf.of(0, p((coklu ? (gi + 1) + ". Sözleşme " : "") + (en ? "Taraflar / Parties:" : "Taraflar:"),
                            true, false, true, FormYazici.GOVDE)));
                    for (int i = 0; i < t.size(); i++) d.add(Paragraf.of(coklu ? 25 : 18, duz((i + 1) + ") " + t.get(i))));
                }
            }
            if (f.dahil(Satirlar.KONU)) {
                if (!d.isEmpty()) d.add(Paragraf.bos());
                d.add(Paragraf.of(0, p(en ? "Konu / Subject:" : "Konu:", true, false, true, FormYazici.GOVDE),
                        duz(" " + nz(f.konuTr))));
                if (en && !nz(f.konuEn).isBlank()) {
                    d.add(Paragraf.bos());
                    d.add(Paragraf.of(0, duz(f.konuEn)));
                }
            }
            boolean bedelVar = f.sozlesmeler.stream().anyMatch(s -> s.bedeller.stream().anyMatch(b -> !b.bos()));
            if (f.dahil(Satirlar.BEDEL) && bedelVar) {
                if (!d.isEmpty()) d.add(Paragraf.bos());
                for (int gi = 0; gi < f.sozlesmeler.size(); gi++) {
                    SozlesmeGrubu sg = f.sozlesmeler.get(gi);
                    List<Bedel> bl = sg.bedeller.stream().filter(b -> !b.bos()).toList();
                    for (int i = 0; i < bl.size(); i++) {
                        String et = coklu ? (gi + 1) + ". Sözleşme Bedeli" : nz(f.bedelEtiketi).isBlank() ? "Bedel" : f.bedelEtiketi;
                        if (en) et += " / " + FormYazici.BEDEL_ETIKETI_EN.getOrDefault(coklu ? "Sözleşme Bedeli" : et, "Price");
                        d.add(i == 0
                                ? Paragraf.of(0, p(et + ":", true, false, true, FormYazici.GOVDE), duz(" " + bl.get(i).metin()))
                                : Paragraf.of(18, duz(bl.get(i).metin())));
                    }
                }
            }
            not(d);
            return d;
        }

        void not(List<Paragraf> d) {
            if (!f.dahil(Satirlar.NOT) || nz(f.notMetni).isBlank()) return;
            if (!d.isEmpty()) d.add(Paragraf.bos());
            String[] s = f.notMetni.trim().split("\\R");
            for (int i = 0; i < s.length; i++) {
                List<Parca> pp = new ArrayList<>();
                if (i == 0) pp.add(p((nz(f.notEtiketi).isBlank() ? "Not:" : f.notEtiketi) + " ", true, true, false, FormYazici.ACIKLAMA));
                pp.add(p(s[i], false, true, false, FormYazici.ACIKLAMA));
                d.add(new Paragraf(pp, 0, false, 0));
            }
        }

        // -------------------------------------------------------------- imza bloğu

        void imzaBlogu(double y) {
            Ayarlar.ImzaBlogu ib = ayarlar.imzaBlogu();
            boolean en = f.ikiDilli();
            List<Imzaci> im = f.anaImzacilar.stream().filter(i -> i != null && !i.ad().isBlank())
                    .limit(ib.anaImzaciMax()).toList();
            y += 28 * oran;
            y = ortaliYaz(p(en ? ib.anaBaslik() + " / " + ib.anaBaslikEn() : ib.anaBaslik(), true, false, true,
                    FormYazici.IMZA_BASLIK), y) + 15 * oran;
            if (!im.isEmpty()) {
                List<Integer> sat = ib.satirlar(im.size());
                int enCok = sat.stream().mapToInt(Integer::intValue).max().orElse(1);
                double hucre = ICERIK / Math.max(2, enCok);
                int idx = 0;
                for (int s = 0; s < sat.size(); s++) {
                    if (s > 0) y += 18 * oran;
                    y = imzaSatiri(im.subList(idx, idx + sat.get(s)), hucre, y, en);
                    idx += sat.get(s);
                }
            }
            if (f.ekOnayAcik && f.ekOnayImzaci != null && !f.ekOnayImzaci.ad().isBlank()) {
                String b = nz(f.ekOnayBaslik).isBlank() ? ib.ekOnayBaslik() : f.ekOnayBaslik;
                y += 24 * oran;
                y = ortaliYaz(p(en ? b + " / " + ib.ekOnayBaslikEn() : b, true, false, true, FormYazici.IMZA_BASLIK), y)
                        + 15 * oran;
                imzaSatiri(List.of(f.ekOnayImzaci), ICERIK / 2, y, en);
            }
        }

        double imzaSatiri(List<Imzaci> kisiler, double hucre, double y, boolean en) {
            double x0 = YAN + (ICERIK - hucre * kisiler.size()) / 2;
            double enAlt = y;
            for (int i = 0; i < kisiler.size(); i++) {
                Imzaci k = kisiler.get(i);
                List<Paragraf> ps = new ArrayList<>();
                ps.add(new Paragraf(List.of(p(k.ad(), true, false, false, FormYazici.IMZACI_AD)), 0, true, 0));
                if (!k.unvan().isBlank()) {
                    ps.add(new Paragraf(List.of(p(k.unvan(), false, true, false, FormYazici.IMZACI_UNVAN)), 0, true, 0));
                }
                if (en && !k.unvanEn().isBlank() && !k.unvanEn().equals(k.unvan())) {
                    ps.add(new Paragraf(List.of(p(k.unvanEn(), false, true, false, FormYazici.IMZACI_UNVAN)), 0, true, 0));
                }
                double h = paragraflar(ps, x0 + i * hucre + 4, hucre - 8, y, true);
                enAlt = Math.max(enAlt, y + h);
            }
            double cizgiY = enAlt + FormYazici.IMZA_BOSLUGU / 20.0 * oran + satirYuksekligi(pt(FormYazici.GOVDE)) * 0.8;
            g.setStroke(new BasicStroke(0.6f));
            g.setColor(Color.BLACK);
            double cizgi = Math.min(hucre - 30, FormYazici.imzaCizgisiUzunlugu((int) (hucre * 20)) * 5.6);
            for (int i = 0; i < kisiler.size(); i++) {
                double orta = x0 + i * hucre + hucre / 2;
                g.draw(new Line2D.Double(orta - cizgi / 2, cizgiY, orta + cizgi / 2, cizgiY));
            }
            return cizgiY + 6;
        }
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
