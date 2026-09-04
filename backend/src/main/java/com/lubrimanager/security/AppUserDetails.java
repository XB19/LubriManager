package com.lubrimanager.security;

import com.lubrimanager.user.Role;
import com.lubrimanager.user.User;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Principal placé dans le {@code SecurityContext}. Expose l'identité minimale utile
 * aux contrôleurs sans re-solliciter la base.
 */
public class AppUserDetails implements UserDetails {

    private final Long id;
    private final String username;
    private final String passwordHash;
    private final Role role;
    private final Long branchId;
    private final boolean active;

    public AppUserDetails(Long id, String username, String passwordHash, Role role, Long branchId, boolean active) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.branchId = branchId;
        this.active = active;
    }

    public static AppUserDetails from(User user) {
        return new AppUserDetails(user.getId(), user.getUsername(), user.getPasswordHash(),
                user.getRole(), user.getBranch().getId(), user.isActive());
    }

    public Long getId() {
        return id;
    }

    public Role getRole() {
        return role;
    }

    public Long getBranchId() {
        return branchId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(role.grantedAuthority());
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
