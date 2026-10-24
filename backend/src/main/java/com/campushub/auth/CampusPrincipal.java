package com.campushub.auth;

import java.io.Serializable;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

public class CampusPrincipal extends User {
    private final Identity identity;

    public record Identity(long id, String loginName, String displayName, List<String> roles) implements Serializable {}

    public CampusPrincipal(IdentityMapper.Account account, List<String> roles) {
        super(account.loginName(), account.passwordHash(), account.enabled(), true, true, true,
                roles.stream().map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role)).toList());
        this.identity = new Identity(account.id(), account.loginName(), account.displayName(), List.copyOf(roles));
    }

    public Identity identity() { return identity; }
}
