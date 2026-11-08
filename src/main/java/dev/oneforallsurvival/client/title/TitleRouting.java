package dev.oneforallsurvival.client.title;

/**
 * Décision pure, sans classe Minecraft (testée par {@code TitleRoutingTest}) : l'écran qui s'ouvre doit-il devenir
 * l'écran titre de Lost Island ?
 *
 * <p>Deux cas, lus au {@code javap} de Minecraft 1.20.1 / Forge 47.4.28 :
 * <ul>
 *   <li>un {@code TitleScreen} de classe exacte (premier titre, sortie d'un monde solo, {@code setScreen(null)} hors
 *       monde) ;</li>
 *   <li>la liste Multijoueur ({@code JoinMultiplayerScreen} de classe exacte) dont le parent est un
 *       {@code TitleScreen} de classe exacte, créé neuf : c'est le retour de {@code PauseScreen.onDisconnect} (et de
 *       {@code ClientPacketListener.onDisconnect} sans écran de rappel) après une déconnexion
 *       ({@code new JoinMultiplayerScreen(new TitleScreen())}). Notre titre n'a pas de bouton Multijoueur, le joueur
 *       n'a donc pas d'autre chemin vers cette liste. Ouverte par un autre mod, depuis un autre écran ou depuis notre
 *       titre ({@code new JoinMultiplayerScreen(this)}, parent de classe non exacte), elle est laissée.</li>
 * </ul>
 * L'expulsion par le serveur ouvre un {@code DisconnectedScreen} dont le retour est notre instance (parent de la
 * connexion) : rien à remplacer.
 */
public final class TitleRouting {
    private TitleRouting() {}

    /** Nature de l'écran qui s'ouvre. */
    public enum Opening {
        /** {@code TitleScreen} de classe exacte. */
        VANILLA_TITLE,
        /** {@code JoinMultiplayerScreen} de classe exacte. */
        MULTIPLAYER_LIST,
        /** Tout autre écran, dont le nôtre et les sous-classes des autres mods. */
        OTHER
    }

    /**
     * @param opening        l'écran qui s'ouvre
     * @param parentIsTitle  pour la liste Multijoueur : son écran parent est un {@code TitleScreen} de classe
     *                       exacte (celui de Minecraft, pas le nôtre ni une sous-classe) ; ignoré sinon
     * @return {@code true} s'il faut ouvrir l'écran titre de Lost Island à sa place
     */
    public static boolean openOurTitle(Opening opening, boolean parentIsTitle) {
        return switch (opening) {
            case VANILLA_TITLE -> true;
            case MULTIPLAYER_LIST -> parentIsTitle;
            case OTHER -> false;
        };
    }
}
