package com.circleguard.auth.security;

import com.circleguard.auth.service.CustomUserDetailsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.*;
import org.springframework.security.ldap.authentication.LdapAuthenticationProvider;
import org.springframework.stereotype.Component;

/**
 * Autenticación en dos cadenas: LDAP y, si falla, la base local.
 *
 * <p>HU-18 (#25): la primera cadena es un <b>feature toggle</b> ({@code circleguard.features.ldap-login-enabled},
 * en Kubernetes la variable {@code FEATURE_LDAP_LOGIN} del ConfigMap). Apagado, el login va directo a la base
 * local sin intentar LDAP, lo que evita esperar a un servidor que en muchos ambientes no existe.
 * Por defecto está encendido: el comportamiento de siempre.
 */
@Component
public class DualChainAuthenticationProvider implements AuthenticationProvider {

    private static final Logger log = LoggerFactory.getLogger(DualChainAuthenticationProvider.class);

    private final LdapAuthenticationProvider ldapProvider;
    private final DaoAuthenticationProvider localProvider;
    private final boolean ldapLoginEnabled;

    /** Comportamiento de siempre: LDAP encendido. */
    public DualChainAuthenticationProvider(LdapAuthenticationProvider ldapProvider, DaoAuthenticationProvider localProvider) {
        this(ldapProvider, localProvider, true);
    }

    @Autowired
    public DualChainAuthenticationProvider(
            LdapAuthenticationProvider ldapProvider,
            DaoAuthenticationProvider localProvider,
            @Value("${circleguard.features.ldap-login-enabled:true}") boolean ldapLoginEnabled) {
        this.ldapProvider = ldapProvider;
        this.localProvider = localProvider;
        this.ldapLoginEnabled = ldapLoginEnabled;
        log.info("Feature toggle circleguard.features.ldap-login-enabled = {}", ldapLoginEnabled);
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        if (!ldapLoginEnabled) {
            return localProvider.authenticate(authentication);
        }
        try {
            // Chain 1: Try LDAP
            return ldapProvider.authenticate(authentication);
        } catch (AuthenticationException e) {
            // Chain 2: Fallback to Local DB
            return localProvider.authenticate(authentication);
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
