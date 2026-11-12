package legal.erk.yobf.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Formdaki satır anahtarları. Kullanıcı her satırı forma dahil edip çıkarabilir
 * (FormVerisi.gizliSatirlar) ve bu satırlara göre ek satır / alt satır konumlandırır.
 */
public final class Satirlar {

    private Satirlar() {}

    // Ana tablo satırları
    public static final String TARIH = "TARIH";
    public static final String BELGE_TURU = "BELGE_TURU";
    public static final String TALEP_EDEN = "TALEP_EDEN";
    public static final String IMZAYA_SUNAN = "IMZAYA_SUNAN";
    public static final String KISILER = "KISILER";
    public static final String SIRKETLER = "SIRKETLER";
    public static final String SONA_ERME = "SONA_ERME";
    /** YOBF'de "Belgenin Konusu/Özeti" satırı, azilname/vekaletnamede "Konu" satırı. */
    public static final String KONU = "KONU";
    public static final String IMZASINA_SUNULACAK = "IMZASINA_SUNULACAK";
    /** Ek satır konumu: tablonun sonu. */
    public static final String SON = "SON";

    // Konu bloğunun içindeki alt bölümler (yalnızca dahil/çıkar için)
    public static final String TARAFLAR = "TARAFLAR";
    public static final String BEDEL = "BEDEL";
    public static final String NOT = "NOT";

    /** Ek satır / alt satır konum seçenekleri (arayüzde gösterilen adlarla). */
    public static Map<String, String> konumlar(String formTuru) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(TARIH, "Tarih");
        m.put(BELGE_TURU, "Belge Türü");
        m.put(TALEP_EDEN, "Belgeyi Talep Eden Departman");
        m.put(IMZAYA_SUNAN, "Belgeyi İmzaya Sunan Departman");
        if ("AZILNAME".equals(formTuru)) {
            m.put(KISILER, "Azledilecek Kişiler");
            m.put(SIRKETLER, "Azleden Şirketler");
            m.put(KONU, "Konu");
        } else if ("VEKALETNAME".equals(formTuru)) {
            m.put(KISILER, "Vekaletname Verilecek Kişiler");
            m.put(SIRKETLER, "Vekaletname Veren Şirket");
            m.put(SONA_ERME, "Sona Erme Tarihi");
            m.put(KONU, "Konu");
        } else {
            m.put(KONU, "Belgenin Konusu/Özeti");
        }
        m.put(IMZASINA_SUNULACAK, "İmzasına Sunulacak Yetkili");
        return m;
    }
}
