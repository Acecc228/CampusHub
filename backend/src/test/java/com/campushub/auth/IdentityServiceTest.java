package com.campushub.auth;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class IdentityServiceTest {
    final IdentityMapper mapper = mock(IdentityMapper.class);
    final IdentityService service = new IdentityService(mapper);

    @Test void formalLoginAcceptsOnlyEnabledNonDemoManagers() {
        for (var roles : List.of(List.of("STUDENT"), List.of("STUDENT", "ADMIN"), List.<String>of())) {
            when(mapper.findByLoginName("manager")).thenReturn(account(false, true));
            when(mapper.roles(7)).thenReturn(roles);
            assertThatThrownBy(() -> service.loadManagementAccount("manager")).isInstanceOf(UsernameNotFoundException.class);
        }
        when(mapper.roles(7)).thenReturn(List.of("ORGANIZER"));
        assertThat(service.loadManagementAccount("manager").identity().roles()).containsExactly("ORGANIZER");
        when(mapper.findByLoginName("manager")).thenReturn(account(true, true));
        assertThatThrownBy(() -> service.loadManagementAccount("manager")).isInstanceOf(UsernameNotFoundException.class);
        when(mapper.findByLoginName("manager")).thenReturn(account(false, false));
        assertThatThrownBy(() -> service.loadManagementAccount("manager")).isInstanceOf(UsernameNotFoundException.class);
    }

    @Test void subsequentRequestsRejectDisabledDeletedOrChangedRoles() {
        var principal = new CampusPrincipal(account(false, true), List.of("ORGANIZER"));
        when(mapper.findById(7)).thenReturn(account(false, true));
        when(mapper.roles(7)).thenReturn(List.of("ORGANIZER"));
        assertThat(service.isCurrent(principal)).isTrue();
        when(mapper.roles(7)).thenReturn(List.of("ADMIN"));
        assertThat(service.isCurrent(principal)).isFalse();
        when(mapper.roles(7)).thenReturn(List.of("ORGANIZER"));
        when(mapper.findById(7)).thenReturn(account(false, false));
        assertThat(service.isCurrent(principal)).isFalse();
        when(mapper.findById(7)).thenReturn(null);
        assertThat(service.isCurrent(principal)).isFalse();
    }

    @Test void passwordResetAndDemoFlagChangeRevokeEvenAfterPrincipalCredentialsWereErased() {
        var principal = new CampusPrincipal(account(false,true),List.of("ORGANIZER"));
        principal.eraseCredentials();
        when(mapper.roles(7)).thenReturn(List.of("ORGANIZER"));
        when(mapper.findById(7)).thenReturn(account(false,true));
        assertThat(service.isCurrent(principal)).isTrue();
        when(mapper.findById(7)).thenReturn(new IdentityMapper.Account(7,"manager","Manager","reset-hash",true,false));
        assertThat(service.isCurrent(principal)).isFalse();
        when(mapper.findById(7)).thenReturn(account(true,true));
        assertThat(service.isCurrent(principal)).isFalse();
    }

    private IdentityMapper.Account account(boolean demo, boolean enabled) {
        return new IdentityMapper.Account(7, "manager", "Manager", "hash", enabled, demo);
    }
}
