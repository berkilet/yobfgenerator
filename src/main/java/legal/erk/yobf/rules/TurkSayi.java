package legal.erk.yobf.rules;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Türk (3.570.000,00) ve İngiliz (3,570,000.00) sayı biçimleri arasında dönüşüm. */
public final class TurkSayi {

    private static final Pattern SAYI = Pattern.compile("\\d{1,3}(?:[.,  ']\\d{3})+(?:[.,]\\d{1,2})?|\\d+(?:[.,]\\d{1,2})?");

    private TurkSayi() {}

    /** Tek bir sayı ifadesini çözer; hem Türk hem İngiliz biçimi denenir. */
    public static Set<BigDecimal> coz(String ifade) {
        Set<BigDecimal> r = new LinkedHashSet<>();
        if (ifade == null) return r;
        String s = ifade.trim().replace(" ", "").replace(" ", "").replace(" ", "").replace("'", "");
        if (s.isEmpty() || !s.matches("[\\d.,]+")) return r;
        cevir(s, '.', ',').ifPresent(r::add);   // Türk: binlik '.', ondalık ','
        cevir(s, ',', '.').ifPresent(r::add);   // İngiliz: binlik ',', ondalık '.'
        return r;
    }

    private static Optional<BigDecimal> cevir(String s, char binlik, char ondalik) {
        int o = s.lastIndexOf(ondalik);
        String tam = o >= 0 ? s.substring(0, o) : s;
        String kesir = o >= 0 ? s.substring(o + 1) : "";
        if (kesir.indexOf(binlik) >= 0 || kesir.indexOf(ondalik) >= 0) return Optional.empty();
        if (tam.indexOf(ondalik) >= 0) return Optional.empty();
        if (tam.indexOf(binlik) >= 0 && !tam.matches("\\d{1,3}(\\" + binlik + "\\d{3})+")) return Optional.empty();
        String d = tam.replace(String.valueOf(binlik), "") + (kesir.isEmpty() ? "" : "." + kesir);
        try {
            BigDecimal v = new BigDecimal(d).stripTrailingZeros();
            return Optional.of(v.scale() < 0 ? v.setScale(0) : v);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** Metindeki tüm sayıları (her iki biçim yorumuyla) toplar. */
    public static Set<BigDecimal> metindekiSayilar(String metin) {
        Set<BigDecimal> r = new LinkedHashSet<>();
        if (metin == null) return r;
        Matcher m = SAYI.matcher(metin);
        while (m.find()) r.addAll(coz(m.group()));
        return r;
    }

    /** Bir tutar ifadesindeki ilk sayıyı bulur ("3.570.000 TL + KDV" → 3570000). */
    public static Set<BigDecimal> tutardakiSayi(String tutar) {
        if (tutar == null) return Set.of();
        Matcher m = SAYI.matcher(tutar);
        return m.find() ? coz(m.group()) : Set.of();
    }

    /** 3570000 → "3.570.000"; 497746.5 → "497.746,50" */
    public static String bicimle(BigDecimal d) {
        java.text.DecimalFormatSymbols sym = new java.text.DecimalFormatSymbols(java.util.Locale.forLanguageTag("tr-TR"));
        sym.setGroupingSeparator('.');
        sym.setDecimalSeparator(',');
        java.text.DecimalFormat f = new java.text.DecimalFormat(d.scale() > 0 ? "#,##0.00" : "#,##0", sym);
        return f.format(d);
    }
}
