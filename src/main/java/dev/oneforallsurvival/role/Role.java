package dev.oneforallsurvival.role;

import net.minecraft.ChatFormatting;

/** Un rôle : identifiant stable, libellé affiché, couleur du nom, et drapeau « codé en dur ». */
public record Role(String id, String label, ChatFormatting color, boolean builtin) {
}
