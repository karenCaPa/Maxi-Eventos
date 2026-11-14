import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.yaml.snakeyaml.Yaml;

/**
 * Compara un contrato de openapi/ con el /v3/api-docs que genera Springdoc desde el código en marcha (Fase 9B).
 * Uso: java -cp snakeyaml.jar CompararOpenApi.java contrato.yaml api-docs.json [publica1,publica2...]
 *   (las operaciones públicas se escriben como "POST /auth/login", relativas al servidor del YAML).
 *
 * Tres grupos de resultados:
 *  - REAL: el YAML no coincide con el código (rutas, métodos, parámetros: nombre, ubicación, obligatorio, tipo y valores
 *    permitidos; campos y tipos de los DTO de request y response). Hay que corregirlas.
 *  - REGLA: lo que /v3/api-docs no puede mostrar y el contrato debe tener (401 en las operaciones protegidas, 400 si hay
 *    parámetros de ruta o cuerpo, bearerAuth, ErrorResponse en los errores, un 2xx, ejemplos). Hay que corregirlas.
 *  - COSM: diferencias cosméticas que solo se informan (nombres de esquemas, formatos, tipos de contenido, "required" que
 *    Springdoc no deduce de @NotBlank, el 201/204 que Springdoc muestra como 200 porque sale de ResponseEntity).
 * Imprime una línea "RESUMEN|reales|reglas|cosmeticas" al final y sale con 1 si hay reales o reglas.
 */
public class CompararOpenApi {
    private static final List<String> METODOS = List.of("get", "post", "put", "patch", "delete");
    private static final Set<String> reales = new LinkedHashSet<>();
    private static final Set<String> reglas = new LinkedHashSet<>();
    private static final Set<String> cosmeticas = new LinkedHashSet<>();
    private static Map<String, Object> yaml;
    private static Map<String, Object> docs;

    public static void main(String[] args) throws Exception {
        yaml = cargar(Path.of(args[0]));
        docs = cargar(Path.of(args[1]));
        Set<String> publicas = new HashSet<>();
        if (args.length > 2 && !args[2].isBlank()) {
            for (String p : args[2].split(",")) publicas.add(p.trim());
        }

        String base = rutaBase(yaml);
        Map<String, Operacion> opsYaml = operaciones(yaml, base);
        Map<String, Operacion> opsDocs = operaciones(docs, "");

        reglasGenerales();
        for (String clave : new TreeSet<>(union(opsYaml.keySet(), opsDocs.keySet()))) {
            Operacion y = opsYaml.get(clave);
            Operacion d = opsDocs.get(clave);
            if (y == null) {
                reales.add(d.titulo() + ": existe en el código y falta en el YAML");
                continue;
            }
            reglasOperacion(y, publicas.contains(y.metodo.toUpperCase() + " " + y.ruta.substring(base.length())));
            if (d == null) {
                reales.add(y.titulo() + ": está en el YAML y no existe en el código");
                continue;
            }
            if (!y.ruta.equals(d.ruta)) {
                reales.add(y.titulo() + ": nombres de los parámetros de ruta distintos (código: " + d.ruta + ")");
            }
            compararParametros(y, d);
            compararCuerpo(y, d);
            compararRespuestas(y, d);
        }

        reales.forEach(r -> System.out.println("REAL  " + r));
        reglas.forEach(r -> System.out.println("REGLA " + r));
        cosmeticas.forEach(c -> System.out.println("COSM  " + c));
        System.out.println("RESUMEN|" + reales.size() + "|" + reglas.size() + "|" + cosmeticas.size());
        System.exit(reales.isEmpty() && reglas.isEmpty() ? 0 : 1);
    }

    // ------------------------------------------------------------------ carga y operaciones

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cargar(Path ruta) throws Exception {
        return (Map<String, Object>) new Yaml().load(Files.readString(ruta, StandardCharsets.UTF_8));
    }

    private static String rutaBase(Map<String, Object> raiz) {
        List<?> servidores = lista(raiz.get("servers"));
        if (servidores.isEmpty()) return "";
        String ruta = URI.create(texto(mapa(servidores.get(0)).get("url"))).getPath();
        return ruta == null || ruta.equals("/") ? "" : ruta.replaceAll("/$", "");
    }

    private record Operacion(String metodo, String ruta, Map<String, Object> def, List<Map<String, Object>> params) {
        String titulo() { return metodo.toUpperCase() + " " + ruta; }
    }

    private static Map<String, Operacion> operaciones(Map<String, Object> raiz, String base) {
        Map<String, Operacion> ops = new LinkedHashMap<>();
        mapa(raiz.get("paths")).forEach((ruta, valor) -> {
            Map<String, Object> item = mapa(valor);
            for (String metodo : METODOS) {
                if (!item.containsKey(metodo)) continue;
                Map<String, Object> def = mapa(item.get(metodo));
                List<Map<String, Object>> params = new ArrayList<>();
                for (Object p : lista(item.get("parameters"))) params.add(resolver(raiz, p));
                for (Object p : lista(def.get("parameters"))) params.add(resolver(raiz, p));
                String completa = (base + ruta).replaceAll("/$", "");
                ops.put(metodo.toUpperCase() + " " + completa.replaceAll("\\{[^}]+}", "{}"),
                        new Operacion(metodo, completa, def, params));
            }
        });
        return ops;
    }

    // ------------------------------------------------------------------ reglas del contrato (solo YAML)

    private static void reglasGenerales() {
        Map<String, Object> esquema = mapa(mapa(mapa(yaml.get("components")).get("securitySchemes")).get("bearerAuth"));
        if (!"http".equals(esquema.get("type")) || !"bearer".equals(esquema.get("scheme"))) {
            reglas.add("components.securitySchemes.bearerAuth (type http, scheme bearer, bearerFormat JWT) no está definido");
        }
        if (!mapa(mapa(yaml.get("components")).get("schemas")).containsKey("ErrorResponse")) {
            reglas.add("falta el esquema ErrorResponse");
        }
    }

    private static void reglasOperacion(Operacion op, boolean publicaPermitida) {
        Object seguridad = op.def.containsKey("security") ? op.def.get("security") : yaml.get("security");
        boolean publica = lista(seguridad).isEmpty();
        Map<String, Object> respuestas = mapa(op.def.get("responses"));
        if (publica && !publicaPermitida) reglas.add(op.titulo() + ": es pública en el YAML (security vacío) y debe exigir bearerAuth");
        if (!publica && publicaPermitida) reglas.add(op.titulo() + ": debe ser pública (security: [])");
        if (!publica && !respuestas.containsKey("401")) reglas.add(op.titulo() + ": falta el 401");
        boolean conRuta = op.params.stream().anyMatch(p -> "path".equals(p.get("in")));
        if ((conRuta || op.def.containsKey("requestBody") || op.params.stream().anyMatch(p -> "query".equals(p.get("in"))))
                && !respuestas.containsKey("400")) {
            reglas.add(op.titulo() + ": falta el 400 (" + (conRuta ? "ruta o identificador inválido" : "datos inválidos") + ")");
        }
        if (respuestas.keySet().stream().noneMatch(c -> c.startsWith("2"))) reglas.add(op.titulo() + ": no tiene respuesta 2xx");
        if (!respuestas.containsKey("500")) reglas.add(op.titulo() + ": falta el 500");
        respuestas.forEach((codigo, valor) -> {
            Map<String, Object> r = resolver(yaml, valor);
            Map<String, Object> contenido = mapa(r.get("content"));
            if (codigo.startsWith("4") || codigo.startsWith("5")) {
                Object esquema = contenido.isEmpty() ? null : mapa(contenido.values().iterator().next()).get("schema");
                if (!"ErrorResponse".equals(nombreRef(esquema))) reglas.add(op.titulo() + " " + codigo + ": el cuerpo no es ErrorResponse");
                if (!tieneEjemplo(contenido)) reglas.add(op.titulo() + " " + codigo + ": falta un ejemplo");
            } else if (codigo.startsWith("2") && !contenido.isEmpty() && !tieneEjemplo(contenido)
                    && contenido.keySet().stream().anyMatch(t -> t.contains("json"))) {
                reglas.add(op.titulo() + " " + codigo + ": falta un ejemplo de respuesta");
            }
        });
        if (op.def.containsKey("requestBody")) {
            Map<String, Object> contenido = mapa(resolver(yaml, op.def.get("requestBody")).get("content"));
            if (!tieneEjemplo(contenido)) reglas.add(op.titulo() + ": falta un ejemplo del cuerpo de la petición");
        }
    }

    private static boolean tieneEjemplo(Map<String, Object> contenido) {
        for (Object media : contenido.values()) {
            Map<String, Object> m = mapa(media);
            if (m.containsKey("example") || m.containsKey("examples")) return true;
            Map<String, Object> esquema = resolver(yaml, m.get("schema"));
            if (esquema.containsKey("example")) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ comparación con el código

    private static void compararParametros(Operacion y, Operacion d) {
        Map<String, Map<String, Object>> py = porClave(y.params);
        Map<String, Map<String, Object>> pd = porClave(d.params);
        for (String clave : new TreeSet<>(union(py.keySet(), pd.keySet()))) {
            Map<String, Object> a = py.get(clave);
            Map<String, Object> b = pd.get(clave);
            boolean autorizacion = clave.equalsIgnoreCase("header:Authorization");
            if (a == null || b == null) {
                String texto = y.titulo() + ": parámetro " + clave + (a == null ? " existe en el código y falta en el YAML" : " está en el YAML y no en el código");
                if (autorizacion) cosmeticas.add(texto + " (cubierto por bearerAuth)");
                else reales.add(texto);
                continue;
            }
            if (Boolean.TRUE.equals(a.get("required")) != Boolean.TRUE.equals(b.get("required"))) {
                reales.add(y.titulo() + ": parámetro " + clave + " obligatorio en " + (Boolean.TRUE.equals(b.get("required")) ? "el código" : "el YAML") + " y opcional en el otro");
            }
            compararEsquema(y.titulo() + " parámetro " + clave, a.get("schema"), b.get("schema"), true, 0, new HashSet<>());
        }
    }

    private static Map<String, Map<String, Object>> porClave(List<Map<String, Object>> params) {
        Map<String, Map<String, Object>> m = new LinkedHashMap<>();
        for (Map<String, Object> p : params) m.put(p.get("in") + ":" + p.get("name"), p);
        return m;
    }

    private static void compararCuerpo(Operacion y, Operacion d) {
        Map<String, Object> cy = mapa(resolver(yaml, y.def.get("requestBody")).get("content"));
        Map<String, Object> cd = mapa(resolver(docs, d.def.get("requestBody")).get("content"));
        if (cy.isEmpty() != cd.isEmpty()) {
            reales.add(y.titulo() + ": cuerpo de la petición " + (cy.isEmpty() ? "existe en el código y falta en el YAML" : "en el YAML pero el código no lo recibe"));
            return;
        }
        if (cy.isEmpty()) return;
        if (!cy.keySet().equals(cd.keySet())) cosmeticas.add(y.titulo() + ": tipo de contenido de la petición " + cy.keySet() + " / código " + cd.keySet());
        compararEsquema(y.titulo() + " petición", primerEsquema(yaml, cy), primerEsquema(docs, cd), true, 0, new HashSet<>());
    }

    private static void compararRespuestas(Operacion y, Operacion d) {
        Map<String, Object> ry = mapa(y.def.get("responses"));
        Map<String, Object> rd = mapa(d.def.get("responses"));
        List<String> exitoYaml = ry.keySet().stream().filter(c -> c.startsWith("2")).sorted().toList();
        List<String> exitoDocs = rd.keySet().stream().filter(c -> c.startsWith("2")).sorted().toList();
        if (exitoYaml.isEmpty() || exitoDocs.isEmpty()) return;
        if (!exitoYaml.equals(exitoDocs)) {
            if (exitoDocs.equals(List.of("200"))) {
                cosmeticas.add(y.titulo() + ": éxito " + exitoYaml + " en el YAML; Springdoc muestra 200 (no ve el código de ResponseEntity)");
            } else {
                reales.add(y.titulo() + ": códigos de éxito " + exitoYaml + " en el YAML y " + exitoDocs + " en el código");
            }
        }
        // El cuerpo del primer 2xx con contenido del YAML contra el 2xx del código
        Map<String, Object> cy = Map.of();
        for (String c : exitoYaml) {
            cy = mapa(resolver(yaml, ry.get(c)).get("content"));
            if (!cy.isEmpty()) break;
        }
        Map<String, Object> cd = mapa(resolver(docs, rd.get(exitoDocs.get(0))).get("content"));
        if (cy.isEmpty() != cd.isEmpty()) {
            reales.add(y.titulo() + ": cuerpo de la respuesta " + (cy.isEmpty() ? "existe en el código y falta en el YAML" : "en el YAML pero el código no devuelve cuerpo"));
            return;
        }
        if (cy.isEmpty()) return;
        if (!cy.keySet().equals(cd.keySet())) cosmeticas.add(y.titulo() + ": tipo de contenido de la respuesta " + cy.keySet() + " / código " + cd.keySet());
        compararEsquema(y.titulo() + " respuesta", primerEsquema(yaml, cy), primerEsquema(docs, cd), false, 0, new HashSet<>());
    }

    private static Object primerEsquema(Map<String, Object> raiz, Map<String, Object> contenido) {
        return mapa(contenido.values().iterator().next()).get("schema");
    }

    private static void compararEsquema(String ctx, Object a, Object b, boolean peticion, int nivel, Set<String> vistos) {
        String na = nombreRef(a), nb = nombreRef(b);
        if (na != null && nb != null && !vistos.add(na + "|" + nb)) return;
        if (nivel > 6) return;
        Map<String, Object> sa = esquemaPlano(yaml, a);
        Map<String, Object> sb = esquemaPlano(docs, b);
        if (sa.isEmpty() && sb.isEmpty()) return;
        if (na != null && nb != null && !na.equals(nb)) cosmeticas.add(ctx + ": esquema " + na + " en el YAML y " + nb + " en el código");
        String ta = tipo(sa), tb = tipo(sb);
        if (!Objects.equals(ta, tb)) {
            reales.add(ctx + ": tipo " + ta + " en el YAML y " + tb + " en el código");
            return;
        }
        if (!Objects.equals(sa.get("format"), sb.get("format"))) {
            cosmeticas.add(ctx + ": formato " + sa.get("format") + " en el YAML y " + sb.get("format") + " en el código");
        }
        Set<String> ea = valores(sa.get("enum")), eb = valores(sb.get("enum"));
        if (!ea.equals(eb)) {
            String texto = ctx + ": valores permitidos " + ea + " en el YAML y " + eb + " en el código";
            // Un String de respuesta que el YAML documenta con sus valores posibles no contradice al código
            if (eb.isEmpty() && !peticion) cosmeticas.add(texto + " (String en el código; el YAML documenta los valores)");
            else reales.add(texto);
        }
        if ("array".equals(ta)) {
            compararEsquema(ctx + "[]", sa.get("items"), sb.get("items"), peticion, nivel + 1, vistos);
        } else if ("object".equals(ta)) {
            Map<String, Object> pa = mapa(sa.get("properties")), pb = mapa(sb.get("properties"));
            for (String campo : new TreeSet<>(union(pa.keySet(), pb.keySet()))) {
                if (!pa.containsKey(campo)) reales.add(ctx + ": campo '" + campo + "' existe en el código y falta en el YAML");
                else if (!pb.containsKey(campo)) reales.add(ctx + ": campo '" + campo + "' está en el YAML y no existe en el código");
                else compararEsquema(ctx + "." + campo, pa.get(campo), pb.get(campo), peticion, nivel + 1, vistos);
            }
            Set<String> qa = valores(sa.get("required")), qb = valores(sb.get("required"));
            for (String campo : qb) {
                if (!qa.contains(campo) && pa.containsKey(campo)) {
                    String texto = ctx + ": '" + campo + "' es obligatorio en el código (@NotNull) y opcional en el YAML";
                    if (peticion) reales.add(texto); else cosmeticas.add(texto);
                }
            }
            for (String campo : qa) {
                if (!qb.contains(campo) && pb.containsKey(campo)) {
                    cosmeticas.add(ctx + ": '" + campo + "' obligatorio en el YAML; Springdoc no lo marca (p. ej. @NotBlank o respuesta)");
                }
            }
            Object adA = sa.get("additionalProperties"), adB = sb.get("additionalProperties");
            if (adA instanceof Map<?, ?> && adB instanceof Map<?, ?>) {
                compararEsquema(ctx + "{}", adA, adB, peticion, nivel + 1, vistos);
            }
        }
    }

    /** Resuelve $ref y combina allOf (propiedades y obligatorios de todas las partes). */
    private static Map<String, Object> esquemaPlano(Map<String, Object> raiz, Object esquema) {
        Map<String, Object> s = resolver(raiz, esquema);
        if (!s.containsKey("allOf")) return s;
        Map<String, Object> plano = new LinkedHashMap<>(s);
        Map<String, Object> props = new LinkedHashMap<>(mapa(s.get("properties")));
        List<Object> req = new ArrayList<>(lista(s.get("required")));
        for (Object parte : lista(s.get("allOf"))) {
            Map<String, Object> p = esquemaPlano(raiz, parte);
            props.putAll(mapa(p.get("properties")));
            req.addAll(lista(p.get("required")));
            for (String clave : List.of("type", "format", "enum", "items")) {
                if (!plano.containsKey(clave) && p.containsKey(clave)) plano.put(clave, p.get(clave));
            }
        }
        plano.remove("allOf");
        plano.put("properties", props);
        plano.put("required", req);
        return plano;
    }

    private static String tipo(Map<String, Object> s) {
        Object t = s.get("type");
        if (t instanceof List<?> l) t = l.stream().filter(x -> !"null".equals(x)).findFirst().orElse(null);
        if (t == null && s.containsKey("properties")) return "object";
        if (t == null && s.containsKey("items")) return "array";
        return t == null ? null : t.toString();
    }

    // ------------------------------------------------------------------ utilidades

    private static Map<String, Object> resolver(Map<String, Object> raiz, Object nodo) {
        Map<String, Object> actual = mapa(nodo);
        for (int i = 0; i < 10 && actual.get("$ref") instanceof String ref && ref.startsWith("#/"); i++) {
            Object destino = raiz;
            for (String parte : ref.substring(2).split("/")) destino = mapa(destino).get(parte);
            actual = mapa(destino);
        }
        return actual;
    }

    private static String nombreRef(Object nodo) {
        Object ref = mapa(nodo).get("$ref");
        return ref instanceof String s ? s.substring(s.lastIndexOf('/') + 1) : null;
    }

    private static Set<String> valores(Object o) {
        Set<String> s = new TreeSet<>();
        for (Object x : lista(o)) s.add(String.valueOf(x));
        return s;
    }

    private static <T> Set<T> union(Set<T> a, Set<T> b) {
        Set<T> u = new LinkedHashSet<>(a);
        u.addAll(b);
        return u;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapa(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private static List<?> lista(Object o) {
        return o instanceof List<?> l ? l : List.of();
    }

    private static String texto(Object o) {
        return o == null ? "" : o.toString();
    }
}
