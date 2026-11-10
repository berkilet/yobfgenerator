package legal.erk.yobf.rules;

import legal.erk.yobf.ai.OzetSemasi;
import legal.erk.yobf.config.Ayarlar;
import legal.erk.yobf.config.Imzaci;
import legal.erk.yobf.config.SirketKaydi;
import legal.erk.yobf.config.Yapilandirma;
import legal.erk.yobf.model.Bedel;
import legal.erk.yobf.model.FormVerisi;
import legal.erk.yobf.model.SozlesmeGrubu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Model çıktısını düzenleme ekranına gidecek FormVerisi'ne çevirir:
 * kanonik unvanlar, grup şirketi önce sıralaması, form türü önerisi, departman ön ayarları.
 * Not alanı ASLA doldurulmaz (§5.4).
 */
public final class FormHazirlayici {

    private final Yapilandirma y;

    public FormHazirlayici(Yapilandirma y) {
        this.y = y;
    }

    public FormVerisi hazirla(OzetSemasi o, int belgeSayisi) {
        FormVerisi f = new FormVerisi();
        f.tarih = TarihKontrol.bugun();
        f.formTuru = formTuru(o);
        f.belgeTuru = belgeTuru(o.belgeTuru(), f.formTuru);
        f.imzayaSunanDepartman = nz(y.ayarlar().imzayaSunanVarsayilan());
        f.konuTr = nz(o.konuTr()).trim();
        f.konuEn = nz(o.konuEn()).trim();

        if (f.azilname() || f.vekaletname()) {
            f.kisiler.addAll(o.kisiler().stream().map(String::trim).filter(s -> !s.isEmpty()).toList());
            f.sirketler.addAll(sirala(o.taraflar().stream().map(OzetSemasi.Taraf::unvan).toList()));
            f.sonaErmeTarihi = nz(o.sonaErmeTarihi()).trim();
        } else {
            TreeMap<Integer, SozlesmeGrubu> gruplar = new TreeMap<>();
            int max = Math.max(1, belgeSayisi);
            for (OzetSemasi.Taraf t : o.taraflar()) {
                int no = Math.min(max, Math.max(1, t.belgeNo()));
                gruplar.computeIfAbsent(no, k -> new SozlesmeGrubu()).taraflar.add(t.unvan());
            }
            for (OzetSemasi.BedelOge b : o.bedel()) {
                int no = Math.min(max, Math.max(1, b.belgeNo()));
                gruplar.computeIfAbsent(no, k -> new SozlesmeGrubu()).bedeller
                        .add(new Bedel(b.tutar(), b.paraBirimi(), b.vergiNotu()));
            }
            if (gruplar.isEmpty()) gruplar.put(1, new SozlesmeGrubu());
            for (SozlesmeGrubu g : gruplar.values()) {
                List<String> s = sirala(g.taraflar);
                g.taraflar.clear();
                g.taraflar.addAll(s);
                f.sozlesmeler.add(g);
            }
            f.bedelEtiketi = bedelEtiketi(o, f.belgeTuru);
        }
        return f;
    }

    /** Grup şirketleri kanonik unvanla ve önce; tekrarlar atılır. */
    public List<String> sirala(List<String> taraflar) {
        List<String> grup = new ArrayList<>();
        List<String> diger = new ArrayList<>();
        for (String t : taraflar) {
            if (t == null || t.isBlank()) continue;
            Optional<SirketKaydi> k = UnvanNormalizer.esle(t, y.sirketler().sirketler());
            if (k.isPresent()) {
                String kanonik = k.get().unvan();
                if (grup.stream().noneMatch(x -> UnvanNormalizer.ayniMi(x, kanonik))) grup.add(kanonik);
            } else {
                String temiz = t.trim().replaceAll("\\s+", " ");
                if (diger.stream().noneMatch(x -> UnvanNormalizer.ayniMi(x, temiz))) diger.add(temiz);
            }
        }
        grup.addAll(diger);
        return grup;
    }

    public boolean grupSirketiMi(String ad) {
        return UnvanNormalizer.esle(ad, y.sirketler().sirketler()).isPresent();
    }

    private String formTuru(OzetSemasi o) {
        String oneri = nz(o.formTuruOnerisi()).trim().toUpperCase(Locale.ROOT);
        if (y.ayarlar().formTuru(oneri).isPresent()) return oneri;
        String bt = UnvanNormalizer.asciiKucuk(nz(o.belgeTuru()));
        if (bt.startsWith("azil")) return "AZILNAME";
        if (bt.startsWith("vekalet")) return "VEKALETNAME";
        return "YOBF";
    }

    /** Model çıktısını ayarlar.json'daki belge türü listesine eşler. */
    private String belgeTuru(String modelTuru, String formTuru) {
        List<String> liste = y.ayarlar().belgeTurleri();
        String n = UnvanNormalizer.asciiKucuk(nz(modelTuru)).trim();
        for (String s : liste) if (UnvanNormalizer.asciiKucuk(s).equals(n)) return s;
        for (String s : liste) {
            String k = UnvanNormalizer.asciiKucuk(s);
            if (!n.isEmpty() && (k.startsWith(n) || n.startsWith(k))) return s;
        }
        if ("AZILNAME".equals(formTuru)) return liste.stream().filter(s -> s.startsWith("Azil")).findFirst().orElse("Azilname");
        return nz(modelTuru).isBlank() ? "" : modelTuru.trim();
    }

    private static String bedelEtiketi(OzetSemasi o, String belgeTuru) {
        Optional<String> m = o.bedel().stream().map(OzetSemasi.BedelOge::etiket)
                .filter(s -> s != null && !s.isBlank()).findFirst();
        if (m.isPresent()) return m.get().trim().replaceAll(":$", "");
        if ("Zeyilname".equals(belgeTuru)) return "Zeyilnameye Konu Tutar";
        return "Bedel";
    }

    /** Talep eden departman seçildiğinde varsayılan imzacılar ve "İmzasına Sunulacak" (§6). */
    public void departmanOnAyari(FormVerisi f, String departman) {
        Ayarlar.DepartmanOnAyari a = y.ayarlar().departmanOnAyarlari().get(departman);
        if (a == null) return;
        f.anaImzacilar.clear();
        for (String id : a.imzacilar()) y.imzacilar().bul(id).ifPresent(f.anaImzacilar::add);
        if (!a.imzasinaSunulacak().isEmpty()) {
            f.imzasinaSunulacak.clear();
            f.imzasinaSunulacak.addAll(a.imzasinaSunulacak());
        }
    }

    /** Unvanlar her zaman imzacilar.json'daki kanonik halinden gelir. */
    public Imzaci kanonik(Imzaci i) {
        if (i == null) return null;
        Optional<Imzaci> k = i.id().isBlank() ? y.imzacilar().adaGoreBul(i.ad()) : y.imzacilar().bul(i.id());
        return k.orElse(i);
    }

    static String nz(String s) { return s == null ? "" : s; }

    static Comparator<String> alfabetik() { return Comparator.comparing(UnvanNormalizer::asciiKucuk); }
}
