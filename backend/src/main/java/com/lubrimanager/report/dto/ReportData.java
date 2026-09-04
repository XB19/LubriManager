package com.lubrimanager.report.dto;

import java.util.List;

/** Résultat tabulaire d'un rapport : en-têtes + lignes. */
public record ReportData(String title, List<String> columns, List<List<Object>> rows) {

    public int rowCount() {
        return rows.size();
    }
}
