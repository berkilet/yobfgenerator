package legal.erk.yobf.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Sirketler(
        @JsonProperty("varsayilan_logo") String varsayilanLogo,
        @JsonProperty("sirketler") List<SirketKaydi> sirketler) {

    public Sirketler {
        sirketler = sirketler == null ? List.of() : List.copyOf(sirketler);
        if (varsayilanLogo == null || varsayilanLogo.isBlank()) varsayilanLogo = "kalyon-enerji.png";
    }
}
