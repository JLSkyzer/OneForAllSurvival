package dev.oneforallsurvival.grid;

/**
 * Un block entity qui porte le nom d'un abonné : le compteur, le disjoncteur. L'écran et le paquet d'inscription
 * ({@link SubscriberNamePacket}) le visent par sa position, sans connaître le bloc.
 */
public interface SubscriberHolder {

    /** Le nom de l'abonné tel qu'inscrit, vide si personne n'est inscrit. */
    String subscriber();

    /** Inscrit l'abonné ; le nom a déjà été validé ({@link MeterLedger#isValidName}). */
    void setSubscriber(String name);
}
