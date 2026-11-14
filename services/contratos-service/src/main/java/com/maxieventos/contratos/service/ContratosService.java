package com.maxieventos.contratos.service;

import com.maxieventos.contratos.dto.ContratoResponseDTO;
import com.maxieventos.contratos.dto.PaginaResponse;
import com.maxieventos.contratos.dto.ResultadoCreacion;
import com.maxieventos.contratos.dto.remoto.ClienteRemoto;
import com.maxieventos.contratos.dto.remoto.CotizacionRemota;
import com.maxieventos.contratos.dto.remoto.EventoRemoto;
import com.maxieventos.contratos.dto.remoto.LugarRemoto;
import com.maxieventos.contratos.entity.Contrato;
import com.maxieventos.contratos.entity.ContratoAdicional;
import com.maxieventos.contratos.entity.FotoContrato;
import com.maxieventos.contratos.exception.RecursoNoEncontradoException;
import com.maxieventos.contratos.exception.ReglaNegocioException;
import com.maxieventos.contratos.repository.ContratoAdicionalRepository;
import com.maxieventos.contratos.repository.ContratoRepository;
import com.maxieventos.contratos.service.remoto.ClientesClient;
import com.maxieventos.contratos.service.remoto.EventosClient;
import com.maxieventos.contratos.service.remoto.FinanzasClient;
import com.maxieventos.contratos.service.remoto.LugaresClient;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContratosService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContratosService.class);
    private final ContratoRepository contratoRepository;
    private final ContratoAdicionalRepository adicionalRepository;
    private final PdfGeneratorService pdfGeneratorService;
    private final EventosClient eventosClient;
    private final ClientesClient clientesClient;
    private final LugaresClient lugaresClient;
    private final FinanzasClient finanzasClient;

    public ContratosService(ContratoRepository contratoRepository, ContratoAdicionalRepository adicionalRepository,
                            PdfGeneratorService pdfGeneratorService, EventosClient eventosClient,
                            ClientesClient clientesClient, LugaresClient lugaresClient, FinanzasClient finanzasClient) {
        this.contratoRepository = contratoRepository;
        this.adicionalRepository = adicionalRepository;
        this.pdfGeneratorService = pdfGeneratorService;
        this.eventosClient = eventosClient;
        this.clientesClient = clientesClient;
        this.lugaresClient = lugaresClient;
        this.finanzasClient = finanzasClient;
    }

    @Transactional(readOnly = true)
    public PaginaResponse<ContratoResponseDTO> listar(Pageable pageable) {
        return PaginaResponse.de(contratoRepository.findAll(pageable), ContratoResponseDTO::from);
    }

    @Transactional(readOnly = true)
    public ContratoResponseDTO obtener(UUID id) {
        return contratoRepository.findById(id).map(ContratoResponseDTO::from)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe un contrato con id " + id));
    }

    /** Contrato del evento (lo consulta eventos-service para validar que esté FIRMADO antes de confirmar). */
    @Transactional(readOnly = true)
    public ContratoResponseDTO obtenerDeEvento(UUID eventoId) {
        return ContratoResponseDTO.from(obtenerPorEvento(eventoId));
    }

    /**
     * Crea el contrato PENDIENTE con una foto de los datos del evento, que se consultan por REST (con el JWT de la
     * petición): evento (Eventos), cliente (Clientes), lugar si tiene (Lugares) y valor total de la cotización (Finanzas).
     * Si el evento ya tiene contrato se devuelve el existente ({@code creado = false}) sin consultar a nadie.
     * Evento inexistente → 404; CANCELADO, sin cotización, o cliente/lugar inexistentes → 409; un servicio caído → 503.
     * Las llamadas REST se hacen fuera de una transacción.
     */
    public ResultadoCreacion<ContratoResponseDTO> crear(UUID eventoId) {
        var existente = contratoRepository.findByEventoId(eventoId);
        if (existente.isPresent()) {
            return new ResultadoCreacion<>(ContratoResponseDTO.from(existente.get()), false);
        }
        FotoContrato foto = tomarFoto(eventoId);
        try {
            Contrato contrato = contratoRepository.saveAndFlush(new Contrato(eventoId, foto));
            LOGGER.info("Contrato creado. contratoId={}, eventoId={}, valorTotal={} {}", contrato.getId(), eventoId,
                    foto.valorTotal(), foto.moneda());
            return new ResultadoCreacion<>(ContratoResponseDTO.from(contrato), true);
        } catch (DataIntegrityViolationException ex) {
            // Otra petición creó el contrato del mismo evento al mismo tiempo (restricción UNIQUE por evento).
            return contratoRepository.findByEventoId(eventoId)
                    .map(otro -> new ResultadoCreacion<>(ContratoResponseDTO.from(otro), false))
                    .orElseThrow(() -> ex);
        }
    }

    @Transactional(readOnly = true)
    public byte[] generarPdfContrato(UUID eventoId) {
        return pdfGeneratorService.generarContrato(obtenerPorEvento(eventoId));
    }

    /**
     * Anexo con los servicios adicionales del evento registrados en {@code contrato_adicionales} (los recibe el consumidor
     * de SERVICIO_ADICIONAL_AGREGADO; no se consulta a Finanzas). Se genera al pedirlo y no modifica el contrato original.
     * Sin contrato → 404; sin adicionales → 409.
     */
    @Transactional(readOnly = true)
    public byte[] generarPdfAnexo(UUID eventoId) {
        Contrato contrato = contratoRepository.findByEventoId(eventoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe un contrato para el evento " + eventoId));
        List<ContratoAdicional> adicionales = adicionalRepository.findByEventoIdOrderByRecibidoEnAsc(eventoId);
        if (adicionales.isEmpty()) {
            throw new ReglaNegocioException("El evento no tiene servicios adicionales; no hay anexo que generar.");
        }
        return pdfGeneratorService.generarAnexo(contrato, adicionales);
    }

    @Transactional
    public ContratoResponseDTO marcarFirmado(UUID eventoId) {
        Contrato contrato = obtenerPorEvento(eventoId);
        contrato.marcarFirmado();
        LOGGER.info("Contrato marcado como firmado. contratoId={}, eventoId={}", contrato.getId(), eventoId);
        return ContratoResponseDTO.from(contratoRepository.save(contrato));
    }

    private FotoContrato tomarFoto(UUID eventoId) {
        EventoRemoto evento = eventosClient.obtener(eventoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe un evento con id " + eventoId));
        if ("CANCELADO".equals(evento.estado())) {
            throw new ReglaNegocioException("El evento está CANCELADO; no se puede crear su contrato.");
        }
        CotizacionRemota cotizacion = finanzasClient.cotizacion(eventoId)
                .orElseThrow(() -> new ReglaNegocioException(
                        "El evento no tiene cotización. Genera la cotización antes de crear el contrato."));
        ClienteRemoto cliente = clientesClient.obtener(evento.clienteId())
                .orElseThrow(() -> new ReglaNegocioException("El cliente " + evento.clienteId() + " del evento no existe."));
        LugarRemoto lugar = null;
        if (evento.lugarId() != null) {
            lugar = lugaresClient.obtener(evento.lugarId())
                    .orElseThrow(() -> new ReglaNegocioException("El lugar " + evento.lugarId()
                            + " asignado al evento no existe. Asigna otro lugar antes de crear el contrato."));
        }
        return new FotoContrato(evento.nombreEvento(), cliente.nombreCompleto(), cliente.telefono(), cliente.correo(),
                evento.fecha(), evento.tipoEvento(), evento.cantidadAdultos(), evento.cantidadNinos(),
                lugar == null ? null : lugar.nombre(), lugar == null ? null : lugar.direccion(),
                cotizacion.total(), cotizacion.moneda());
    }

    private Contrato obtenerPorEvento(UUID eventoId) {
        return contratoRepository.findByEventoId(eventoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe un contrato para el evento " + eventoId));
    }
}
