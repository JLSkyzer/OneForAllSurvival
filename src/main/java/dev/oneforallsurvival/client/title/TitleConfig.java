package dev.oneforallsurvival.client.title;

/** Réglages de l'écran titre fixés dans le code. */
public final class TitleConfig {
    /**
     * Adresse du serveur de Lost Island (« hôte » ou « hôte:port »). Vide tant que le serveur n'existe pas : le
     * bouton « Rejoindre l'île » est alors grisé avec l'infobulle « Serveur bientôt disponible ».
     */
    public static final String SERVER_ADDRESS = "";
    /** Nom du serveur montré pendant la connexion (il n'est pas ajouté à la liste des serveurs). */
    public static final String SERVER_NAME = "Lost Island RP";

    private TitleConfig() {}
}
