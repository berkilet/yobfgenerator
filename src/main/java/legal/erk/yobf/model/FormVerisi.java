package legal.erk.yobf.model;

import legal.erk.yobf.config.Imzaci;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Forma yazılacak tüm alanlar. Kullanıcı onayından sonra FormYazici'ya verilir. */
public class FormVerisi {

    public String formTuru = "YOBF";

    // Ortak üst alanlar
    public String tarih = "";
    public String belgeTuru = "";
    public String talepEdenDepartman = "";
    public String imzayaSunanDepartman = "";

    // YOBF / YOBF_TR_EN
    public final List<SozlesmeGrubu> sozlesmeler = new ArrayList<>();
    public String bedelEtiketi = "Bedel";
    public String konuTr = "";
    public String konuEn = "";

    // AZILNAME / VEKALETNAME
    public final List<String> kisiler = new ArrayList<>();
    public final List<String> sirketler = new ArrayList<>();
    public String sonaErmeTarihi = "";

    // Not (hukukçu yazar; model doldurmaz)
    public String notEtiketi = "Not:";
    public String notMetni = "";

    // İmzasına Sunulacak Yetkili: her satır ör. "Sn. Murathan KALYONCU (Kalyon İşletme için)"
    public final List<String> imzasinaSunulacak = new ArrayList<>();

    // UYGUNDUR bloğu (1–5) + 1 ek onay
    public final List<Imzaci> anaImzacilar = new ArrayList<>();
    public boolean ekOnayAcik = false;
    public String ekOnayBaslik = "ONAY";
    public Imzaci ekOnayImzaci;

    public Path logo;

    /** Forma dahil edilmeyecek satırlar (Satirlar anahtarları). */
    public final java.util.Set<String> gizliSatirlar = new java.util.HashSet<>();
    /** Kullanıcının eklediği satırlar ve alt satırlar. */
    public final List<EkSatir> ekSatirlar = new ArrayList<>();

    public boolean dahil(String satir) { return !gizliSatirlar.contains(satir); }

    public boolean ikiDilli() { return "YOBF_TR_EN".equals(formTuru); }
    public boolean azilname() { return "AZILNAME".equals(formTuru); }
    public boolean vekaletname() { return "VEKALETNAME".equals(formTuru); }

    /** Tüm sözleşme gruplarındaki taraflar (sıralı, tekrarlı olabilir). */
    public List<String> tumTaraflar() {
        List<String> r = new ArrayList<>();
        for (SozlesmeGrubu g : sozlesmeler) r.addAll(g.taraflar);
        return r;
    }

    /** Logo ve grup şirketi tespiti için bakılacak şirket adları. */
    public List<String> logoIcinSirketler() {
        return (azilname() || vekaletname()) ? new ArrayList<>(sirketler) : tumTaraflar();
    }

    /** Karşı taraf: grup şirketi olmayan ilk taraf (dosya adı için). */
    public String ilkTaraf(java.util.function.Predicate<String> grupMu) {
        for (String t : logoIcinSirketler()) if (!grupMu.test(t)) return t;
        List<String> l = logoIcinSirketler();
        return l.isEmpty() ? "" : l.get(0);
    }
}
