package com.campushub.auth;

import java.io.Serializable;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

public class CampusPrincipal extends User {
    private final Identity identity;
    private final byte[] credentialFingerprint;
    private final boolean demoAccount;

    public record Identity(long id, String loginName, String displayName, List<String> roles) implements Serializable {}

    public CampusPrincipal(IdentityMapper.Account account, List<String> roles) {
        super(account.loginName(), account.passwordHash() == null ? "" : account.passwordHash(), account.enabled(), true, true, true,
                roles.stream().map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role)).toList());
        this.identity = new Identity(account.id(), account.loginName(), account.displayName(), List.copyOf(roles));
        this.credentialFingerprint = fingerprint(account.passwordHash());
        this.demoAccount = account.demoAccount();
    }

    public Identity identity() { return identity; }

    // DaoAuthenticationProvider erases User.password; keep only a one-way snapshot for reset revocation.
    boolean matchesAccountState(IdentityMapper.Account account) {
        return demoAccount == account.demoAccount()
                && MessageDigest.isEqual(credentialFingerprint,fingerprint(account.passwordHash()));
    }

    private static byte[] fingerprint(String passwordHash) {
        try {
            return MessageDigest.getInstance("SHA-256").digest((passwordHash == null ? "0" : "1" + passwordHash).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("Required digest unavailable"); }
    }
}
