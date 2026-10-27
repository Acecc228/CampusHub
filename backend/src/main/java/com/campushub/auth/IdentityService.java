package com.campushub.auth;

import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class IdentityService {
    private final IdentityMapper mapper;

    public IdentityService(IdentityMapper mapper) { this.mapper = mapper; }

    public CampusPrincipal loadDemoAccount(String loginName) {
        var account = mapper.findByLoginName(loginName);
        if (account == null || !account.demoAccount() || account.passwordHash() == null) {
            throw new UsernameNotFoundException("Invalid credentials");
        }
        var roles = mapper.roles(account.id());
        if (roles.isEmpty()) throw new UsernameNotFoundException("Invalid credentials");
        return new CampusPrincipal(account, roles);
    }

    public CampusPrincipal loadManagementAccount(String loginName) {
        var account = mapper.findByLoginName(loginName);
        if (account == null || !account.enabled() || account.demoAccount() || account.passwordHash() == null) {
            throw new UsernameNotFoundException("Invalid credentials");
        }
        var roles = mapper.roles(account.id());
        if (roles.isEmpty() || roles.stream().anyMatch(role -> !role.equals("ORGANIZER") && !role.equals("ADMIN"))) {
            throw new UsernameNotFoundException("Invalid credentials");
        }
        return new CampusPrincipal(account, roles);
    }

    public boolean isCurrent(CampusPrincipal principal) {
        var account = mapper.findById(principal.identity().id());
        return account != null && account.enabled() && account.loginName().equals(principal.identity().loginName())
                && principal.matchesAccountState(account)
                && mapper.roles(account.id()).equals(principal.identity().roles());
    }
}
