package com.ejemplo.plantilla.clientes;

import com.ejemplo.plantilla.web.Textos;

/** Un teléfono del cliente: prefijo (el país) y número sin prefijo. */
public class TelefonoForm {

    private Long id;
    private Long paisId;
    private String numero;
    private boolean esPrincipal;

    public void normalizar() {
        numero = Textos.limpio(numero);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPaisId() { return paisId; }
    public void setPaisId(Long paisId) { this.paisId = paisId; }
    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }
    public boolean isEsPrincipal() { return esPrincipal; }
    public void setEsPrincipal(boolean esPrincipal) { this.esPrincipal = esPrincipal; }
}
