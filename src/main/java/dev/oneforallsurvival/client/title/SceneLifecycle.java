package dev.oneforallsurvival.client.title;

/**
 * Vie de la scène animée entre l'overlay du chargement, l'écran titre et le jeu : logique pure, sans GL ni classe
 * Minecraft. {@link TitleScene} la tient et fait ce qu'elle décide (garder, libérer, recréer).
 *
 * <ul>
 *   <li>{@link State#LOADING_OVERLAY} : l'overlay du mod tient la scène (ou n'a pas encore paru) ;</li>
 *   <li>{@link State#TITLE} : la scène est au détenteur, prête, dessinée par l'écran titre ;</li>
 *   <li>{@link State#IN_WORLD} : pas de scène (libérée en entrant dans un monde, ou par l'overlay sans écran titre
 *       derrière lui) ; le prochain écran titre la recrée ;</li>
 *   <li>{@link State#RECREATING} : scène recréée dans le contexte du jeu, textures en cours de chargement ;</li>
 *   <li>{@link State#UNAVAILABLE} : pas de scène pour cette session (jar de service absent, repli du chantier 1,
 *       échec GL) ; l'écran titre dessine son fond de repli. Décidé une fois, jamais retenté.</li>
 * </ul>
 */
public final class SceneLifecycle {
    public enum State { LOADING_OVERLAY, TITLE, IN_WORLD, RECREATING, UNAVAILABLE }

    /** Ce que le détenteur doit faire quand l'écran titre demande la scène. */
    public enum Request {
        /** Pas de scène : fond de repli. */
        NONE,
        /** Rendre la scène tenue. */
        USE,
        /** En créer une dans le contexte du jeu (une seule tentative), sur l'aube tenue. */
        CREATE
    }

    private State state = State.LOADING_OVERLAY;
    private boolean overlayStarted;

    public State state() {
        return state;
    }

    /** Vrai si le détenteur a une scène à lui (prête ou en chargement). */
    public boolean holdsScene() {
        return state == State.TITLE || state == State.RECREATING;
    }

    /** L'overlay du mod est créé : la scène de la fenêtre anticipée existe, il la tient. */
    public void overlayStarted() {
        if (state == State.LOADING_OVERLAY) {
            overlayStarted = true;
        }
    }

    /** Fin de l'overlay avec un écran titre derrière lui : la scène passe au détenteur, sans être libérée. */
    public void handedOver() {
        if (state == State.LOADING_OVERLAY) {
            state = State.TITLE;
        }
    }

    /** Fin de l'overlay sans écran titre (ou pont pas arrêté) : il a libéré la scène, comme au chantier 1. */
    public void overlayReleased() {
        if (state == State.LOADING_OVERLAY) {
            state = State.IN_WORLD;
        }
    }

    /** L'overlay a échoué (passé à celui de Forge, ou image noire) : pas de scène pour cette session. */
    public void overlayFailed() {
        if (state == State.LOADING_OVERLAY) {
            state = State.UNAVAILABLE;
        }
    }

    /**
     * L'écran titre demande la scène (à chaque image). Sans overlay du mod, la première demande conclut une fois
     * pour toutes : pas de scène.
     */
    public Request acquire() {
        switch (state) {
            case LOADING_OVERLAY:
                if (!overlayStarted) {
                    // Aucun overlay du mod n'a paru avant l'écran titre : jar de service absent, ou fenêtre
                    // anticipée de Forge. Rien ne viendra.
                    state = State.UNAVAILABLE;
                }
                return Request.NONE;
            case TITLE:
            case RECREATING:
                return Request.USE;
            case IN_WORLD:
                state = State.RECREATING;
                return Request.CREATE;
            default:
                return Request.NONE;
        }
    }

    /** La création demandée par {@link Request#CREATE} a échoué : repli pour le reste de la session. */
    public void creationFailed() {
        if (state == State.RECREATING) {
            state = State.UNAVAILABLE;
        }
    }

    /** La scène recréée a ses textures (ciel, décor, effets, logo) : elle s'affiche. */
    public void sceneReady() {
        if (state == State.RECREATING) {
            state = State.TITLE;
        }
    }

    /** Un monde est rejoint. {@code true} si le détenteur doit libérer sa scène. */
    public boolean worldJoined() {
        if (holdsScene()) {
            state = State.IN_WORLD;
            return true;
        }
        return false;
    }

    /** Une image de la scène a échoué au titre. {@code true} si le détenteur doit libérer sa scène. */
    public boolean renderFailed() {
        boolean held = holdsScene();
        state = State.UNAVAILABLE;
        return held;
    }
}
