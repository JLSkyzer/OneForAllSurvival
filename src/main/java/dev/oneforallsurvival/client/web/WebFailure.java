package dev.oneforallsurvival.client.web;

import java.util.Locale;

/** Raisons pour lesquelles le navigateur n'a pas pu servir : chacune a un texte de repli traduit. */
public enum WebFailure {
    ABSENT,
    NOT_INITIALIZED,
    CREATE_FAILED,
    LOAD_ERROR,
    NO_READY;

    public String translationKey() {
        return "message.oneforallsurvival.web.fallback." + name().toLowerCase(Locale.ROOT);
    }
}
