package legal.erk.yobf.ui;

import legal.erk.yobf.config.Ayarlar;
import legal.erk.yobf.config.Imzaci;
import legal.erk.yobf.config.Imzacilar;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.DropMode;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.TransferHandler;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.util.ArrayList;
import java.util.List;

/**
 * UYGUNDUR bloğu: 1–5 ana imzacı (sürükle-bırak ile sıralanır) + opsiyonel tek imzacılı ek onay bloğu (§6).
 */
public class ImzaciPaneli extends JPanel {

    private final Imzacilar kayitlar;
    private final Ayarlar.ImzaBlogu ib;
    private final DefaultListModel<Imzaci> model = new DefaultListModel<>();
    private final JList<Imzaci> liste = new JList<>(model);
    private final JComboBox<Imzaci> secici;
    private final JCheckBox ekOnay = new JCheckBox("Ek onay bloğu (+1)");
    private final JTextField ekOnayBaslik = new JTextField();
    private final JComboBox<Imzaci> ekOnaySecici;
    private final JLabel sayac = new JLabel();
    private Imzaci ekOnayElle;
    private Runnable degisti = () -> {};

    public ImzaciPaneli(Imzacilar kayitlar, Ayarlar.ImzaBlogu ib) {
        super(new BorderLayout(6, 6));
        this.kayitlar = kayitlar;
        this.ib = ib;
        setBorder(BorderFactory.createTitledBorder("İmzacılar — " + ib.anaBaslik()));

        secici = new JComboBox<>(kayitlar.imzacilar().toArray(new Imzaci[0]));
        ekOnaySecici = new JComboBox<>(kayitlar.imzacilar().toArray(new Imzaci[0]));

        liste.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        liste.setVisibleRowCount(5);
        liste.setDragEnabled(true);
        liste.setDropMode(DropMode.INSERT);
        liste.setTransferHandler(new SiralamaAktarici());
        liste.setCellRenderer((l, v, i, sec, odak) -> new ImzaciSatiri(i + 1, v, sec));
        liste.setFixedCellHeight(52);

        JButton ekle = new JButton("Ekle", Simgeler.al("arti", 14));
        ekle.addActionListener(e -> ekle((Imzaci) secici.getSelectedItem()));
        JButton elle = new JButton("Elle ekle…");
        elle.setToolTipText("Listede olmayan bir kişiyi ekle");
        elle.addActionListener(e -> elleGir().ifPresent(this::ekle));
        JButton kaldir = new JButton(Simgeler.al("kapat", 14));
        kaldir.setToolTipText("Seçili imzacıyı kaldır");
        kaldir.addActionListener(e -> {
            int i = liste.getSelectedIndex();
            if (i >= 0) { model.remove(i); guncelle(); }
        });
        JButton yukari = new JButton("↑");
        yukari.setToolTipText("Yukarı taşı (sürükleyerek de sıralayabilirsiniz)");
        yukari.addActionListener(e -> tasi(-1));
        JButton asagi = new JButton("↓");
        asagi.setToolTipText("Aşağı taşı");
        asagi.addActionListener(e -> tasi(1));

        JPanel ust = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        ust.add(secici);
        ust.add(ekle);
        ust.add(elle);
        add(ust, BorderLayout.NORTH);

        JScrollPane sp = new JScrollPane(liste);
        sp.setPreferredSize(new Dimension(420, 176));
        for (JButton b : new JButton[]{yukari, asagi, kaldir}) {
            b.putClientProperty(com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE,
                    com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        }
        add(sp, BorderLayout.CENTER);

        JPanel sag = new JPanel(new GridLayout(0, 1, 4, 4));
        sag.add(yukari);
        sag.add(asagi);
        sag.add(kaldir);
        JPanel sagDis = new JPanel(new BorderLayout());
        sagDis.add(sag, BorderLayout.NORTH);
        add(sagDis, BorderLayout.EAST);

        ekOnayBaslik.setText(ib.ekOnayBaslik());

        ekOnay.setSelected(ib.ekOnayAcikVarsayilan());
        JButton ekOnayElleBtn = new JButton("Elle…");
        ekOnaySecici.setPreferredSize(new Dimension(250, ekOnaySecici.getPreferredSize().height));
        ekOnayBaslik.setColumns(7);
        sayac.setForeground(Tema.ikincilYazi());
        ekOnayElleBtn.addActionListener(e -> elleGir().ifPresent(i -> {
            ekOnayElle = i;
            ekOnaySecici.addItem(i);
            ekOnaySecici.setSelectedItem(i);
        }));
        JPanel alt = new JPanel();
        alt.setLayout(new BoxLayout(alt, BoxLayout.Y_AXIS));
        JPanel a1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        a1.add(sayac);
        JPanel a2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        a2.add(ekOnay);
        a2.add(new JLabel("Başlık:"));
        a2.add(ekOnayBaslik);
        a2.add(ekOnaySecici);
        a2.add(ekOnayElleBtn);
        alt.add(a1);
        alt.add(Box.createVerticalStrut(2));
        alt.add(a2);
        add(alt, BorderLayout.SOUTH);

        ekOnay.addActionListener(e -> ekOnayDurumu());
        ekOnayDurumu();
        guncelle();
    }

    public void degisinceCalistir(Runnable r) { this.degisti = r; }

    private void ekOnayDurumu() {
        boolean a = ekOnay.isSelected();
        ekOnayBaslik.setEnabled(a);
        ekOnaySecici.setEnabled(a);
        degisti.run();
    }

    private void ekle(Imzaci i) {
        if (i == null) return;
        if (model.size() >= ib.anaImzaciMax()) {
            Diyalog.bilgi(this, Diyalog.Tur.UYARI, "En fazla " + ib.anaImzaciMax() + " ana imzacı",
                    "Ek bir onay için alttaki <b>Ek onay bloğu</b>nu kullanın.");
            return;
        }
        for (int k = 0; k < model.size(); k++) {
            if (model.get(k).ad().equals(i.ad())) return;
        }
        model.addElement(i);
        guncelle();
    }

    private void tasi(int yon) {
        int i = liste.getSelectedIndex();
        int j = i + yon;
        if (i < 0 || j < 0 || j >= model.size()) return;
        Imzaci x = model.remove(i);
        model.add(j, x);
        liste.setSelectedIndex(j);
        guncelle();
    }

    private void guncelle() {
        int n = model.size();
        List<Integer> s = n == 0 ? List.of() : ib.satirlar(n);
        sayac.setText(n + " / " + ib.anaImzaciMax() + " imzacı"
                + (n > 0 ? "   ·   yerleşim: " + String.join(" + ", s.stream().map(String::valueOf).toList()) : ""));
        degisti.run();
    }

    private java.util.Optional<Imzaci> elleGir() {
        JTextField ad = new JTextField(24);
        JTextField unvan = new JTextField(24);
        JTextField unvanEn = new JTextField(24);
        JPanel p = new JPanel(new GridLayout(0, 2, 4, 4));
        p.add(new JLabel("Ad SOYAD:"));
        p.add(ad);
        p.add(new JLabel("Unvan:"));
        p.add(unvan);
        p.add(new JLabel("Unvan (EN):"));
        p.add(unvanEn);
        if (!Diyalog.onayla(this, Diyalog.Tur.BILGI, "İmzacı ekle", "Listede olmayan kişiyi girin. "
                + "Listede kayıtlı biriyse unvanı imzacilar.json'dan alınır.", p, "Vazgeç", "Ekle")
                || ad.getText().isBlank()) {
            return java.util.Optional.empty();
        }
        // Listedeki bir kişiyse kanonik unvan kullanılır
        return java.util.Optional.of(kayitlar.adaGoreBul(ad.getText())
                .orElse(new Imzaci("", ad.getText().trim(), unvan.getText().trim(), unvanEn.getText().trim())));
    }

    /** Liste satırı: sıra rozeti, baş harfler yuvarlağı, ad (kalın) ve unvan. */
    private static final class ImzaciSatiri extends JPanel {
        ImzaciSatiri(int sira, Imzaci v, boolean secili) {
            super(new BorderLayout(10, 0));
            setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 10));
            setOpaque(true);
            java.awt.Color vurgu = Tema.vurgu();
            setBackground(secili ? new java.awt.Color(vurgu.getRed(), vurgu.getGreen(), vurgu.getBlue(), 38) : Tema.kart());
            String bh = basHarfler(v.ad());
            JLabel avatar = new JLabel(bh, JLabel.CENTER) {
                @Override protected void paintComponent(java.awt.Graphics g0) {
                    java.awt.Graphics2D g = (java.awt.Graphics2D) g0.create();
                    g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setColor(vurgu);
                    g.fillOval(0, 0, getWidth() - 1, getHeight() - 1);
                    g.dispose();
                    super.paintComponent(g0);
                }
            };
            avatar.setForeground(java.awt.Color.WHITE);
            avatar.setFont(avatar.getFont().deriveFont(java.awt.Font.BOLD, 12f));
            avatar.setPreferredSize(new Dimension(34, 34));
            JLabel no = new JLabel(String.valueOf(sira));
            no.setForeground(Tema.ikincilYazi());
            no.setPreferredSize(new Dimension(14, 34));
            JPanel sol = new JPanel(new BorderLayout(8, 0));
            sol.setOpaque(false);
            sol.add(no, BorderLayout.WEST);
            sol.add(avatar, BorderLayout.CENTER);
            add(sol, BorderLayout.WEST);
            JPanel yazi = new JPanel(new java.awt.GridLayout(2, 1));
            yazi.setOpaque(false);
            JLabel ad = new JLabel(v.ad());
            ad.setFont(ad.getFont().deriveFont(java.awt.Font.BOLD));
            JLabel unvan = new JLabel(v.unvan().isBlank() ? "(unvan yok)" : v.unvan());
            unvan.setForeground(Tema.ikincilYazi());
            yazi.add(ad);
            yazi.add(unvan);
            add(yazi, BorderLayout.CENTER);
        }

        static String basHarfler(String ad) {
            String[] p = ad.replaceAll("^(Av\\.|Dr\\.|Prof\\.|Sn\\.)\\s*", "").trim().split("\\s+");
            if (p.length == 0 || p[0].isEmpty()) return "?";
            String s = p[0].substring(0, 1) + (p.length > 1 ? p[p.length - 1].substring(0, 1) : "");
            return s.toUpperCase(java.util.Locale.forLanguageTag("tr-TR"));
        }
    }

    // ---------------------------------------------------------------- veri

    public void yukle(List<Imzaci> ana, boolean ekAcik, String ekBaslik, Imzaci ekImzaci) {
        model.clear();
        for (Imzaci i : ana) if (i != null && model.size() < ib.anaImzaciMax()) model.addElement(i);
        ekOnay.setSelected(ekAcik);
        ekOnayBaslik.setText(ekBaslik == null || ekBaslik.isBlank() ? ib.ekOnayBaslik() : ekBaslik);
        if (ekImzaci != null) {
            boolean var = false;
            for (int i = 0; i < ekOnaySecici.getItemCount(); i++) var |= ekOnaySecici.getItemAt(i).ad().equals(ekImzaci.ad());
            if (!var) ekOnaySecici.addItem(ekImzaci);
            ekOnaySecici.setSelectedItem(ekImzaci);
        }
        ekOnayDurumu();
        guncelle();
    }

    public List<Imzaci> anaImzacilar() {
        List<Imzaci> r = new ArrayList<>();
        for (int i = 0; i < model.size(); i++) r.add(model.get(i));
        return r;
    }

    public boolean ekOnayAcik() { return ekOnay.isSelected(); }

    public String ekOnayBaslik() { return ekOnayBaslik.getText().trim(); }

    public Imzaci ekOnayImzaci() {
        Object o = ekOnaySecici.getSelectedItem();
        return o instanceof Imzaci i ? i : ekOnayElle;
    }

    public JComponent listeBileseni() { return liste; }

    // ---------------------------------------------------------------- sürükle-bırak sıralama

    private class SiralamaAktarici extends TransferHandler {
        private int kaynak = -1;

        @Override public int getSourceActions(JComponent c) { return MOVE; }

        @Override protected Transferable createTransferable(JComponent c) {
            kaynak = liste.getSelectedIndex();
            return new StringSelection(String.valueOf(kaynak));
        }

        @Override public boolean canImport(TransferSupport s) {
            return s.isDrop() && s.isDataFlavorSupported(DataFlavor.stringFlavor);
        }

        @Override public boolean importData(TransferSupport s) {
            if (!canImport(s) || kaynak < 0) return false;
            int hedef = ((JList.DropLocation) s.getDropLocation()).getIndex();
            Imzaci x = model.get(kaynak);
            model.remove(kaynak);
            if (hedef > kaynak) hedef--;
            model.add(hedef, x);
            liste.setSelectedIndex(hedef);
            kaynak = -1;
            guncelle();
            return true;
        }
    }
}
