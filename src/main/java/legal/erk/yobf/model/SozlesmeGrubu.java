package legal.erk.yobf.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Bir belgenin (sözleşme, protokol, taahhütname …) tarafları ve bedel(ler)i.
 * Birden fazla ayrı belgede numaralı gruplar halinde yazılır (§5.3): "1. Sözleşme Taraflar:", "2. Protokol Taraflar:".
 */
public class SozlesmeGrubu {
    /** Grubun belge türü (ör. "Sözleşme", "Protokol"); boşsa formun belge türü / "Belge" kullanılır. */
    public String belgeTuru = "";
    public final List<String> taraflar = new ArrayList<>();
    public final List<Bedel> bedeller = new ArrayList<>();

    public SozlesmeGrubu() {}

    public SozlesmeGrubu(List<String> taraflar, List<Bedel> bedeller) {
        this.taraflar.addAll(taraflar);
        this.bedeller.addAll(bedeller);
    }
}
