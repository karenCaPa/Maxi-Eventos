import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.yaml.snakeyaml.Yaml;

/** Uso: java -cp snakeyaml.jar ValidarOpenApi.java archivo1.yaml archivo2.yaml ... */
public class ValidarOpenApi {
    public static void main(String[] args) throws Exception {
        int fallos = 0;
        for (String arg : args) {
            Path ruta = Path.of(arg);
            byte[] bytes = Files.readAllBytes(ruta);
            try {
                StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes));
            } catch (CharacterCodingException e) {
                System.out.println("FALLO " + arg + ": no es UTF-8 válido");
                fallos++;
                continue;
            }
            Object raiz;
            try {
                raiz = new Yaml().load(new String(bytes, StandardCharsets.UTF_8));
            } catch (Exception e) {
                System.out.println("FALLO " + arg + ": YAML inválido: " + e.getMessage());
                fallos++;
                continue;
            }
            List<String> refs = new ArrayList<>();
            recolectar(raiz, refs);
            List<String> rotas = new ArrayList<>();
            for (String ref : refs) {
                if (!ref.startsWith("#/")) continue;
                Object actual = raiz;
                for (String parte : ref.substring(2).split("/")) {
                    actual = actual instanceof Map<?, ?> m ? m.get(parte) : null;
                }
                if (actual == null) rotas.add(ref);
            }
            if (rotas.isEmpty()) {
                System.out.println("OK    " + arg + " (" + refs.size() + " $ref)");
            } else {
                System.out.println("FALLO " + arg + ": $ref rotas " + new TreeSet<>(rotas));
                fallos++;
            }
        }
        System.exit(fallos == 0 ? 0 : 1);
    }

    private static void recolectar(Object nodo, List<String> refs) {
        if (nodo instanceof Map<?, ?> m) {
            m.forEach((k, v) -> {
                if ("$ref".equals(k) && v instanceof String s) refs.add(s);
                else recolectar(v, refs);
            });
        } else if (nodo instanceof List<?> l) {
            l.forEach(e -> recolectar(e, refs));
        }
    }
}
