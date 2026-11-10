package legal.erk.yobf;

import legal.erk.yobf.config.Yapilandirma;

import java.nio.file.Path;

public final class TestVerisi {
    private TestVerisi() {}

    public static Yapilandirma yapilandirma() {
        return Yapilandirma.yukle(Path.of("config"));
    }
}
