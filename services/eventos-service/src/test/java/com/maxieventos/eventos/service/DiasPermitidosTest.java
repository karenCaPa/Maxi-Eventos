package com.maxieventos.eventos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Regla: solo se registran eventos en los días permitidos (configuración)")
class DiasPermitidosTest {

    private static final LocalDate LUNES = LocalDate.of(2026, 11, 30);

    @Test
    void porDefectoViernesSabadoYDomingo() {
        DiasPermitidos dias = DiasPermitidos.desde("FRIDAY,SATURDAY,SUNDAY");
        assertThat(dias.dias()).containsExactly(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
        assertThat(dias.mensaje()).isEqualTo("Solo se pueden registrar eventos en viernes, sábado o domingo.");
        for (int i = 0; i < 7; i++) {
            LocalDate dia = LUNES.plusDays(i);
            assertThat(dias.permite(dia)).as(dia.getDayOfWeek().toString()).isEqualTo(i >= 4);
        }
    }

    @Test
    void ignoraMayusculasEspaciosYRepetidosYOrdenaLosDias() {
        DiasPermitidos dias = DiasPermitidos.desde(" sunday , Saturday,SATURDAY, ");
        assertThat(dias.dias()).containsExactly(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
        assertThat(dias.mensaje()).isEqualTo("Solo se pueden registrar eventos en sábado o domingo.");
    }

    @Test
    void unSoloDiaYVariosDias() {
        assertThat(DiasPermitidos.desde("SATURDAY").mensaje()).isEqualTo("Solo se pueden registrar eventos en sábado.");
        assertThat(DiasPermitidos.desde("MONDAY,WEDNESDAY,SATURDAY").mensaje())
                .isEqualTo("Solo se pueden registrar eventos en lunes, miércoles o sábado.");
    }

    @Test
    void unaListaVaciaOUnDiaInexistenteImpidenArrancar() {
        assertThatThrownBy(() -> DiasPermitidos.desde("")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("al menos un día");
        assertThatThrownBy(() -> DiasPermitidos.desde(" , ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DiasPermitidos.desde("FRIDAY,SABADO")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'SABADO'");
    }
}
