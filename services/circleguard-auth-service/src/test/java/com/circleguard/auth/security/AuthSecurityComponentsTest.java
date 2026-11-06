package com.circleguard.auth.security;

import com.circleguard.auth.service.JwtTokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.ldap.authentication.LdapAuthenticationProvider;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthSecurityComponentsTest {

    private static final String SECRET = "unit-test-jwt-secret-key-with-32-chars-min";

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest requestWith(String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (authorization != null) request.addHeader("Authorization", authorization);
        return request;
    }

    @Test
    void validBearerTokenAuthenticatesWithItsPermissions() throws Exception {
        UUID anonymousId = UUID.randomUUID();
        String token = new JwtTokenService(SECRET, 60_000).generateToken(anonymousId,
                new UsernamePasswordAuthenticationToken("u", null, List.of(new SimpleGrantedAuthority("ROLE_GATE_STAFF"))));
        MockFilterChain chain = new MockFilterChain();

        new JwtAuthenticationFilter(SECRET).doFilter(requestWith("Bearer " + token), new MockHttpServletResponse(), chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getName()).isEqualTo(anonymousId.toString());
        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_GATE_STAFF");
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void invalidTokenLeavesRequestAnonymousButContinuesTheChain() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        new JwtAuthenticationFilter(SECRET).doFilter(requestWith("Bearer no-es-un-jwt"), new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void requestWithoutHeaderIsNotAuthenticated() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        new JwtAuthenticationFilter(SECRET).doFilter(requestWith(null), new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void dualChainUsesLdapFirst() {
        LdapAuthenticationProvider ldap = mock(LdapAuthenticationProvider.class);
        DaoAuthenticationProvider local = mock(DaoAuthenticationProvider.class);
        Authentication request = new UsernamePasswordAuthenticationToken("u", "p");
        Authentication ldapResult = new UsernamePasswordAuthenticationToken("u", null, List.of());
        when(ldap.authenticate(request)).thenReturn(ldapResult);

        Authentication result = new DualChainAuthenticationProvider(ldap, local).authenticate(request);

        assertThat(result).isSameAs(ldapResult);
        verifyNoInteractions(local);
    }

    @Test
    void dualChainFallsBackToLocalUsersWhenLdapFails() {
        LdapAuthenticationProvider ldap = mock(LdapAuthenticationProvider.class);
        DaoAuthenticationProvider local = mock(DaoAuthenticationProvider.class);
        Authentication request = new UsernamePasswordAuthenticationToken("super_admin", "password");
        Authentication localResult = new UsernamePasswordAuthenticationToken("super_admin", null, List.of());
        when(ldap.authenticate(any())).thenThrow(new BadCredentialsException("LDAP no disponible"));
        when(local.authenticate(request)).thenReturn(localResult);

        DualChainAuthenticationProvider provider = new DualChainAuthenticationProvider(ldap, local);

        assertThat(provider.authenticate(request)).isSameAs(localResult);
        assertThat(provider.supports(UsernamePasswordAuthenticationToken.class)).isTrue();
        assertThat(provider.supports(Authentication.class)).isFalse();
    }
}
