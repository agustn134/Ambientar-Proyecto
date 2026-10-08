package com.proyecto.servicios.model.auth;

import java.time.Instant;

public record LoginResponse(String accessToken, String tokenType, long expiresIn, Instant expiresAt) {
    @Override public String toString() { return "LoginResponse[token protegido]"; }
}
