package com.maxieventos.clientes.service;

import com.maxieventos.clientes.config.RedisCacheConfig;
import com.maxieventos.clientes.dto.ClienteRequest;
import com.maxieventos.clientes.dto.ClienteResponse;
import com.maxieventos.clientes.dto.PaginaResponse;
import com.maxieventos.clientes.entity.Cliente;
import com.maxieventos.clientes.repository.ClienteRepository;

import com.maxieventos.clientes.exception.RecursoNoEncontradoException;
import com.maxieventos.clientes.exception.ClienteDuplicadoException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import java.util.UUID;

@Service
public class ClienteService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClienteService.class);

    private final ClienteRepository repository;

    public ClienteService(ClienteRepository repository) {
        this.repository = repository;
    }

    /** El listado no se cachea: cambia a menudo y tiene muchas combinaciones de búsqueda. */
    @Transactional(readOnly = true)
    public PaginaResponse<ClienteResponse> listar(String nombre, Pageable pageable) {
        Page<Cliente> clientes = nombre == null
                ? repository.findAll(pageable)
                : repository.findByNombreCompletoContainingIgnoreCase(nombre, pageable);
        return PaginaResponse.de(clientes, ClienteResponse::from);
    }

    @Transactional
    public ClienteResponse crear(ClienteRequest request) {
        validarDuplicados(request, null);
        Cliente cliente = repository.save(new Cliente(
                request.nombreCompleto(),
                request.telefono(),
                request.correo(),
                request.direccion()));
        LOGGER.info("Cliente creado. id={}", cliente.getId());
        return ClienteResponse.from(cliente);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = RedisCacheConfig.CLIENTES, key = "#id")
    public ClienteResponse obtener(UUID id) {
        return ClienteResponse.from(buscar(id));
    }

    @Transactional
    @CachePut(value = RedisCacheConfig.CLIENTES, key = "#id")
    public ClienteResponse actualizar(UUID id, ClienteRequest request) {
        Cliente cliente = buscar(id);
        validarDuplicados(request, id);
        cliente.actualizar(
                request.nombreCompleto(),
                request.telefono(),
                request.correo(),
                request.direccion());
        LOGGER.info("Cliente actualizado. id={}", id);
        return ClienteResponse.from(cliente);
    }

    @Transactional
    @CacheEvict(value = RedisCacheConfig.CLIENTES, key = "#id")
    public void eliminar(UUID id) {
        Cliente cliente = buscar(id);
        repository.delete(cliente);
        LOGGER.info("Cliente eliminado. id={}", id);
    }

    private Cliente buscar(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe un cliente con id " + id));
    }

    private void validarDuplicados(ClienteRequest request, UUID idActual) {
        boolean correoExiste = idActual == null
                ? repository.existsByCorreo(request.correo())
                : repository.existsByCorreoAndIdNot(request.correo(), idActual);
        if (correoExiste) {
            throw new ClienteDuplicadoException("El correo " + request.correo() + " ya se encuentra registrado");
        }

        boolean telefonoExiste = idActual == null
                ? repository.existsByTelefono(request.telefono())
                : repository.existsByTelefonoAndIdNot(request.telefono(), idActual);
        if (telefonoExiste) {
            throw new ClienteDuplicadoException("El teléfono " + request.telefono() + " ya se encuentra registrado");
        }
    }
}
