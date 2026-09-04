package com.lubrimanager.user;

import com.lubrimanager.common.web.PageResponse;
import com.lubrimanager.security.AppUserDetails;
import com.lubrimanager.user.dto.ChangePasswordRequest;
import com.lubrimanager.user.dto.CreateUserRequest;
import com.lubrimanager.user.dto.ResetPasswordRequest;
import com.lubrimanager.user.dto.UpdateUserRequest;
import com.lubrimanager.user.dto.UpdateUserStatusRequest;
import com.lubrimanager.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@Tag(name = "Utilisateurs")
@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "Lister / rechercher les utilisateurs")
    public PageResponse<UserResponse> list(@RequestParam(required = false) String q,
                                           @RequestParam(required = false) Role role,
                                           @RequestParam(required = false) Boolean active,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<User> result = userService.search(q, role, active,
                PageRequest.of(Math.max(page, 0), safeSize, Sort.by("username").ascending()));
        return PageResponse.of(result, UserResponse::from);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'un utilisateur")
    public UserResponse get(@PathVariable Long id) {
        return UserResponse.from(userService.get(id));
    }

    @PostMapping
    @Operation(summary = "Créer un utilisateur")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request,
                                               UriComponentsBuilder uriBuilder) {
        User created = userService.create(request);
        return ResponseEntity
                .created(uriBuilder.path("/api/users/{id}").buildAndExpand(created.getId()).toUri())
                .body(UserResponse.from(created));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier un utilisateur (nom, e-mail, rôle)")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return UserResponse.from(userService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Activer / désactiver un utilisateur")
    public UserResponse updateStatus(@PathVariable Long id,
                                     @Valid @RequestBody UpdateUserStatusRequest request,
                                     @AuthenticationPrincipal AppUserDetails current) {
        return UserResponse.from(userService.updateStatus(id, request.active(), current.getId()));
    }

    @PostMapping("/{id}/reset-password")
    @Operation(summary = "Réinitialiser le mot de passe d'un utilisateur")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Mot de passe réinitialisé")
    public ResponseEntity<Void> resetPassword(@PathVariable Long id,
                                              @Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(id, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/password")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Changer son propre mot de passe")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Mot de passe modifié")
    public ResponseEntity<Void> changeMyPassword(@Valid @RequestBody ChangePasswordRequest request,
                                                 @AuthenticationPrincipal AppUserDetails current) {
        userService.changeOwnPassword(current.getId(), request);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
