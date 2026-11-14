package com.maxieventos.contratos.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.maxieventos.contratos.entity.Contrato;
import com.maxieventos.contratos.entity.ContratoAdicional;
import com.maxieventos.contratos.entity.FotoContrato;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

/** Genera los PDF reales y extrae su texto: datos completos, formato es-CO, tildes y ñ correctas. */
class PdfGeneratorServiceTest {

    private final PdfGeneratorService generador = new PdfGeneratorService(new DefaultResourceLoader(), "America/Bogota");
    private final UUID eventoId = UUID.randomUUID();

    private Contrato contratoConFoto(String lugar) {
        return new Contrato(eventoId, new FotoContrato("Quince años de Sofía Muñoz", "María José Peña Ñáñez",
                "3001234567", "maria.pena@ejemplo.com", LocalDate.of(2026, 11, 14), "QUINCE_ANOS", 90, 15,
                lugar, lugar == null ? null : "Vía a Rivera km 3", new BigDecimal("8700000.00"), "COP"));
    }

    private static String texto(byte[] pdf) throws IOException {
        PdfReader lector = new PdfReader(pdf);
        StringBuilder texto = new StringBuilder();
        for (int pagina = 1; pagina <= lector.getNumberOfPages(); pagina++) {
            texto.append(new PdfTextExtractor(lector).getTextFromPage(pagina)).append('\n');
        }
        return texto.toString();
    }

    @Test
    void contratoConTodosLosDatosDelEvento() throws IOException {
        String texto = texto(generador.generarContrato(contratoConFoto("Salón Los Ángeles")));

        assertThat(texto)
                .contains("Maxi Eventos")
                .contains("CONTRATO DE SERVICIOS PARA EVENTO")
                .contains("Pendiente de firma")
                .contains("Datos del cliente")
                .contains("María José Peña Ñáñez")
                .contains("3001234567")
                .contains("maria.pena@ejemplo.com")
                .contains("Datos del evento")
                .contains("Quince años de Sofía Muñoz")
                .contains("sábado 14 de noviembre de 2026")
                .contains("105 (90 adultos y 15 niños)")
                .contains("Salón Los Ángeles (Vía a Rivera km 3)")
                .contains("Valor total del contrato")
                .contains("8.700.000,00 COP")
                .contains("Firma del cliente")
                .contains("Página 1")
                .doesNotContain("{{");
    }

    @Test
    void eventoSinLugarDiceQueEstaPorDefinir() throws IOException {
        assertThat(texto(generador.generarContrato(contratoConFoto(null)))).contains("Por definir");
    }

    @Test
    void contratoAnteriorSinFotoNoFalla() throws IOException {
        String texto = texto(generador.generarContrato(new Contrato(eventoId, "Boda antigua")));

        assertThat(texto).contains("Boda antigua").contains("No registrado")
                .contains("Valor total del contrato").doesNotContain("{{");
    }

    @Test
    void anexoListaLosAdicionalesYSusValores() throws IOException {
        List<ContratoAdicional> adicionales = List.of(
                new ContratoAdicional(UUID.randomUUID(), eventoId, "decoracion-premium", "Decoración premium", 1,
                        new BigDecimal("250000"), new BigDecimal("250000"), "COP", Instant.now()),
                new ContratoAdicional(UUID.randomUUID(), eventoId, "hora-extra", "Hora adicional de servicio", 2,
                        new BigDecimal("120000"), new BigDecimal("240000"), "COP", Instant.now()));

        String texto = texto(generador.generarAnexo(contratoConFoto("Salón Los Ángeles"), adicionales));

        assertThat(texto)
                .contains("ANEXO DE SERVICIOS ADICIONALES")
                .contains("María José Peña Ñáñez")
                .contains("Decoración premium")
                .contains("Hora adicional de servicio")
                .contains("250.000,00 COP")
                .contains("120.000,00 COP")
                .contains("240.000,00 COP")
                .contains("Total de servicios adicionales")
                .contains("490.000,00 COP")
                .contains("no modifica el contrato original, cuyo valor total sigue siendo 8.700.000,00 COP")
                .contains("9.190.000,00 COP en total")
                .doesNotContain("{{");
    }

    @Test
    void formatoDeDinero() {
        assertThat(PdfGeneratorService.dinero(new BigDecimal("15290000"), "COP")).isEqualTo("15.290.000,00 COP");
        assertThat(PdfGeneratorService.dinero(null, "COP")).isEqualTo("No registrado");
    }
}
