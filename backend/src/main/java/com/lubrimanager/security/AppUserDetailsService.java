package com.lubrimanager.security;

import com.lubrimanager.user.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Chargement des utilisateurs depuis la base. Déclaré pour désactiver
 * l'utilisateur par défaut généré par Spring Security ; l'authentification
 * elle-même est pilotée par {@code AuthService} (gestion du verrouillage).
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    public AppUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        return users.findByUsernameIgnoreCase(username)
                .map(AppUserDetails::from)
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur introuvable"));
    }
}
