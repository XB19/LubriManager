package com.lubrimanager.user;

import com.lubrimanager.auth.RefreshTokenService;
import com.lubrimanager.branch.Branch;
import com.lubrimanager.branch.BranchRepository;
import com.lubrimanager.common.error.ApiException;
import com.lubrimanager.user.dto.ChangePasswordRequest;
import com.lubrimanager.user.dto.CreateUserRequest;
import com.lubrimanager.user.dto.ResetPasswordRequest;
import com.lubrimanager.user.dto.UpdateUserRequest;
import java.time.Clock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Gestion des comptes applicatifs. Toute la logique (unicité, garde du dernier
 * administrateur, révocation des sessions) vit ici, jamais dans le contrôleur.
 */
@Service
@Transactional
public class UserService {

    private final UserRepository users;
    private final BranchRepository branches;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokens;
    private final Clock clock;

    public UserService(UserRepository users, BranchRepository branches, PasswordEncoder passwordEncoder,
                       RefreshTokenService refreshTokens, Clock clock) {
        this.users = users;
        this.branches = branches;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokens = refreshTokens;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<User> search(String q, Role role, Boolean active, Pageable pageable) {
        String pattern = StringUtils.hasText(q)
                ? "%" + q.trim().toLowerCase(java.util.Locale.ROOT) + "%"
                : "%";
        return users.search(pattern, role, active, pageable);
    }

    @Transactional(readOnly = true)
    public User get(Long id) {
        return users.findById(id)
                .orElseThrow(() -> ApiException.notFound("Utilisateur %d introuvable".formatted(id)));
    }

    public User create(CreateUserRequest req) {
        if (users.existsByUsernameIgnoreCase(req.username())) {
            throw ApiException.conflict("Ce nom d'utilisateur est déjà pris.");
        }
        if (StringUtils.hasText(req.email()) && users.existsByEmailIgnoreCase(req.email())) {
            throw ApiException.conflict("Cette adresse e-mail est déjà utilisée.");
        }

        User user = new User();
        user.setUsername(req.username().trim());
        user.setEmail(StringUtils.hasText(req.email()) ? req.email().trim() : null);
        user.setFullName(req.fullName().trim());
        user.setRole(req.role());
        user.setActive(true);
        user.setBranch(resolveBranch(req.branchId()));
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setPasswordChangedAt(clock.instant());
        return users.save(user);
    }

    public User update(Long id, UpdateUserRequest req) {
        User user = get(id);

        if (user.getRole() == Role.ADMIN && req.role() != Role.ADMIN
                && users.countByRoleAndActiveTrue(Role.ADMIN) <= 1) {
            throw ApiException.businessRule("Impossible de retirer le rôle du dernier administrateur actif.");
        }
        if (StringUtils.hasText(req.email())
                && !req.email().equalsIgnoreCase(user.getEmail())
                && users.existsByEmailIgnoreCase(req.email())) {
            throw ApiException.conflict("Cette adresse e-mail est déjà utilisée.");
        }

        user.setFullName(req.fullName().trim());
        user.setEmail(StringUtils.hasText(req.email()) ? req.email().trim() : null);
        user.setRole(req.role());
        return user;
    }

    public User updateStatus(Long id, boolean active, Long actingUserId) {
        User user = get(id);
        if (user.getId().equals(actingUserId)) {
            throw ApiException.businessRule("Vous ne pouvez pas modifier votre propre statut.");
        }
        if (!active && user.getRole() == Role.ADMIN
                && users.countByRoleAndActiveTrue(Role.ADMIN) <= 1) {
            throw ApiException.businessRule("Impossible de désactiver le dernier administrateur actif.");
        }

        user.setActive(active);
        if (!active) {
            refreshTokens.revokeAllForUser(user.getId()); // RG-17
        }
        return user;
    }

    public void changeOwnPassword(Long userId, ChangePasswordRequest req) {
        User user = get(userId);
        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw ApiException.businessRule("Le mot de passe actuel est incorrect.");
        }
        if (passwordEncoder.matches(req.newPassword(), user.getPasswordHash())) {
            throw ApiException.businessRule("Le nouveau mot de passe doit être différent de l'ancien.");
        }
        applyNewPassword(user, req.newPassword());
    }

    public void resetPassword(Long id, ResetPasswordRequest req) {
        applyNewPassword(get(id), req.newPassword());
    }

    private void applyNewPassword(User user, String rawPassword) {
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setPasswordChangedAt(clock.instant());
        refreshTokens.revokeAllForUser(user.getId()); // toutes les sessions doivent se reconnecter
    }

    private Branch resolveBranch(Long branchId) {
        if (branchId != null) {
            return branches.findById(branchId)
                    .orElseThrow(() -> ApiException.notFound("Agence %d introuvable".formatted(branchId)));
        }
        return branches.findByCode("MAIN")
                .or(() -> branches.findAll().stream().findFirst())
                .orElseThrow(() -> ApiException.businessRule("Aucune agence n'est configurée."));
    }
}
