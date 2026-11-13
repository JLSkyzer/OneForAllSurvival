package dev.oneforallsurvival.client.title;

/** Réglages de l'écran titre fixés dans le code. */
public final class TitleConfig {
    /**
     * Adresse de repli du serveur de Lost Island (« hôte » ou « hôte:port »), utilisée seulement si le site n'a
     * jamais répondu et qu'aucune adresse n'est gardée. Vide : le bouton « Rejoindre l'île » est alors grisé avec
     * l'infobulle « Serveur bientôt disponible ». L'adresse en usage est celle du site ({@link SiteAddressClient}).
     */
    public static final String SERVER_ADDRESS = "";
    /** Page publique du site qui donne l'adresse du serveur et l'état de maintenance. */
    public static final String SITE_ADDRESS_URL = "https://lostisland.eriniumlab.com/api/adresse";
    /** Mettre à {@code false} pour ne plus interroger le site (le fichier gardé et le repli restent utilisés). */
    public static final boolean SITE_LOOKUP_ENABLED = true;
    /** Nom du serveur montré pendant la connexion (il n'est pas ajouté à la liste des serveurs). */
    public static final String SERVER_NAME = "Lost Island RP";

    private TitleConfig() {}
}
