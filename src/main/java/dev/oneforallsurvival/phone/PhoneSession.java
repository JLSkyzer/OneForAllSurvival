package dev.oneforallsurvival.phone;

import dev.oneforallsurvival.machine.ItemBattery;
import dev.oneforallsurvival.phone.PhoneStatePacket.Lock;
import java.util.UUID;
import net.minecraft.world.InteractionHand;

/**
 * Un téléphone ouvert par un joueur, côté serveur seulement (jamais sauvegardé : un redémarrage ou une déconnexion
 * ferme tout). Le numéro {@link #id} grandit à chaque ouverture : un geste qui porte un autre numéro est périmé.
 * L'état du verrou de la session ({@link #lock}) est celui que ce joueur a atteint ; ce que le téléphone sait (code,
 * blocage, réglages) vit dans {@link PhoneData}.
 */
public final class PhoneSession {

    private final int id;
    private final UUID phoneId;
    private final InteractionHand hand;
    private final ItemBattery.Prepaid prepaid = new ItemBattery.Prepaid();
    private Lock lock;

    PhoneSession(int id, UUID phoneId, InteractionHand hand, Lock lock) {
        this.id = id;
        this.phoneId = phoneId;
        this.hand = hand;
        this.lock = lock;
    }

    public int id() {
        return id;
    }

    public UUID phoneId() {
        return phoneId;
    }

    public InteractionHand hand() {
        return hand;
    }

    public Lock lock() {
        return lock;
    }

    void setLock(Lock lock) {
        this.lock = lock;
    }

    /** Les tics d'écran déjà payés (dépense par blocs, tâche 6). */
    public ItemBattery.Prepaid prepaid() {
        return prepaid;
    }
}
