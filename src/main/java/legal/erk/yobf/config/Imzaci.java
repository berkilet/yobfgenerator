package legal.erk.yobf.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** UYGUNDUR bloğundaki bir imzacı. Elle girilen kişilerde id boş olabilir. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Imzaci(
        @JsonProperty("id") String id,
        @JsonProperty("ad") String ad,
        @JsonProperty("unvan") String unvan,
        @JsonProperty("unvan_en") String unvanEn) {

    public Imzaci {
        id = id == null ? "" : id;
        ad = ad == null ? "" : ad;
        unvan = unvan == null ? "" : unvan;
        unvanEn = unvanEn == null ? "" : unvanEn;
    }

    @Override
    public String toString() {
        return unvan.isBlank() ? ad : ad + " — " + unvan;
    }
}
