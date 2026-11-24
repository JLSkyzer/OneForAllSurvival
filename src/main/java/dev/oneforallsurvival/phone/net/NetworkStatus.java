package dev.oneforallsurvival.phone.net;

/**
 * Ce que le téléphone montre du réseau, relevé par le serveur ({@link PhoneNetwork#status}).
 *
 * @param sim ce que vaut la carte logée
 * @param number le numéro de la ligne (sans espaces), vide sans ligne
 * @param holderName le titulaire de la carte, vide sans ligne ou pour une carte sans nom
 * @param operatorName l'opérateur de la carte, vide sans ligne
 * @param bars les barres de signal, 0 à 4
 * @param airplane le mode avion est-il mis ?
 */
public record NetworkStatus(PhoneSims.Validity sim, String number, String holderName, String operatorName, int bars,
        boolean airplane) {}
