package com.circleguard.auth.security;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.ldap.authentication.LdapAuthenticationProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * HU-18 (#25): feature toggle {@code circleguard.features.ldap-login-enabled}. Se prueban los dos estados:
 * encendido (la cadena de siempre: LDAP y luego la base local) y apagado (solo la base local, sin tocar LDAP).
 */
class LdapLoginToggleTest {

    private final LdapAuthenticationProvider ldap = mock(LdapAuthenticationProvider.class);
    private final DaoAuthenticationProvider local = mock(DaoAuthenticationProvider.class);
    private final Authentication request = new UsernamePasswordAuthenticationToken("super_admin", "password");
    private final Authentication authenticated = mock(Authentication.class);

    @Test
    void conElToggleEncendidoSeProbaPrimeroLdapYLuegoLaBaseLocal() {
        when(ldap.authenticate(any())).thenThrow(new BadCredentialsException("no esta en LDAP"));
        when(local.authenticate(any())).thenReturn(authenticated);

        Authentication result = new DualChainAuthenticationProvider(ldap, local, true).authenticate(request);

        assertThat(result).isSameAs(authenticated);
        verify(ldap).authenticate(request);
        verify(local).authenticate(request);
    }

    @Test
    void conElToggleEncendidoYLdapExitosoNoSeConsultaLaBaseLocal() {
        when(ldap.authenticate(any())).thenReturn(authenticated);

        Authentication result = new DualChainAuthenticationProvider(ldap, local, true).authenticate(request);

        assertThat(result).isSameAs(authenticated);
        verifyNoInteractions(local);
    }

    @Test
    void conElToggleApagadoNuncaSeLlamaALdapYSeUsaLaBaseLocal() {
        when(local.authenticate(any())).thenReturn(authenticated);

        Authentication result = new DualChainAuthenticationProvider(ldap, local, false).authenticate(request);

        assertThat(result).isSameAs(authenticated);
        verifyNoInteractions(ldap);
        verify(local).authenticate(request);
    }

    @Test
    void conElToggleApagadoLasCredencialesMalasSiguenRechazandose() {
        when(local.authenticate(any())).thenThrow(new BadCredentialsException("clave incorrecta"));

        var provider = new DualChainAuthenticationProvider(ldap, local, false);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> provider.authenticate(request))
                .isInstanceOf(BadCredentialsException.class);
        verify(ldap, never()).authenticate(any());
    }

    @Test
    void elConstructorDeDosArgumentosConservaElComportamientoDeSiempre() {
        when(ldap.authenticate(any())).thenThrow(new BadCredentialsException("no esta en LDAP"));
        when(local.authenticate(any())).thenReturn(authenticated);

        new DualChainAuthenticationProvider(ldap, local).authenticate(request);

        verify(ldap).authenticate(request);
    }

    // --- El valor llega desde la configuración externa (en Kubernetes, el ConfigMap) ---------------------

    @Configuration
    static class Mocks {
        @Bean
        LdapAuthenticationProvider ldapProvider() {
            return Mockito.mock(LdapAuthenticationProvider.class);
        }

        @Bean
        DaoAuthenticationProvider localProvider() {
            return Mockito.mock(DaoAuthenticationProvider.class);
        }
    }

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(Mocks.class)
            .withBean(DualChainAuthenticationProvider.class);

    @Test
    void sinConfiguracionElToggleQuedaEncendido() {
        contextRunner.run(ctx -> {
            when(ctx.getBean(LdapAuthenticationProvider.class).authenticate(any())).thenReturn(authenticated);

            ctx.getBean(DualChainAuthenticationProvider.class).authenticate(request);

            verify(ctx.getBean(LdapAuthenticationProvider.class)).authenticate(request);
        });
    }

    @Test
    void laPropiedadExternaApagaElToggle() {
        contextRunner.withPropertyValues("circleguard.features.ldap-login-enabled=false").run(ctx -> {
            when(ctx.getBean(DaoAuthenticationProvider.class).authenticate(any())).thenReturn(authenticated);

            ctx.getBean(DualChainAuthenticationProvider.class).authenticate(request);

            // El contenedor de Spring también toca al mock (setMessageSource...): se verifica que nunca autenticó.
            verify(ctx.getBean(LdapAuthenticationProvider.class), never()).authenticate(any());
        });
    }
}
