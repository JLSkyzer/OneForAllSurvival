package dev.oneforallsurvival.bench;

/** Issue d'une commande à l'établi. {@link #OK} est la seule qui a prélevé quelque chose. */
public enum OrderResult {
    OK,
    NOT_ENGINEER,
    UNKNOWN_RECIPE,
    MISSING_INGREDIENTS,
    QUEUE_FULL,
    TOO_FAR,
    BAD_COUNT
}
