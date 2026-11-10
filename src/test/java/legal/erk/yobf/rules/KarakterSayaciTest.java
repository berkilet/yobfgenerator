package legal.erk.yobf.rules;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class KarakterSayaciTest {

    @Test
    void turkceKarakterlerTekHarfSayilir() {
        assertEquals(8, KarakterSayaci.say("çğıİöşüâ"));
        assertEquals(8, KarakterSayaci.say("ÇĞIİÖŞÜÂ"));
        // Ayrışık yazılmış İ (I + U+0307) ve ü (u + U+0308) NFC ile tek harf sayılır
        assertEquals(2, KarakterSayaci.say("İü"));
    }

    @Test
    void boslukVeNoktalamaSayilmaz() {
        assertEquals(0, KarakterSayaci.say(" .,;:!?()\"'’“”-–—/\n\t"));
        assertEquals(9, KarakterSayaci.say("3.570.000 TL"));
        assertEquals(0, KarakterSayaci.say(null));
    }

    // CLAUDE.md §11 — örnek formlardaki Konu metinleri için beklenen değerler
    @ParameterizedTest(name = "{0} = {1}")
    @CsvSource(delimiter = '|', value = {
            "Venüs|194|Karapınar 2 projemiz kapsamında ÇSED sonrası biyoçeşitlilik ek izlemeleri hizmet sözleşmesi kapsamında ilave işlerin sözleşme kapsamına dahil edilmesine ilişkin olarak hazırlanan zeyilname, onay ve imzanıza sunulmaktadır.",
            "GE VO|213|Karapınar-1 GES Projesi kapsamında imzalanan GE kontratına ek yedek parça siparişlerinin (Inverters, RMUs, DC Combiner Boxes, Tracker Systems) dahil edilmesine ilişkin olarak hazırlanan Variation Form No.12’nin onay ve imzanıza sunulmasından ibarettir.",
            "O&M|215|Karapınar-2 GES projesinin kısmen işletmeye geçmiş olması sebebiyle 19.06.2026 tarihinden itibaren geçerli olmak üzere maliyet + kar kapsamında anlaşılan 31.12.2026'ya kadar geçerli olacak şekilde düzenlenen O&M sözleşmesi onay ve imzanıza sunulmaktadır.",
            "Geocell|273|Gaziantep GES Projemizde gerçekleştirilecek “Geocell” imalatları kapsamında 1. Sözleşme yüklenicisi olan firma saha toprak işlerini gerçekleştirecek olup, 2. Sözleşme yüklenicisi olan firma ise malzeme tedariki ve serim işlerini gerçekleştirecektir. Bu kapsamda hazırlanan sözleşmeler onay ve imzanıza sunulmaktadır.",
            "ADCB|279|Finansman kapsamında Kalyon YEKA RES 3 Rüzgar Enerjisi Yatırımları A.Ş. iştirakimiz için Abu Dhabi Commercial Bank PJSC nezdinde hesap açılması, işletilmesi, bankacılık ve finansman hizmetlerinin sağlanması ile bu kapsamda gerekli yetkilendirmelerin yapılmasına ilişkin yönetim kurulu kararı onay ve imzanıza sunulmaktadır.",
    })
    void ornekKonuMetinleri(String ad, int beklenen, String metin) {
        assertEquals(beklenen, KarakterSayaci.say(metin), ad);
    }

    @Test
    void sinirKontrolu() {
        assertFalse(KarakterSayaci.sinirAsildiMi("a".repeat(400), 400));
        assertTrue(KarakterSayaci.sinirAsildiMi("a".repeat(401), 400));
        assertFalse(KarakterSayaci.sinirAsildiMi("a ".repeat(400), 400));
    }
}
