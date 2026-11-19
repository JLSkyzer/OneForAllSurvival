package dev.oneforallsurvival.phone;

import dev.oneforallsurvival.machine.ItemBattery;
import dev.oneforallsurvival.phone.PhoneStatePacket.Lock;
import java.util.UUID;
import javax.annotation.Nullable;
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
    /** Le sel du code qui a déverrouillé cette session (ou qu'elle a posé) : un autre sel, et elle n'est plus ouverte. */
    @Nullable private String unlockedSalt;
    /** Le pourcentage de batterie que le client connaît (dernier état envoyé) : un autre déclenche un {@code UPDATE}. */
    private int shownPercent;

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
        if (lock != Lock.UNLOCKED) {
            unlockedSalt = null;
        }
    }

    /** Déverrouille la session par le code dont {@code salt} est le sel. */
    void unlock(String salt) {
        this.lock = Lock.UNLOCKED;
        this.unlockedSalt = salt;
    }

    @Nullable String unlockedSalt() {
        return unlockedSalt;
    }

    int shownPercent() {
        return shownPercent;
    }

    void showPercent(int percent) {
        this.shownPercent = percent;
    }

    /** Les tics d'écran déjà payés (dépense par blocs, tâche 6). */
    public ItemBattery.Prepaid prepaid() {
        return prepaid;
    }
}
