package com.maxieventos.logistica.dto;

import com.maxieventos.logistica.entity.ItemCompraEntity;
import com.maxieventos.logistica.entity.OrigenItemCompra;
import java.util.UUID;

/** Ítem de la lista de compras. {@code origen} y {@code nota} desde la 10B3. */
public record ItemCompraResponseDTO(UUID id, String nombreItem, String cantidadEstimada, boolean comprado,
                                    OrigenItemCompra origen, String nota) {
    public static ItemCompraResponseDTO from(ItemCompraEntity item) {
        return new ItemCompraResponseDTO(item.getId(), item.getNombreItem(), item.getCantidadEstimada(), item.isComprado(),
                item.getOrigen(), item.getNota());
    }
}
