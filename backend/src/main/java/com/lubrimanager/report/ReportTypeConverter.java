package com.lubrimanager.report;

import java.util.Locale;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/** Accepte le type de rapport dans l'URL quelle que soit la casse (ex. {@code sales}). */
@Component
public class ReportTypeConverter implements Converter<String, ReportType> {

    @Override
    public ReportType convert(String source) {
        return ReportType.valueOf(source.trim().toUpperCase(Locale.ROOT));
    }
}
