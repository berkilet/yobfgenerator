package legal.erk.yobf.ui;

import legal.erk.yobf.config.Ayarlar;
import legal.erk.yobf.config.Imzaci;
import legal.erk.yobf.config.Yapilandirma;
import legal.erk.yobf.model.Bedel;
import legal.erk.yobf.model.FormVerisi;
import legal.erk.yobf.model.SozlesmeGrubu;
import legal.erk.yobf.rules.FormHazirlayici;
import legal.erk.yobf.rules.KarakterSayaci;
import legal.erk.yobf.rules.Uyari;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.JTextComponent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Düzenleme ekranı: tüm alanlar düzenlenebilir; Konu yanında canlı karakter sayacı (§2.5). */
public class DuzenlemePaneli extends JPanel {

    static final String GRUP_AYRACI = "---";
    static final String LOGO_OTOMATIK = "(Otomatik — taraflara göre)";
    static final String LOGO_GOZAT = "Gözat…";

    private final Yapilandirma y;
    private final FormHazirlayici hazirlayici;

    private final JComboBox<Ayarlar.FormTuru> formTuru;
    private final JTextField tarih = new JTextField(10);
    private final JComboBox<String> belgeTuru;
    private final JComboBox<String> talepEden;
    private final JComboBox<String> imzayaSunan;

    private final JTextArea taraflar = alan(4);
    private final JComboBox<String> bedelEtiketi = new JComboBox<>(new String[]{"Bedel", "Sözleşme Bedeli", "Zeyilnameye Konu Tutar"});
    private final JTextArea bedeller = alan(2);

    private final JTextArea kisiler = alan(2);
    private final JTextArea sirketler = alan(4);
    private final JTextField sonaErme = new JTextField(10);

    private final JTextArea konuTr = alan(6);
    private final JLabel konuSayac = new JLabel();
    private final JTextArea konuEn = alan(5);

    private final JComboBox<String> notEtiketi = new JComboBox<>(new String[]{"Not:", "Müşavirliğimizin Notu:"});
    private final JTextArea notMetni = alan(3);

    private final JTextArea imzasinaSunulacak = alan(2);
    private final JComboBox<String> yetkiliSecici;

    private final JComboBox<Object> logo = new JComboBox<>();
    private Path elleLogo;

    private final ImzaciPaneli imzaciPaneli;

    private final List<JComponent> yobfAlanlari = new ArrayList<>();
    private final List<JComponent> ozelAlanlar = new ArrayList<>();
    private final List<JComponent> vekaletAlanlari = new ArrayList<>();
    private final List<JComponent> enAlanlari = new ArrayList<>();
    private final Map<String, List<JComponent>> alanBilesenleri = new HashMap<>();
    private final Map<JComponent, Object[]> ilkRenk = new HashMap<>();
    private Runnable degisti = () -> {};
    private boolean yukleniyor;

    public DuzenlemePaneli(Yapilandirma y) {
        super(new GridBagLayout());
        this.y = y;
        this.hazirlayici = new FormHazirlayici(y);
        Ayarlar a = y.ayarlar();

        formTuru = new JComboBox<>(a.formTurleri().toArray(new Ayarlar.FormTuru[0]));
        belgeTuru = duzenlenebilir(a.belgeTurleri());
        talepEden = duzenlenebilir(a.talepEdenDepartmanlar());
        imzayaSunan = duzenlenebilir(a.imzayaSunanDepartmanlar());
        bedelEtiketi.setEditable(true);
        notEtiketi.setEditable(true);
        List<String> yetkililer = new ArrayList<>();
        yetkililer.add("— listeden ekle —");
        yetkililer.addAll(y.imzacilar().imzasinaSunulacakYetkililer());
        yetkiliSecici = new JComboBox<>(yetkililer.toArray(new String[0]));
        imzaciPaneli = new ImzaciPaneli(y.imzacilar(), a.imzaBlogu());

        int r = 0;
        r = satir(r, "Form türü", formTuru, null);
        r = satir(r, "Tarih (GG.AA.YYYY)", tarih, "Tarih");
        r = satir(r, "Belge Türü", belgeTuru, "Belge Türü");
        r = satir(r, "Talep Eden Departman", talepEden, null);
        r = satir(r, "İmzaya Sunan Departman", imzayaSunan, null);

        r = satir(r, "<html>Taraflar<br><small>her satıra bir taraf;<br>çoklu sözleşmede<br>gruplar arası \"---\"</small></html>",
                kaydir(taraflar), "Taraflar", yobfAlanlari);
        JPanel bedelPanel = new JPanel(new BorderLayout(4, 4));
        bedelPanel.add(bedelEtiketi, BorderLayout.NORTH);
        bedelPanel.add(kaydir(bedeller), BorderLayout.CENTER);
        r = satir(r, "<html>Bedel<br><small>ör. 3.570.000 TL + KDV;<br>gruplar arası \"---\"</small></html>",
                bedelPanel, "Bedel", yobfAlanlari);

        r = satir(r, "<html>Kişiler<br><small>virgülle ayırın</small></html>", kaydir(kisiler), "Kişiler", ozelAlanlar);
        r = satir(r, "<html>Şirketler<br><small>her satıra bir şirket</small></html>", kaydir(sirketler), "Şirketler", ozelAlanlar);
        r = satir(r, "Sona Erme Tarihi", sonaErme, "Sona Erme Tarihi", vekaletAlanlari);

        JPanel konuPanel = new JPanel(new BorderLayout(4, 2));
        konuPanel.add(kaydir(konuTr), BorderLayout.CENTER);
        JPanel sayacPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        sayacPanel.add(konuSayac);
        konuPanel.add(sayacPanel, BorderLayout.SOUTH);
        r = satir(r, "Konu", konuPanel, "Konu");
        r = satir(r, "Konu (EN)", kaydir(konuEn), "Konu (EN)", enAlanlari);

        JPanel notPanel = new JPanel(new BorderLayout(4, 4));
        notPanel.add(notEtiketi, BorderLayout.NORTH);
        notPanel.add(kaydir(notMetni), BorderLayout.CENTER);
        r = satir(r, "<html>Not<br><small>hukukçu yazar;<br>model doldurmaz</small></html>", notPanel, null);

        JPanel yetkiliPanel = new JPanel(new BorderLayout(4, 4));
        yetkiliPanel.add(yetkiliSecici, BorderLayout.NORTH);
        yetkiliPanel.add(kaydir(imzasinaSunulacak), BorderLayout.CENTER);
        r = satir(r, "<html>İmzasına Sunulacak<br>Yetkili <small>(opsiyonel)</small><br>"
                + "<small>ör. Sn. Ad SOYAD (X için)</small></html>", yetkiliPanel, null);

        JPanel logoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        logoPanel.add(logo);
        r = satir(r, "Logo", logoPanel, "Logo");

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = r;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.insets = new Insets(8, 4, 4, 4);
        add(imzaciPaneli, c);
        alanBilesenleri.put("İmzacılar", List.of(imzaciPaneli.listeBileseni()));
        alanBilesenleri.put("Ek Onay", List.of(imzaciPaneli.listeBileseni()));
        c.gridy = r + 1;
        c.weighty = 1;
        add(new JPanel(), c);

        alanBilesenleri.put("Tarih", List.of(tarih));
        alanBilesenleri.put("Belge Türü", List.of(belgeTuru));
        alanBilesenleri.put("Taraflar", List.of(taraflar));
        alanBilesenleri.put("Bedel", List.of(bedeller));
        alanBilesenleri.put("Kişiler", List.of(kisiler));
        alanBilesenleri.put("Şirketler", List.of(sirketler));
        alanBilesenleri.put("Sona Erme Tarihi", List.of(sonaErme));
        alanBilesenleri.put("Konu", List.of(konuTr));
        alanBilesenleri.put("Konu (EN)", List.of(konuEn));
        alanBilesenleri.put("Logo", List.of(logo));

        logolariYukle();
        dinleyiciler();
        formTuruDegisti();
        konuSay();
    }

    // ---------------------------------------------------------------- yerleşim yardımcıları

    private static JTextArea alan(int satir) {
        JTextArea t = new JTextArea(satir, 40);
        t.setLineWrap(true);
        t.setWrapStyleWord(true);
        return t;
    }

    private static JScrollPane kaydir(JTextArea t) {
        return new JScrollPane(t);
    }

    private static JComboBox<String> duzenlenebilir(List<String> secenekler) {
        JComboBox<String> c = new JComboBox<>(secenekler.toArray(new String[0]));
        c.setEditable(true);
        c.setSelectedItem("");
        return c;
    }

    private int satir(int r, String etiket, JComponent bilesen, String alan) {
        return satir(r, etiket, bilesen, alan, null);
    }

    private int satir(int r, String etiket, JComponent bilesen, String alan, List<JComponent> grup) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = r;
        c.insets = new Insets(4, 4, 4, 8);
        c.anchor = GridBagConstraints.NORTHWEST;
        JLabel l = new JLabel(etiket);
        add(l, c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4, 0, 4, 4);
        add(bilesen, c);
        if (grup != null) {
            grup.add(l);
            grup.add(bilesen);
        }
        return r + 1;
    }

    private void logolariYukle() {
        logo.removeAllItems();
        logo.addItem(LOGO_OTOMATIK);
        Path klasor = legal.erk.yobf.config.UygulamaYolu.logolar();
        if (Files.isDirectory(klasor)) {
            try (Stream<Path> s = Files.list(klasor)) {
                s.filter(p -> p.getFileName().toString().toLowerCase().matches(".*\\.(png|jpe?g)$"))
                        .sorted()
                        .forEach(p -> logo.addItem(p.getFileName().toString()));
            } catch (IOException ignored) {
                // logo listesi boş kalır
            }
        }
        logo.addItem(LOGO_GOZAT);
    }

    private void dinleyiciler() {
        formTuru.addActionListener(e -> formTuruDegisti());
        talepEden.addActionListener(e -> {
            if (yukleniyor) return;
            Object s = talepEden.getSelectedItem();
            if (s != null) departmanOnAyari(s.toString());
        });
        yetkiliSecici.addActionListener(e -> {
            if (yetkiliSecici.getSelectedIndex() > 0) {
                String s = (String) yetkiliSecici.getSelectedItem();
                String m = imzasinaSunulacak.getText();
                imzasinaSunulacak.setText(m.isBlank() ? s : m.stripTrailing() + "\n" + s);
                yetkiliSecici.setSelectedIndex(0);
            }
        });
        logo.addActionListener(e -> {
            if (LOGO_GOZAT.equals(logo.getSelectedItem())) {
                JFileChooser fc = new JFileChooser(legal.erk.yobf.config.UygulamaYolu.logolar().toFile());
                fc.setFileFilter(new FileNameExtensionFilter("Logo (PNG, JPG)", "png", "jpg", "jpeg"));
                if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                    elleLogo = fc.getSelectedFile().toPath();
                    logo.insertItemAt(elleLogo, logo.getItemCount() - 1);
                    logo.setSelectedItem(elleLogo);
                } else {
                    logo.setSelectedIndex(0);
                }
            }
        });
        DocumentListener d = new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { konuSay(); }
            public void removeUpdate(DocumentEvent e) { konuSay(); }
            public void changedUpdate(DocumentEvent e) { konuSay(); }
        };
        konuTr.getDocument().addDocumentListener(d);
    }

    private void departmanOnAyari(String departman) {
        FormVerisi gecici = new FormVerisi();
        gecici.imzasinaSunulacak.addAll(satirlar(imzasinaSunulacak.getText()));
        if (!y.ayarlar().departmanOnAyarlari().containsKey(departman)) return;
        hazirlayici.departmanOnAyari(gecici, departman);
        imzaciPaneli.yukle(gecici.anaImzacilar, imzaciPaneli.ekOnayAcik(), imzaciPaneli.ekOnayBaslik(), imzaciPaneli.ekOnayImzaci());
        imzasinaSunulacak.setText(String.join("\n", gecici.imzasinaSunulacak));
    }

    private void konuSay() {
        int n = KarakterSayaci.say(konuTr.getText());
        int sinir = y.ayarlar().konuMaxKarakter();
        konuSayac.setText(n + " / " + sinir);
        konuSayac.setForeground(n > sinir ? new Color(0xC62828) : n > sinir * 0.9 ? new Color(0xB26A00) : UIManager.getColor("Label.foreground"));
        degisti.run();
    }

    private void formTuruDegisti() {
        String kod = secilenFormTuru();
        boolean ozel = "AZILNAME".equals(kod) || "VEKALETNAME".equals(kod);
        yobfAlanlari.forEach(c -> c.setVisible(!ozel));
        ozelAlanlar.forEach(c -> c.setVisible(ozel));
        vekaletAlanlari.forEach(c -> c.setVisible("VEKALETNAME".equals(kod)));
        enAlanlari.forEach(c -> c.setVisible("YOBF_TR_EN".equals(kod)));
        ((JLabel) ozelAlanlar.get(0)).setText("AZILNAME".equals(kod)
                ? "<html>Azledilecek Kişiler<br><small>virgülle ayırın</small></html>"
                : "<html>Vekaletname Verilecek<br>Kişiler <small>(virgülle)</small></html>");
        ((JLabel) ozelAlanlar.get(2)).setText("AZILNAME".equals(kod)
                ? "<html>Azleden Şirketler<br><small>her satıra bir şirket</small></html>"
                : "<html>Vekaletname Veren<br>Şirket <small>(satır başına bir)</small></html>");
        revalidate();
        repaint();
        degisti.run();
    }

    private String secilenFormTuru() {
        Object o = formTuru.getSelectedItem();
        return o instanceof Ayarlar.FormTuru f ? f.kod() : "YOBF";
    }

    public void degisinceCalistir(Runnable r) {
        this.degisti = r;
        imzaciPaneli.degisinceCalistir(r);
    }

    // ---------------------------------------------------------------- veri aktarımı

    public void yukle(FormVerisi f) {
        yukleniyor = true;
        try {
            y.ayarlar().formTuru(f.formTuru).ifPresent(formTuru::setSelectedItem);
            tarih.setText(f.tarih);
            belgeTuru.setSelectedItem(f.belgeTuru);
            talepEden.setSelectedItem(f.talepEdenDepartman);
            imzayaSunan.setSelectedItem(f.imzayaSunanDepartman);
            List<String> tg = new ArrayList<>();
            List<String> bg = new ArrayList<>();
            for (SozlesmeGrubu g : f.sozlesmeler) {
                tg.add(String.join("\n", g.taraflar));
                bg.add(String.join("\n", g.bedeller.stream().map(Bedel::metin).toList()));
            }
            String ayrac = "\n" + GRUP_AYRACI + "\n";
            taraflar.setText(String.join(ayrac, tg));
            bedeller.setText(bg.stream().allMatch(String::isBlank) ? "" : String.join(ayrac, bg));
            bedelEtiketi.setSelectedItem(f.bedelEtiketi);
            kisiler.setText(String.join(", ", f.kisiler));
            sirketler.setText(String.join("\n", f.sirketler));
            sonaErme.setText(f.sonaErmeTarihi);
            konuTr.setText(f.konuTr);
            konuEn.setText(f.konuEn);
            notEtiketi.setSelectedItem(f.notEtiketi);
            notMetni.setText(f.notMetni);
            imzasinaSunulacak.setText(String.join("\n", f.imzasinaSunulacak));
            imzaciPaneli.yukle(f.anaImzacilar, f.ekOnayAcik, f.ekOnayBaslik, f.ekOnayImzaci);
            logo.setSelectedIndex(0);
            konuTr.setCaretPosition(0);
        } finally {
            yukleniyor = false;
        }
        formTuruDegisti();
    }

    /** Ekrandaki değerlerden FormVerisi oluşturur. Logo burada çözülmez (seçim ayrı). */
    public FormVerisi oku() {
        FormVerisi f = new FormVerisi();
        f.formTuru = secilenFormTuru();
        f.tarih = tarih.getText().trim();
        f.belgeTuru = metin(belgeTuru);
        f.talepEdenDepartman = metin(talepEden);
        f.imzayaSunanDepartman = metin(imzayaSunan);
        List<List<String>> tg = gruplar(taraflar.getText());
        List<List<String>> bg = gruplar(bedeller.getText());
        int n = Math.max(tg.size(), bg.size());
        for (int i = 0; i < Math.max(1, n); i++) {
            SozlesmeGrubu g = new SozlesmeGrubu();
            if (i < tg.size()) g.taraflar.addAll(tg.get(i));
            if (i < bg.size()) bg.get(i).forEach(b -> g.bedeller.add(Bedel.metindenOlustur(b)));
            f.sozlesmeler.add(g);
        }
        f.bedelEtiketi = metin(bedelEtiketi);
        Arrays.stream(kisiler.getText().split("[,\\n]")).map(String::trim).filter(s -> !s.isEmpty()).forEach(f.kisiler::add);
        f.sirketler.addAll(satirlar(sirketler.getText()));
        f.sonaErmeTarihi = sonaErme.getText().trim();
        f.konuTr = konuTr.getText().trim();
        f.konuEn = konuEn.getText().trim();
        f.notEtiketi = metin(notEtiketi);
        f.notMetni = notMetni.getText().trim();
        f.imzasinaSunulacak.addAll(satirlar(imzasinaSunulacak.getText()));
        for (Imzaci i : imzaciPaneli.anaImzacilar()) f.anaImzacilar.add(hazirlayici.kanonik(i));
        f.ekOnayAcik = imzaciPaneli.ekOnayAcik();
        f.ekOnayBaslik = imzaciPaneli.ekOnayBaslik();
        f.ekOnayImzaci = hazirlayici.kanonik(imzaciPaneli.ekOnayImzaci());
        return f;
    }

    /** Elle seçilen logo; "Otomatik" ise null. */
    public Path secilenLogo() {
        Object o = logo.getSelectedItem();
        if (o instanceof Path p) return p;
        if (o instanceof String s && !s.equals(LOGO_OTOMATIK) && !s.equals(LOGO_GOZAT)) {
            return legal.erk.yobf.config.UygulamaYolu.logolar().resolve(s);
        }
        return null;
    }

    static List<List<String>> gruplar(String metin) {
        List<List<String>> r = new ArrayList<>();
        List<String> g = new ArrayList<>();
        for (String s : metin.split("\\R")) {
            String t = s.trim();
            if (t.equals(GRUP_AYRACI)) {
                r.add(g);
                g = new ArrayList<>();
            } else if (!t.isEmpty()) {
                g.add(t.replaceFirst("^\\(?\\d+\\)\\s*", ""));
            }
        }
        r.add(g);
        while (r.size() > 1 && r.get(r.size() - 1).isEmpty()) r.remove(r.size() - 1);
        return r;
    }

    private static List<String> satirlar(String metin) {
        return Arrays.stream(metin.split("\\R")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static String metin(JComboBox<?> c) {
        Object o = c.isEditable() ? c.getEditor().getItem() : c.getSelectedItem();
        return o == null ? "" : o.toString().trim();
    }

    /** Uyarıların ait olduğu alanları sarı/kırmızı işaretler. */
    public void isaretle(List<Uyari> uyarilar) {
        for (Map.Entry<JComponent, Object[]> e : ilkRenk.entrySet()) {
            e.getKey().setBorder((javax.swing.border.Border) e.getValue()[1]);
            e.getKey().setBackground((Color) e.getValue()[0]);
        }
        ilkRenk.clear();
        Map<String, Uyari.Seviye> enKotu = new HashMap<>();
        for (Uyari u : uyarilar) enKotu.merge(u.alan(), u.seviye(), (a, b) -> a == Uyari.Seviye.KIRMIZI ? a : b);
        for (Map.Entry<String, Uyari.Seviye> e : enKotu.entrySet()) {
            for (JComponent c : alanBilesenleri.getOrDefault(e.getKey(), List.of())) {
                ilkRenk.putIfAbsent(c, new Object[]{c.getBackground(), c.getBorder()});
                Color renk = e.getValue() == Uyari.Seviye.KIRMIZI ? new Color(0xFFEBEE) : new Color(0xFFF8E1);
                if (c instanceof JTextComponent) c.setBackground(renk);
                c.setBorder(BorderFactory.createLineBorder(
                        e.getValue() == Uyari.Seviye.KIRMIZI ? new Color(0xE53935) : new Color(0xFFB300), 2));
            }
        }
        repaint();
    }
}
