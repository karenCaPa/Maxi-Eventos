package com.maxieventos.gateway.dto;

/** Input {@code LugarInput}; se envía tal cual a {@code POST /lugares} y {@code PUT /lugares/{id}} (mismos nombres que {@code LugarRequest}). */
public record LugarInput(String nombre, String direccion, String tipo, Integer capacidad) {
}
