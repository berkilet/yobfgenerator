package legal.erk.yobf.model;

import java.util.ArrayList;
import java.util.List;

/** Bir sözleşmenin tarafları ve bedel(ler)i. Çoklu sözleşmede numaralı gruplar halinde yazılır (§5.3). */
public class SozlesmeGrubu {
    public final List<String> taraflar = new ArrayList<>();
    public final List<Bedel> bedeller = new ArrayList<>();

    public SozlesmeGrubu() {}

    public SozlesmeGrubu(List<String> taraflar, List<Bedel> bedeller) {
        this.taraflar.addAll(taraflar);
        this.bedeller.addAll(bedeller);
    }
}
