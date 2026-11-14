package com.maxieventos.lugares.dto;

import com.maxieventos.lugares.entity.EstadoLugar;
import com.maxieventos.lugares.entity.Lugar;
import java.util.UUID;

public record LugarResponseDTO(UUID id, String nombre, String direccion, String tipo, Integer capacidad,
                               EstadoLugar estado) {
    public static LugarResponseDTO from(Lugar lugar) {
        return new LugarResponseDTO(lugar.getId(), lugar.getNombre(), lugar.getDireccion(), lugar.getTipo(),
                lugar.getCapacidad(), lugar.getEstado());
    }
}
