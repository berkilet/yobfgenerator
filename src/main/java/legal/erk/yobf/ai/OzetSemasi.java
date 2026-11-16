package legal.erk.yobf.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/** CLAUDE.md §7 — modelden dönen yapılandırılmış çıktı. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OzetSemasi(
        @JsonProperty("belge_turu")
        @JsonPropertyDescription("Sözleşme | Zeyilname | Protokol | Gizlilik Sözleşmesi | Variation Order | Yönetim Kurulu Kararı | Vekaletname | Azilname | Avukatlık Ücret Sözleşmesi | Diğer")
        String belgeTuru,

        @JsonProperty("form_turu_onerisi")
        @JsonPropertyDescription("YOBF | YOBF_TR_EN | AZILNAME | VEKALETNAME")
        String formTuruOnerisi,

        @JsonProperty("taraflar")
        @JsonPropertyDescription("Belgenin tarafları; grup şirketleri önce. Azilname/vekaletnamede azleden / vekaletname veren şirketler.")
        List<Taraf> taraflar,

        @JsonProperty("konu_tr")
        @JsonPropertyDescription("Türkçe konu/özet metni. Bedel yazılmaz.")
        String konuTr,

        @JsonProperty("konu_en")
        @JsonPropertyDescription("İngilizce konu metni; yalnızca iki dilli form gerekiyorsa doldurulur, aksi halde boş.")
        String konuEn,

        @JsonProperty("bedel")
        @JsonPropertyDescription("Belgede yazan bedel(ler). Bedel yoksa boş liste.")
        List<BedelOge> bedel,

        @JsonProperty("kisiler")
        @JsonPropertyDescription("Azilname/vekaletnamede azledilecek / vekaletname verilecek kişilerin adları. Diğer belgelerde boş.")
        List<String> kisiler,

        @JsonProperty("sona_erme_tarihi")
        @JsonPropertyDescription("Vekaletname sona erme tarihi GG.AA.YYYY; yoksa boş.")
        String sonaErmeTarihi,

        @JsonProperty("dikkat_hususlari")
        @JsonPropertyDescription("En fazla 5 madde; hukukçuya öneri, forma yazılmaz.")
        List<String> dikkatHususlari,

        @JsonProperty("guven")
        Guven guven,

        @JsonProperty("belgeler")
        @JsonPropertyDescription("Verilen her belgenin sıra numarası ve türü (tek belgede tek eleman).")
        List<BelgeBilgisi> belgeler,

        @JsonProperty("coklu_belge_iliskisi")
        @JsonPropertyDescription("Birden fazla belgede: AYRI_BELGELER (bağımsız sözleşme/protokol vb., ör. aynı projede iki ayrı yükleniciyle iki sözleşme) veya AYNI_ISLEM (aynı işin/başvurunun parçaları, ör. dilekçe + taahhütname + yönetim kurulu kararı). Tek belgede boş.")
        String cokluBelgeIliskisi) {

    public OzetSemasi {
        taraflar = taraflar == null ? List.of() : taraflar;
        bedel = bedel == null ? List.of() : bedel;
        kisiler = kisiler == null ? List.of() : kisiler;
        dikkatHususlari = dikkatHususlari == null ? List.of() : dikkatHususlari;
        if (guven == null) guven = new Guven(0, 0);
        belgeler = belgeler == null ? List.of() : belgeler;
        if (cokluBelgeIliskisi == null) cokluBelgeIliskisi = "";
    }

    public boolean ayniIslem() { return "AYNI_ISLEM".equalsIgnoreCase(cokluBelgeIliskisi.trim()); }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BelgeBilgisi(
            @JsonProperty("belge_no") @JsonPropertyDescription("Belgenin sıra numarası (1'den başlar)") int belgeNo,
            @JsonProperty("belge_turu") @JsonPropertyDescription("Belgenin türü: Sözleşme, Protokol, Zeyilname, Taahhütname, Dilekçe, Yönetim Kurulu Kararı, Variation Order, Gizlilik Sözleşmesi … (belgenin kendi başlığına göre, kısa)") String belgeTuru) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Taraf(
            @JsonProperty("unvan") String unvan,
            @JsonProperty("rol") @JsonPropertyDescription("ör. İşveren, Yüklenici, Alıcı, Satıcı") String rol,
            @JsonProperty("belgedeki_ifade") @JsonPropertyDescription("Unvanın belgede geçtiği aynen hali") String belgedekiIfade,
            @JsonProperty("belge_no") @JsonPropertyDescription("Birden fazla belge verildiyse tarafın ait olduğu belgenin sıra numarası (1'den başlar); tek belgede 1") int belgeNo) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BedelOge(
            @JsonProperty("etiket") @JsonPropertyDescription("Bedel | Sözleşme Bedeli | Zeyilnameye Konu Tutar") String etiket,
            @JsonProperty("tutar") @JsonPropertyDescription("Türk sayı biçimi, ör. 3.570.000 veya 497.746,00") String tutar,
            @JsonProperty("para_birimi") @JsonPropertyDescription("TL | USD | EUR | GBP ...") String paraBirimi,
            @JsonProperty("vergi_notu") @JsonPropertyDescription("ör. '+ KDV' veya '(KDV ve stopaj dahil değildir.)'; yoksa boş") String vergiNotu,
            @JsonProperty("belge_no") @JsonPropertyDescription("Bedelin ait olduğu belgenin sıra numarası (1'den başlar)") int belgeNo) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Guven(
            @JsonProperty("taraflar") @JsonPropertyDescription("0.0–1.0") double taraflar,
            @JsonProperty("bedel") @JsonPropertyDescription("0.0–1.0") double bedel) {}

    /** Kısaltma çağrısının çıktısı. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Kisaltma(
            @JsonProperty("konu_tr") String konuTr,
            @JsonProperty("konu_en") String konuEn) {}
}
