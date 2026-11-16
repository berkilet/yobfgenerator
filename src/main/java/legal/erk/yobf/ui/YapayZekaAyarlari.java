package legal.erk.yobf.ui;

import legal.erk.yobf.ai.ApiAnahtari;
import legal.erk.yobf.ai.IstemciFabrikasi;
import legal.erk.yobf.ai.ModelIstemcisi;
import legal.erk.yobf.ai.OzetSemasi;
import legal.erk.yobf.ai.Saglayici;
import legal.erk.yobf.config.Ayarlar;
import legal.erk.yobf.config.Tercihler;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.net.URI;
import java.util.EnumMap;
import java.util.Map;

/**
 * Yapay zekâ sağlayıcısı ayarları: Anthropic, OpenAI, Google Gemini veya OpenAI uyumlu servis
 * (Mistral, DeepSeek, Groq, yerel Ollama …). Anahtarlar %APPDATA%\YOBF altında, sağlayıcı başına ayrı dosyada.
 */
public final class YapayZekaAyarlari {

    /** Her sağlayıcının ekrandaki alanları. */
    private static final class Bolum {
        final Saglayici s;
        final JRadioButton sec;
        final JLabel durum = new JLabel();
        final JPasswordField anahtar = new JPasswordField(28);
        final JComboBox<Object> model = new JComboBox<>();
        final JTextField adres = new JTextField(28);

        Bolum(Saglayici s) {
            this.s = s;
            this.sec = new JRadioButton(s.ad);
            sec.setFont(sec.getFont().deriveFont(java.awt.Font.BOLD));
            anahtar.putClientProperty(com.formdev.flatlaf.FlatClientProperties.STYLE, "showRevealButton:true");
            model.setEditable(true);
        }

        String modelId() {
            Object o = model.isEditable() ? model.getEditor().getItem() : model.getSelectedItem();
            if (o instanceof Ayarlar.ModelSecenegi m) return m.id();
            String t = o == null ? "" : o.toString().trim();
            // Listedeki bir seçeneğin görünen adı yazılıysa id'ye çevir
            for (int i = 0; i < model.getItemCount(); i++) {
                if (model.getItemAt(i) instanceof Ayarlar.ModelSecenegi m && m.toString().equals(t)) return m.id();
            }
            return t;
        }

        String girilenAnahtar() { return new String(anahtar.getPassword()).trim(); }
    }

    private YapayZekaAyarlari() {}

    /** @return kaydedildiyse true */
    public static boolean goster(Component sahip, Ayarlar ayarlar, Tercihler tercihler, Saglayici odak) {
        Map<Saglayici, Bolum> bolumler = new EnumMap<>(Saglayici.class);
        ButtonGroup grup = new ButtonGroup();
        JPanel liste = new JPanel(new GridBagLayout());
        GridBagConstraints lc = new GridBagConstraints();
        lc.gridx = 0;
        lc.fill = GridBagConstraints.HORIZONTAL;
        lc.weightx = 1;
        lc.insets = new Insets(0, 0, 10, 0);
        Saglayici aktif = odak != null ? odak : Saglayici.kodla(tercihler.saglayici());

        for (Saglayici s : Saglayici.values()) {
            Bolum b = new Bolum(s);
            bolumler.put(s, b);
            grup.add(b.sec);
            b.sec.setSelected(s == aktif);
            for (Ayarlar.ModelSecenegi m : ayarlar.modeller(s.kod)) b.model.addItem(m);
            String secili = tercihler.model(s.kod, s == Saglayici.ANTHROPIC ? ayarlar.model()
                    : ayarlar.modeller(s.kod).isEmpty() ? "" : ayarlar.modeller(s.kod).get(0).id());
            boolean bulundu = false;
            for (int i = 0; i < b.model.getItemCount(); i++) {
                if (b.model.getItemAt(i) instanceof Ayarlar.ModelSecenegi m && m.id().equals(secili)) {
                    b.model.setSelectedIndex(i);
                    bulundu = true;
                }
            }
            if (!bulundu) b.model.setSelectedItem(secili);
            if (s == Saglayici.UYUMLU) b.adres.setText(tercihler.tabanAdres(ayarlar.tabanAdres("uyumlu")));
            durumYaz(b);
            liste.add(bolumPaneli(b), lc);
        }

        JLabel gizlilik = new JLabel("<html><div style='width:520px'><b>Gizlilik:</b> Analizde belge metni (TCKN, IBAN, "
                + "telefon ve e-posta maskelenmiş olarak) seçtiğiniz sağlayıcıya gönderilir. Ücretsiz katmanlarda "
                + "(ör. Gemini ücretsiz) içerik sağlayıcının ürünlerini geliştirmek için kullanılabilir; gizli belgeler için "
                + "ücretli / kurumsal hesap kullanın.</div></html>");
        gizlilik.setForeground(Tema.ikincilYazi());
        gizlilik.setFont(gizlilik.getFont().deriveFont(11.5f));

        JLabel testSonucu = new JLabel(" ");
        JButton dene = new JButton("Seçili sağlayıcıyı dene", Simgeler.al("analiz", 14));
        dene.addActionListener(e -> {
            Bolum b = bolumler.values().stream().filter(x -> x.sec.isSelected()).findFirst().orElse(null);
            if (b == null) return;
            String anahtar = b.girilenAnahtar().isEmpty() ? ApiAnahtari.oku(b.s).orElse("") : b.girilenAnahtar();
            dene.setEnabled(false);
            testSonucu.setForeground(Tema.ikincilYazi());
            testSonucu.setText("Deneniyor…");
            new SwingWorker<String, Void>() {
                @Override protected String doInBackground() throws Exception {
                    try (ModelIstemcisi m = IstemciFabrikasi.olustur(ayarlar, b.s, b.modelId(), anahtar, b.adres.getText().trim())) {
                        m.yapilandirilmis(OzetSemasi.Kisaltma.class, "Kısa ve yalnızca istenen JSON ile yanıt ver.",
                                "konu_tr alanına 'tamam', konu_en alanına 'ok' yaz.", 300);
                        return m.etiket();
                    }
                }

                @Override protected void done() {
                    dene.setEnabled(true);
                    try {
                        testSonucu.setForeground(new Color(0x16A34A));
                        testSonucu.setText("✓ Bağlantı başarılı: " + get());
                    } catch (Exception ex) {
                        Throwable t = ex.getCause() != null ? ex.getCause() : ex;
                        testSonucu.setForeground(new Color(0xDC2626));
                        testSonucu.setText("<html><div style='width:520px'>✗ " + t.getMessage() + "</div></html>");
                    }
                }
            }.execute();
        });
        JPanel testSatiri = new JPanel(new BorderLayout(10, 0));
        testSatiri.add(dene, BorderLayout.WEST);
        testSatiri.add(testSonucu, BorderLayout.CENTER);

        JPanel govde = new JPanel(new BorderLayout(0, 10));
        // Küçük ekranlarda (ör. 1366×768) pencere sığsın: sağlayıcı listesi gerekirse kaydırılır
        int ekran = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds().height;
        java.awt.Dimension tercih = liste.getPreferredSize();
        int sinir = Math.max(300, ekran - 380);
        if (tercih.height > sinir) {
            javax.swing.JScrollPane sp = new javax.swing.JScrollPane(liste);
            sp.setBorder(BorderFactory.createEmptyBorder());
            sp.getVerticalScrollBar().setUnitIncrement(Kaydirma.ADIM);
            sp.setPreferredSize(new java.awt.Dimension(tercih.width + 14, sinir));
            govde.add(sp, BorderLayout.NORTH);
        } else {
            govde.add(liste, BorderLayout.NORTH);
        }
        govde.add(testSatiri, BorderLayout.CENTER);
        govde.add(gizlilik, BorderLayout.SOUTH);

        if (!Diyalog.onayla(sahip, Diyalog.Tur.BILGI, "Yapay zekâ ayarları",
                "Analizde kullanılacak sağlayıcıyı seçin ve API anahtarını girin. "
                        + "Anahtarlar yalnızca bu bilgisayarda, kullanıcı klasörünüzde saklanır.",
                govde, "Vazgeç", "Kaydet")) {
            return false;
        }

        for (Bolum b : bolumler.values()) {
            if (b.sec.isSelected()) tercihler.saglayiciAyarla(b.s.kod);
            String m = b.modelId();
            if (!m.isBlank()) tercihler.modelAyarla(b.s.kod, m);
            if (b.s == Saglayici.UYUMLU) tercihler.tabanAdresAyarla(b.adres.getText().trim());
            if (!b.girilenAnahtar().isEmpty()) {
                try {
                    ApiAnahtari.kaydet(b.s, b.girilenAnahtar());
                } catch (Exception e) {
                    Diyalog.bilgi(sahip, Diyalog.Tur.HATA, "Anahtar kaydedilemedi", b.s.ad + ": " + e.getMessage());
                }
            }
        }
        return true;
    }

    private static void durumYaz(Bolum b) {
        var ortam = ApiAnahtari.ortamDegiskeni(b.s);
        boolean dosyada = java.nio.file.Files.isRegularFile(ApiAnahtari.dosya(b.s));
        if (ortam.isPresent()) {
            b.durum.setText("✓ ortam değişkeninden (" + ortam.get() + ")");
            b.durum.setForeground(new Color(0x16A34A));
        } else if (dosyada) {
            b.durum.setText("✓ anahtar kayıtlı");
            b.durum.setForeground(new Color(0x16A34A));
        } else {
            b.durum.setText(b.s == Saglayici.UYUMLU ? "anahtar isteğe bağlı" : "anahtar yok");
            b.durum.setForeground(Tema.ikincilYazi());
        }
        b.anahtar.putClientProperty(com.formdev.flatlaf.FlatClientProperties.PLACEHOLDER_TEXT,
                dosyada || ortam.isPresent() ? "kayıtlı — değiştirmek için yeni anahtarı yazın" : "API anahtarı");
    }

    private static JComponent bolumPaneli(Bolum b) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(true);
        p.putClientProperty(Kart.OPAK, true);
        p.setBackground(Tema.koyu() ? new Color(0x2E3238) : new Color(0xF3F6FB));
        p.putClientProperty(com.formdev.flatlaf.FlatClientProperties.STYLE, "arc:12");
        p.setBorder(BorderFactory.createEmptyBorder(10, 12, 12, 12));
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(2, 0, 2, 8);

        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;
        p.add(b.sec, c);
        c.gridx = 2;
        c.gridwidth = 1;
        c.anchor = GridBagConstraints.EAST;
        c.weightx = 1;
        p.add(b.durum, c);
        c.anchor = GridBagConstraints.WEST;
        c.weightx = 0;

        int satir = 1;
        if (b.s == Saglayici.UYUMLU) {
            satir = alan(p, satir, "API adresi", b.adres, null);
            JLabel ipucu = new JLabel("<html>ör. Mistral: https://api.mistral.ai/v1 · DeepSeek: https://api.deepseek.com/v1 · "
                    + "Groq: https://api.groq.com/openai/v1 · yerel Ollama: http://localhost:11434/v1</html>");
            ipucu.setForeground(Tema.ikincilYazi());
            ipucu.setFont(ipucu.getFont().deriveFont(11f));
            GridBagConstraints ic = new GridBagConstraints();
            ic.gridx = 1;
            ic.gridy = satir++;
            ic.gridwidth = 2;
            ic.anchor = GridBagConstraints.WEST;
            ic.insets = new Insets(0, 0, 4, 0);
            p.add(ipucu, ic);
        }

        JPanel anahtarSatiri = new JPanel(new BorderLayout(6, 0));
        anahtarSatiri.setOpaque(false);
        anahtarSatiri.add(b.anahtar, BorderLayout.CENTER);
        JPanel dugmeler = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        dugmeler.setOpaque(false);
        if (b.s.anahtarSayfasi != null) {
            JButton al = new JButton("Anahtar al ↗");
            Tema.cizgisiz(al);
            al.setForeground(Tema.vurgu());
            al.setToolTipText(b.s.anahtarSayfasi);
            al.addActionListener(e -> {
                try {
                    Desktop.getDesktop().browse(URI.create(b.s.anahtarSayfasi));
                } catch (Exception ignored) {
                    // tarayıcı açılamazsa adres ipucunda görünür
                }
            });
            dugmeler.add(al);
        }
        JButton sil = new JButton("Sil");
        Tema.cizgisiz(sil);
        sil.setToolTipText("Bu bilgisayarda kayıtlı anahtarı sil");
        sil.addActionListener(e -> {
            try {
                ApiAnahtari.sil(b.s);
                b.anahtar.setText("");
                durumYaz(b);
            } catch (Exception ex) {
                b.durum.setText("silinemedi: " + ex.getMessage());
            }
        });
        dugmeler.add(sil);
        anahtarSatiri.add(dugmeler, BorderLayout.EAST);
        satir = alan(p, satir, "API anahtarı", anahtarSatiri, null);
        alan(p, satir, "Model", b.model, "Listeden seçin veya model adını yazın");
        return p;
    }

    private static int alan(JPanel p, int satir, String etiket, JComponent bilesen, String ipucu) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = satir;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(4, 26, 2, 10);
        JLabel l = new JLabel(etiket);
        l.setForeground(Tema.ikincilYazi());
        p.add(l, c);
        c.gridx = 1;
        c.gridwidth = 2;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4, 0, 2, 0);
        if (ipucu != null) bilesen.setToolTipText(ipucu);
        p.add(bilesen, c);
        return satir + 1;
    }
}
