package dev.oneforallsurvival.grid;

import net.minecraft.server.level.ServerPlayer;

/**
 * Un block entity qui porte le nom d'un abonné : le compteur, le disjoncteur. L'écran et le paquet d'inscription
 * ({@link SubscriberNamePacket}) le visent par sa position, sans connaître le bloc.
 */
public interface SubscriberHolder {

    /** Le nom de l'abonné tel qu'inscrit, vide si personne n'est inscrit. */
    String subscriber();

    /** Inscrit l'abonné ; le nom a déjà été validé ({@link MeterLedger#isValidName}). */
    void setSubscriber(String name);

    /**
     * L'inscription par l'ingénieur {@code engineer} ({@link SubscriberNamePacket}) : par défaut, {@link #setSubscriber}.
     * Le compteur y ajoute la trace au registre, pour que la facture parte de l'inscription, et remet à l'ingénieur le
     * relevé de clôture de l'ancien abonné.
     */
    default void inscribe(String name, ServerPlayer engineer) {
        setSubscriber(name);
    }
}
