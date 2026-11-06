package com.circleguard.auth.service;

import com.circleguard.auth.model.LocalUser;
import com.circleguard.auth.model.Permission;
import com.circleguard.auth.model.Role;
import com.circleguard.auth.repository.LocalUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomUserDetailsServiceTest {

    private final LocalUserRepository repository = mock(LocalUserRepository.class);
    private final CustomUserDetailsService service = new CustomUserDetailsService(repository);

    private LocalUser user(boolean active) {
        Permission lookup = Permission.builder().name("identity:lookup").build();
        Permission report = Permission.builder().name("symptom:report").build();
        Role health = Role.builder().name("HEALTH_CENTER").permissions(Set.of(lookup, report)).build();
        return LocalUser.builder()
                .username("health_user").password("{bcrypt}hash").isActive(active)
                .roles(Set.of(health)).build();
    }

    @Test
    void mapsRolesWithPrefixAndPermissionsAsAuthorities() {
        when(repository.findByUsername("health_user")).thenReturn(Optional.of(user(true)));

        UserDetails details = service.loadUserByUsername("health_user");

        assertThat(details.getUsername()).isEqualTo("health_user");
        assertThat(details.getPassword()).isEqualTo("{bcrypt}hash");
        assertThat(details.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_HEALTH_CENTER", "identity:lookup", "symptom:report");
    }

    @Test
    void unknownUserThrowsUsernameNotFound() {
        when(repository.findByUsername("nadie")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("nadie"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("nadie");
    }

    @Test
    void disabledUserIsRejected() {
        when(repository.findByUsername("health_user")).thenReturn(Optional.of(user(false)));

        assertThatThrownBy(() -> service.loadUserByUsername("health_user")).isInstanceOf(DisabledException.class);
    }
}
