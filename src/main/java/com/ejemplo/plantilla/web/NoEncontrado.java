package com.ejemplo.plantilla.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** El registro pedido no existe: la página de error muestra "Página no encontrada" (404). */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class NoEncontrado extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public NoEncontrado() {
        super("No existe el registro pedido.");
    }
}
