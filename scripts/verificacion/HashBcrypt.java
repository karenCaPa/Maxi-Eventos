import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Lee una contraseña de la entrada estándar (una línea) y escribe su hash BCrypt, con el mismo BCryptPasswordEncoder
 * (coste 10) que usa auth-service. La contraseña nunca se recibe como argumento (quedaría en la lista de procesos).
 * Uso (lo llama rotar-password.ps1): java -cp spring-security-crypto.jar;spring-jcl.jar HashBcrypt.java
 */
public class HashBcrypt {
    public static void main(String[] args) throws Exception {
        String password = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine();
        // PowerShell puede anteponer un BOM (U+FEFF) al escribir en la tubería; no forma parte de la contraseña
        if (password != null && password.startsWith("﻿")) {
            password = password.substring(1);
        }
        if (password == null || password.isBlank()) {
            System.err.println("No se recibió ninguna contraseña.");
            System.exit(1);
        }
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String hash = encoder.encode(password.strip());
        if (!encoder.matches(password.strip(), hash)) {
            System.err.println("El hash generado no coincide con la contraseña.");
            System.exit(2);
        }
        System.out.println(hash);
    }
}
