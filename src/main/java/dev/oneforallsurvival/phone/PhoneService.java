package dev.oneforallsurvival.phone;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

/** Le serveur du téléphone : seul juge du code, de la batterie et du propriétaire. Amorce ; la tâche 5 la remplit. */
public final class PhoneService {

    public enum Outcome { OPENED, EMPTY_BATTERY, DONE, REFUSED, NO_SESSION }

    private PhoneService() {}

    /** Ouvre le téléphone tenu en main. Amorce : ne fait rien et rend {@link Outcome#NO_SESSION}. */
    public static Outcome open(ServerPlayer player, InteractionHand hand) {
        return Outcome.NO_SESSION;
    }
}
