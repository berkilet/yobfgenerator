package legal.erk.yobf.ui;

import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.event.MouseWheelEvent;

/**
 * İç içe kaydırma alanları (ör. sayfa içindeki metin kutuları) için tekerlek davranışı:
 * iç alan o yönde kayabiliyorsa onu kaydırır; içerik sığıyorsa veya kenara gelinmişse
 * olay hemen dıştaki sayfaya geçer. Böylece fare bir kutunun üzerindeyken sayfa "takılmaz".
 */
public final class Kaydirma {

    /** Ana sayfadaki bir tekerlek adımında kayılacak piksel. */
    public static final int ADIM = 28;

    private Kaydirma() {}

    public static JScrollPane icIce(JScrollPane ic) {
        if (Boolean.TRUE.equals(ic.getClientProperty("yobf.icIce"))) return ic;
        ic.putClientProperty("yobf.icIce", true);
        ic.setWheelScrollingEnabled(false);
        ic.getVerticalScrollBar().setUnitIncrement(16);
        ic.addMouseWheelListener(e -> tekerlek(ic, e));
        return ic;
    }

    private static void tekerlek(JScrollPane ic, MouseWheelEvent e) {
        JScrollBar sb = e.isShiftDown() ? ic.getHorizontalScrollBar() : ic.getVerticalScrollBar();
        double donus = e.getPreciseWheelRotation();
        boolean kayabilir = sb.isVisible() && sb.getMaximum() - sb.getVisibleAmount() > sb.getMinimum()
                && (donus < 0 ? sb.getValue() > sb.getMinimum() : sb.getValue() < sb.getMaximum() - sb.getVisibleAmount());
        if (kayabilir) {
            int fark = (int) Math.round(donus * sb.getUnitIncrement() * Math.max(1, e.getScrollAmount()));
            sb.setValue(sb.getValue() + (fark == 0 ? (int) Math.signum(donus) : fark));
            e.consume();
            return;
        }
        JScrollPane dis = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, ic.getParent());
        if (dis != null) {
            dis.dispatchEvent(SwingUtilities.convertMouseEvent(ic, e, dis));
            e.consume();
        }
    }
}
