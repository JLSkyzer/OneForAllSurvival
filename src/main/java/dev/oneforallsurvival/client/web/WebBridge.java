package dev.oneforallsurvival.client.web;

import com.google.gson.JsonObject;

/**
 * Ce que la page envoie au jeu. Les messages sont déjà passés par {@link WebMessage#parse} et arrivent sur le fil du
 * client ({@code Minecraft.execute}) ; ils ne valent jamais plus qu'une demande.
 */
public interface WebBridge {

    void onMessage(JsonObject message);

    default void onFailure(WebFailure failure) {}
}
