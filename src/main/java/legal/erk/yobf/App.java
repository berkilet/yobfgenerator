package legal.erk.yobf;

import legal.erk.yobf.config.UygulamaYolu;
import legal.erk.yobf.config.Yapilandirma;
import legal.erk.yobf.ui.AnaPencere;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.util.Locale;

public final class App {

    private App() {}

    public static void main(String[] args) {
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        SwingUtilities.invokeLater(() -> {
            legal.erk.yobf.ui.Tema.kur(legal.erk.yobf.config.Tercihler.varsayilan().koyuTema());
            Yapilandirma y;
            try {
                y = Yapilandirma.yukle();
            } catch (RuntimeException e) {
                JOptionPane.showMessageDialog(null,
                        e.getMessage() + "\n\nUygulama klasörü: " + UygulamaYolu.kok()
                                + "\nconfig/ klasörünün uygulama ile aynı yerde olduğundan emin olun.",
                        "YÖBF Otomatörü — yapılandırma hatası", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
                return;
            }
            new AnaPencere(y).setVisible(true);
        });
    }
}
