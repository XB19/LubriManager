package com.lubrimanager.dashboard;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

/** Bornes temporelles « jour / semaine / mois » évaluées dans le fuseau d'exploitation. */
final class DashboardPeriods {

    static final ZoneId ZONE = ZoneId.of("Africa/Lome");

    private DashboardPeriods() {
    }

    record Range(Instant from, Instant to) {
    }

    static Range today(Clock clock) {
        LocalDate day = LocalDate.now(clock.withZone(ZONE));
        return new Range(startOf(day), startOf(day.plusDays(1)));
    }

    static Range currentWeek(Clock clock) {
        LocalDate day = LocalDate.now(clock.withZone(ZONE));
        LocalDate monday = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return new Range(startOf(monday), startOf(day.plusDays(1)));
    }

    static Range currentMonth(Clock clock) {
        LocalDate day = LocalDate.now(clock.withZone(ZONE));
        return new Range(startOf(day.withDayOfMonth(1)), startOf(day.plusDays(1)));
    }

    private static Instant startOf(LocalDate date) {
        return date.atStartOfDay(ZONE).toInstant();
    }
}
