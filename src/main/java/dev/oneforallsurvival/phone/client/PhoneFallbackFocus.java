package dev.oneforallsurvival.phone.client;

import java.util.List;
import javax.annotation.Nullable;

/**
 * Le focus de l'écran de repli, sans Minecraft. L'écran se reconstruit (appel qui arrive, page qui change) en retirant
 * tous ses boutons ; le clic qui a déclenché la reconstruction remet ensuite le focus sur le bouton cliqué, déjà retiré.
 * Espace ou Entrée l'actionneraient alors sous le nouvel écran (« Changer le code » sans ses champs : crash). Seul un
 * focus encore présent parmi les enfants de l'écran est gardé.
 */
public final class PhoneFallbackFocus {

    private PhoneFallbackFocus() {}

    /** Le focus à garder : {@code focused} s'il est encore un enfant de l'écran, sinon aucun. */
    @Nullable
    public static <T> T kept(@Nullable T focused, List<?> children) {
        return focused != null && children.contains(focused) ? focused : null;
    }
}
