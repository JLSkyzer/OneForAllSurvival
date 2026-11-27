package dev.oneforallsurvival.dev.wiki;

import java.util.ArrayList;
import java.util.List;

/**
 * Ce qu'un chapitre reçoit pour déclarer ses scènes : {@code atelier.scene("antenne")} rend une scène vide, dont le
 * premier pas (implicite) prépare un terrain neuf ; la classe du chapitre y enchaîne préparation, caméra et prises.
 */
public final class Atelier {

    private final List<Scene> scenes = new ArrayList<>();
    private String chapitre = "";

    Atelier() {}

    void chapitre(String id) {
        chapitre = id;
    }

    /** Une nouvelle scène du chapitre en cours ; son nom sert au filtre {@code chapitre:scène} et aux prises. */
    public Scene scene(String nom) {
        for (Scene s : scenes) {
            if (s.chapitre().equals(chapitre) && s.nom().equals(nom)) {
                throw new IllegalArgumentException("scène en double : " + chapitre + ":" + nom);
            }
        }
        Scene s = new Scene(chapitre, nom);
        scenes.add(s);
        return s;
    }

    List<Scene> scenes() {
        return scenes;
    }
}
