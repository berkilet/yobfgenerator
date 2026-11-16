package legal.erk.yobf.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.extras.FlatAnimatedLafChange;
import com.formdev.flatlaf.fonts.inter.FlatInterFont;

import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Insets;
import java.util.Map;

/** Modern görünüm: Inter yazı tipi, Kalyon laciverti vurgu, yuvarlak köşeler; açık/koyu tema. */
public final class Tema {

    /** Kalyon laciverti (logodan). */
    public static final Color LACIVERT = new Color(0x002F6D);
    private static boolean koyu;

    private Tema() {}

    public static boolean koyu() { return koyu; }

    public static void kur(boolean koyuTema) {
        koyu = koyuTema;
        FlatInterFont.install();
        FlatLaf.setPreferredFontFamily(FlatInterFont.FAMILY);
        FlatLaf.setGlobalExtraDefaults(Map.of(
                "@accentColor", koyuTema ? "#5B9BE6" : "#0B4A9E",
                "@background", koyuTema ? "#1B1D21" : "#F3F5F9"));
        if (koyuTema) FlatDarkLaf.setup(); else FlatLightLaf.setup();
        UIManager.put("defaultFont", UIManager.getFont("defaultFont").deriveFont(13f));
        UIManager.put("Component.arc", 10);
        UIManager.put("Button.arc", 10);
        UIManager.put("TextComponent.arc", 8);
        UIManager.put("CheckBox.arc", 6);
        UIManager.put("ProgressBar.arc", 999);
        UIManager.put("Component.focusWidth", 1);
        UIManager.put("Component.innerFocusWidth", 0);
        UIManager.put("Button.margin", new Insets(6, 14, 6, 14));
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("ScrollBar.showButtons", false);
        UIManager.put("ScrollPane.smoothScrolling", true);
        UIManager.put("TabbedPane.showTabSeparators", false);
        UIManager.put("TabbedPane.tabHeight", 34);
        UIManager.put("SplitPane.dividerSize", 8);
        UIManager.put("SplitPaneDivider.style", "plain");
        UIManager.put("TitlePane.unifiedBackground", true);
    }

    /** Tema değiştirir ve tüm pencereleri yumuşak geçişle günceller. */
    public static void degistir(boolean koyuTema) {
        FlatAnimatedLafChange.showSnapshot();
        kur(koyuTema);
        FlatLaf.updateUI();
        FlatAnimatedLafChange.hideSnapshotWithAnimation();
    }

    /** Birincil (dolu, vurgu renkli) düğme biçimi. */
    public static void birincil(javax.swing.JButton b) {
        String v = String.format("#%06X", vurgu().getRGB() & 0xFFFFFF);
        b.putClientProperty(com.formdev.flatlaf.FlatClientProperties.STYLE,
                "background:" + v + ";foreground:#FFFFFF;borderWidth:0;focusWidth:0;font:bold;"
                        + "hoverBackground:darken(" + v + ",6%);pressedBackground:darken(" + v + ",12%);"
                        + "disabledBackground:lighten(" + v + ",35%);disabledText:#FFFFFFCC;margin:7,16,7,16");
    }

    /** Kenarlıksız, metin gibi görünen düğme. */
    public static void cizgisiz(javax.swing.JButton b) {
        b.putClientProperty(com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE,
                com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE_BORDERLESS);
    }

    public static String hex(Color c) { return String.format("#%06X", c.getRGB() & 0xFFFFFF); }

    public static Color vurgu() { return koyu ? new Color(0x5B9BE6) : new Color(0x0B4A9E); }
    public static Color kart() { return koyu ? new Color(0x26292E) : Color.WHITE; }
    public static Color kartKenar() { return koyu ? new Color(0x34383F) : new Color(0xE2E6EE); }
    public static Color ikincilYazi() { return koyu ? new Color(0x9AA3AF) : new Color(0x6B7280); }
    public static Color ustBant() { return koyu ? new Color(0x101826) : LACIVERT; }
}
