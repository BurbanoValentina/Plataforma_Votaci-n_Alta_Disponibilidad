package com.voting.platform.edge.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO para la solicitud de registro o empadronamiento de un votante en el censo electoral.
 */
public class RegistrarVotanteRequest {

    @NotBlank(message = "El identificador del votante (cédula) es obligatorio")
    @Size(min = 5, max = 15, message = "El identificador del votante debe tener entre 5 y 15 caracteres")
    private String voterId;

    @NotBlank(message = "El nombre completo del votante es obligatorio")
    private String fullName;

    @NotBlank(message = "La ciudad de votación es obligatoria")
    private String city;

    private String region;

    /**
     * Constructor por defecto sin argumentos requerido para la serialización y deserialización JSON.
     */
    public RegistrarVotanteRequest() {
    }

    /**
     * Constructor con todos los campos.
     *
     * @param voterId  identificador único del votante (cédula)
     * @param fullName nombre completo del ciudadano
     * @param city     ciudad de votación
     * @param region   región geográfica/electoral asociada (opcional)
     */
    public RegistrarVotanteRequest(String voterId, String fullName, String city, String region) {
        this.voterId = voterId;
        this.fullName = fullName;
        this.city = city;
        this.region = region;
    }

    public String getVoterId() {
        return voterId;
    }

    public void setVoterId(String voterId) {
        this.voterId = voterId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    @Override
    public String toString() {
        return "RegistrarVotanteRequest{" +
                "voterId='" + voterId + '\'' +
                ", fullName='" + fullName + '\'' +
                ", city='" + city + '\'' +
                ", region='" + region + '\'' +
                '}';
    }
}
