package legal.erk.yobf.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.ui.FlatLineBorder;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.Insets;

/** Yuvarlak köşeli, başlıklı içerik kartı. İçindeki ara paneller şeffaf yapılır. */
public class Kart extends JPanel {

    private final JLabel baslik = new JLabel();
    private final JLabel aciklama = new JLabel();
    private final JPanel ust = new JPanel(new BorderLayout(10, 0));
    private final JComponent govde;

    public Kart(String baslikMetni, String simge, JComponent govde) {
        super(new BorderLayout(0, 10));
        this.govde = govde;
        baslik.setText(baslikMetni);
        if (simge != null) baslik.setIcon(Simgeler.vurgu(simge, 18));
        baslik.setIconTextGap(8);
        JPanel sol = new JPanel(new BorderLayout(0, 2));
        sol.setOpaque(false);
        sol.add(baslik, BorderLayout.NORTH);
        sol.add(aciklama, BorderLayout.CENTER);
        aciklama.setVisible(false);
        ust.setOpaque(false);
        ust.add(sol, BorderLayout.CENTER);
        add(ust, BorderLayout.NORTH);
        add(govde, BorderLayout.CENTER);
        stil();
    }

    public Kart aciklama(String metin) {
        aciklama.setText(metin);
        aciklama.setVisible(metin != null && !metin.isBlank());
        return this;
    }

    /** Başlığın sağına bileşen (ör. "Temizle" bağlantısı). */
    public Kart sag(Component c) {
        ust.add(c, BorderLayout.EAST);
        return this;
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (baslik != null) stil();
    }

    private void stil() {
        setOpaque(true);
        setBackground(Tema.kart());
        setBorder(new FlatLineBorder(new Insets(14, 16, 16, 16), Tema.kartKenar(), 1f, 16));
        putClientProperty(FlatClientProperties.STYLE, "arc:16");
        Font f = baslik.getFont();
        baslik.setFont(f.deriveFont(Font.BOLD, f.getSize2D() + 2f));
        aciklama.setForeground(Tema.ikincilYazi());
        aciklama.setFont(aciklama.getFont().deriveFont(aciklama.getFont().getSize2D() - 1f));
        seffaf(govde);
    }

    /** Kartın içindeki düz panelleri şeffaf yapar (kart rengi görünsün). */
    static void seffaf(Component c) {
        if (c instanceof JScrollPane || c instanceof JViewport) return;
        if (c instanceof JPanel p && !(c instanceof Kart) && !(c instanceof OnizlemePaneli)) {
            p.setOpaque(false);
            if (p.getBorder() instanceof javax.swing.border.TitledBorder) p.setBorder(BorderFactory.createEmptyBorder());
        }
        if (c instanceof Container k) for (Component x : k.getComponents()) seffaf(x);
    }
}
