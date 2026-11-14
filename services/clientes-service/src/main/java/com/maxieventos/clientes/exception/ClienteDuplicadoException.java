package com.maxieventos.clientes.exception;

/**
 * Ya existe un cliente con el mismo correo o teléfono. Se responde con 409.
 */
public class ClienteDuplicadoException extends ReglaNegocioException {

    public ClienteDuplicadoException(String message) {
        super(message);
    }
}
