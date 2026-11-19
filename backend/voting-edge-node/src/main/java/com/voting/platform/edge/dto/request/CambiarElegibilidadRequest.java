package com.voting.platform.edge.dto.request;

/**
 * DTO para la solicitud de actualización de elegibilidad de un votante en el censo electoral.
 */
public class CambiarElegibilidadRequest {

    private boolean eligible = true;

    /**
     * Constructor por defecto sin argumentos requerido para la serialización y deserialización JSON.
     * Inicializa la elegibilidad por defecto en true.
     */
    public CambiarElegibilidadRequest() {
    }

    /**
     * Constructor con el estado de elegibilidad.
     *
     * @param eligible indica si el votante se encuentra habilitado para sufragar
     */
    public CambiarElegibilidadRequest(boolean eligible) {
        this.eligible = eligible;
    }

    public boolean isEligible() {
        return eligible;
    }

    public boolean getEligible() {
        return eligible;
    }

    public void setEligible(boolean eligible) {
        this.eligible = eligible;
    }

    @Override
    public String toString() {
        return "CambiarElegibilidadRequest{" +
                "eligible=" + eligible +
                '}';
    }
}
