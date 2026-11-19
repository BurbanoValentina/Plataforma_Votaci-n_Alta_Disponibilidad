package com.voting.platform.edge.dto.request;

/**
 * DTO para la solicitud de configuración de notificaciones y alertas mediante Telegram Bot.
 */
public class ConfigurarTelegramRequest {

    private Boolean enabled;
    private String botToken;
    private String chatId;
    private Boolean notifyVotes;
    private Boolean notifyFraud;

    /**
     * Constructor por defecto sin argumentos requerido para la serialización y deserialización JSON.
     */
    public ConfigurarTelegramRequest() {
    }

    /**
     * Constructor con todos los campos.
     *
     * @param enabled     activa o desactiva las notificaciones globales hacia Telegram
     * @param botToken    token de autenticación del Bot otorgado por BotFather
     * @param chatId      identificador del chat o canal receptor de alertas
     * @param notifyVotes indica si se deben enviar notificaciones por cada voto procesado
     * @param notifyFraud indica si se deben emitir alertas ante incidentes de seguridad o fraude
     */
    public ConfigurarTelegramRequest(
        Boolean enabled,
        String botToken,
        String chatId,
        Boolean notifyVotes,
        Boolean notifyFraud
    ) {
        this.enabled = enabled;
        this.botToken = botToken;
        this.chatId = chatId;
        this.notifyVotes = notifyVotes;
        this.notifyFraud = notifyFraud;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getBotToken() {
        return botToken;
    }

    public void setBotToken(String botToken) {
        this.botToken = botToken;
    }

    public String getChatId() {
        return chatId;
    }

    public void setChatId(String chatId) {
        this.chatId = chatId;
    }

    public Boolean getNotifyVotes() {
        return notifyVotes;
    }

    public void setNotifyVotes(Boolean notifyVotes) {
        this.notifyVotes = notifyVotes;
    }

    public Boolean getNotifyFraud() {
        return notifyFraud;
    }

    public void setNotifyFraud(Boolean notifyFraud) {
        this.notifyFraud = notifyFraud;
    }

    @Override
    public String toString() {
        return "ConfigurarTelegramRequest{" +
                "enabled=" + enabled +
                ", botToken='" + (botToken != null ? "[CONFIGURED]" : "null") + '\'' +
                ", chatId='" + chatId + '\'' +
                ", notifyVotes=" + notifyVotes +
                ", notifyFraud=" + notifyFraud +
                '}';
    }
}
