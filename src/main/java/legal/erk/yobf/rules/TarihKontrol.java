package legal.erk.yobf.rules;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Optional;
import java.util.regex.Pattern;

/** GG.AA.YYYY biçimi ve tarih geçerliliği. "0110.2026" gibi yazım hataları reddedilir. */
public final class TarihKontrol {

    public static final DateTimeFormatter BICIM = DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT);
    private static final Pattern DESEN = Pattern.compile("^\\d{2}\\.\\d{2}\\.\\d{4}$");

    private TarihKontrol() {}

    public static Optional<LocalDate> coz(String s) {
        if (s == null) return Optional.empty();
        String t = s.trim();
        if (!DESEN.matcher(t).matches()) return Optional.empty();
        try {
            return Optional.of(LocalDate.parse(t, BICIM));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    public static boolean gecerliMi(String s) {
        return coz(s).isPresent();
    }

    public static String bugun() {
        return LocalDate.now().format(BICIM);
    }

    public static String dosyaAdiTarihi(LocalDate d) {
        return d.format(DateTimeFormatter.ofPattern("ddMMuuuu"));
    }
}
