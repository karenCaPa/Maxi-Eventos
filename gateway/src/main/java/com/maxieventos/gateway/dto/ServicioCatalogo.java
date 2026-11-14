package com.maxieventos.gateway.dto;

import java.math.BigDecimal;

public record ServicioCatalogo(String codigo, String nombre, BigDecimal costoUnitario, String moneda) {
}
