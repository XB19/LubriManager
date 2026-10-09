package com.lubrimanager.report;

import com.lubrimanager.report.dto.ReportData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rapports (§32) — {@code format=json} (tabulaire) ou {@code format=csv}
 * (téléchargement compatible Excel). Le rendu PDF / XLSX natif est une évolution.
 */
@Tag(name = "Rapports")
@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AUDITEUR')")
public class ReportController {

    private static final MediaType CSV = new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8);

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/{type}")
    @Operation(summary = "Générer un rapport (purchases, recharges, transactions, cancellations, audit)")
    public ResponseEntity<?> generate(
            @PathVariable ReportType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "json") String format) {

        Instant fromInstant = from != null ? from.atStartOfDay(ZoneOffset.UTC).toInstant() : Instant.EPOCH;
        Instant toInstant = to != null ? to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
                : Instant.parse("9999-01-01T00:00:00Z");

        ReportData data = reportService.generate(type, fromInstant, toInstant);

        if ("csv".equalsIgnoreCase(format)) {
            byte[] csv = CsvRenderer.render(data);
            String filename = "rapport-%s-%s.csv".formatted(type.name().toLowerCase(), LocalDate.now());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
            return ResponseEntity.ok().headers(headers).contentType(CSV).body(csv);
        }
        return ResponseEntity.ok(data);
    }
}
