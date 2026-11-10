package legal.erk.yobf.ui;

import legal.erk.yobf.ai.ApiAnahtari;
import legal.erk.yobf.ai.ClaudeIstemci;
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
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
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
    private final JList<Path> belgeListesi = new JList<>(belgeler);
    private final DefaultListModel<Uyari> uyarilar = new DefaultListModel<>();
    private final DefaultListModel<String> dikkat = new DefaultListModel<>();
    private final JLabel durum = new JLabel(" ");
    private final JButton analizEt = new JButton("Analiz Et");
    private final JButton wordOlustur = new JButton("Onayla ve Word Oluştur…");
    private final JComboBox<String> formTuruTercihi = new JComboBox<>();
    private DuzenlemePaneli panel;
    private JScrollPane panelKaydirma;
    /** Son analizdeki maskeli kaynak metin (doğrulama için). Maskelenen değerler modele gitmez. */
    private String kaynakMetin;

    public AnaPencere(Yapilandirma y) {
        super("YÖBF Otomatörü");
        this.y = y;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setJMenuBar(menu());
        setLayout(new BorderLayout(6, 6));

        add(belgeAlani(), BorderLayout.NORTH);

        panel = new DuzenlemePaneli(y);
        panel.degisinceCalistir(this::canliDogrula);
        panelKaydirma = new JScrollPane(panel);
        panelKaydirma.getVerticalScrollBar().setUnitIncrement(16);
        panelKaydirma.setBorder(BorderFactory.createTitledBorder("Düzenleme"));

        JSplitPane bolme = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, panelKaydirma, yanPanel());
        bolme.setResizeWeight(0.7);
        add(bolme, BorderLayout.CENTER);

        JPanel alt = new JPanel(new BorderLayout());
        alt.setBorder(BorderFactory.createEmptyBorder(4, 8, 6, 8));
        alt.add(durum, BorderLayout.CENTER);
        JButton dogrula = new JButton("Doğrula");
        dogrula.addActionListener(e -> canliDogrula());
        JPanel btn = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        btn.add(dogrula);
        btn.add(wordOlustur);
        alt.add(btn, BorderLayout.EAST);
        add(alt, BorderLayout.SOUTH);

        wordOlustur.addActionListener(e -> wordOlustur());
        setSize(new Dimension(1280, 860));
        setLocationRelativeTo(null);

        bosForm();
        if (ApiAnahtari.oku().isEmpty()) {
            durum("API anahtarı tanımlı değil — Ayarlar > API Anahtarı… (formu elle de doldurabilirsiniz)");
        }
    }

    // ---------------------------------------------------------------- arayüz parçaları

    private JMenuBar menu() {
        JMenuBar mb = new JMenuBar();
        JMenu dosya = new JMenu("Dosya");
        JMenuItem ekle = new JMenuItem("Belge Ekle…");
        ekle.addActionListener(e -> belgeSec());
        JMenuItem bos = new JMenuItem("Yeni Boş Form");
        bos.addActionListener(e -> bosForm());
        JMenuItem gunluk = new JMenuItem("İşlem Günlüğünü Aç");
        gunluk.addActionListener(e -> ac(IslemGunlugu.varsayilan().dosya()));
        JMenuItem cik = new JMenuItem("Çıkış");
        cik.addActionListener(e -> dispose());
        dosya.add(ekle);
        dosya.add(bos);
        dosya.addSeparator();
        dosya.add(gunluk);
        dosya.addSeparator();
        dosya.add(cik);

        JMenu ayar = new JMenu("Ayarlar");
        JMenuItem anahtar = new JMenuItem("API Anahtarı…");
        anahtar.addActionListener(e -> anahtarGir());
        JMenuItem yenile = new JMenuItem("Yapılandırmayı Yeniden Yükle");
        yenile.addActionListener(e -> yapilandirmayiYenile());
        JMenuItem klasor = new JMenuItem("Uygulama Klasörünü Aç");
        klasor.addActionListener(e -> ac(UygulamaYolu.kok()));
        ayar.add(anahtar);
        ayar.add(yenile);
        ayar.add(klasor);

        JMenu yardim = new JMenu("Yardım");
        JMenuItem hakkinda = new JMenuItem("Hakkında");
        hakkinda.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "YÖBF Otomatörü\nYönetici Özet Bilgilendirme Formu hazırlama aracı.\n\n"
                        + "Model: " + y.ayarlar().model() + "\nUygulama klasörü: " + UygulamaYolu.kok()
                        + "\nAPI anahtarı dosyası: " + ApiAnahtari.dosya(),
                "Hakkında", JOptionPane.INFORMATION_MESSAGE));
        yardim.add(hakkinda);

        mb.add(dosya);
        mb.add(ayar);
        mb.add(yardim);
        return mb;
    }

    private JPanel belgeAlani() {
        JPanel p = new JPanel(new BorderLayout(6, 6));
        p.setBorder(BorderFactory.createTitledBorder("Belgeler — PDF / DOCX dosyalarını buraya sürükleyin "
                + "(birden fazla belge = tek forma bağlı birden fazla sözleşme)"));
        belgeListesi.setVisibleRowCount(3);
        belgeListesi.setCellRenderer((l, v, i, sec, odak) -> {
            JLabel r = new JLabel((i + 1) + ". " + v.getFileName() + "   (" + v.getParent() + ")");
            r.setOpaque(true);
            r.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
            r.setBackground(sec ? l.getSelectionBackground() : l.getBackground());
            r.setForeground(sec ? l.getSelectionForeground() : l.getForeground());
            return r;
        });
        TransferHandler th = new TransferHandler() {
            @Override public boolean canImport(TransferSupport s) {
                return s.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override @SuppressWarnings("unchecked")
            public boolean importData(TransferSupport s) {
                try {
                    List<File> l = (List<File>) s.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    dosyalariEkle(l.stream().map(File::toPath).toList());
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
        };
        belgeListesi.setTransferHandler(th);
        p.setTransferHandler(th);
        JScrollPane sp = new JScrollPane(belgeListesi);
        sp.setPreferredSize(new Dimension(600, 80));
        sp.setTransferHandler(th);
        p.add(sp, BorderLayout.CENTER);

        JButton ekle = new JButton("Dosya Ekle…");
        ekle.addActionListener(e -> belgeSec());
        JButton kaldir = new JButton("Kaldır");
        kaldir.addActionListener(e -> {
            for (Path x : belgeListesi.getSelectedValuesList()) belgeler.removeElement(x);
        });
        JButton temizle = new JButton("Temizle");
        temizle.addActionListener(e -> belgeler.clear());

        formTuruTercihi.addItem("Form türü: otomatik öner");
        y.ayarlar().formTurleri().forEach(f -> formTuruTercihi.addItem(f.kod()));
        analizEt.addActionListener(e -> analizEt());

        JPanel btn = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        btn.add(ekle);
        btn.add(kaldir);
        btn.add(temizle);
        btn.add(formTuruTercihi);
        btn.add(analizEt);
        p.add(btn, BorderLayout.SOUTH);
        return p;
    }

    private JPanel yanPanel() {
        JList<Uyari> ul = new JList<>(uyarilar);
        ul.setCellRenderer((l, v, i, sec, odak) -> {
            JLabel r = new JLabel("<html><b>" + v.alan() + ":</b> " + v.mesaj() + "</html>");
            r.setOpaque(true);
            r.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
            r.setForeground(Color.BLACK);
            r.setBackground(v.seviye() == Uyari.Seviye.KIRMIZI ? new Color(0xFFCDD2) : new Color(0xFFF59D));
            return r;
        });
        JScrollPane us = new JScrollPane(ul);
        us.setBorder(BorderFactory.createTitledBorder("Doğrulama uyarıları"));

        JList<String> dl = new JList<>(dikkat);
        dl.setCellRenderer((l, v, i, sec, odak) -> {
            JLabel r = new JLabel("<html><div style='width:260px'>• " + v + "</div></html>");
            r.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
            return r;
        });
        JScrollPane ds = new JScrollPane(dl);
        ds.setBorder(BorderFactory.createTitledBorder("Dikkat edilmesi önerilen hususlar (forma yazılmaz)"));

        JSplitPane s = new JSplitPane(JSplitPane.VERTICAL_SPLIT, us, ds);
        s.setResizeWeight(0.55);
        JPanel p = new JPanel(new BorderLayout());
        p.add(s, BorderLayout.CENTER);
        p.setPreferredSize(new Dimension(360, 400));
        return p;
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
            JOptionPane.showMessageDialog(this, "Yalnızca PDF ve DOCX desteklenir:\n" + String.join("\n", red),
                    "Desteklenmeyen dosya", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void bosForm() {
        FormVerisi f = new FormVerisi();
        f.tarih = TarihKontrol.bugun();
        f.imzayaSunanDepartman = y.ayarlar().imzayaSunanVarsayilan();
        f.ekOnayBaslik = y.ayarlar().imzaBlogu().ekOnayBaslik();
        f.ekOnayAcik = y.ayarlar().imzaBlogu().ekOnayAcikVarsayilan();
        kaynakMetin = null;
        dikkat.clear();
        panel.yukle(f);
        canliDogrula();
    }

    private void analizEt() {
        if (belgeler.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Önce en az bir belge ekleyin.", "Analiz", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String anahtar = ApiAnahtari.oku().orElse(null);
        if (anahtar == null) {
            anahtarGir();
            anahtar = ApiAnahtari.oku().orElse(null);
            if (anahtar == null) return;
        }
        List<Path> dosyalar = new ArrayList<>();
        for (int i = 0; i < belgeler.size(); i++) dosyalar.add(belgeler.get(i));
        String tercih = formTuruTercihi.getSelectedIndex() > 0 ? (String) formTuruTercihi.getSelectedItem() : null;
        String apiAnahtari = anahtar;

        analizEt.setEnabled(false);
        wordOlustur.setEnabled(false);
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
                try (ClaudeIstemci c = new ClaudeIstemci(y.ayarlar(), UygulamaYolu.prompt("ozet_prompt.md"), apiAnahtari)) {
                    OzetSemasi o = c.ozetCikar(maskeli, ikiDilli, this::publish);
                    return new Object[]{o, kaynak.toString(), dosyalar.size()};
                }
            }

            @Override protected void process(List<String> l) {
                durum(l.get(l.size() - 1));
            }

            @Override protected void done() {
                analizEt.setEnabled(true);
                wordOlustur.setEnabled(true);
                try {
                    Object[] r = get();
                    OzetSemasi o = (OzetSemasi) r[0];
                    kaynakMetin = (String) r[1];
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
                    if (!mevcut.imzayaSunanDepartman.isBlank()) f.imzayaSunanDepartman = mevcut.imzayaSunanDepartman;
                    panel.yukle(f);
                    dikkat.clear();
                    o.dikkatHususlari().stream().filter(s -> s != null && !s.isBlank()).forEach(dikkat::addElement);
                    durum("Analiz tamamlandı. Güven — taraflar: " + yuzde(o.guven().taraflar())
                            + ", bedel: " + yuzde(o.guven().bedel()) + ". Alanları kontrol edip onaylayın.");
                    canliDogrula();
                } catch (ExecutionException e) {
                    Throwable t = e.getCause();
                    String baslik = t instanceof TaranmisBelgeException ? "Taranmış belge" : "Analiz hatası";
                    durum(baslik + ": " + t.getMessage());
                    JOptionPane.showMessageDialog(AnaPencere.this, t.getMessage(), baslik,
                            t instanceof TaranmisBelgeException ? JOptionPane.WARNING_MESSAGE : JOptionPane.ERROR_MESSAGE);
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
        List<Uyari> l = new Dogrulayici(y.ayarlar(), y.sirketler()).dogrula(panel.oku(), kaynakMetin);
        uyarilar.clear();
        l.stream().sorted((a, b) -> b.seviye().compareTo(a.seviye())).forEach(uyarilar::addElement);
        panel.isaretle(l);
        return l;
    }

    private void wordOlustur() {
        FormVerisi f = panel.oku();
        List<Uyari> u = canliDogrula();

        // 1) Logo kuralı (§4) — form oluşturulurken ilk çalışan kural
        LogoCozumleyici lc = new LogoCozumleyici(y.sirketler(), UygulamaYolu.logolar());
        LogoCozumleyici.Sonuc ls = lc.coz(f.logoIcinSirketler(), panel.secilenLogo());
        LogoCozumleyici.Aday secilen = ls.secili();
        if (ls.kullaniciyaSor()) {
            Object s = JOptionPane.showInputDialog(this,
                    "Taraflarda farklı logolara sahip birden fazla grup şirketi var. Hangi logo kullanılsın?",
                    "Logo seçimi", JOptionPane.QUESTION_MESSAGE, null, ls.adaylar().toArray(), ls.secili());
            if (s == null) return;
            secilen = (LogoCozumleyici.Aday) s;
        }
        f.logo = secilen.dosya();
        List<String> ekUyari = new ArrayList<>();
        ls.uyari().ifPresent(ekUyari::add);

        // 2) Kullanıcı onayı — hiçbir alan onaysız nihai çıktıya yazılmaz
        StringBuilder m = new StringBuilder("<html><body style='width:420px'>");
        long kirmizi = u.stream().filter(x -> x.seviye() == Uyari.Seviye.KIRMIZI).count();
        long sari = u.size() - kirmizi;
        if (kirmizi > 0) m.append("<p style='color:#C62828'><b>").append(kirmizi).append(" kırmızı uyarı var.</b></p>");
        if (sari > 0) m.append("<p style='color:#8D6E00'>").append(sari).append(" sarı uyarı var.</p>");
        for (String s : ekUyari) m.append("<p>").append(s).append("</p>");
        m.append("<p>Form türü: <b>").append(f.formTuru).append("</b><br>Logo: ")
                .append(f.logo == null ? "(yok)" : f.logo.getFileName()).append("<br>İmzacı: ")
                .append(f.anaImzacilar.size()).append(f.ekOnayAcik ? " + 1 ek onay" : "").append("</p>");
        m.append("<p>Ekrandaki tüm alanları kontrol ettiğinizi ve forma yazılmasını onaylıyor musunuz?</p></body></html>");
        int onay = JOptionPane.showConfirmDialog(this, m.toString(), "Onay",
                JOptionPane.YES_NO_OPTION, kirmizi > 0 ? JOptionPane.WARNING_MESSAGE : JOptionPane.QUESTION_MESSAGE);
        if (onay != JOptionPane.YES_OPTION) return;

        // 3) Kaydet
        FormHazirlayici h = new FormHazirlayici(y);
        LocalDate t = TarihKontrol.coz(f.tarih).orElse(LocalDate.now());
        String ad = FormYazici.dosyaAdi(f, h::grupSirketiMi, t);
        JFileChooser fc = new JFileChooser(belgeler.isEmpty() ? null : belgeler.get(0).getParent().toFile());
        fc.setSelectedFile(new File(fc.getCurrentDirectory(), ad));
        fc.setFileFilter(new FileNameExtensionFilter("Word belgesi (.docx)", "docx"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path hedef = fc.getSelectedFile().toPath();
        if (!hedef.getFileName().toString().toLowerCase().endsWith(".docx")) {
            hedef = hedef.resolveSibling(hedef.getFileName() + ".docx");
        }
        if (Files.exists(hedef) && JOptionPane.showConfirmDialog(this, hedef.getFileName() + " zaten var. Üzerine yazılsın mı?",
                "Kaydet", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            new FormYazici(y.ayarlar()).yaz(f, hedef);
            List<String> adlar = new ArrayList<>();
            for (int i = 0; i < belgeler.size(); i++) adlar.add(belgeler.get(i).getFileName().toString());
            if (adlar.isEmpty()) adlar.add(hedef.getFileName().toString());
            List<String> taraflar = f.logoIcinSirketler();
            List<String> bedeller = f.sozlesmeler.stream().flatMap(g -> g.bedeller.stream()).map(Bedel::metin)
                    .filter(s -> !s.isBlank()).toList();
            try {
                IslemGunlugu.varsayilan().ekle(adlar, f.formTuru, taraflar, bedeller);
            } catch (Exception e) {
                durum("Uyarı: işlem günlüğü yazılamadı — " + e.getMessage());
            }
            durum("Kaydedildi: " + hedef);
            if (JOptionPane.showConfirmDialog(this, "Form oluşturuldu:\n" + hedef + "\n\nŞimdi açılsın mı?", "Tamamlandı",
                    JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE) == JOptionPane.YES_OPTION) {
                ac(hedef);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Word dosyası yazılamadı:\n" + e.getMessage(), "Hata", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void anahtarGir() {
        JPasswordField pf = new JPasswordField(40);
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.add(new JLabel("<html>Anthropic API anahtarı<br><small>Şuraya kaydedilir: " + ApiAnahtari.dosya()
                + "<br>(ANTHROPIC_API_KEY ortam değişkeni tanımlıysa o önceliklidir)</small></html>"), BorderLayout.NORTH);
        p.add(pf, BorderLayout.CENTER);
        if (JOptionPane.showConfirmDialog(this, p, "API Anahtarı", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            String k = new String(pf.getPassword()).trim();
            if (k.isEmpty()) return;
            try {
                ApiAnahtari.kaydet(k);
                durum("API anahtarı kaydedildi.");
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Kaydedilemedi: " + e.getMessage(), "Hata", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void yapilandirmayiYenile() {
        try {
            Yapilandirma yeni = Yapilandirma.yukle();
            FormVerisi f = panel.oku();
            this.y = yeni;
            panel = new DuzenlemePaneli(yeni);
            panel.degisinceCalistir(this::canliDogrula);
            panelKaydirma.setViewportView(panel);
            panel.yukle(f);
            canliDogrula();
            durum("Yapılandırma yeniden yüklendi.");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Yapılandırma hatası", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void ac(Path p) {
        try {
            if (Files.notExists(p)) {
                JOptionPane.showMessageDialog(this, "Henüz yok: " + p);
                return;
            }
            Desktop.getDesktop().open(p.toFile());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Açılamadı: " + p + "\n" + e.getMessage());
        }
    }

    private void durum(String s) {
        SwingUtilities.invokeLater(() -> durum.setText(s));
    }
}
