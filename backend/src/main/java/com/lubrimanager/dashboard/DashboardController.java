package com.lubrimanager.dashboard;

import com.lubrimanager.dashboard.dto.AgentDaySummary;
import com.lubrimanager.dashboard.dto.DashboardResponse;
import com.lubrimanager.dashboard.dto.TopCustomerRow;
import com.lubrimanager.dashboard.dto.TopProductRow;
import com.lubrimanager.dashboard.dto.UserActivityRow;
import com.lubrimanager.security.AppUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Tableau de bord")
@RestController
@RequestMapping("/api/dashboard")
@PreAuthorize("isAuthenticated()")
public class DashboardController {

    private static final Instant DEFAULT_FROM = Instant.parse("2000-01-01T00:00:00Z");
    private static final Instant FAR_FUTURE = Instant.parse("9999-01-01T00:00:00Z");

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AUDITEUR')")
    @Operation(summary = "Indicateurs globaux + agrégats jour / semaine / mois")
    public DashboardResponse overview() {
        return dashboardService.overview();
    }

    @GetMapping("/top-products")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AUDITEUR')")
    @Operation(summary = "Produits les plus vendus sur une période")
    public List<TopProductRow> topProducts(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                           @RequestParam(defaultValue = "10") int limit) {
        return dashboardService.topProducts(from(from), to(to), limit);
    }

    @GetMapping("/top-customers")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AUDITEUR')")
    @Operation(summary = "Clients les plus actifs sur une période")
    public List<TopCustomerRow> topCustomers(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                             @RequestParam(defaultValue = "10") int limit) {
        return dashboardService.topCustomers(from(from), to(to), limit);
    }

    @GetMapping("/user-activity")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AUDITEUR')")
    @Operation(summary = "Activité par utilisateur / agent sur une période")
    public List<UserActivityRow> userActivity(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return dashboardService.userActivity(from(from), to(to));
    }

    @GetMapping("/me")
    @Operation(summary = "Rapport de caisse du jour de l'utilisateur connecté (DEC-20)")
    public AgentDaySummary myDay(@AuthenticationPrincipal AppUserDetails current) {
        return dashboardService.agentDay(current.getId(), current.getUsername());
    }

    private static Instant from(LocalDate d) {
        return d != null ? d.atStartOfDay(ZoneOffset.UTC).toInstant() : DEFAULT_FROM;
    }

    private static Instant to(LocalDate d) {
        return d != null ? d.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant() : FAR_FUTURE;
    }
}
