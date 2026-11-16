package legal.erk.yobf;

import legal.erk.yobf.config.UygulamaYolu;
import legal.erk.yobf.config.Yapilandirma;
import legal.erk.yobf.ui.AnaPencere;

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
                legal.erk.yobf.ui.Diyalog.bilgi(null, legal.erk.yobf.ui.Diyalog.Tur.HATA, "Yapılandırma okunamadı",
                        e.getMessage() + "<br><br>Uygulama klasörü: " + UygulamaYolu.kok()
                                + "<br>config/ klasörünün uygulama ile aynı yerde olduğundan emin olun.");
                System.exit(1);
                return;
            }
            new AnaPencere(y).setVisible(true);
        });
    }
}
