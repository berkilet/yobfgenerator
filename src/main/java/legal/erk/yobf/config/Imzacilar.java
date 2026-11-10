package legal.erk.yobf.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Imzacilar(
        @JsonProperty("imzacilar") List<Imzaci> imzacilar,
        @JsonProperty("imzasina_sunulacak_yetkililer") List<String> imzasinaSunulacakYetkililer) {

    public Imzacilar {
        imzacilar = imzacilar == null ? List.of() : List.copyOf(imzacilar);
        imzasinaSunulacakYetkililer = imzasinaSunulacakYetkililer == null ? List.of() : List.copyOf(imzasinaSunulacakYetkililer);
    }

    public Optional<Imzaci> bul(String id) {
        return imzacilar.stream().filter(i -> i.id().equals(id)).findFirst();
    }

    /** Ada göre kanonik kaydı bulur (elle girilen adı kanonik unvana bağlamak için). */
    public Optional<Imzaci> adaGoreBul(String ad) {
        if (ad == null) return Optional.empty();
        String n = legal.erk.yobf.rules.UnvanNormalizer.normalize(ad);
        return imzacilar.stream()
                .filter(i -> legal.erk.yobf.rules.UnvanNormalizer.normalize(i.ad()).equals(n))
                .findFirst();
    }
}
