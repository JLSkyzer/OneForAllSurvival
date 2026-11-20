package dev.oneforallsurvival.phone.net;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;

/**
 * La demande d'une carte SIM faite par un joueur auprès d'un opérateur, en attente de la réponse de son fondateur.
 *
 * @param id identifiant de la demande
 * @param operatorId l'opérateur sollicité
 * @param player le joueur qui demande
 * @param playerName son nom, tel qu'il était à la demande
 * @param at instant de la demande ({@code EconomyClock.now()})
 */
public record SimRequest(UUID id, String operatorId, UUID player, String playerName, long at) {

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("operator", operatorId);
        tag.putUUID("player", player);
        tag.putString("playerName", playerName);
        tag.putLong("at", at);
        return tag;
    }

    /** Relit une demande ; nulle si la ligne est inutilisable (sans identifiant, opérateur ou joueur). */
    @Nullable
    static SimRequest load(CompoundTag tag) {
        String operator = tag.getString("operator");
        if (!tag.hasUUID("id") || operator.isEmpty() || !tag.hasUUID("player")) {
            return null;
        }
        return new SimRequest(tag.getUUID("id"), operator, tag.getUUID("player"), tag.getString("playerName"),
                Math.max(0L, tag.getLong("at")));
    }
}
