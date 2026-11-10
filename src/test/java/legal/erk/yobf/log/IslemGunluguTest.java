package legal.erk.yobf.log;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IslemGunluguTest {
    @Test
    void satirEklenir(@TempDir Path d) throws Exception {
        IslemGunlugu g = new IslemGunlugu(d.resolve("kayit/islem_gunlugu.csv"));
        g.ekle(List.of("a.pdf"), "YOBF", List.of("X A.Ş.", "Y; Ltd."), List.of("1.000 TL"));
        g.ekle(List.of("b.docx"), "AZILNAME", List.of(), List.of());
        List<String> s = Files.readAllLines(g.dosya(), StandardCharsets.UTF_8);
        assertEquals(3, s.size());
        assertTrue(s.get(0).endsWith(IslemGunlugu.BASLIK));
        assertTrue(s.get(1).contains("\"X A.Ş. | Y; Ltd.\""));
    }
}
