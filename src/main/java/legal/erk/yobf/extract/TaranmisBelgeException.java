package legal.erk.yobf.extract;

import java.io.IOException;

/** PDF'te metin katmanı yok — OCR v1 kapsamı dışında. */
public class TaranmisBelgeException extends IOException {
    public TaranmisBelgeException(String dosya) {
        super("'" + dosya + "' taranmış belge gibi görünüyor (metin katmanı yok). "
                + "OCR bu sürümde desteklenmiyor; metin içeren PDF veya DOCX yükleyin.");
    }
}
