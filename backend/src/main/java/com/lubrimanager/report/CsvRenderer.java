package com.lubrimanager.report;

import com.lubrimanager.report.dto.ReportData;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Rend un {@link ReportData} en CSV lisible par Excel (français) : BOM UTF-8,
 * séparateur {@code ;}, fins de ligne CRLF, champs échappés si besoin.
 */
final class CsvRenderer {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private CsvRenderer() {
    }

    static byte[] render(ReportData data) {
        StringBuilder sb = new StringBuilder();
        writeRow(sb, data.columns());
        for (List<Object> row : data.rows()) {
            writeRow(sb, row.stream().map(CsvRenderer::stringify).toList());
        }
        byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[BOM.length + body.length];
        System.arraycopy(BOM, 0, out, 0, BOM.length);
        System.arraycopy(body, 0, out, BOM.length, body.length);
        return out;
    }

    private static void writeRow(StringBuilder sb, List<?> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                sb.append(';');
            }
            sb.append(escape(String.valueOf(cells.get(i))));
        }
        sb.append("\r\n");
    }

    private static String stringify(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String escape(String value) {
        if (value.contains(";") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }
}
