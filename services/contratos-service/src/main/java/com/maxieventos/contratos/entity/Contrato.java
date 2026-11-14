package com.maxieventos.contratos.entity;

import com.maxieventos.contratos.exception.ReglaNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Contrato de un evento (uno por evento). Desde la Fase 4 guarda una foto de los datos del evento al crearse
 * (columnas nulas en los contratos anteriores y en los que crea el consumidor de Kafka).
 */
@Entity
@Table(name = "contratos", uniqueConstraints = @UniqueConstraint(name = "uk_contrato_evento", columnNames = "evento_id"))
public class Contrato {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "evento_id", nullable = false)
    private UUID eventoId;

    @Column(nullable = false)
    private String nombreEvento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoContrato estado = EstadoContrato.PENDIENTE;

    @Column(nullable = false)
    private Instant creadoEn = Instant.now();

    @Column(name = "firmado_en")
    private Instant firmadoEn;

    @Column(name = "cliente_nombre")
    private String clienteNombre;
    @Column(name = "cliente_telefono")
    private String clienteTelefono;
    @Column(name = "cliente_correo")
    private String clienteCorreo;
    @Column(name = "fecha_evento")
    private LocalDate fechaEvento;
    @Column(name = "tipo_evento", length = 20)
    private String tipoEvento;
    @Column(name = "cantidad_adultos")
    private Integer cantidadAdultos;
    @Column(name = "cantidad_ninos")
    private Integer cantidadNinos;
    @Column(name = "lugar_nombre")
    private String lugarNombre;
    @Column(name = "lugar_direccion")
    private String lugarDireccion;
    @Column(name = "valor_total", precision = 19, scale = 2)
    private BigDecimal valorTotal;
    @Column(length = 10)
    private String moneda;

    protected Contrato() {
    }

    /** Contrato sin foto de datos (lo usa el consumidor de EVENTO_CONFIRMADO; se revisa en la Fase 5). */
    public Contrato(UUID eventoId, String nombreEvento) {
        this.eventoId = eventoId;
        this.nombreEvento = nombreEvento;
    }

    public Contrato(UUID eventoId, FotoContrato foto) {
        this(eventoId, foto.nombreEvento());
        this.clienteNombre = foto.clienteNombre();
        this.clienteTelefono = foto.clienteTelefono();
        this.clienteCorreo = foto.clienteCorreo();
        this.fechaEvento = foto.fechaEvento();
        this.tipoEvento = foto.tipoEvento();
        this.cantidadAdultos = foto.cantidadAdultos();
        this.cantidadNinos = foto.cantidadNinos();
        this.lugarNombre = foto.lugarNombre();
        this.lugarDireccion = foto.lugarDireccion();
        this.valorTotal = foto.valorTotal();
        this.moneda = foto.moneda();
    }

    /** Firmar dos veces → 409. */
    public void marcarFirmado() {
        if (estado == EstadoContrato.FIRMADO) {
            throw new ReglaNegocioException("El contrato del evento " + eventoId + " ya está FIRMADO.");
        }
        this.estado = EstadoContrato.FIRMADO;
        this.firmadoEn = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getEventoId() { return eventoId; }
    public String getNombreEvento() { return nombreEvento; }
    public EstadoContrato getEstado() { return estado; }
    public Instant getCreadoEn() { return creadoEn; }
    public Instant getFirmadoEn() { return firmadoEn; }
    public String getClienteNombre() { return clienteNombre; }
    public String getClienteTelefono() { return clienteTelefono; }
    public String getClienteCorreo() { return clienteCorreo; }
    public LocalDate getFechaEvento() { return fechaEvento; }
    public String getTipoEvento() { return tipoEvento; }
    public Integer getCantidadAdultos() { return cantidadAdultos; }
    public Integer getCantidadNinos() { return cantidadNinos; }
    public String getLugarNombre() { return lugarNombre; }
    public String getLugarDireccion() { return lugarDireccion; }
    public BigDecimal getValorTotal() { return valorTotal; }
    public String getMoneda() { return moneda; }
}
