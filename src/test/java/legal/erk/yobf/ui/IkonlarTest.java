package legal.erk.yobf.ui;

import org.junit.jupiter.api.Test;

import java.awt.Image;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IkonlarTest {
    @Test
    void tumBoyutlarYuklenir() {
        List<Image> l = Ikonlar.liste();
        assertEquals(Ikonlar.BOYUTLAR.length, l.size());
        assertEquals(16, l.get(0).getWidth(null));
        assertEquals(256, l.get(l.size() - 1).getWidth(null));
    }

    @Test
    void exeSimgesiVar() throws Exception {
        Path ico = Path.of("packaging", "yobf.ico");
        assertTrue(Files.size(ico) > 10_000);
        byte[] b = Files.readAllBytes(ico);
        assertEquals(0, b[0]);
        assertEquals(1, b[2], "ICO başlığı");
        assertTrue((b[4] & 0xFF) >= 6, "birden fazla boyut içermeli");
    }
}
