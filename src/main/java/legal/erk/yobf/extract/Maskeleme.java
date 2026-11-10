package legal.erk.yobf.extract;

import legal.erk.yobf.config.Ayarlar;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TCKN, IBAN, telefon ve e-postayı modele gitmeden önce [TCKN] vb. ile değiştirir.
 * Maskelenen değerler yalnızca yerel olarak (bu nesnede) tutulur.
 */
public final class Maskeleme {

    public record Eslesme(String tur, String deger) {}

    public record Sonuc(String metin, List<Eslesme> maskelenenler) {
        public long sayi(String tur) { return maskelenenler.stream().filter(e -> e.tur().equals(tur)).count(); }
    }

    private static final Pattern EPOSTA = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern IBAN = Pattern.compile("\\b[A-Z]{2}\\d{2}(?:[ ]?[A-Z0-9]){11,30}\\b");
    private static final Pattern TCKN = Pattern.compile("(?<![\\d.,])[1-9]\\d{10}(?![\\d.,]\\d)");
    private static final Pattern TELEFON_TR = Pattern.compile(
            "(?<![\\d.,])(?:\\+90[\\s-]?|0090[\\s-]?|0[\\s-]?)\\(?[2-5]\\d{2}\\)?[\\s.-]?\\d{3}[\\s.-]?\\d{2}[\\s.-]?\\d{2}(?!\\d)");
    private static final Pattern TELEFON_INT = Pattern.compile("(?<![\\w+])\\+\\d{1,3}[\\s.-]?(?:\\(?\\d{1,4}\\)?[\\s.-]?){2,5}\\d{2,4}(?!\\d)");

    private final Ayarlar.Maskeleme ayar;

    public Maskeleme(Ayarlar.Maskeleme ayar) {
        this.ayar = ayar;
    }

    public Sonuc uygula(String metin) {
        if (metin == null) return new Sonuc("", List.of());
        List<Eslesme> l = new ArrayList<>();
        String m = metin;
        if (ayar.eposta()) m = degistir(m, EPOSTA, "EPOSTA", l, s -> true);
        if (ayar.iban()) m = degistir(m, IBAN, "IBAN", l, Maskeleme::ibanGecerli);
        if (ayar.tckn()) m = degistir(m, TCKN, "TCKN", l, Maskeleme::tcknGecerli);
        if (ayar.telefon()) {
            m = degistir(m, TELEFON_TR, "TELEFON", l, s -> true);
            m = degistir(m, TELEFON_INT, "TELEFON", l, s -> s.replaceAll("\\D", "").length() >= 8);
        }
        return new Sonuc(m, List.copyOf(l));
    }

    private static String degistir(String metin, Pattern p, String tur, List<Eslesme> l,
                                   java.util.function.Predicate<String> kontrol) {
        Matcher mt = p.matcher(metin);
        StringBuilder sb = new StringBuilder();
        while (mt.find()) {
            String g = mt.group();
            if (kontrol.test(g)) {
                l.add(new Eslesme(tur, g));
                mt.appendReplacement(sb, Matcher.quoteReplacement("[" + tur + "]"));
            } else {
                mt.appendReplacement(sb, Matcher.quoteReplacement(g));
            }
        }
        mt.appendTail(sb);
        return sb.toString();
    }

    /** T.C. Kimlik No algoritması (10. ve 11. hane kontrolü). */
    public static boolean tcknGecerli(String s) {
        if (s == null || !s.matches("[1-9]\\d{10}")) return false;
        int[] d = s.chars().map(c -> c - '0').toArray();
        int tek = d[0] + d[2] + d[4] + d[6] + d[8];
        int cift = d[1] + d[3] + d[5] + d[7];
        int d10 = Math.floorMod(tek * 7 - cift, 10);
        int d11 = (tek + cift + d[9]) % 10;
        return d[9] == d10 && d[10] == d11;
    }

    /** ISO 13616 mod-97 kontrolü. */
    public static boolean ibanGecerli(String s) {
        String i = s.replace(" ", "").toUpperCase();
        if (i.length() < 15 || i.length() > 34) return false;
        if (i.startsWith("TR") && i.length() != 26) return false;
        String r = i.substring(4) + i.substring(0, 4);
        StringBuilder n = new StringBuilder();
        for (char c : r.toCharArray()) {
            if (Character.isDigit(c)) n.append(c);
            else if (c >= 'A' && c <= 'Z') n.append(c - 'A' + 10);
            else return false;
        }
        return new BigInteger(n.toString()).mod(BigInteger.valueOf(97)).intValue() == 1;
    }
}
