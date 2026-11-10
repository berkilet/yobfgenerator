package legal.erk.yobf.rules;

import legal.erk.yobf.config.SirketKaydi;
import legal.erk.yobf.config.Sirketler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * CLAUDE.md §4 — form oluşturulurken ilk çalışan kural.
 * Taraflardan grup şirketlerini bulur ve kullanılacak logoyu (veya seçenekleri) döndürür.
 */
public final class LogoCozumleyici {

    private final Sirketler sirketler;
    private final Path logoKlasoru;

    public LogoCozumleyici(Sirketler sirketler, Path logoKlasoru) {
        this.sirketler = sirketler;
        this.logoKlasoru = logoKlasoru;
    }

    /** Bir logo adayı: hangi şirket(ler)den geldiği ve çözümlenmiş dosya. */
    public record Aday(Path dosya, List<SirketKaydi> sirketler) {
        public String etiket() {
            String ad = dosya == null ? "(logo yok)" : dosya.getFileName().toString();
            if (sirketler.isEmpty()) return ad;
            return ad + " — " + String.join(", ", sirketler.stream().map(SirketKaydi::unvan).toList());
        }
        @Override public String toString() { return etiket(); }
    }

    /**
     * @param secili   Seçeneklerden ön seçili olan (ilk eşleşen grup şirketinin logosu)
     * @param adaylar  Farklı logo seçenekleri (tek elemanlıysa kullanıcıya sorulmaz)
     * @param kullaniciyaSor 2+ farklı logo varsa true
     * @param uyari    ör. "Taraflarda grup şirketi tespit edilemedi"
     */
    public record Sonuc(Aday secili, List<Aday> adaylar, boolean kullaniciyaSor,
                        List<SirketKaydi> grupSirketleri, Optional<String> uyari) {
        public Path logo() { return secili.dosya(); }
    }

    public Sonuc coz(List<String> taraflar, Path kullaniciSecimi) {
        List<SirketKaydi> grup = grupSirketleri(taraflar);

        if (kullaniciSecimi != null) {
            Aday a = new Aday(kullaniciSecimi, grup);
            return new Sonuc(a, List.of(a), false, grup, Optional.empty());
        }

        if (grup.isEmpty()) {
            Aday a = new Aday(varsayilan(), List.of());
            return new Sonuc(a, List.of(a), false, grup,
                    Optional.of("Taraflarda grup şirketi tespit edilemedi; varsayılan logo kullanıldı."));
        }

        Map<Path, List<SirketKaydi>> logoyaGore = new LinkedHashMap<>();
        for (SirketKaydi k : grup) {
            logoyaGore.computeIfAbsent(logoDosyasi(k), x -> new ArrayList<>()).add(k);
        }
        List<Aday> adaylar = logoyaGore.entrySet().stream()
                .map(e -> new Aday(e.getKey(), List.copyOf(e.getValue())))
                .toList();
        Optional<String> uyari = adaylar.get(0).dosya() == null
                ? Optional.of("Logo dosyası bulunamadı: assets/logos/" + sirketler.varsayilanLogo())
                : Optional.empty();
        return new Sonuc(adaylar.get(0), adaylar, adaylar.size() > 1, grup, uyari);
    }

    /** Taraflar içinden grup şirketlerini (tekrarsız, ilk geliş sırasıyla) bulur. */
    public List<SirketKaydi> grupSirketleri(List<String> taraflar) {
        Set<SirketKaydi> r = new LinkedHashSet<>();
        if (taraflar != null) {
            for (String t : taraflar) UnvanNormalizer.esle(t, sirketler.sirketler()).ifPresent(r::add);
        }
        return List.copyOf(r);
    }

    /** logo → logo_yedek zinciri → varsayilan_logo. Hiçbiri yoksa null. */
    public Path logoDosyasi(SirketKaydi k) {
        Set<String> ziyaret = new HashSet<>();
        String ad = k.logo();
        SirketKaydi mevcut = k;
        while (ad != null && !ad.isBlank() && ziyaret.add(ad)) {
            Path p = logoKlasoru.resolve(ad);
            if (Files.isRegularFile(p)) return p;
            // yedek zinciri: önce bu kaydın yedeği, sonra yedek dosyayı logo olarak kullanan kaydın yedeği
            String yedek = mevcut == null ? null : mevcut.logoYedek();
            mevcut = yedek == null ? null : kayitByLogo(yedek).orElse(null);
            ad = yedek;
        }
        return varsayilan();
    }

    private Optional<SirketKaydi> kayitByLogo(String logo) {
        return sirketler.sirketler().stream().filter(s -> logo.equals(s.logo())).findFirst();
    }

    public Path varsayilan() {
        Path p = logoKlasoru.resolve(sirketler.varsayilanLogo());
        return Files.isRegularFile(p) ? p : null;
    }
}
