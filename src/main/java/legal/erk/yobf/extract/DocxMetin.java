package legal.erk.yobf.extract;

import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DocxMetin {

    private DocxMetin() {}

    public static String metin(Path docx) throws IOException {
        try (InputStream in = Files.newInputStream(docx);
             XWPFDocument doc = new XWPFDocument(in);
             XWPFWordExtractor ex = new XWPFWordExtractor(doc)) {
            return ex.getText();
        }
    }
}
