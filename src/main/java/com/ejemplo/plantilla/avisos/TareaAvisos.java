package com.ejemplo.plantilla.avisos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tarea programada: cada pocos minutos convierte en avisos de la campana los recordatorios de la agenda cuya hora
 * llegó. Intervalo en app.avisos.intervalo-ms (por defecto, 2 minutos); se apaga con app.avisos.activo=false.
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "app.avisos.activo", havingValue = "true", matchIfMissing = true)
public class TareaAvisos {

    private static final Logger LOG = LoggerFactory.getLogger(TareaAvisos.class);

    private final AvisosServicio servicio;

    public TareaAvisos(AvisosServicio servicio) {
        this.servicio = servicio;
    }

    @Scheduled(initialDelayString = "${app.avisos.demora-inicial-ms:30000}", fixedDelayString = "${app.avisos.intervalo-ms:120000}")
    public void ejecutar() {
        try {
            int n = servicio.procesarPendientes();
            if (n > 0) LOG.info("Avisos de agenda generados: {}", n);
        } catch (RuntimeException e) {
            // Un error (por ejemplo, la base caída) no detiene la tarea: se reintenta en la próxima pasada.
            LOG.warn("No se pudieron procesar los avisos de agenda: {}", e.getMessage());
        }
    }
}
