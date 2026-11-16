package legal.erk.yobf.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

/** Kesik çizgili, yuvarlak köşeli "dosyaları buraya bırakın" alanı. Tıklayınca dosya seçici açılır. */
public class BirakmaAlani extends JPanel {

    private final JLabel simge = new JLabel();
    private final JLabel metin = new JLabel("PDF veya Word dosyalarını buraya bırakın");
    private final JButton sec = new JButton("veya dosya seçin");
    private boolean vurgulu;
    private boolean kucuk;

    public BirakmaAlani(Runnable dosyaSec) {
        super(new GridBagLayout());
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        metin.setFont(metin.getFont().deriveFont(java.awt.Font.BOLD, 13.5f));
        sec.putClientProperty(com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE,
                com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE_BORDERLESS);
        sec.addActionListener(e -> dosyaSec.run());
        JPanel ic = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        ic.setOpaque(false);
        ic.add(simge);
        ic.add(metin);
        ic.add(sec);
        add(ic);
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { dosyaSec.run(); }
            @Override public void mouseEntered(MouseEvent e) { vurgula(true); }
            @Override public void mouseExited(MouseEvent e) { vurgula(false); }
        });
        kucuk(false);
    }

    /** Belge eklendikten sonra alan daralır. */
    public void kucuk(boolean k) {
        this.kucuk = k;
        setBorder(BorderFactory.createEmptyBorder(k ? 10 : 26, 12, k ? 10 : 26, 12));
        simge.setIcon(Simgeler.vurgu("yukle", k ? 18 : 26));
        metin.setText(k ? "Başka belge eklemek için buraya bırakın" : "PDF veya Word dosyalarını buraya bırakın");
        revalidate();
        repaint();
    }

    public void vurgula(boolean v) {
        if (vurgulu != v) {
            vurgulu = v;
            repaint();
        }
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (simge != null) kucuk(kucuk);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color v = Tema.vurgu();
        RoundRectangle2D r = new RoundRectangle2D.Double(1, 1, getWidth() - 3, getHeight() - 3, 14, 14);
        g.setColor(new Color(v.getRed(), v.getGreen(), v.getBlue(), vurgulu ? 34 : 14));
        g.fill(r);
        g.setColor(new Color(v.getRed(), v.getGreen(), v.getBlue(), vurgulu ? 220 : 120));
        g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{6f, 5f}, 0f));
        g.draw(r);
        g.dispose();
        super.paintComponent(g0);
    }
}
