package legal.erk.yobf.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.KeyEvent;

/**
 * Uygulamanın modern diyalog penceresi: renkli simgeli başlık, gövde, sağda düğmeler
 * (son düğme birincil/lacivert). JOptionPane yerine kullanılır.
 */
public final class Diyalog {

    public enum Tur {
        BILGI("bilgi"), UYARI("uyari"), HATA("uyari"), SORU("bilgi"), BASARI("onay");

        final String simge;

        Tur(String simge) { this.simge = simge; }

        Color renk() {
            return switch (this) {
                case UYARI -> new Color(0xD97706);
                case HATA -> new Color(0xDC2626);
                case BASARI -> new Color(0x16A34A);
                default -> Tema.vurgu();
            };
        }
    }

    private Diyalog() {}

    /**
     * @param dugmeler soldan sağa; son düğme birincil. Esc / pencere kapatma = -1.
     * @return basılan düğmenin sırası
     */
    public static int goster(Component sahip, Tur tur, String baslik, String altBaslik, JComponent govde, String... dugmeler) {
        Window w = sahip == null ? null : (sahip instanceof Window x ? x : SwingUtilities.getWindowAncestor(sahip));
        JDialog d = new JDialog(w, baslik, JDialog.ModalityType.APPLICATION_MODAL);
        d.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        int[] sonuc = {-1};

        JPanel kok = new JPanel(new BorderLayout(0, 16));
        kok.setBorder(BorderFactory.createEmptyBorder(22, 24, 18, 24));
        kok.setBackground(Tema.kart());

        JPanel ust = new JPanel(new BorderLayout(14, 0));
        ust.setOpaque(false);
        ust.add(new Rozet(tur), BorderLayout.WEST);
        JPanel yazilar = new JPanel(new BorderLayout(0, 3));
        yazilar.setOpaque(false);
        JLabel b = new JLabel(baslik);
        b.setFont(b.getFont().deriveFont(Font.BOLD, b.getFont().getSize2D() + 4f));
        yazilar.add(b, BorderLayout.NORTH);
        if (altBaslik != null && !altBaslik.isBlank()) {
            JLabel a = new JLabel("<html><div style='width:420px'>" + altBaslik + "</div></html>");
            a.setForeground(Tema.ikincilYazi());
            yazilar.add(a, BorderLayout.CENTER);
        }
        ust.add(yazilar, BorderLayout.CENTER);
        kok.add(ust, BorderLayout.NORTH);

        if (govde != null) {
            Kart.seffaf(govde);
            kok.add(govde, BorderLayout.CENTER);
        }

        JPanel alt = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        alt.setOpaque(false);
        for (int i = 0; i < dugmeler.length; i++) {
            JButton btn = new JButton(dugmeler[i]);
            int no = i;
            btn.addActionListener(e -> {
                sonuc[0] = no;
                d.dispose();
            });
            if (i == dugmeler.length - 1) {
                Tema.birincil(btn);
                d.getRootPane().setDefaultButton(btn);
            }
            alt.add(btn);
        }
        kok.add(alt, BorderLayout.SOUTH);

        d.getRootPane().registerKeyboardAction(e -> d.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        d.setContentPane(kok);
        d.pack();
        Dimension boy = d.getSize();
        d.setSize(Math.max(boy.width, 460), boy.height);
        d.setResizable(false);
        d.setLocationRelativeTo(w);
        d.setVisible(true);
        return sonuc[0];
    }

    public static void bilgi(Component sahip, Tur tur, String baslik, String mesaj) {
        goster(sahip, tur, baslik, mesaj, null, "Tamam");
    }

    /** Evet/Hayır sorusu; birincil düğme "evet". */
    public static boolean onayla(Component sahip, Tur tur, String baslik, String mesaj, JComponent govde,
                                 String hayir, String evet) {
        return goster(sahip, tur, baslik, mesaj, govde, hayir, evet) == 1;
    }

    /** Başlık yanındaki renkli yuvarlak simge. */
    private static final class Rozet extends JComponent {
        private final Tur tur;

        Rozet(Tur tur) {
            this.tur = tur;
            setPreferredSize(new Dimension(42, 42));
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color r = tur.renk();
            g.setColor(new Color(r.getRed(), r.getGreen(), r.getBlue(), 36));
            g.fillOval(0, 0, 42, 42);
            Simgeler.al(tur.simge, 22, r).paintIcon(this, g, 10, 10);
            g.dispose();
        }
    }
}
