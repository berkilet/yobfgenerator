package legal.erk.yobf.ui;

import com.formdev.flatlaf.ui.FlatLineBorder;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.Point;
import java.util.LinkedHashMap;
import java.util.Map;

/** Pencerenin sağ altında birkaç saniye görünen bildirim ("Kaydedildi" vb.); isteğe bağlı eylem düğmeleri. */
public final class Bildirim {

    private static JWindow acik;

    private Bildirim() {}

    public static void goster(JFrame sahip, Diyalog.Tur tur, String baslik, String mesaj) {
        goster(sahip, tur, baslik, mesaj, Map.of());
    }

    /** @param eylemler düğme adı → eylem (sırası korunur; LinkedHashMap verin) */
    public static void goster(JFrame sahip, Diyalog.Tur tur, String baslik, String mesaj, Map<String, Runnable> eylemler) {
        if (sahip == null || !sahip.isShowing()) return;
        if (acik != null) acik.dispose();
        JWindow w = new JWindow(sahip);
        acik = w;

        JPanel kok = new JPanel(new BorderLayout(12, 0));
        kok.setBackground(Tema.kart());
        Color renk = tur.renk();
        kok.setBorder(BorderFactory.createCompoundBorder(
                new FlatLineBorder(new Insets(0, 0, 0, 0), Tema.kartKenar(), 1f, 14),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 4, 0, 0, renk),
                        BorderFactory.createEmptyBorder(12, 12, 12, 10))));
        JLabel simge = new JLabel(Simgeler.al(tur.simge, 20, renk));
        simge.setVerticalAlignment(JLabel.TOP);
        kok.add(simge, BorderLayout.WEST);

        JPanel orta = new JPanel(new BorderLayout(0, 4));
        orta.setOpaque(false);
        JLabel b = new JLabel(baslik);
        b.setFont(b.getFont().deriveFont(java.awt.Font.BOLD));
        orta.add(b, BorderLayout.NORTH);
        if (mesaj != null && !mesaj.isBlank()) {
            JLabel m = new JLabel("<html><div style='width:300px'>" + mesaj + "</div></html>");
            m.setForeground(Tema.ikincilYazi());
            orta.add(m, BorderLayout.CENTER);
        }
        if (!eylemler.isEmpty()) {
            JPanel d = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
            d.setOpaque(false);
            for (Map.Entry<String, Runnable> e : new LinkedHashMap<>(eylemler).entrySet()) {
                JButton btn = new JButton(e.getKey());
                Tema.cizgisiz(btn);
                btn.setFocusable(false);
                btn.setForeground(Tema.vurgu());
                btn.setFont(btn.getFont().deriveFont(java.awt.Font.BOLD));
                btn.addActionListener(x -> {
                    w.dispose();
                    e.getValue().run();
                });
                d.add(btn);
            }
            orta.add(d, BorderLayout.SOUTH);
        }
        kok.add(orta, BorderLayout.CENTER);

        JButton kapat = new JButton(Simgeler.al("kapat", 12));
        kapat.putClientProperty(com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE,
                com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        kapat.setFocusable(false);
        kapat.addActionListener(e -> w.dispose());
        JPanel sag = new JPanel(new BorderLayout());
        sag.setOpaque(false);
        sag.add(kapat, BorderLayout.NORTH);
        kok.add(sag, BorderLayout.EAST);

        w.setContentPane(kok);
        w.pack();
        Point p = sahip.getLocationOnScreen();
        w.setLocation(p.x + sahip.getWidth() - w.getWidth() - 24, p.y + sahip.getHeight() - w.getHeight() - 80);
        w.setVisible(true);
        Timer t = new Timer(eylemler.isEmpty() ? 4500 : 9000, e -> w.dispose());
        t.setRepeats(false);
        t.start();
    }

    /** Test/ekran görüntüsü için. */
    static JWindow acikBildirim() { return acik; }
}
