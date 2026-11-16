package legal.erk.yobf.ui;

import legal.erk.yobf.ai.ApiAnahtari;
import legal.erk.yobf.ai.IstemciFabrikasi;
import legal.erk.yobf.ai.OzetCikarici;
import legal.erk.yobf.ai.Saglayici;
import legal.erk.yobf.ai.OzetSemasi;
import legal.erk.yobf.config.UygulamaYolu;
import legal.erk.yobf.config.Yapilandirma;
import legal.erk.yobf.extract.BelgeOkuyucu;
import legal.erk.yobf.extract.Maskeleme;
import legal.erk.yobf.extract.TaranmisBelgeException;
import legal.erk.yobf.log.IslemGunlugu;
import legal.erk.yobf.model.Bedel;
import legal.erk.yobf.model.FormVerisi;
import legal.erk.yobf.rules.Dogrulayici;
import legal.erk.yobf.rules.FormHazirlayici;
import legal.erk.yobf.rules.LogoCozumleyici;
import legal.erk.yobf.rules.TarihKontrol;
import legal.erk.yobf.rules.Uyari;
import legal.erk.yobf.render.FormYazici;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.TransferHandler;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/** Ana pencere: belge ekleme (sürükle-bırak) → analiz → düzenleme → onay → .docx (§2). */
public class AnaPencere extends JFrame {

    private Yapilandirma y;
    private final DefaultListModel<Path> belgeler = new DefaultListModel<>();
    private final DefaultListModel<Uyari> uyarilar = new DefaultListModel<>();
    private final DefaultListModel<String> dikkat = new DefaultListModel<>();
    private final JLabel durum = new JLabel(" ");
    private final JButton analizEt = new JButton("Analiz Et");
    private final JButton wordOlustur = new JButton("Onayla ve Word Oluştur…");
    private final JButton pdfOlusturBtn = new JButton("Onayla ve PDF Oluştur…");
    private final JComboBox<String> formTuruTercihi = new JComboBox<>();
    private final JComboBox<Object> modelSecici = new JComboBox<>();
    private final JComboBox<Saglayici> saglayiciSecici = new JComboBox<>(Saglayici.values());
    private boolean modelDolduruluyor;
    private final legal.erk.yobf.config.Tercihler tercihler = legal.erk.yobf.config.Tercihler.varsayilan();
    private OnizlemePaneli onizleme;
    private final javax.swing.Timer onizlemeZamanlayici = new javax.swing.Timer(300, e -> onizlemeGuncelle());
    private DuzenlemePaneli panel;
    private JScrollPane panelKaydirma;
    /** Son analizdeki maskeli kaynak metin (doğrulama için). Maskelenen değerler modele gitmez. */
    private Dogrulayici.Kaynak kaynak;
    private List<Uyari> sonUyarilar;
    /** Yazarken her tuşta değil, kısa bir duraksamadan sonra doğrular (arayüz akıcı kalır). */
    private final javax.swing.Timer dogrulamaZamanlayici = new javax.swing.Timer(150, e -> canliDogrula());

    private final JLabel[] adimlar = new JLabel[3];
    private int aktifAdim = 1;
    private final javax.swing.JProgressBar analizIlerleme = new javax.swing.JProgressBar();
    private final JPanel belgeEtiketleri = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
    private BirakmaAlani birakmaAlani;
    private final JButton temaDugmesi = new JButton();
    private final JButton dogrula = new JButton("Doğrula");
    private JPanel solAkis;

    public AnaPencere(Yapilandirma y) {
        super("YÖBF Otomatörü");
        this.y = y;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        dogrulamaZamanlayici.setRepeats(false);
        setIconImages(Ikonlar.liste());
        setLayout(new BorderLayout());
        getRootPane().putClientProperty("JRootPane.titleBarBackground", Tema.ustBant());
        getRootPane().putClientProperty("JRootPane.titleBarForeground", Color.WHITE);

        add(ustBant(), BorderLayout.NORTH);

        panel = new DuzenlemePaneli(y);
        panel.degisinceCalistir(dogrulamaZamanlayici::restart);
        JPanel sol = new JPanel(new BorderLayout(0, 12));
        solAkis = sol;
        sol.setOpaque(false);
        sol.add(belgeKarti(), BorderLayout.NORTH);
        sol.add(panel, BorderLayout.CENTER);
        JPanel solKenar = new JPanel(new BorderLayout());
        solKenar.setOpaque(false);
        solKenar.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 8));
        solKenar.add(sol, BorderLayout.CENTER);
        panelKaydirma = new JScrollPane(solKenar);
        panelKaydirma.setBorder(BorderFactory.createEmptyBorder());
        panelKaydirma.getVerticalScrollBar().setUnitIncrement(Kaydirma.ADIM);
        panelKaydirma.getVerticalScrollBar().setBlockIncrement(400);

        JPanel sag = new JPanel(new BorderLayout());
        sag.setBorder(BorderFactory.createEmptyBorder(16, 8, 16, 16));
        sag.add(yanPanel(), BorderLayout.CENTER);

        JSplitPane bolme = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, panelKaydirma, sag);
        bolme.setResizeWeight(0.62);
        bolme.setBorder(BorderFactory.createEmptyBorder());
        bolme.setContinuousLayout(true);
        add(bolme, BorderLayout.CENTER);

        add(eylemCubugu(), BorderLayout.SOUTH);

        wordOlustur.addActionListener(e -> olustur(false));
        pdfOlusturBtn.addActionListener(e -> olustur(true));
        pdfOlusturBtn.setToolTipText("Formu doğrudan PDF olarak kaydeder (bilgisayardaki Microsoft Word ile; yoksa LibreOffice)");
        stilleriUygula();
        setSize(new Dimension(1360, 900));
        setMinimumSize(new Dimension(1040, 680));
        setLocationRelativeTo(null);

        bosForm();
        adim(1);
        Saglayici bas = secilenSaglayici();
        if (bas != Saglayici.UYUMLU && ApiAnahtari.oku(bas).isEmpty()) {
            durum(bas.ad + " API anahtarı tanımlı değil — ☰ › Yapay zekâ ayarları (formu elle de doldurabilirsiniz)");
        }
    }

    // ---------------------------------------------------------------- arayüz parçaları

    /** Lacivert üst bant: simge + başlık, adım göstergesi, tema ve menü düğmeleri. */
    private JPanel ustBant() {
        JPanel bant = new JPanel(new BorderLayout(16, 0)) {
            @Override protected void paintComponent(java.awt.Graphics g) {
                g.setColor(Tema.ustBant());
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        bant.setBorder(BorderFactory.createEmptyBorder(10, 18, 10, 14));

        JLabel simge = new JLabel(new javax.swing.ImageIcon(Ikonlar.liste().isEmpty() ? new java.awt.image.BufferedImage(1, 1, 2)
                : Ikonlar.liste().get(Ikonlar.liste().size() - 1).getScaledInstance(34, 34, java.awt.Image.SCALE_SMOOTH)));
        JLabel baslik = new JLabel("YÖBF Otomatörü");
        baslik.setForeground(Color.WHITE);
        baslik.setFont(baslik.getFont().deriveFont(java.awt.Font.BOLD, 17f));
        JLabel alt = new JLabel("Yönetici Özet Bilgilendirme Formu");
        alt.setForeground(new Color(255, 255, 255, 170));
        alt.setFont(alt.getFont().deriveFont(12f));
        JPanel yazi = new JPanel(new java.awt.GridLayout(2, 1));
        yazi.setOpaque(false);
        yazi.add(baslik);
        yazi.add(alt);
        JPanel solTaraf = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        solTaraf.setOpaque(false);
        solTaraf.add(simge);
        solTaraf.add(yazi);
        bant.add(solTaraf, BorderLayout.WEST);

        JPanel orta = new JPanel(new java.awt.GridBagLayout());
        orta.setOpaque(false);
        String[] adlar = {"Belge", "Düzenle", "Onay ve kayıt"};
        JPanel satir = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        satir.setOpaque(false);
        for (int i = 0; i < 3; i++) {
            if (i > 0) {
                JLabel cizgi = new JLabel("──────");
                cizgi.setForeground(new Color(255, 255, 255, 90));
                cizgi.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
                satir.add(cizgi);
            }
            adimlar[i] = new AdimEtiketi((i + 1) + "   " + adlar[i]);
            adimlar[i].setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 14));
            adimlar[i].setFont(adimlar[i].getFont().deriveFont(java.awt.Font.BOLD, 12.5f));
            satir.add(adimlar[i]);
        }
        orta.add(satir);
        bant.add(orta, BorderLayout.CENTER);

        JPanel sagTaraf = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        sagTaraf.setOpaque(false);
        temaDugmesi.setToolTipText("Açık / koyu tema");
        temaDugmesi.addActionListener(e -> {
            boolean koyu = !Tema.koyu();
            Tema.degistir(koyu);
            tercihler.koyuTemaAyarla(koyu);
            stilleriUygula();
        });
        JButton menuDugmesi = new JButton(Simgeler.al("menu", 18, Color.WHITE));
        menuDugmesi.setToolTipText("Menü");
        javax.swing.JPopupMenu menu = menu();
        menuDugmesi.addActionListener(e -> menu.show(menuDugmesi, menuDugmesi.getWidth() - menu.getPreferredSize().width,
                menuDugmesi.getHeight() + 4));
        for (JButton b : new JButton[]{temaDugmesi, menuDugmesi}) {
            b.putClientProperty(com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE,
                    com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
            b.putClientProperty(com.formdev.flatlaf.FlatClientProperties.STYLE,
                    "hoverBackground:#FFFFFF22;pressedBackground:#FFFFFF33;arc:10");
            sagTaraf.add(b);
        }
        bant.add(sagTaraf, BorderLayout.EAST);
        return bant;
    }

    /** Adım göstergesi: 1 Belge → 2 Düzenle → 3 Onay ve kayıt. */
    private void adim(int n) {
        aktifAdim = n;
        for (int i = 0; i < 3; i++) {
            AdimEtiketi a = (AdimEtiketi) adimlar[i];
            boolean aktif = i + 1 == n, gecti = i + 1 < n;
            a.setText((gecti ? "✓" : String.valueOf(i + 1)) + "   " + a.getText().replaceFirst("^\\S+\\s+", ""));
            a.aktif = aktif;
            a.setForeground(aktif ? Tema.ustBant() : new Color(255, 255, 255, gecti ? 230 : 150));
            a.repaint();
        }
    }

    /** Adım göstergesi etiketi: etkin adım beyaz "hap" içinde. */
    private static final class AdimEtiketi extends JLabel {
        boolean aktif;

        AdimEtiketi(String metin) {
            super(metin);
            setOpaque(false);
        }

        @Override protected void paintComponent(java.awt.Graphics g0) {
            if (aktif) {
                java.awt.Graphics2D g = (java.awt.Graphics2D) g0.create();
                g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(Color.WHITE);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                g.dispose();
            }
            super.paintComponent(g0);
        }
    }

    /** Belge kartı: bırakma alanı, eklenen belgeler, form türü / model ve "Analiz Et". */
    private JComponent belgeKarti() {
        TransferHandler th = new TransferHandler() {
            @Override public boolean canImport(TransferSupport s) {
                boolean ok = s.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
                if (ok && birakmaAlani != null) birakmaAlani.vurgula(true);
                return ok;
            }

            @Override @SuppressWarnings("unchecked")
            public boolean importData(TransferSupport s) {
                if (birakmaAlani != null) birakmaAlani.vurgula(false);
                try {
                    List<File> l = (List<File>) s.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    dosyalariEkle(l.stream().map(File::toPath).toList());
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
        };
        birakmaAlani = new BirakmaAlani(this::belgeSec);
        birakmaAlani.setTransferHandler(th);

        belgeEtiketleri.setOpaque(false);
        belgeler.addListDataListener(new javax.swing.event.ListDataListener() {
            public void intervalAdded(javax.swing.event.ListDataEvent e) { etiketleriYenile(); }
            public void intervalRemoved(javax.swing.event.ListDataEvent e) { etiketleriYenile(); }
            public void contentsChanged(javax.swing.event.ListDataEvent e) { etiketleriYenile(); }
        });

        formTuruTercihi.addItem("Form türü: otomatik");
        y.ayarlar().formTurleri().forEach(f -> formTuruTercihi.addItem(f.kod()));
        saglayiciSecici.setSelectedItem(Saglayici.kodla(tercihler.saglayici()));
        saglayiciSecici.setToolTipText("Yapay zekâ sağlayıcısı — anahtarlar: ☰ › Yapay zekâ ayarları");
        saglayiciSecici.addActionListener(e -> {
            if (modelDolduruluyor) return;
            Saglayici s = secilenSaglayici();
            tercihler.saglayiciAyarla(s.kod);
            modelleriDoldur();
            if (s != Saglayici.UYUMLU && ApiAnahtari.oku(s).isEmpty()) {
                durum(s.ad + " için API anahtarı girilmemiş — ☰ › Yapay zekâ ayarları");
            } else {
                durum("Sağlayıcı: " + s.ad);
            }
        });
        modelSecici.setEditable(true);
        modelSecici.setToolTipText("Analizde kullanılacak model — listeden seçin veya model adını yazın");
        modelSecici.addActionListener(e -> {
            if (modelDolduruluyor) return;
            String m = secilenModel();
            if (!m.isBlank()) tercihler.modelAyarla(secilenSaglayici().kod, m);
        });
        modelleriDoldur();
        analizEt.addActionListener(e -> analizEt());
        analizIlerleme.setIndeterminate(true);
        analizIlerleme.setVisible(false);
        analizIlerleme.setPreferredSize(new Dimension(120, 6));

        JPanel kontroller = new JPanel(new java.awt.GridBagLayout());
        kontroller.setOpaque(false);
        java.awt.GridBagConstraints c = new java.awt.GridBagConstraints();
        c.insets = new java.awt.Insets(0, 0, 0, 8);
        c.anchor = java.awt.GridBagConstraints.WEST;
        kontroller.add(formTuruTercihi, c);
        kontroller.add(saglayiciSecici, c);
        kontroller.add(modelSecici, c);
        c.weightx = 1;
        c.fill = java.awt.GridBagConstraints.HORIZONTAL;
        c.insets = new java.awt.Insets(0, 8, 0, 12);
        kontroller.add(analizIlerleme, c);
        c.weightx = 0;
        c.fill = java.awt.GridBagConstraints.NONE;
        c.insets = new java.awt.Insets(0, 0, 0, 0);
        kontroller.add(analizEt, c);

        JPanel govde = new JPanel(new BorderLayout(0, 10));
        govde.setOpaque(false);
        govde.add(birakmaAlani, BorderLayout.NORTH);
        govde.add(belgeEtiketleri, BorderLayout.CENTER);
        govde.add(kontroller, BorderLayout.SOUTH);

        JButton temizle = new JButton("Temizle");
        temizle.putClientProperty(com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE,
                com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE_BORDERLESS);
        temizle.addActionListener(e -> belgeler.clear());
        Kart k = new Kart("Belgeler", "belge", govde)
                .aciklama("Birden fazla belge tek forma bağlanır: ayrı belgeler veya aynı işleme ait belgeler.")
                .sag(temizle);
        k.setTransferHandler(th);
        etiketleriYenile();
        return k;
    }

    private Saglayici secilenSaglayici() {
        Object o = saglayiciSecici.getSelectedItem();
        return o instanceof Saglayici s ? s : Saglayici.ANTHROPIC;
    }

    /** Seçili/yazılı model kimliği (listedeki seçeneğin görünen adı yazılıysa kimliğe çevrilir). */
    private String secilenModel() {
        Object o = modelSecici.getEditor().getItem();
        if (o instanceof legal.erk.yobf.config.Ayarlar.ModelSecenegi m) return m.id();
        String t = o == null ? "" : o.toString().trim();
        for (int i = 0; i < modelSecici.getItemCount(); i++) {
            if (modelSecici.getItemAt(i) instanceof legal.erk.yobf.config.Ayarlar.ModelSecenegi m && m.toString().equals(t)) {
                return m.id();
            }
        }
        return t;
    }

    /** Seçili sağlayıcının modellerini listeler ve son kullanılan modeli seçer. */
    private void modelleriDoldur() {
        modelDolduruluyor = true;
        try {
            Saglayici s = secilenSaglayici();
            var liste = y.ayarlar().modeller(s.kod);
            modelSecici.removeAllItems();
            liste.forEach(modelSecici::addItem);
            String varsayilan = s == Saglayici.ANTHROPIC ? y.ayarlar().model() : liste.isEmpty() ? "" : liste.get(0).id();
            String kayitli = tercihler.model(s.kod, varsayilan);
            boolean bulundu = false;
            for (int i = 0; i < modelSecici.getItemCount(); i++) {
                if (modelSecici.getItemAt(i) instanceof legal.erk.yobf.config.Ayarlar.ModelSecenegi m && m.id().equals(kayitli)) {
                    modelSecici.setSelectedIndex(i);
                    bulundu = true;
                }
            }
            if (!bulundu) modelSecici.setSelectedItem(kayitli);
        } finally {
            modelDolduruluyor = false;
        }
    }

    /** Yapay zekâ ayarları penceresi; kaydedilirse seçimler yenilenir. */
    private void ayarlariAc(Saglayici odak) {
        if (YapayZekaAyarlari.goster(this, y.ayarlar(), tercihler, odak)) {
            modelDolduruluyor = true;
            saglayiciSecici.setSelectedItem(Saglayici.kodla(tercihler.saglayici()));
            modelDolduruluyor = false;
            modelleriDoldur();
            durum("Yapay zekâ ayarları kaydedildi — sağlayıcı: " + secilenSaglayici().ad);
            Bildirim.goster(this, Diyalog.Tur.BASARI, "Yapay zekâ ayarları kaydedildi", "Sağlayıcı: " + secilenSaglayici().ad);
        }
    }

    /** Eklenen belgeleri kaldırılabilir "etiket"ler olarak gösterir. */
    private void etiketleriYenile() {
        belgeEtiketleri.removeAll();
        for (int i = 0; i < belgeler.size(); i++) {
            Path p = belgeler.get(i);
            JPanel etiket = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
            etiket.putClientProperty(com.formdev.flatlaf.FlatClientProperties.STYLE, "arc:999");
            etiket.setBackground(Tema.koyu() ? new Color(0x33405A) : new Color(0xE8EEF8));
            etiket.putClientProperty(Kart.OPAK, true);
            etiket.setBorder(BorderFactory.createEmptyBorder(1, 6, 1, 2));
            String ad = p.getFileName().toString();
            JLabel l = new JLabel(ad.length() > 46 ? ad.substring(0, 43) + "…" : ad,
                    Simgeler.vurgu(ad.toLowerCase().endsWith(".pdf") ? "pdf" : "word", 16), JLabel.LEFT);
            l.setToolTipText(p.toString());
            JButton x = new JButton(Simgeler.al("kapat", 12));
            x.putClientProperty(com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE,
                    com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
            x.setToolTipText("Kaldır");
            x.addActionListener(e -> belgeler.removeElement(p));
            etiket.add(l);
            etiket.add(x);
            belgeEtiketleri.add(etiket);
        }
        belgeEtiketleri.setVisible(!belgeler.isEmpty());
        if (birakmaAlani != null) birakmaAlani.kucuk(!belgeler.isEmpty());
        analizEt.setEnabled(!belgeler.isEmpty() && !analizIlerleme.isVisible());
        belgeEtiketleri.revalidate();
        belgeEtiketleri.repaint();
    }

    /** Alt eylem çubuğu: durum mesajı + Doğrula / Word / PDF. */
    private JPanel eylemCubugu() {
        JPanel alt = new JPanel(new BorderLayout(12, 0)) {
            @Override protected void paintComponent(java.awt.Graphics g) {
                g.setColor(Tema.kart());
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(Tema.kartKenar());
                g.drawLine(0, 0, getWidth(), 0);
            }
        };
        alt.setBorder(BorderFactory.createEmptyBorder(10, 18, 10, 16));
        durum.setForeground(Tema.ikincilYazi());
        alt.add(durum, BorderLayout.CENTER);
        dogrula.addActionListener(e -> canliDogrula());
        dogrula.putClientProperty(com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE,
                com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE_BORDERLESS);
        JPanel btn = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btn.setOpaque(false);
        btn.add(dogrula);
        btn.add(wordOlustur);
        btn.add(pdfOlusturBtn);
        alt.add(btn, BorderLayout.EAST);
        return alt;
    }

    /** Temaya bağlı renk ve simgeleri (yeniden) uygular. */
    private void stilleriUygula() {
        String v = String.format("#%06X", Tema.vurgu().getRGB() & 0xFFFFFF);
        for (JButton b : new JButton[]{analizEt, pdfOlusturBtn}) {
            b.putClientProperty(com.formdev.flatlaf.FlatClientProperties.STYLE,
                    "background:" + v + ";foreground:#FFFFFF;borderWidth:0;focusWidth:0;font:bold;"
                            + "hoverBackground:darken(" + v + ",6%);pressedBackground:darken(" + v + ",12%);"
                            + "disabledBackground:lighten(" + v + ",35%);disabledText:#FFFFFFCC;margin:7,16,7,16");
        }
        analizEt.setText("Analiz Et");
        analizEt.setIcon(Simgeler.al("analiz", 16, Color.WHITE));
        pdfOlusturBtn.setText("PDF oluştur");
        pdfOlusturBtn.setIcon(Simgeler.al("pdf", 16, Color.WHITE));
        wordOlustur.setText("Word olarak kaydet");
        wordOlustur.setIcon(Simgeler.al("word", 16));
        dogrula.setIcon(Simgeler.al("onay", 16));
        temaDugmesi.setIcon(Simgeler.al(Tema.koyu() ? "gunes" : "ay", 18, Color.WHITE));
        durum.setIcon(Simgeler.al("bilgi", 15));
        durum.setForeground(Tema.ikincilYazi());
        getRootPane().putClientProperty("JRootPane.titleBarBackground", Tema.ustBant());
        adim(aktifAdim);
        etiketleriYenile();
        repaint();
    }

    /** Eski menü çubuğunun içeriği, üst banttaki ☰ düğmesinden açılır. */
    private javax.swing.JPopupMenu menu() {
        javax.swing.JPopupMenu m = new javax.swing.JPopupMenu();
        JMenuItem ekle = new JMenuItem("Belge ekle…", Simgeler.al("yukle", 16));
        ekle.addActionListener(e -> belgeSec());
        JMenuItem bos = new JMenuItem("Yeni boş form", Simgeler.al("arti", 16));
        bos.addActionListener(e -> bosForm());
        JMenuItem wordMenu = new JMenuItem("Word olarak oluştur…", Simgeler.al("word", 16));
        wordMenu.addActionListener(e -> olustur(false));
        JMenuItem pdfMenu = new JMenuItem("PDF olarak oluştur…", Simgeler.al("pdf", 16));
        pdfMenu.addActionListener(e -> olustur(true));
        JMenuItem gunluk = new JMenuItem("İşlem günlüğünü aç");
        gunluk.addActionListener(e -> ac(IslemGunlugu.varsayilan().dosya()));
        JMenuItem anahtar = new JMenuItem("Yapay zekâ ayarları…", Simgeler.al("analiz", 16));
        anahtar.addActionListener(e -> ayarlariAc(null));
        JMenuItem yenile = new JMenuItem("Yapılandırmayı yeniden yükle");
        yenile.addActionListener(e -> yapilandirmayiYenile());
        JMenuItem klasor = new JMenuItem("Uygulama klasörünü aç");
        klasor.addActionListener(e -> ac(UygulamaYolu.kok()));
        JMenuItem hakkinda = new JMenuItem("Hakkında", Simgeler.al("bilgi", 16));
        hakkinda.addActionListener(e -> Diyalog.bilgi(this, Diyalog.Tur.BILGI, "YÖBF Otomatörü",
                "Yönetici Özet Bilgilendirme Formu hazırlama aracı.<br><br>"
                        + "<b>Yapay zekâ:</b> " + secilenSaglayici().ad + " · " + secilenModel()
                        + "<br><b>Uygulama klasörü:</b> " + UygulamaYolu.kok()
                        + "<br><b>Anahtarlar:</b> " + ApiAnahtari.dosya().getParent()));
        JMenuItem cik = new JMenuItem("Çıkış");
        cik.addActionListener(e -> dispose());
        m.add(ekle);
        m.add(bos);
        m.addSeparator();
        m.add(wordMenu);
        m.add(pdfMenu);
        m.addSeparator();
        m.add(anahtar);
        m.add(yenile);
        m.add(klasor);
        m.add(gunluk);
        m.addSeparator();
        m.add(hakkinda);
        m.add(cik);
        return m;
    }

    private JPanel yanPanel() {
        JList<Uyari> ul = new JList<>(uyarilar);
        ul.setCellRenderer((l, v, i, sec, odak) -> {
            JLabel r = new JLabel("<html><b>" + v.alan() + ":</b> " + v.mesaj() + "</html>",
                    Simgeler.al("uyari", 15, v.seviye() == Uyari.Seviye.KIRMIZI ? new Color(0xC62828) : new Color(0xB26A00)), JLabel.LEFT);
            r.setOpaque(true);
            r.setIconTextGap(8);
            r.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0),
                    BorderFactory.createEmptyBorder(6, 8, 6, 8)));
            r.setForeground(new Color(0x1F2937));
            r.setBackground(v.seviye() == Uyari.Seviye.KIRMIZI ? new Color(0xFDECEC) : new Color(0xFFF6DB));
            return r;
        });
        JScrollPane us = new JScrollPane(ul);
        us.setBorder(BorderFactory.createEmptyBorder());

        JList<String> dl = new JList<>(dikkat);
        dl.setCellRenderer((l, v, i, sec, odak) -> {
            JLabel r = new JLabel("<html><div style='width:300px'>" + v + "</div></html>", Simgeler.vurgu("fikir", 15), JLabel.LEFT);
            r.setIconTextGap(8);
            r.setVerticalTextPosition(JLabel.TOP);
            r.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
            return r;
        });
        JScrollPane ds = new JScrollPane(dl);
        ds.setBorder(BorderFactory.createEmptyBorder());

        onizleme = new OnizlemePaneli(y.ayarlar());
        onizleme.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        onizleme.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) { buyukOnizleme(); }
        });
        JPanel onizlemeSekmesi = new JPanel(new BorderLayout(0, 6));
        onizlemeSekmesi.add(onizleme, BorderLayout.CENTER);
        JLabel not = new JLabel("Yaklaşık görünüm — kesin hali Word çıktısındadır. Büyütmek için tıklayın.");
        not.setForeground(Tema.ikincilYazi());
        not.setFont(not.getFont().deriveFont(11.5f));
        onizlemeSekmesi.add(not, BorderLayout.SOUTH);

        javax.swing.JTabbedPane sekmeler = new javax.swing.JTabbedPane();
        sekmeler.addTab("Önizleme", Simgeler.al("goz", 16), onizlemeSekmesi);
        sekmeler.addTab("Uyarılar", Simgeler.al("uyari", 16), us);
        sekmeler.addTab("Dikkat hususları", Simgeler.al("fikir", 16), ds);
        sekmeler.putClientProperty(com.formdev.flatlaf.FlatClientProperties.STYLE,
                "tabAreaAlignment:leading;tabInsets:4,10,4,10;contentSeparatorHeight:1");
        uyarilar.addListDataListener(new javax.swing.event.ListDataListener() {
            void say() { sekmeler.setTitleAt(1, uyarilar.isEmpty() ? "Uyarılar" : "Uyarılar (" + uyarilar.size() + ")"); }
            public void intervalAdded(javax.swing.event.ListDataEvent e) { say(); }
            public void intervalRemoved(javax.swing.event.ListDataEvent e) { say(); }
            public void contentsChanged(javax.swing.event.ListDataEvent e) { say(); }
        });
        dikkat.addListDataListener(new javax.swing.event.ListDataListener() {
            void say() { sekmeler.setTitleAt(2, dikkat.isEmpty() ? "Dikkat hususları" : "Dikkat hususları (" + dikkat.size() + ")"); }
            public void intervalAdded(javax.swing.event.ListDataEvent e) { say(); }
            public void intervalRemoved(javax.swing.event.ListDataEvent e) { say(); }
            public void contentsChanged(javax.swing.event.ListDataEvent e) { say(); }
        });
        Kart k = new Kart("Önizleme ve kontrol", "goz", sekmeler);
        k.setPreferredSize(new Dimension(440, 400));
        onizlemeZamanlayici.setRepeats(false);
        return k;
    }

    // ---------------------------------------------------------------- işlemler

    private void belgeSec() {
        JFileChooser fc = new JFileChooser();
        fc.setMultiSelectionEnabled(true);
        fc.setFileFilter(new FileNameExtensionFilter("PDF ve Word belgeleri", "pdf", "docx"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            dosyalariEkle(java.util.Arrays.stream(fc.getSelectedFiles()).map(File::toPath).toList());
        }
    }

    private void dosyalariEkle(List<Path> l) {
        List<String> red = new ArrayList<>();
        for (Path p : l) {
            if (!BelgeOkuyucu.destekleniyor(p)) red.add(p.getFileName().toString());
            else if (!belgeler.contains(p)) belgeler.addElement(p);
        }
        if (!red.isEmpty()) {
            Bildirim.goster(this, Diyalog.Tur.UYARI, "Desteklenmeyen dosya",
                    "Yalnızca PDF ve DOCX eklenebilir: " + String.join(", ", red));
        }
    }

    private void bosForm() {
        FormVerisi f = new FormVerisi();
        f.tarih = TarihKontrol.bugun();
        f.imzayaSunanDepartman = y.ayarlar().imzayaSunanVarsayilan();
        f.ekOnayBaslik = y.ayarlar().imzaBlogu().ekOnayBaslik();
        f.ekOnayAcik = y.ayarlar().imzaBlogu().ekOnayAcikVarsayilan();
        kaynak = null;
        dikkat.clear();
        panel.yukle(f);
        canliDogrula();
    }

    private void analizEt() {
        if (belgeler.isEmpty()) {
            Bildirim.goster(this, Diyalog.Tur.BILGI, "Belge yok", "Analiz için önce en az bir PDF veya Word belgesi ekleyin.");
            return;
        }
        Saglayici saglayici = secilenSaglayici();
        String anahtar = ApiAnahtari.oku(saglayici).orElse(null);
        if (anahtar == null && saglayici != Saglayici.UYUMLU) {
            ayarlariAc(saglayici);
            saglayici = secilenSaglayici();
            anahtar = ApiAnahtari.oku(saglayici).orElse(null);
            if (anahtar == null && saglayici != Saglayici.UYUMLU) return;
        }
        Saglayici secSaglayici = saglayici;
        String tabanAdres = tercihler.tabanAdres(y.ayarlar().tabanAdres("uyumlu"));
        List<Path> dosyalar = new ArrayList<>();
        for (int i = 0; i < belgeler.size(); i++) dosyalar.add(belgeler.get(i));
        String tercih = formTuruTercihi.getSelectedIndex() > 0 ? (String) formTuruTercihi.getSelectedItem() : null;
        String apiAnahtari = anahtar;
        String modelId = secilenModel();

        analizEt.setEnabled(false);
        analizIlerleme.setVisible(true);
        ciktiDugmeleri(false);
        new SwingWorker<Object[], String>() {
            @Override protected Object[] doInBackground() throws Exception {
                Maskeleme mask = new Maskeleme(y.ayarlar().maskeleme());
                List<BelgeOkuyucu.Belge> maskeli = new ArrayList<>();
                StringBuilder kaynak = new StringBuilder();
                int maskelenen = 0;
                for (Path p : dosyalar) {
                    publish("Okunuyor: " + p.getFileName());
                    BelgeOkuyucu.Belge b = BelgeOkuyucu.oku(p);
                    List<String> sayfalar = new ArrayList<>();
                    for (String s : b.sayfalar()) {
                        Maskeleme.Sonuc m = mask.uygula(s);
                        maskelenen += m.maskelenenler().size();
                        sayfalar.add(m.metin());
                    }
                    BelgeOkuyucu.Belge mb = new BelgeOkuyucu.Belge(p, sayfalar);
                    maskeli.add(mb);
                    kaynak.append(mb.metin()).append('\n');
                }
                if (maskelenen > 0) publish(maskelenen + " kişisel veri maskelendi (modele gönderilmedi).");
                boolean ikiDilli = "YOBF_TR_EN".equals(tercih);
                try (OzetCikarici c = new OzetCikarici(y.ayarlar(), UygulamaYolu.prompt("ozet_prompt.md"),
                        IstemciFabrikasi.olustur(y.ayarlar(), secSaglayici, modelId, apiAnahtari, tabanAdres))) {
                    OzetSemasi o = c.ozetCikar(maskeli, ikiDilli, this::publish);
                    // Uzun belgede pahalı hazırlık arka planda, bir kez
                    return new Object[]{o, new Dogrulayici.Kaynak(kaynak.toString()), dosyalar.size()};
                }
            }

            @Override protected void process(List<String> l) {
                durum(l.get(l.size() - 1));
            }

            @Override protected void done() {
                analizIlerleme.setVisible(false);
                analizEt.setEnabled(!belgeler.isEmpty());
                ciktiDugmeleri(true);
                try {
                    Object[] r = get();
                    OzetSemasi o = (OzetSemasi) r[0];
                    AnaPencere.this.kaynak = (Dogrulayici.Kaynak) r[1];
                    FormHazirlayici h = new FormHazirlayici(y);
                    FormVerisi f = h.hazirla(o, (int) r[2]);
                    if (tercih != null) f.formTuru = tercih;
                    if (f.ikiDilli() && f.konuEn.isBlank()) {
                        durum("İki dilli form seçildi ama İngilizce konu boş — 'Analiz Et'i form türü YOBF_TR_EN seçili olarak tekrarlayın.");
                    }
                    FormVerisi mevcut = panel.oku();
                    f.talepEdenDepartman = mevcut.talepEdenDepartman;
                    f.anaImzacilar.addAll(mevcut.anaImzacilar);
                    f.imzasinaSunulacak.addAll(mevcut.imzasinaSunulacak);
                    f.ekOnayAcik = mevcut.ekOnayAcik;
                    f.ekOnayBaslik = mevcut.ekOnayBaslik;
                    f.ekOnayImzaci = mevcut.ekOnayImzaci;
                    f.gizliSatirlar.addAll(mevcut.gizliSatirlar);
                    f.ekSatirlar.addAll(mevcut.ekSatirlar);
                    f.notMetni = mevcut.notMetni;
                    f.notEtiketi = mevcut.notEtiketi;
                    if (!mevcut.grupBasligi.isBlank()) f.grupBasligi = mevcut.grupBasligi;
                    if (!mevcut.imzayaSunanDepartman.isBlank()) f.imzayaSunanDepartman = mevcut.imzayaSunanDepartman;
                    panel.yukle(f);
                    dikkat.clear();
                    o.dikkatHususlari().stream().filter(s -> s != null && !s.isBlank()).forEach(dikkat::addElement);
                    adim(2);
                    durum("Analiz tamamlandı. Güven — taraflar: " + yuzde(o.guven().taraflar())
                            + ", bedel: " + yuzde(o.guven().bedel()) + ". Alanları kontrol edip onaylayın.");
                    canliDogrula();
                } catch (ExecutionException e) {
                    Throwable t = e.getCause();
                    String baslik = t instanceof TaranmisBelgeException ? "Taranmış belge" : "Analiz hatası";
                    durum(baslik + ": " + t.getMessage());
                    Diyalog.bilgi(AnaPencere.this, t instanceof TaranmisBelgeException ? Diyalog.Tur.UYARI : Diyalog.Tur.HATA,
                            baslik, t.getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }.execute();
    }

    private static String yuzde(double d) {
        return Math.round(d * 100) + "%";
    }

    private List<Uyari> canliDogrula() {
        if (panel == null) return List.of();
        dogrulamaZamanlayici.stop();
        List<Uyari> l = new Dogrulayici(y.ayarlar(), y.sirketler()).dogrula(panel.oku(), kaynak);
        // Uyarılar değişmediyse listeyi ve alan çerçevelerini yeniden kurma (gereksiz yeniden çizim olmasın)
        if (!l.equals(sonUyarilar)) {
            sonUyarilar = List.copyOf(l);
            uyarilar.clear();
            l.stream().sorted((a, b) -> b.seviye().compareTo(a.seviye())).forEach(uyarilar::addElement);
            panel.isaretle(l);
        }
        onizlemeZamanlayici.restart();
        return l;
    }

    /** Önizlemede gösterilecek form: ekrandaki değerler + otomatik/elle seçilen logo. */
    private void onizlemeGuncelle() {
        if (panel == null || onizleme == null) return;
        FormVerisi f = panel.oku();
        LogoCozumleyici lc = new LogoCozumleyici(y.sirketler(), UygulamaYolu.logolar());
        f.logo = lc.coz(f.logoIcinSirketler(), panel.secilenLogo()).logo();
        onizleme.goster(f);
    }

    private void buyukOnizleme() {
        javax.swing.JDialog d = new javax.swing.JDialog(this, "Önizleme (yaklaşık)", false);
        OnizlemePaneli buyuk = new OnizlemePaneli(y.ayarlar());
        buyuk.goster(onizleme.form());
        d.add(buyuk);
        d.setSize(720, Math.min(1000, getGraphicsConfiguration().getBounds().height - 60));
        d.setLocationRelativeTo(this);
        d.setVisible(true);
    }

    private void ciktiDugmeleri(boolean acik) {
        wordOlustur.setEnabled(acik);
        pdfOlusturBtn.setEnabled(acik);
    }

    /**
     * Logo seçimi → kullanıcı onayı → kayıt. pdf=true: doğrudan PDF (Word/LibreOffice ile çevrilir,
     * istenirse .docx de yanına kaydedilir); pdf=false: Word (.docx), istenirse PDF de.
     */
    private void olustur(boolean pdf) {
        FormVerisi f = panel.oku();
        List<Uyari> u = canliDogrula();

        // 1) Logo kuralı (§4) — form oluşturulurken ilk çalışan kural
        LogoCozumleyici lc = new LogoCozumleyici(y.sirketler(), UygulamaYolu.logolar());
        LogoCozumleyici.Sonuc ls = lc.coz(f.logoIcinSirketler(), panel.secilenLogo());
        LogoCozumleyici.Aday secilen = ls.secili();
        if (ls.kullaniciyaSor()) {
            JList<LogoCozumleyici.Aday> secim = new JList<>(ls.adaylar().toArray(new LogoCozumleyici.Aday[0]));
            secim.setSelectedValue(ls.secili(), true);
            secim.setCellRenderer((l, v, i, sec, odak) -> {
                JLabel r = new JLabel("<html><b>" + (v.dosya() == null ? "(logo yok)" : v.dosya().getFileName()) + "</b><br>"
                        + String.join("<br>", v.sirketler().stream().map(legal.erk.yobf.config.SirketKaydi::unvan).toList()) + "</html>");
                if (v.dosya() != null) {
                    try {
                        java.awt.image.BufferedImage im = javax.imageio.ImageIO.read(v.dosya().toFile());
                        int h = 36, w = Math.min(170, im.getWidth() * h / im.getHeight());
                        r.setIcon(new javax.swing.ImageIcon(im.getScaledInstance(w, Math.max(1, im.getHeight() * w / im.getWidth()),
                                java.awt.Image.SCALE_SMOOTH)));
                    } catch (Exception ignored) {
                        // önizlemesiz
                    }
                }
                r.setIconTextGap(14);
                r.setOpaque(true);
                r.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
                r.setBackground(sec ? new Color(Tema.vurgu().getRed(), Tema.vurgu().getGreen(), Tema.vurgu().getBlue(), 40) : Tema.kart());
                r.setForeground(UIManagerRenk.yazi());
                return r;
            });
            JScrollPane sp = new JScrollPane(secim);
            sp.setPreferredSize(new Dimension(480, Math.min(300, 70 * ls.adaylar().size() + 6)));
            if (!Diyalog.onayla(this, Diyalog.Tur.SORU, "Logo seçimi",
                    "Taraflarda farklı logolara sahip birden fazla grup şirketi var. Formda hangi logo kullanılsın?",
                    sp, "Vazgeç", "Bu logoyu kullan") || secim.getSelectedValue() == null) {
                return;
            }
            secilen = secim.getSelectedValue();
        }
        f.logo = secilen.dosya();
        List<String> ekUyari = new ArrayList<>();
        ls.uyari().ifPresent(ekUyari::add);

        // 2) Kullanıcı onayı — hiçbir alan onaysız nihai çıktıya yazılmaz
        long kirmizi = u.stream().filter(x -> x.seviye() == Uyari.Seviye.KIRMIZI).count();
        long sari = u.size() - kirmizi;
        JPanel ozet = new JPanel(new java.awt.GridLayout(2, 2, 10, 10));
        ozet.add(ozetKutusu("Çıktı", pdf ? "PDF" : "Word (.docx)", pdf ? "pdf" : "word"));
        ozet.add(ozetKutusu("Form türü", f.formTuru, "belge"));
        ozet.add(ozetKutusu("Logo", f.logo == null ? "(yok)" : f.logo.getFileName().toString(), "goz"));
        ozet.add(ozetKutusu("İmzacı", f.anaImzacilar.size() + (f.ekOnayAcik ? " + 1 ek onay" : ""), "onay"));
        JPanel govde = new JPanel(new BorderLayout(0, 12));
        govde.add(ozet, BorderLayout.NORTH);
        JPanel uyariKutusu = new JPanel();
        uyariKutusu.setLayout(new javax.swing.BoxLayout(uyariKutusu, javax.swing.BoxLayout.Y_AXIS));
        if (kirmizi > 0) uyariKutusu.add(serit(Diyalog.Tur.HATA, kirmizi + " kırmızı uyarı var — kontrol etmeden devam etmeyin."));
        if (sari > 0) uyariKutusu.add(serit(Diyalog.Tur.UYARI, sari + " sarı uyarı var."));
        for (String s2 : ekUyari) uyariKutusu.add(serit(Diyalog.Tur.UYARI, s2));
        if (u.isEmpty() && ekUyari.isEmpty()) uyariKutusu.add(serit(Diyalog.Tur.BASARI, "Doğrulama uyarısı yok."));
        govde.add(uyariKutusu, BorderLayout.CENTER);
        javax.swing.JCheckBox ekKutu = pdf
                ? new javax.swing.JCheckBox("Word (.docx) dosyasını da yanına kaydet", tercihler.wordDeSakla())
                : new javax.swing.JCheckBox("PDF olarak da kaydet (Microsoft Word veya LibreOffice gerekir)",
                        tercihler.pdfDeOlustur());
        govde.add(ekKutu, BorderLayout.SOUTH);
        if (!Diyalog.onayla(this, kirmizi > 0 ? Diyalog.Tur.UYARI : Diyalog.Tur.SORU, "Formu onaylıyor musunuz?",
                "Ekrandaki tüm alanları kontrol ettiğinizi ve forma yazılmasını onaylıyor musunuz?",
                govde, "Vazgeç", pdf ? "Onayla ve PDF oluştur" : "Onayla ve kaydet")) {
            return;
        }
        boolean ek = ekKutu.isSelected();
        adim(3);
        if (pdf) tercihler.wordDeSaklaAyarla(ek);
        else tercihler.pdfDeOlusturAyarla(ek);

        // 3) Kayıt yeri
        FormHazirlayici h = new FormHazirlayici(y);
        LocalDate t = TarihKontrol.coz(f.tarih).orElse(LocalDate.now());
        String ad = FormYazici.dosyaAdi(f, h::grupSirketiMi, t);
        if (pdf) ad = ad.replaceFirst("(?i)\\.docx$", ".pdf");
        String uzanti = pdf ? ".pdf" : ".docx";
        JFileChooser fc = new JFileChooser(belgeler.isEmpty() ? null : belgeler.get(0).getParent().toFile());
        fc.setSelectedFile(new File(fc.getCurrentDirectory(), ad));
        fc.setFileFilter(pdf ? new FileNameExtensionFilter("PDF belgesi (.pdf)", "pdf")
                : new FileNameExtensionFilter("Word belgesi (.docx)", "docx"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path hedef = fc.getSelectedFile().toPath();
        if (!hedef.getFileName().toString().toLowerCase().endsWith(uzanti)) {
            hedef = hedef.resolveSibling(hedef.getFileName() + uzanti);
        }
        if (Files.exists(hedef) && !Diyalog.onayla(this, Diyalog.Tur.UYARI, "Dosya zaten var",
                "<b>" + hedef.getFileName() + "</b> bu klasörde zaten var. Üzerine yazılsın mı?", null, "Vazgeç", "Üzerine yaz")) {
            return;
        }

        // 4) Yaz
        try {
            if (pdf) {
                Path docxYanina = PdfDonusturucuYardim.docxYolu(hedef);
                Path gecici = Files.createTempDirectory("yobf");
                Path docx = gecici.resolve(docxYanina.getFileName());
                new FormYazici(y.ayarlar()).yaz(f, docx);
                gunluge(f, hedef);
                pdfeCevir(docx, hedef, ek ? docxYanina : null, gecici);
            } else {
                new FormYazici(y.ayarlar()).yaz(f, hedef);
                gunluge(f, hedef);
                durum("Kaydedildi: " + hedef);
                if (ek) {
                    pdfeCevir(hedef, legal.erk.yobf.render.PdfDonusturucu.pdfYolu(hedef), hedef, null);
                } else {
                    Path h2 = hedef;
                    Bildirim.goster(this, Diyalog.Tur.BASARI, "Word belgesi kaydedildi", h2.getFileName().toString(),
                            eylemler("Belgeyi aç", () -> ac(h2), "Klasörü aç", () -> ac(h2.toAbsolutePath().getParent())));
                }
            }
        } catch (Exception e) {
            Diyalog.bilgi(this, Diyalog.Tur.HATA, "Dosya yazılamadı", e.getMessage());
        }
    }

    /** Onay penceresindeki özet kutucuğu (simge + küçük başlık + değer). */
    private static JPanel ozetKutusu(String baslik, String deger, String simge) {
        JPanel k = new JPanel(new BorderLayout(10, 0));
        k.setOpaque(true);
        k.setBackground(Tema.koyu() ? new Color(0x2E3238) : new Color(0xF3F6FB));
        k.putClientProperty(Kart.OPAK, true);
        k.putClientProperty(com.formdev.flatlaf.FlatClientProperties.STYLE, "arc:12");
        k.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        k.add(new JLabel(Simgeler.vurgu(simge, 20)), BorderLayout.WEST);
        JPanel y = new JPanel(new BorderLayout(0, 1));
        y.setOpaque(false);
        JLabel b = new JLabel(baslik);
        b.setForeground(Tema.ikincilYazi());
        b.setFont(b.getFont().deriveFont(11.5f));
        JLabel d = new JLabel(deger);
        d.setFont(d.getFont().deriveFont(java.awt.Font.BOLD));
        y.add(b, BorderLayout.NORTH);
        y.add(d, BorderLayout.CENTER);
        k.add(y, BorderLayout.CENTER);
        return k;
    }

    /** Renkli bilgi şeridi (uyarı / başarı). */
    private static JPanel serit(Diyalog.Tur tur, String metin) {
        Color r = tur.renk();
        JPanel p = new JPanel(new BorderLayout(8, 0));
        p.setOpaque(true);
        p.setBackground(new Color(r.getRed(), r.getGreen(), r.getBlue(), Tema.koyu() ? 50 : 26));
        p.putClientProperty(Kart.OPAK, true);
        p.putClientProperty(com.formdev.flatlaf.FlatClientProperties.STYLE, "arc:10");
        p.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        p.add(new JLabel(Simgeler.al(tur.simge, 16, r)), BorderLayout.WEST);
        p.add(new JLabel("<html>" + metin + "</html>"), BorderLayout.CENTER);
        p.setAlignmentX(0f);
        JPanel dis = new JPanel(new BorderLayout());
        dis.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        dis.add(p);
        dis.setAlignmentX(0f);
        return dis;
    }

    /** Bildirim eylemleri (sıralı): ad1, eylem1, ad2, eylem2 … */
    private static java.util.Map<String, Runnable> eylemler(String a, Runnable ea, String b, Runnable eb) {
        java.util.Map<String, Runnable> m = new java.util.LinkedHashMap<>();
        m.put(a, ea);
        m.put(b, eb);
        return m;
    }

    private static java.util.Map<String, Runnable> eylemler(String a, Runnable ea, String b, Runnable eb, String c, Runnable ec) {
        java.util.Map<String, Runnable> m = eylemler(a, ea, b, eb);
        m.put(c, ec);
        return m;
    }

    /** UIManager yazı rengi (koyu/açık temaya uyar). */
    private static final class UIManagerRenk {
        static Color yazi() { return javax.swing.UIManager.getColor("Label.foreground"); }
    }

    /** PDF hedefinin yanındaki .docx adı. */
    private static final class PdfDonusturucuYardim {
        static Path docxYolu(Path pdf) {
            String n = pdf.getFileName().toString().replaceFirst("(?i)\\.pdf$", "") + ".docx";
            return pdf.toAbsolutePath().resolveSibling(n);
        }
    }

    private void gunluge(FormVerisi f, Path hedef) {
        List<String> adlar = new ArrayList<>();
        for (int i = 0; i < belgeler.size(); i++) adlar.add(belgeler.get(i).getFileName().toString());
        if (adlar.isEmpty()) adlar.add(hedef.getFileName().toString());
        List<String> bedeller = f.sozlesmeler.stream().flatMap(g -> g.bedeller.stream()).map(Bedel::metin)
                .filter(s -> !s.isBlank()).toList();
        try {
            IslemGunlugu.varsayilan().ekle(adlar, f.formTuru, f.logoIcinSirketler(), bedeller);
        } catch (Exception e) {
            durum("Uyarı: işlem günlüğü yazılamadı — " + e.getMessage());
        }
    }

    /**
     * .docx → PDF (arka planda). Oluşan PDF hedefPdf'e taşınır.
     * @param saklanacakDocx PDF ile birlikte kalacak Word dosyası (null: Word dosyası saklanmaz)
     * @param geciciKlasor   işlem sonunda silinecek geçici klasör (null: yok)
     */
    private void pdfeCevir(Path docx, Path hedefPdf, Path saklanacakDocx, Path geciciKlasor) {
        ciktiDugmeleri(false);
        durum("PDF oluşturuluyor (Word arka planda açılıyor, birkaç saniye sürebilir)…");
        new SwingWorker<Path, Void>() {
            @Override protected Path doInBackground() throws Exception {
                var sonuc = legal.erk.yobf.render.PdfDonusturucu.donustur(docx);
                if (sonuc.isEmpty()) return null;
                Path pdf = sonuc.get().pdf();
                if (!pdf.toAbsolutePath().equals(hedefPdf.toAbsolutePath())) {
                    Files.move(pdf, hedefPdf, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                if (saklanacakDocx != null && !saklanacakDocx.toAbsolutePath().equals(docx.toAbsolutePath())) {
                    Files.copy(docx, saklanacakDocx, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                return hedefPdf;
            }

            @Override protected void done() {
                ciktiDugmeleri(true);
                try {
                    Path pdf = get();
                    if (pdf == null) {
                        pdfYapilamadi("bilgisayarda Microsoft Word veya LibreOffice bulunamadı.", docx, hedefPdf, saklanacakDocx);
                        return;
                    }
                    durum("Kaydedildi: " + pdf + (saklanacakDocx != null ? " (+ " + saklanacakDocx.getFileName() + ")" : ""));
                    Bildirim.goster(AnaPencere.this, Diyalog.Tur.BASARI, "PDF oluşturuldu",
                            pdf.getFileName() + (saklanacakDocx != null ? " ve " + saklanacakDocx.getFileName() : ""),
                            saklanacakDocx != null
                                    ? eylemler("PDF'i aç", () -> ac(pdf), "Word'ü aç", () -> ac(saklanacakDocx),
                                            "Klasörü aç", () -> ac(pdf.toAbsolutePath().getParent()))
                                    : eylemler("PDF'i aç", () -> ac(pdf), "Klasörü aç", () -> ac(pdf.toAbsolutePath().getParent())));
                } catch (ExecutionException e) {
                    pdfYapilamadi(e.getCause().getMessage(), docx, hedefPdf, saklanacakDocx);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    if (geciciKlasor != null) sil(geciciKlasor);
                }
            }
        }.execute();
    }

    /** PDF'e çevrilemedi: Word dosyası kaydedilmediyse kaydetmeyi önerir. */
    private void pdfYapilamadi(String neden, Path docx, Path hedefPdf, Path saklanacakDocx) {
        durum("PDF oluşturulamadı: " + neden);
        boolean wordVar = saklanacakDocx != null && Files.exists(saklanacakDocx);
        if (wordVar) {
            Diyalog.bilgi(this, Diyalog.Tur.UYARI, "PDF oluşturulamadı",
                    "Word belgesi kaydedildi: <b>" + saklanacakDocx.getFileName() + "</b><br><br>" + neden
                            + "<br><br>PDF'i Word'de <i>Farklı Kaydet → PDF</i> ile alabilirsiniz.");
            return;
        }
        Path yedek = PdfDonusturucuYardim.docxYolu(hedefPdf);
        if (Diyalog.onayla(this, Diyalog.Tur.UYARI, "PDF oluşturulamadı",
                neden + "<br><br>Form Word (.docx) olarak kaydedilsin mi?<br><b>" + yedek.getFileName() + "</b>",
                null, "Vazgeç", "Word olarak kaydet")) {
            try {
                Files.copy(docx, yedek, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                durum("Word olarak kaydedildi: " + yedek);
                ac(yedek);
            } catch (Exception e) {
                Diyalog.bilgi(this, Diyalog.Tur.HATA, "Kaydedilemedi", e.getMessage());
            }
        }
    }

    private static void sil(Path klasor) {
        try (var s = Files.walk(klasor)) {
            s.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (Exception ignored) { /* geçici dosya */ }
            });
        } catch (Exception ignored) {
            // geçici klasör silinemezse sistem temizler
        }
    }

    private void yapilandirmayiYenile() {
        try {
            Yapilandirma yeni = Yapilandirma.yukle();
            FormVerisi f = panel.oku();
            DuzenlemePaneli eskiPanel = panel;
            this.y = yeni;
            panel = new DuzenlemePaneli(yeni);
            sonUyarilar = null;
            panel.degisinceCalistir(dogrulamaZamanlayici::restart);
            solAkis.remove(eskiPanel);
            solAkis.add(panel, BorderLayout.CENTER);
            solAkis.revalidate();
            panel.yukle(f);
            canliDogrula();
            modelleriDoldur();
            durum("Yapılandırma yeniden yüklendi.");
            Bildirim.goster(this, Diyalog.Tur.BASARI, "Yapılandırma yeniden yüklendi", "config/ klasöründeki değişiklikler uygulandı.");
        } catch (Exception e) {
            Diyalog.bilgi(this, Diyalog.Tur.HATA, "Yapılandırma hatası", e.getMessage());
        }
    }

    private void ac(Path p) {
        try {
            if (Files.notExists(p)) {
                Bildirim.goster(this, Diyalog.Tur.UYARI, "Dosya bulunamadı", p.toString());
                return;
            }
            Desktop.getDesktop().open(p.toFile());
        } catch (Exception e) {
            Bildirim.goster(this, Diyalog.Tur.HATA, "Açılamadı", p.getFileName() + ": " + e.getMessage());
        }
    }

    private void durum(String s) {
        SwingUtilities.invokeLater(() -> durum.setText(s));
    }
}
