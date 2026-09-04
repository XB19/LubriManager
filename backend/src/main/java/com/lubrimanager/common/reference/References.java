package com.lubrimanager.common.reference;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Formatage des références métier. Logique pure (testable sans base) ; l'attribution
 * du numéro séquentiel est faite par {@link ReferenceCounterService}.
 */
public final class References {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private References() {
    }

    /** Ex. {@code format("CLI", 42, 6)} -> {@code "CLI-000042"}. */
    public static String format(String prefix, long value, int width) {
        return prefix + "-" + pad(value, width);
    }

    /** Ex. {@code formatDated("RECH", 2026-09-03, 1, 6)} -> {@code "RECH-20260903-000001"}. */
    public static String formatDated(String prefix, LocalDate date, long value, int width) {
        return prefix + "-" + DATE.format(date) + "-" + pad(value, width);
    }

    /** Portée du compteur pour une référence datée (une séquence par jour). */
    public static String datedScope(String prefix, LocalDate date) {
        return prefix + "-" + DATE.format(date);
    }

    private static String pad(long value, int width) {
        if (value < 0) {
            throw new IllegalArgumentException("La valeur de séquence doit être positive : " + value);
        }
        return String.format("%0" + width + "d", value);
    }
}
