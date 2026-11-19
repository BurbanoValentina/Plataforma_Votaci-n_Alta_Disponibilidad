package com.voting.platform.edge.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO para la solicitud de autenticación de administradores en la consola de gestión electoral.
 */
public class LoginAdminRequest {

    @NotBlank(message = "El correo electrónico es obligatorio")
    @Email(message = "El correo electrónico debe tener un formato válido")
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 5, message = "La contraseña debe tener al menos 5 caracteres")
    private String password;

    /**
     * Constructor por defecto sin argumentos requerido para la serialización y deserialización JSON.
     */
    public LoginAdminRequest() {
    }

    /**
     * Constructor con todos los campos.
     *
     * @param email    correo electrónico administrativo
     * @param password contraseña de acceso
     */
    public LoginAdminRequest(String email, String password) {
        this.email = email;
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String toString() {
        return "LoginAdminRequest{" +
                "email='" + email + '\'' +
                ", password='[PROTECTED]'" +
                '}';
    }
}
