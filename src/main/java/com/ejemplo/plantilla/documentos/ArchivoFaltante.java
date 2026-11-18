package com.ejemplo.plantilla.documentos;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** El documento existe en la base pero su archivo no está en la carpeta de documentos (se movió o se borró a mano). */
@ResponseStatus(value = HttpStatus.GONE, reason = "El archivo del documento no está en la carpeta de documentos.")
public class ArchivoFaltante extends RuntimeException {
    private static final long serialVersionUID = 1L;
}
