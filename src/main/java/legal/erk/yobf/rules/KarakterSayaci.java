package legal.erk.yobf.rules;

import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Konu sınırı için deterministik sayaç: yalnızca harf (\p{L}) ve rakam (\p{N})
 * sayılır; boşluk ve noktalama sayılmaz. Metin önce NFC'ye çevrilir, böylece
 * ayrışık yazılmış "İ" (I + U+0307) tek harf sayılır.
 */
public final class KarakterSayaci {

    private static final Pattern HARF_RAKAM = Pattern.compile("[\\p{L}\\p{N}]");

    private KarakterSayaci() {}

    public static int say(String metin) {
        if (metin == null || metin.isEmpty()) return 0;
        String nfc = Normalizer.normalize(metin, Normalizer.Form.NFC);
        Matcher m = HARF_RAKAM.matcher(nfc);
        int n = 0;
        while (m.find()) n++;
        return n;
    }

    public static boolean sinirAsildiMi(String metin, int sinir) {
        return say(metin) > sinir;
    }
}
