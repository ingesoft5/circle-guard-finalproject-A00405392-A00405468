package com.circleguard.auth.client;

/**
 * identity-service no respondió después de los reintentos, o el circuito está abierto.
 * El login la traduce a 503 para que el cliente sepa que puede intentar más tarde.
 */
public class IdentityServiceUnavailableException extends RuntimeException {

    public IdentityServiceUnavailableException(Throwable cause) {
        super("identity-service no disponible", cause);
    }
}
