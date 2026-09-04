package com.lubrimanager.user;

import com.lubrimanager.branch.Branch;
import com.lubrimanager.branch.BranchRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Crée le compte administrateur initial si la table est vide. Sans ce compte,
 * personne ne pourrait se connecter au premier lancement.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AdminBootstrapProperties props;
    private final UserRepository users;
    private final BranchRepository branches;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public AdminBootstrap(AdminBootstrapProperties props, UserRepository users, BranchRepository branches,
                          PasswordEncoder passwordEncoder, Clock clock) {
        this.props = props;
        this.users = users;
        this.branches = branches;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!props.enabled()) {
            return;
        }
        if (users.countByRole(Role.ADMIN) > 0) {
            return;
        }
        if (!StringUtils.hasText(props.username()) || !StringUtils.hasText(props.password())) {
            log.warn("Bootstrap admin activé mais username/password non fournis — compte non créé.");
            return;
        }

        Branch branch = branches.findByCode("MAIN")
                .or(() -> branches.findAll().stream().findFirst())
                .orElseThrow(() -> new IllegalStateException("Aucune agence — la migration V2 doit s'exécuter avant."));

        User admin = new User();
        admin.setUsername(props.username().trim());
        admin.setFullName(StringUtils.hasText(props.fullName()) ? props.fullName().trim() : "Administrateur");
        admin.setRole(Role.ADMIN);
        admin.setActive(true);
        admin.setBranch(branch);
        admin.setPasswordHash(passwordEncoder.encode(props.password()));
        admin.setPasswordChangedAt(clock.instant());
        users.save(admin);

        log.warn("Compte administrateur initial « {} » créé. Connectez-vous et changez immédiatement le mot de passe.",
                admin.getUsername());
    }
}
