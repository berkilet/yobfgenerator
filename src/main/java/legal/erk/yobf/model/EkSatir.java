package legal.erk.yobf.model;

/**
 * Kullanıcının eklediği satır.
 *
 * @param alt    false: ana tabloya yeni bir "Etiket : Değer" satırı (konum satırından sonra);
 *               true: konum satırının değer hücresinin içine alt satır ("Etiket: değer", kalın/altı çizili etiket)
 * @param konum  Satirlar anahtarı (ör. Satirlar.KONU); bulunamazsa satır tablonun sonuna eklenir
 * @param etiket ör. "Süre" (boş olabilir: alt satırda yalnızca metin yazılır)
 * @param deger  birden çok satır olabilir
 */
public record EkSatir(boolean alt, String konum, String etiket, String deger) {
    public EkSatir {
        konum = konum == null || konum.isBlank() ? Satirlar.SON : konum;
        etiket = etiket == null ? "" : etiket.trim();
        deger = deger == null ? "" : deger.strip();
    }

    public boolean bos() {
        return etiket.isEmpty() && deger.isEmpty();
    }
}
