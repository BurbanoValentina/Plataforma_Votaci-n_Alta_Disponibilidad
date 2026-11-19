package com.voting.platform.edge.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

/**
 * DTO para la solicitud de configuración del simulador de tráfico de votos y pruebas de estrés.
 */
public class ConfigurarSimuladorRequest {

    private Boolean simulatorEnabled;

    @Positive(message = "El intervalo de tasa de simulación debe ser un valor positivo")
    private Integer simulatorRateMs;

    @Min(value = 1, message = "El tamaño de lote de simulación debe ser de al menos 1")
    private Integer simulatorBatchSize;

    private Boolean triggerBotAttack;

    /**
     * Constructor por defecto sin argumentos requerido para la serialización y deserialización JSON.
     */
    public ConfigurarSimuladorRequest() {
    }

    /**
     * Constructor con todos los campos.
     *
     * @param simulatorEnabled  activa o desactiva la generación automática de votos simulados
     * @param simulatorRateMs   intervalo en milisegundos entre emisiones del simulador
     * @param simulatorBatchSize cantidad de votos emitidos por cada iteración del simulador
     * @param triggerBotAttack  dispara una simulación inmediata de ataque distribuido de bots
     */
    public ConfigurarSimuladorRequest(
        Boolean simulatorEnabled,
        Integer simulatorRateMs,
        Integer simulatorBatchSize,
        Boolean triggerBotAttack
    ) {
        this.simulatorEnabled = simulatorEnabled;
        this.simulatorRateMs = simulatorRateMs;
        this.simulatorBatchSize = simulatorBatchSize;
        this.triggerBotAttack = triggerBotAttack;
    }

    public Boolean getSimulatorEnabled() {
        return simulatorEnabled;
    }

    public void setSimulatorEnabled(Boolean simulatorEnabled) {
        this.simulatorEnabled = simulatorEnabled;
    }

    public Integer getSimulatorRateMs() {
        return simulatorRateMs;
    }

    public void setSimulatorRateMs(Integer simulatorRateMs) {
        this.simulatorRateMs = simulatorRateMs;
    }

    public Integer getSimulatorBatchSize() {
        return simulatorBatchSize;
    }

    public void setSimulatorBatchSize(Integer simulatorBatchSize) {
        this.simulatorBatchSize = simulatorBatchSize;
    }

    public Boolean getTriggerBotAttack() {
        return triggerBotAttack;
    }

    public void setTriggerBotAttack(Boolean triggerBotAttack) {
        this.triggerBotAttack = triggerBotAttack;
    }

    @Override
    public String toString() {
        return "ConfigurarSimuladorRequest{" +
                "simulatorEnabled=" + simulatorEnabled +
                ", simulatorRateMs=" + simulatorRateMs +
                ", simulatorBatchSize=" + simulatorBatchSize +
                ", triggerBotAttack=" + triggerBotAttack +
                '}';
    }
}
