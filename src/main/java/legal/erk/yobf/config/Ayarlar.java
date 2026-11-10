package legal.erk.yobf.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Ayarlar(
        @JsonProperty("model") String model,
        @JsonProperty("konu_max_karakter") int konuMaxKarakter,
        @JsonProperty("konu_sinir_sert") boolean konuSinirSert,
        @JsonProperty("kisaltma_deneme_sayisi") int kisaltmaDenemeSayisi,
        @JsonProperty("maskeleme") Maskeleme maskeleme,
        @JsonProperty("proxy") Proxy proxy,
        @JsonProperty("form_turleri") List<FormTuru> formTurleri,
        @JsonProperty("belge_turleri") List<String> belgeTurleri,
        @JsonProperty("talep_eden_departmanlar") List<String> talepEdenDepartmanlar,
        @JsonProperty("imzaya_sunan_departmanlar") List<String> imzayaSunanDepartmanlar,
        @JsonProperty("imzaya_sunan_varsayilan") String imzayaSunanVarsayilan,
        @JsonProperty("imza_blogu") ImzaBlogu imzaBlogu,
        @JsonProperty("departman_on_ayarlari") Map<String, DepartmanOnAyari> departmanOnAyarlari,
        @JsonProperty("sunucu_tarafi_yedek_model") Boolean sunucuTarafiYedekModel) {

    public Ayarlar {
        if (konuMaxKarakter <= 0) konuMaxKarakter = 400;
        if (kisaltmaDenemeSayisi < 0) kisaltmaDenemeSayisi = 0;
        if (maskeleme == null) maskeleme = new Maskeleme(true, true, true, true);
        if (proxy == null) proxy = new Proxy("", 0);
        formTurleri = formTurleri == null ? List.of() : List.copyOf(formTurleri);
        belgeTurleri = belgeTurleri == null ? List.of() : List.copyOf(belgeTurleri);
        talepEdenDepartmanlar = talepEdenDepartmanlar == null ? List.of() : List.copyOf(talepEdenDepartmanlar);
        imzayaSunanDepartmanlar = imzayaSunanDepartmanlar == null ? List.of() : List.copyOf(imzayaSunanDepartmanlar);
        if (imzaBlogu == null) imzaBlogu = new ImzaBlogu("UYGUNDUR", "APPROVED", 1, 5, null, false, "ONAY", "APPROVAL");
        departmanOnAyarlari = departmanOnAyarlari == null ? Map.of() : new LinkedHashMap<>(departmanOnAyarlari);
        // Model bir isteği reddederse (stop_reason=refusal) sunucu tarafında yedek modele geçilir.
        if (sunucuTarafiYedekModel == null) sunucuTarafiYedekModel = true;
    }

    public Optional<FormTuru> formTuru(String kod) {
        return formTurleri.stream().filter(f -> f.kod().equals(kod)).findFirst();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Maskeleme(
            @JsonProperty("tckn") boolean tckn,
            @JsonProperty("iban") boolean iban,
            @JsonProperty("telefon") boolean telefon,
            @JsonProperty("eposta") boolean eposta) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Proxy(@JsonProperty("host") String host, @JsonProperty("port") int port) {
        public boolean tanimli() { return host != null && !host.isBlank() && port > 0; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FormTuru(
            @JsonProperty("kod") String kod,
            @JsonProperty("baslik") String baslik,
            @JsonProperty("baslik_en") String baslikEn) {
        @Override public String toString() { return kod + " — " + baslik; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ImzaBlogu(
            @JsonProperty("ana_baslik") String anaBaslik,
            @JsonProperty("ana_baslik_en") String anaBaslikEn,
            @JsonProperty("ana_imzaci_min") int anaImzaciMin,
            @JsonProperty("ana_imzaci_max") int anaImzaciMax,
            @JsonProperty("yerlesim") Map<String, List<Integer>> yerlesim,
            @JsonProperty("ek_onay_acik_varsayilan") boolean ekOnayAcikVarsayilan,
            @JsonProperty("ek_onay_baslik") String ekOnayBaslik,
            @JsonProperty("ek_onay_baslik_en") String ekOnayBaslikEn) {

        public ImzaBlogu {
            if (anaBaslik == null) anaBaslik = "UYGUNDUR";
            if (anaBaslikEn == null) anaBaslikEn = "APPROVED";
            if (anaImzaciMin <= 0) anaImzaciMin = 1;
            if (anaImzaciMax <= 0) anaImzaciMax = 5;
            if (yerlesim == null || yerlesim.isEmpty()) {
                yerlesim = Map.of("1", List.of(1), "2", List.of(2), "3", List.of(3),
                        "4", List.of(2, 2), "5", List.of(3, 2));
            }
            if (ekOnayBaslik == null) ekOnayBaslik = "ONAY";
            if (ekOnayBaslikEn == null) ekOnayBaslikEn = "APPROVAL";
        }

        /** n imzacı için satır dağılımı, ör. 5 → [3, 2]. */
        public List<Integer> satirlar(int n) {
            List<Integer> s = yerlesim.get(String.valueOf(n));
            if (s != null && s.stream().mapToInt(Integer::intValue).sum() == n) return s;
            // Tanımsızsa: 3'lük satırlar
            List<Integer> r = new java.util.ArrayList<>();
            for (int kalan = n; kalan > 0; kalan -= 3) r.add(Math.min(3, kalan));
            return r;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DepartmanOnAyari(
            @JsonProperty("imzacilar") List<String> imzacilar,
            @JsonProperty("imzasina_sunulacak") List<String> imzasinaSunulacak) {
        public DepartmanOnAyari {
            imzacilar = imzacilar == null ? List.of() : List.copyOf(imzacilar);
            imzasinaSunulacak = imzasinaSunulacak == null ? List.of() : List.copyOf(imzasinaSunulacak);
        }
    }
}
