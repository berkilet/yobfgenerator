package legal.erk.yobf.rules;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class TurkSayiTest {
    @Test
    void ikiBicimDeCozulur() {
        assertTrue(TurkSayi.coz("3.570.000").contains(new BigDecimal("3570000")));
        assertTrue(TurkSayi.coz("497.746,00").contains(new BigDecimal("497746")));
        assertTrue(TurkSayi.coz("497,746.00").contains(new BigDecimal("497746")));
        assertTrue(TurkSayi.tutardakiSayi("145.000 EUR + KDV").contains(new BigDecimal("145000")));
        assertTrue(TurkSayi.metindekiSayilar("bedel USD 3,000,000.00 olup").contains(new BigDecimal("3000000")));
    }

    @Test
    void bicimleme() {
        assertEquals("3.570.000", TurkSayi.bicimle(new BigDecimal("3570000")));
        assertEquals("497.746,50", TurkSayi.bicimle(new BigDecimal("497746.50")));
    }
}
