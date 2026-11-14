package com.maxieventos.eventos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/** Fase 11E: qué umbral se envía según los días que faltan (por defecto 7 y 1). */
class UmbralesRecordatorioTest {

    private final UmbralesRecordatorio umbrales = UmbralesRecordatorio.desde("7,1");

    @Test
    void seEnviaElUmbralMasPequenoQueAplica() {
        assertThat(umbrales.aplicable(8)).as("8 días: aún no").isEmpty();
        assertThat(umbrales.aplicable(7)).as("7 días").isEqualTo(OptionalInt.of(7));
        assertThat(umbrales.aplicable(6)).as("6 días (la tarea no corrió el día 7)").isEqualTo(OptionalInt.of(7));
        assertThat(umbrales.aplicable(2)).isEqualTo(OptionalInt.of(7));
        assertThat(umbrales.aplicable(1)).as("1 día: el de 1, aunque ya saliera el de 7").isEqualTo(OptionalInt.of(1));
        assertThat(umbrales.aplicable(0)).as("hoy: no").isEmpty();
        assertThat(umbrales.aplicable(-3)).as("pasado: no").isEmpty();
    }

    @Test
    void laConfiguracionSeOrdenaYSeValida() {
        assertThat(umbrales.dias()).containsExactly(1, 7);
        assertThat(umbrales.maximo()).isEqualTo(7);
        assertThat(UmbralesRecordatorio.desde(" 3, 14 ,3 ").dias()).containsExactly(3, 14);
        assertThatThrownBy(() -> UmbralesRecordatorio.desde("")).hasMessageContaining("RECORDATORIOS_DIAS");
        assertThatThrownBy(() -> UmbralesRecordatorio.desde("0,7")).hasMessageContaining("mayor que 0");
        assertThatThrownBy(() -> UmbralesRecordatorio.desde("siete")).hasMessageContaining("números enteros");
    }
}
