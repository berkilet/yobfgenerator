package legal.erk.yobf.model;

/** Ör. tutar "3.570.000", paraBirimi "TL", vergiNotu "+ KDV" → "3.570.000 TL + KDV". */
public record Bedel(String tutar, String paraBirimi, String vergiNotu) {

    public Bedel {
        tutar = tutar == null ? "" : tutar.trim();
        paraBirimi = paraBirimi == null ? "" : paraBirimi.trim();
        vergiNotu = vergiNotu == null ? "" : vergiNotu.trim();
    }

    public String metin() {
        StringBuilder b = new StringBuilder(tutar);
        if (!paraBirimi.isEmpty() && !tutar.contains(paraBirimi)) b.append(b.isEmpty() ? "" : " ").append(paraBirimi);
        if (!vergiNotu.isEmpty()) b.append(b.isEmpty() ? "" : " ").append(vergiNotu);
        return b.toString().trim();
    }

    public boolean bos() {
        return tutar.isEmpty() && paraBirimi.isEmpty() && vergiNotu.isEmpty();
    }

    /** Düzenleme ekranındaki serbest metinden geri çözer. */
    public static Bedel metindenOlustur(String metin) {
        if (metin == null || metin.isBlank()) return new Bedel("", "", "");
        return new Bedel(metin.trim(), "", "");
    }
}
