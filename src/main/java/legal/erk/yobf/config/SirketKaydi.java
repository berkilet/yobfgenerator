package legal.erk.yobf.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SirketKaydi(
        @JsonProperty("id") String id,
        @JsonProperty("unvan") String unvan,
        @JsonProperty("takma_adlar") List<String> takmaAdlar,
        @JsonProperty("logo") String logo,
        @JsonProperty("logo_yedek") String logoYedek) {

    public SirketKaydi {
        takmaAdlar = takmaAdlar == null ? List.of() : List.copyOf(takmaAdlar);
    }
}
