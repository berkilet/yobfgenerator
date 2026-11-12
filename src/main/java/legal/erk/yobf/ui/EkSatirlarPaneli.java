package legal.erk.yobf.ui;

import legal.erk.yobf.model.EkSatir;
import legal.erk.yobf.model.Satirlar;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Kullanıcının eklediği satırlar:
 * "Satır ekle" → ana tabloya seçilen satırdan sonra yeni "Etiket : Değer" satırı;
 * "Alt satır ekle" → seçilen satırın içine (değer hücresinin sonuna) "Etiket: değer" alt satırı.
 */
public class EkSatirlarPaneli extends JPanel {

    record Konum(String anahtar, String ad) {
        @Override public String toString() { return ad; }
    }

    private final JPanel liste = new JPanel();
    private final List<Oge> ogeler = new ArrayList<>();
    private final JLabel bosBilgi = new JLabel("<html><small>Henüz eklenen satır yok. Formda olmayan bir bilgi için "
            + "<b>Satır ekle</b> (yeni etiket/değer satırı) veya bir satırın içine bilgi eklemek için "
            + "<b>Alt satır ekle</b> kullanın.</small></html>");
    private String formTuru = "YOBF";
    private Runnable degisti = () -> {};

    public EkSatirlarPaneli() {
        super(new BorderLayout(4, 4));
        setBorder(BorderFactory.createTitledBorder("Eklenen satırlar"));
        liste.setLayout(new BoxLayout(liste, BoxLayout.Y_AXIS));
        add(liste, BorderLayout.CENTER);

        JButton satirEkle = new JButton("+ Satır ekle");
        satirEkle.setToolTipText("Tabloya, seçeceğiniz satırdan sonra yeni bir \"Etiket : Değer\" satırı ekler");
        satirEkle.addActionListener(e -> ekle(new EkSatir(false, Satirlar.IMZASINA_SUNULACAK, "", "")));
        JButton altEkle = new JButton("+ Alt satır ekle");
        altEkle.setToolTipText("Seçeceğiniz satırın içine (ör. Belgenin Konusu/Özeti) \"Etiket: değer\" şeklinde alt satır ekler");
        altEkle.addActionListener(e -> ekle(new EkSatir(true, Satirlar.KONU, "", "")));
        JPanel btn = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        btn.add(satirEkle);
        btn.add(altEkle);
        add(btn, BorderLayout.SOUTH);
        yenile();
    }

    public void degisinceCalistir(Runnable r) { this.degisti = r; }

    public void formTuruDegisti(String kod) {
        this.formTuru = kod;
        for (Oge o : ogeler) o.konumlariDoldur();
    }

    public void yukle(List<EkSatir> l) {
        ogeler.clear();
        for (EkSatir e : l) ogeler.add(new Oge(e));
        yenile();
    }

    public List<EkSatir> oku() {
        List<EkSatir> r = new ArrayList<>();
        for (Oge o : ogeler) r.add(o.oku());
        return r;
    }

    private void ekle(EkSatir e) {
        Oge o = new Oge(e);
        ogeler.add(o);
        yenile();
        o.etiket.requestFocusInWindow();
    }

    private void yenile() {
        liste.removeAll();
        if (ogeler.isEmpty()) liste.add(bosBilgi);
        for (int i = 0; i < ogeler.size(); i++) {
            if (i > 0) liste.add(Box.createVerticalStrut(6));
            liste.add(ogeler.get(i).panel);
        }
        revalidate();
        repaint();
        degisti.run();
    }

    /** Tek bir eklenen satırın düzenleme bileşenleri. */
    private class Oge {
        final boolean alt;
        final JComboBox<Konum> konum = new JComboBox<>();
        final JTextField etiket = new JTextField(14);
        final JTextArea deger = new JTextArea(2, 30);
        final JPanel panel = new JPanel(new GridBagLayout());
        String seciliAnahtar;

        Oge(EkSatir e) {
            this.alt = e.alt();
            this.seciliAnahtar = e.konum();
            etiket.setText(e.etiket());
            deger.setText(e.deger());
            deger.setLineWrap(true);
            deger.setWrapStyleWord(true);

            panel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 3, 0, 0, alt ? new Color(0x7E57C2) : new Color(0x1E88E5)),
                    BorderFactory.createEmptyBorder(2, 6, 2, 2)));
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(2, 2, 2, 4);
            c.anchor = GridBagConstraints.WEST;
            JLabel tur = new JLabel(alt ? "<html><b>Alt satır</b> — şu satırın içine:</html>"
                    : "<html><b>Satır</b> — şu satırdan sonra:</html>");
            panel.add(tur, c);
            c.gridx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.weightx = 1;
            panel.add(konum, c);
            c.gridx = 2;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            JButton sil = new JButton("Sil");
            sil.addActionListener(ev -> {
                ogeler.remove(this);
                yenile();
            });
            panel.add(sil, c);

            c.gridx = 0;
            c.gridy = 1;
            panel.add(new JLabel(alt ? "Etiket (ör. Süre):" : "Etiket (sol sütun):"), c);
            c.gridx = 1;
            c.gridwidth = 2;
            c.fill = GridBagConstraints.HORIZONTAL;
            panel.add(etiket, c);
            c.gridx = 0;
            c.gridy = 2;
            c.gridwidth = 1;
            c.fill = GridBagConstraints.NONE;
            c.anchor = GridBagConstraints.NORTHWEST;
            panel.add(new JLabel("Değer:"), c);
            c.gridx = 1;
            c.gridwidth = 2;
            c.fill = GridBagConstraints.HORIZONTAL;
            JScrollPane sp = new JScrollPane(deger);
            sp.setPreferredSize(new Dimension(300, 48));
            panel.add(sp, c);

            konumlariDoldur();
            konum.addActionListener(ev -> {
                if (konum.getSelectedItem() instanceof Konum k) seciliAnahtar = k.anahtar();
                degisti.run();
            });
            DocumentListener d = new DocumentListener() {
                public void insertUpdate(DocumentEvent ev) { degisti.run(); }
                public void removeUpdate(DocumentEvent ev) { degisti.run(); }
                public void changedUpdate(DocumentEvent ev) { degisti.run(); }
            };
            etiket.getDocument().addDocumentListener(d);
            deger.getDocument().addDocumentListener(d);
        }

        void konumlariDoldur() {
            String onceki = seciliAnahtar;
            konum.removeAllItems();
            Map<String, String> m = Satirlar.konumlar(formTuru);
            for (Map.Entry<String, String> e : m.entrySet()) konum.addItem(new Konum(e.getKey(), e.getValue()));
            if (!alt) konum.addItem(new Konum(Satirlar.SON, "(Tablonun en sonuna)"));
            seciliAnahtar = onceki;
            for (int i = 0; i < konum.getItemCount(); i++) {
                if (konum.getItemAt(i).anahtar().equals(onceki)) {
                    konum.setSelectedIndex(i);
                    return;
                }
            }
            // Bu form türünde olmayan konum: sona/konuya düş
            konum.setSelectedItem(null);
            for (int i = 0; i < konum.getItemCount(); i++) {
                String a = konum.getItemAt(i).anahtar();
                if (a.equals(alt ? Satirlar.KONU : Satirlar.SON)) konum.setSelectedIndex(i);
            }
        }

        EkSatir oku() {
            return new EkSatir(alt, seciliAnahtar, etiket.getText(), deger.getText());
        }
    }
}
