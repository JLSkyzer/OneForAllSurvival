package dev.oneforallsurvival.dev.wiki;

/**
 * Un chapitre du wiki pour l'atelier de captures (développement seulement) : ses scènes, écrites dans une classe à lui
 * ({@link ScenesControle}, puis une classe par chapitre). L'identifiant est le dossier de sortie
 * ({@code run-wiki/captures/<id>/}) et le premier mot du filtre {@code -PwikiSeul=<chapitre>[:<scène>]}.
 */
public interface Chapitre {

    String id();

    /** Déclare les scènes du chapitre ({@link Atelier#scene}). Rien ne se joue ici : les pas tournent plus tard. */
    void scenes(Atelier atelier);
}
