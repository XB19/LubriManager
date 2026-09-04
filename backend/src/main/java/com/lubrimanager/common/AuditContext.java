package com.lubrimanager.common;

/**
 * Contexte technique de la requête HTTP courante (IP, agent), déposé par un
 * filtre et lu par la couche audit. Vide en dehors d'une requête HTTP (tâches
 * planifiées).
 */
public final class AuditContext {

    private static final ThreadLocal<Info> HOLDER = new ThreadLocal<>();

    private AuditContext() {
    }

    public record Info(String ip, String userAgent) {
    }

    public static void set(String ip, String userAgent) {
        HOLDER.set(new Info(ip, trim(userAgent)));
    }

    public static Info get() {
        Info info = HOLDER.get();
        return info != null ? info : new Info(null, null);
    }

    public static void clear() {
        HOLDER.remove();
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 300 ? value : value.substring(0, 300);
    }
}
