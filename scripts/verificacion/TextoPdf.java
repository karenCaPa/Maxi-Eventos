import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Extrae el texto de un PDF y lo guarda en UTF-8 (para comprobar el contenido de los contratos y anexos).
 * Uso: java -cp openpdf-1.3.30.jar TextoPdf.java archivo.pdf salida.txt
 * Se escribe en un archivo y no en la consola para que las tildes no dependan de la codificación de PowerShell.
 */
public class TextoPdf {
    public static void main(String[] args) throws Exception {
        PdfReader lector = new PdfReader(Files.readAllBytes(Path.of(args[0])));
        StringBuilder texto = new StringBuilder();
        for (int pagina = 1; pagina <= lector.getNumberOfPages(); pagina++) {
            texto.append(new PdfTextExtractor(lector).getTextFromPage(pagina)).append('\n');
        }
        Files.writeString(Path.of(args[1]), texto, StandardCharsets.UTF_8);
    }
}
