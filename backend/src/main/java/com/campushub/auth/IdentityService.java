package com.campushub.auth;

import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class IdentityService {
    private final IdentityMapper mapper;

    public IdentityService(IdentityMapper mapper) { this.mapper = mapper; }

    // Future WeChat exchange resolves openid to an internal account, never a client-supplied user ID.
    public CampusPrincipal loadDemoAccount(String loginName) {
        var account = mapper.findByLoginName(loginName);
        if (account == null || !account.demoAccount() || account.passwordHash() == null) {
            throw new UsernameNotFoundException("Invalid credentials");
        }
        var roles = mapper.roles(account.id());
        if (roles.isEmpty()) throw new UsernameNotFoundException("Invalid credentials");
        return new CampusPrincipal(account, roles);
    }
}
