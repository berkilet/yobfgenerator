package legal.erk.yobf.rules;

import legal.erk.yobf.config.SirketKaydi;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Ticaret unvanlarını karşılaştırılabilir hale getirir: büyük/küçük harf, Türkçe
 * karakterler, "Tic."↔"Ticaret", "San."↔"Sanayi", "Ltd. Şti.", "A.Ş." varyasyonları,
 * fazla boşluk ve noktalama.
 */
public final class UnvanNormalizer {

    private static final Locale TR = Locale.forLanguageTag("tr-TR");

    /** Kısaltma → açılım. Sıra önemlidir (önce birleşik kalıplar). */
    private static final String[][] KISALTMALAR = {
            {"\\banonim\\s+sirketi\\b", " anonim sirketi "},
            {"\\ba\\s*\\.?\\s*s\\s*\\.?(?=\\s|$|[,;)])", " anonim sirketi "},
            {"\\bltd\\s*\\.?\\s*sti\\b\\.?", " limited sirketi "},
            {"\\blimited\\s+sirketi\\b", " limited sirketi "},
            {"\\bltd\\b\\.?", " limited "},
            {"\\bsti\\b\\.?", " sirketi "},
            {"\\btic\\b\\.?", " ticaret "},
            {"\\bsan\\b\\.?", " sanayi "},
            {"\\bins\\b\\.?", " insaat "},
            {"\\bith\\b\\.?", " ithalat "},
            {"\\bihr\\b\\.?", " ihracat "},
            {"\\bmuh\\b\\.?", " muhendislik "},
            {"\\bmah\\b\\.?", " mahallesi "},
            {"\\bhizm\\b\\.?", " hizmetleri "},
            {"\\bhiz\\b\\.?", " hizmetleri "},
            {"\\bvb\\b\\.?", " "},
    };
    private static final List<Pattern> DESENLER = new ArrayList<>();
    static {
        for (String[] k : KISALTMALAR) DESENLER.add(Pattern.compile(k[0]));
    }

    private UnvanNormalizer() {}

    /** Türkçe karakterleri ASCII'ye indirger ve küçük harfe çevirir. */
    public static String asciiKucuk(String s) {
        if (s == null) return "";
        String k = Normalizer.normalize(s, Normalizer.Form.NFC).toLowerCase(TR);
        StringBuilder b = new StringBuilder(k.length());
        for (int i = 0; i < k.length(); i++) {
            char c = k.charAt(i);
            switch (c) {
                case 'ç' -> b.append('c');
                case 'ğ' -> b.append('g');
                case 'ı', 'î', 'ì', 'í' -> b.append('i');
                case 'ö', 'ô' -> b.append('o');
                case 'ş' -> b.append('s');
                case 'ü', 'û', 'ù', 'ú' -> b.append('u');
                case 'â', 'à', 'á', 'ä' -> b.append('a');
                case 'é', 'è', 'ê' -> b.append('e');
                case '̇' -> { /* birleşik nokta: yok say */ }
                case '’', '‘', '`', '´' -> b.append('\'');
                default -> b.append(c);
            }
        }
        return b.toString();
    }

    public static String normalize(String unvan) {
        String s = asciiKucuk(unvan);
        s = s.replace("&", " ve ");
        s = s.replaceAll("\\s+", " ");
        for (int i = 0; i < DESENLER.size(); i++) {
            s = DESENLER.get(i).matcher(s).replaceAll(KISALTMALAR[i][1]);
        }
        s = s.replaceAll("[^a-z0-9 ]", " ");
        s = s.replaceAll("\\s+", " ").trim();
        return s;
    }

    /** Sondaki "(GE UK)" gibi tanımlı kısaltmayı atar. */
    public static String parantezsiz(String unvan) {
        if (unvan == null) return "";
        return unvan.replaceAll("\\s*\\([^()]*\\)\\s*$", "").trim();
    }

    public static boolean ayniMi(String a, String b) {
        return normalize(a).equals(normalize(b));
    }

    /**
     * Taraf adını grup şirketi kaydıyla eşleştirir.
     * 1) unvan veya takma ad ile tam eşleşme, 2) kanonik unvanın taraf adı içinde geçmesi,
     * 3) en uzun takma adın taraf adı içinde bütün kelime olarak geçmesi.
     */
    public static Optional<SirketKaydi> esle(String tarafAdi, Collection<SirketKaydi> kayitlar) {
        if (tarafAdi == null || tarafAdi.isBlank()) return Optional.empty();
        String n = normalize(tarafAdi);
        String nParantezsiz = normalize(parantezsiz(tarafAdi));
        for (SirketKaydi k : kayitlar) {
            if (normalize(k.unvan()).equals(n) || normalize(k.unvan()).equals(nParantezsiz)) return Optional.of(k);
            for (String t : k.takmaAdlar()) {
                String nt = normalize(t);
                if (nt.equals(n) || nt.equals(nParantezsiz)) return Optional.of(k);
            }
        }
        String bosluklu = " " + n + " ";
        SirketKaydi enIyi = null;
        int enUzun = 0;
        for (SirketKaydi k : kayitlar) {
            String u = normalize(k.unvan());
            if (!u.isEmpty() && bosluklu.contains(" " + u + " ") && u.length() > enUzun) {
                enIyi = k;
                enUzun = u.length();
            }
        }
        if (enIyi != null) return Optional.of(enIyi);
        for (SirketKaydi k : kayitlar) {
            for (String t : k.takmaAdlar()) {
                String nt = normalize(t);
                // tek kelimelik takma adlar (ör. "KALEN") yalnızca tam eşleşmede kullanılır
                if (nt.contains(" ") && bosluklu.contains(" " + nt + " ") && nt.length() > enUzun) {
                    enIyi = k;
                    enUzun = nt.length();
                }
            }
        }
        return Optional.ofNullable(enIyi);
    }

    /** Normalize edilmiş metin içinde normalize edilmiş ifade bütün kelimeler olarak geçiyor mu? */
    public static boolean metindeGeciyor(String ifade, String normalizeKaynak) {
        String n = normalize(ifade);
        if (n.isEmpty()) return false;
        return (" " + normalizeKaynak + " ").contains(" " + n + " ");
    }
}
