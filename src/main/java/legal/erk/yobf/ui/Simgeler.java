package legal.erk.yobf.ui;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.UIManager;
import java.awt.Color;

/** Arayüz simgeleri: /simge/*.svg (ince çizgi), temaya göre renklenir. */
public final class Simgeler {

    private Simgeler() {}

    /** Metin rengiyle (açık/koyu temaya uyar). */
    public static FlatSVGIcon al(String ad, int boy) {
        FlatSVGIcon i = new FlatSVGIcon("simge/" + ad + ".svg", boy, boy);
        i.setColorFilter(new FlatSVGIcon.ColorFilter(c -> {
            Color f = UIManager.getColor("Label.foreground");
            return f != null ? f : c;
        }));
        return i;
    }

    /** Sabit renkle (ör. lacivert düğme üzerinde beyaz). */
    public static FlatSVGIcon al(String ad, int boy, Color renk) {
        FlatSVGIcon i = new FlatSVGIcon("simge/" + ad + ".svg", boy, boy);
        i.setColorFilter(new FlatSVGIcon.ColorFilter(c -> renk));
        return i;
    }

    /** Vurgu renginde (Kalyon laciverti; koyu temada açık mavi). */
    public static FlatSVGIcon vurgu(String ad, int boy) {
        FlatSVGIcon i = new FlatSVGIcon("simge/" + ad + ".svg", boy, boy);
        i.setColorFilter(new FlatSVGIcon.ColorFilter(c -> Tema.vurgu()));
        return i;
    }
}
