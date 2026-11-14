package com.maxieventos.contratos.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Datos del evento tal como estaban al crear el contrato (de Eventos, Clientes, Lugares y Finanzas). Se guardan en el
 * contrato para que el documento no cambie aunque cambien los datos de origen. {@code lugarNombre} y
 * {@code lugarDireccion} son nulos si el evento aún no tiene lugar.
 */
public record FotoContrato(
        String nombreEvento,
        String clienteNombre,
        String clienteTelefono,
        String clienteCorreo,
        LocalDate fechaEvento,
        String tipoEvento,
        int cantidadAdultos,
        int cantidadNinos,
        String lugarNombre,
        String lugarDireccion,
        BigDecimal valorTotal,
        String moneda) {
}
