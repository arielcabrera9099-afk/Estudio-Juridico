package com.ejemplo.plantilla.documentos;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Un archivo que supera el límite del servidor (spring.servlet.multipart.max-file-size) se rechaza antes de llegar al
 * controlador: vuelve a la carga con el mensaje, en lugar de una página de error.
 */
@ControllerAdvice
public class ArchivoMuyGrande {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String demasiadoGrande() {
        return "redirect:/documentos/nuevo?error="
                + URLEncoder.encode("El archivo es demasiado grande para el servidor (máximo 100 MB).", StandardCharsets.UTF_8);
    }
}
