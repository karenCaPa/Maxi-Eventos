package com.maxieventos.clientes.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.maxieventos.clientes.dto.ClienteRequest;
import com.maxieventos.clientes.dto.ClienteResponse;
import com.maxieventos.clientes.service.ClienteService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cache.Cache;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Fase 8 (decisión Redis d) — la caché de clientes contra un Redis 7 real (Testcontainers, la imagen del compose y con
 * contraseña como en el compose): el {@code ClienteResponse} se guarda como JSON con la clave y el TTL configurados y se
 * lee de vuelta como {@code ClienteResponse} (sin pasar por la base de datos); actualizar refresca la entrada y borrar
 * la invalida. La base de datos es H2, como en la prueba existente de Clientes: aquí lo que se prueba es Redis.
 * <p>
 * Requiere Docker Desktop en marcha; sin Docker estas pruebas se SALTAN (no fallan).
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:clientes-cache;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=never",
        "auth.jwt.secret=test-secret-for-authentication-0123456789"})
@Testcontainers(disabledWithoutDocker = true)
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("Caché Redis de clientes-service (Redis real)")
class CacheRedisIntegracionTest {

    private static final String CONTRASENA = "clave-redis-solo-para-pruebas";

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withCommand("redis-server", "--requirepass", CONTRASENA)
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void conexionRedis(DynamicPropertyRegistry registro) {
        registro.add("spring.data.redis.host", redis::getHost);
        registro.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registro.add("spring.data.redis.password", () -> CONTRASENA);
    }

    @Autowired
    ClienteService service;
    @Autowired
    StringRedisTemplate redisTexto;
    @Autowired
    RedisConnectionFactory conexion;
    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void limpiar() {
        jdbc.execute("DELETE FROM clientes");
        redisTexto.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    void elClienteSeGuardaEnRedisComoJsonYSeLeeDeVueltaSinIrALaBaseDeDatos(CapturedOutput salida) {
        ClienteResponse creado = service.crear(new ClienteRequest("María José Peña Ñáñez", "3001234567",
                "maria.pena@ejemplo.com", null));
        String clave = "maxi:clientes:clientes::" + creado.id();

        ClienteResponse primera = service.obtener(creado.id());

        assertThat(primera).isEqualTo(creado);
        assertThat(salida.getOut()).contains("Caché MISS  cache=clientes clave=" + creado.id());
        // En Redis: JSON legible del DTO, con la clave y el TTL de application.yml (PT10M)
        assertThat(redisTexto.opsForValue().get(clave))
                .isEqualTo("{\"id\":\"" + creado.id() + "\",\"nombreCompleto\":\"María José Peña Ñáñez\","
                        + "\"telefono\":\"3001234567\",\"correo\":\"maria.pena@ejemplo.com\",\"direccion\":null}");
        assertThat(redisTexto.getExpire(clave, TimeUnit.SECONDS)).isBetween(590L, 600L);

        // Se cambia la fila por debajo (sin pasar por el service): si la segunda lectura sale de Redis, no lo ve
        jdbc.update("UPDATE clientes SET nombre_completo = 'Otro nombre' WHERE id = ?", creado.id());
        ClienteResponse segunda = service.obtener(creado.id());

        assertThat(segunda).isInstanceOf(ClienteResponse.class).isEqualTo(creado);
        assertThat(salida.getOut()).contains("Caché HIT   cache=clientes clave=" + creado.id());
    }

    @Test
    void actualizarRefrescaLaEntradaYEliminarLaInvalida() {
        ClienteResponse creado = service.crear(new ClienteRequest("Carlos Ruiz", "3109876543", "carlos@ejemplo.com",
                "Calle 10 # 5-20, Neiva"));
        String clave = "maxi:clientes:clientes::" + creado.id();
        service.obtener(creado.id());

        ClienteResponse actualizado = service.actualizar(creado.id(), new ClienteRequest("Carlos Andrés Ruiz",
                "3109876543", "carlos@ejemplo.com", "Vereda El Caguán, Neiva"));

        assertThat(redisTexto.opsForValue().get(clave)).contains("\"nombreCompleto\":\"Carlos Andrés Ruiz\"")
                .contains("\"direccion\":\"Vereda El Caguán, Neiva\"");
        assertThat(service.obtener(creado.id())).isEqualTo(actualizado);

        service.eliminar(creado.id());

        assertThat(redisTexto.hasKey(clave)).isFalse();
    }

    /**
     * Hoy ningún DTO cacheado del proyecto tiene fechas (ClienteResponse, LugarResponseDTO, ProveedorResponseDTO,
     * RequerimientosLogistica). Esta prueba usa la MISMA configuración de producción ({@link ConfiguracionCache#json})
     * con un DTO de prueba que sí las tiene: se guardan en ISO-8601 (no como números) y vuelven iguales.
     */
    @Test
    void lasFechasSeGuardanEnIsoYVuelvenIguales() {
        RedisCacheManager manager = RedisCacheManager.builder(conexion)
                .withCacheConfiguration("con-fechas", ConfiguracionCache.json("clientes", Duration.ofMinutes(5),
                        ConfiguracionCache.tipo(DtoConFechas.class)))
                .disableCreateOnMissingCache()
                .build();
        manager.afterPropertiesSet();
        Cache cache = manager.getCache("con-fechas");
        DtoConFechas valor = new DtoConFechas(UUID.randomUUID(), LocalDate.of(2026, 12, 5),
                Instant.parse("2026-10-28T15:30:45.123456Z"));

        cache.put(valor.id(), valor);

        assertThat(redisTexto.opsForValue().get("maxi:clientes:con-fechas::" + valor.id()))
                .contains("\"fecha\":\"2026-12-05\"")
                .contains("\"creadoEn\":\"2026-10-28T15:30:45.123456Z\"");
        assertThat(cache.get(valor.id(), DtoConFechas.class)).isEqualTo(valor);
    }

    record DtoConFechas(UUID id, LocalDate fecha, Instant creadoEn) {
    }
}
