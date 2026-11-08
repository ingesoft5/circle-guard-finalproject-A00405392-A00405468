package com.circleguard.identity.service;

import com.circleguard.identity.model.IdentityMapping;
import com.circleguard.identity.repository.IdentityMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class IdentityVaultServiceTest {

    private final IdentityMappingRepository repository = mock(IdentityMappingRepository.class);
    private final IdentityVaultService service = new IdentityVaultService(repository);

    @BeforeEach
    void setSalt() {
        ReflectionTestUtils.setField(service, "hashSalt", "sal-de-prueba");
    }

    @Test
    void existingIdentityReturnsTheSameAnonymousIdWithoutSaving() {
        UUID existing = UUID.randomUUID();
        when(repository.findByIdentityHash(anyString()))
                .thenReturn(Optional.of(IdentityMapping.builder().anonymousId(existing).build()));

        assertThat(service.getOrCreateAnonymousId("ana@icesi.edu.co")).isEqualTo(existing);
        verify(repository, never()).save(any());
    }

    @Test
    void newIdentityIsStoredWithHashAndSaltButNotInPlainHash() {
        UUID created = UUID.randomUUID();
        when(repository.findByIdentityHash(anyString())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> {
            IdentityMapping m = inv.getArgument(0);
            m.setAnonymousId(created);
            return m;
        });

        assertThat(service.getOrCreateAnonymousId("ana@icesi.edu.co")).isEqualTo(created);

        ArgumentCaptor<IdentityMapping> saved = ArgumentCaptor.forClass(IdentityMapping.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getRealIdentity()).isEqualTo("ana@icesi.edu.co");
        assertThat(saved.getValue().getIdentityHash()).hasSize(64).doesNotContain("ana");
        assertThat(saved.getValue().getSalt()).isNotBlank();
    }

    @Test
    void sameIdentityAlwaysProducesTheSameHash() {
        when(repository.findByIdentityHash(anyString())).thenReturn(Optional.of(IdentityMapping.builder().anonymousId(UUID.randomUUID()).build()));

        service.getOrCreateAnonymousId("ana@icesi.edu.co");
        service.getOrCreateAnonymousId("ana@icesi.edu.co");
        service.getOrCreateAnonymousId("otro@icesi.edu.co");

        ArgumentCaptor<String> hashes = ArgumentCaptor.forClass(String.class);
        verify(repository, times(3)).findByIdentityHash(hashes.capture());
        assertThat(hashes.getAllValues().get(0)).isEqualTo(hashes.getAllValues().get(1));
        assertThat(hashes.getAllValues().get(0)).isNotEqualTo(hashes.getAllValues().get(2));
    }

    @Test
    void resolvesRealIdentityOrFailsWith404() {
        UUID known = UUID.randomUUID();
        when(repository.findById(any(UUID.class))).thenAnswer(inv ->
                known.equals(inv.getArgument(0))
                        ? Optional.of(IdentityMapping.builder().realIdentity("ana@icesi.edu.co").build())
                        : Optional.empty());

        assertThat(service.resolveRealIdentity(known)).isEqualTo("ana@icesi.edu.co");
        assertThatThrownBy(() -> service.resolveRealIdentity(UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Identity not found");
    }
}
