package com.maxieventos.eventos.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.maxieventos.eventos.entity.EstadoFecha;
import com.maxieventos.eventos.entity.FechaAgenda;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Regla: un solo evento por fin de semana (viernes a domingo)")
class ReglaFinDeSemanaTest {

    // Fin de semana normal: viernes 4, sábado 5 y domingo 6 de diciembre de 2026
    private static final LocalDate VIERNES = LocalDate.of(2026, 12, 4);
    private static final LocalDate SABADO = VIERNES.plusDays(1);
    private static final LocalDate DOMINGO = VIERNES.plusDays(2);
    private static final LocalDate MIERCOLES = VIERNES.minusDays(2);

    // Cruce de mes: viernes 30 de abril, sábado 1 y domingo 2 de mayo de 2027
    private static final LocalDate VIERNES_FIN_DE_MES = LocalDate.of(2027, 4, 30);
    // Cruce de año: viernes 31 de diciembre de 2027, sábado 1 y domingo 2 de enero de 2028
    private static final LocalDate VIERNES_FIN_DE_ANO = LocalDate.of(2027, 12, 31);

    private static FechaAgenda registro(LocalDate fecha, EstadoFecha estado) {
        FechaAgenda agenda = new FechaAgenda(fecha);
        agenda.asignar(estado, estado == EstadoFecha.DISPONIBLE ? null : UUID.randomUUID());
        return agenda;
    }

    @Test
    void lasFechasDePruebaCaenEnElDiaEsperado() {
        assertThat(VIERNES.getDayOfWeek()).isEqualTo(DayOfWeek.FRIDAY);
        assertThat(VIERNES_FIN_DE_MES.getDayOfWeek()).isEqualTo(DayOfWeek.FRIDAY);
        assertThat(VIERNES_FIN_DE_ANO.getDayOfWeek()).isEqualTo(DayOfWeek.FRIDAY);
        assertThat(MIERCOLES.getDayOfWeek()).isEqualTo(DayOfWeek.WEDNESDAY);
    }

    @Test
    void losDiasPermitidosPorDefectoSonExactamenteLosDelFinDeSemana() {
        // Desde la 10B3 solo se registran eventos en días permitidos; por defecto coinciden con este fin de semana, así
        // que la regla "entre semana solo se valida la fecha" ya no se alcanza al crear (se conserva por si se
        // configura otro día, ver EventoServiceCreacionTest).
        LocalDate lunes = VIERNES.minusDays(4);
        for (int i = 0; i < 14; i++) {
            LocalDate dia = lunes.plusDays(i);
            assertThat(DiasPermitidos.FIN_DE_SEMANA.permite(dia)).as(dia.toString()).isEqualTo(ReglaFinDeSemana.esFinDeSemana(dia));
        }
    }

    @Nested
    @DisplayName("Qué días forman el fin de semana")
    class DiasDelFinDeSemana {

        @Test
        void viernesSabadoYDomingoSonFinDeSemana() {
            assertThat(ReglaFinDeSemana.esFinDeSemana(VIERNES)).isTrue();
            assertThat(ReglaFinDeSemana.esFinDeSemana(SABADO)).isTrue();
            assertThat(ReglaFinDeSemana.esFinDeSemana(DOMINGO)).isTrue();
        }

        @Test
        void deLunesAJuevesNoSonFinDeSemana() {
            LocalDate lunes = VIERNES.minusDays(4);
            for (int i = 0; i < 4; i++) {
                assertThat(ReglaFinDeSemana.esFinDeSemana(lunes.plusDays(i))).as(lunes.plusDays(i).toString()).isFalse();
            }
        }

        @Test
        void cadaDiaDelFinDeSemanaConoceLosOtrosDos() {
            assertThat(ReglaFinDeSemana.otrosDiasDelFinDeSemana(VIERNES)).containsExactly(SABADO, DOMINGO);
            assertThat(ReglaFinDeSemana.otrosDiasDelFinDeSemana(SABADO)).containsExactly(VIERNES, DOMINGO);
            assertThat(ReglaFinDeSemana.otrosDiasDelFinDeSemana(DOMINGO)).containsExactly(VIERNES, SABADO);
        }

        @Test
        void entreSemanaNoHayOtrosDiasQueValidar() {
            assertThat(ReglaFinDeSemana.otrosDiasDelFinDeSemana(MIERCOLES)).isEmpty();
        }

        @Test
        void unJuevesNoSeAgrupaConElViernesAnterior() {
            // Error de la versión anterior: tomaba el viernes anterior para fechas entre semana
            LocalDate jueves = VIERNES.plusDays(6);
            assertThat(ReglaFinDeSemana.otrosDiasDelFinDeSemana(jueves)).isEmpty();
            assertThat(ReglaFinDeSemana.conflicto(jueves, List.of(registro(VIERNES, EstadoFecha.OCUPADA)))).isEmpty();
        }
    }

    @Nested
    @DisplayName("Conflictos")
    class Conflictos {

        @Test
        void sabadoConViernesPendienteEsConflicto() {
            assertThat(ReglaFinDeSemana.conflicto(SABADO, List.of(registro(VIERNES, EstadoFecha.PENDIENTE))))
                    .get().extracting(FechaAgenda::getFecha).isEqualTo(VIERNES);
        }

        @Test
        void viernesConDomingoOcupadoEsConflicto() {
            assertThat(ReglaFinDeSemana.conflicto(VIERNES, List.of(registro(DOMINGO, EstadoFecha.OCUPADA)))).isPresent();
        }

        @Test
        void domingoConSabadoOcupadoEsConflicto() {
            assertThat(ReglaFinDeSemana.conflicto(DOMINGO, List.of(registro(SABADO, EstadoFecha.OCUPADA)))).isPresent();
        }

        @Test
        void unaFechaDisponibleDelFinDeSemanaNoBloquea() {
            // Por ejemplo, la fecha de un evento cancelado
            assertThat(ReglaFinDeSemana.conflicto(SABADO, List.of(registro(VIERNES, EstadoFecha.DISPONIBLE)))).isEmpty();
        }

        @Test
        void otroFinDeSemanaNoBloquea() {
            LocalDate sabadoSiguiente = SABADO.plusWeeks(1);
            assertThat(ReglaFinDeSemana.conflicto(sabadoSiguiente, List.of(registro(VIERNES, EstadoFecha.OCUPADA)))).isEmpty();
        }

        @Test
        void entreSemanaLosDiasVecinosOcupadosNoBloquean() {
            List<FechaAgenda> vecinos = List.of(registro(MIERCOLES.minusDays(1), EstadoFecha.OCUPADA),
                    registro(MIERCOLES.plusDays(1), EstadoFecha.PENDIENTE));
            assertThat(ReglaFinDeSemana.conflicto(MIERCOLES, vecinos)).isEmpty();
        }

        @Test
        void laPropiaFechaNoSeConsideraConflictoDeFinDeSemana() {
            // Que la fecha pedida esté ocupada lo valida quien llama; la regla solo mira los otros días
            assertThat(ReglaFinDeSemana.conflicto(SABADO, List.of(registro(SABADO, EstadoFecha.OCUPADA)))).isEmpty();
        }
    }

    @Nested
    @DisplayName("Cruce de mes y de año")
    class Cruces {

        @Test
        void cruceDeMesSabadoUnoDeMayoConViernesTreintaDeAbril() {
            LocalDate sabado = VIERNES_FIN_DE_MES.plusDays(1);
            assertThat(sabado).isEqualTo(LocalDate.of(2027, 5, 1));
            assertThat(ReglaFinDeSemana.otrosDiasDelFinDeSemana(sabado))
                    .containsExactly(VIERNES_FIN_DE_MES, LocalDate.of(2027, 5, 2));
            assertThat(ReglaFinDeSemana.conflicto(sabado, List.of(registro(VIERNES_FIN_DE_MES, EstadoFecha.PENDIENTE))))
                    .isPresent();
        }

        @Test
        void cruceDeMesDomingoDosDeMayoConViernesTreintaDeAbril() {
            LocalDate domingo = LocalDate.of(2027, 5, 2);
            assertThat(ReglaFinDeSemana.conflicto(domingo, List.of(registro(VIERNES_FIN_DE_MES, EstadoFecha.OCUPADA))))
                    .isPresent();
        }

        @Test
        void cruceDeAnoSabadoUnoDeEneroConViernesTreintaYUnoDeDiciembre() {
            LocalDate sabado = LocalDate.of(2028, 1, 1);
            assertThat(sabado.getDayOfWeek()).isEqualTo(DayOfWeek.SATURDAY);
            assertThat(ReglaFinDeSemana.otrosDiasDelFinDeSemana(sabado))
                    .containsExactly(VIERNES_FIN_DE_ANO, LocalDate.of(2028, 1, 2));
            assertThat(ReglaFinDeSemana.conflicto(sabado, List.of(registro(VIERNES_FIN_DE_ANO, EstadoFecha.OCUPADA))))
                    .isPresent();
        }

        @Test
        void cruceDeAnoViernesConDomingoDosDeEnero() {
            assertThat(ReglaFinDeSemana.conflicto(VIERNES_FIN_DE_ANO,
                    List.of(registro(LocalDate.of(2028, 1, 2), EstadoFecha.PENDIENTE)))).isPresent();
        }

        @Test
        void elLunesTresDeEneroNoQuedaBloqueadoPorElFinDeSemanaAnterior() {
            assertThat(ReglaFinDeSemana.conflicto(LocalDate.of(2028, 1, 3),
                    List.of(registro(VIERNES_FIN_DE_ANO, EstadoFecha.OCUPADA)))).isEmpty();
        }
    }
}
