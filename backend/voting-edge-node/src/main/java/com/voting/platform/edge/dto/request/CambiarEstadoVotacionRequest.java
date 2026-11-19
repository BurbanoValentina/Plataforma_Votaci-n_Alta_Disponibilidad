package com.voting.platform.edge.dto.request;

import jakarta.validation.constraints.Pattern;

/**
 * DTO para la solicitud de cambio de estado del ciclo de vida electoral y configuración del censo estricto.
 */
public class CambiarEstadoVotacionRequest {

    @Pattern(
        regexp = "^(HABILITADO|EN_AUDITORIA|CERRADO)$",
        message = "El estado debe ser HABILITADO, EN_AUDITORIA o CERRADO"
    )
    private String lifecycleState;

    private Boolean strictCensusMode;

    /**
     * Constructor por defecto sin argumentos requerido para la serialización y deserialización JSON.
     */
    public CambiarEstadoVotacionRequest() {
    }

    /**
     * Constructor con todos los campos.
     *
     * @param lifecycleState   nuevo estado del ciclo electoral (HABILITADO, EN_AUDITORIA o CERRADO)
     * @param strictCensusMode indicador para activar o desactivar la verificación estricta de censo
     */
    public CambiarEstadoVotacionRequest(String lifecycleState, Boolean strictCensusMode) {
        this.lifecycleState = lifecycleState;
        this.strictCensusMode = strictCensusMode;
    }

    public String getLifecycleState() {
        return lifecycleState;
    }

    public void setLifecycleState(String lifecycleState) {
        this.lifecycleState = lifecycleState;
    }

    public Boolean getStrictCensusMode() {
        return strictCensusMode;
    }

    public void setStrictCensusMode(Boolean strictCensusMode) {
        this.strictCensusMode = strictCensusMode;
    }

    @Override
    public String toString() {
        return "CambiarEstadoVotacionRequest{" +
                "lifecycleState='" + lifecycleState + '\'' +
                ", strictCensusMode=" + strictCensusMode +
                '}';
    }
}
