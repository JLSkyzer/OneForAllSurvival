package dev.oneforallsurvival.economy.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.netty.buffer.Unpooled;
import java.time.LocalDate;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

/**
 * Ce que le paquet d'état porte des quêtes d'événement : le titre libre de l'équipe ressort tel quel (jamais interprété
 * comme une clé de langue ni un format), coupé à 64 caractères ; une référence d'événement ou d'attente d'événement
 * (origine, début) fait l'aller-retour du réseau à l'identique.
 */
class QuestSyncPacketTest {

    /** 70 caractères : un format, un code de couleur et une clé de langue livrée. */
    private static final String TITRE = "%s §cFête quest.oneforallsurvival.template.fish %1$s et 100 % de vivres !!!!";

    private static Quest evenement(String titre) {
        return new Quest("fete", QuestCategory.BREAK, null, 10, 30L, 0, false, titre);
    }

    @Test
    void unTitreDEvenementRessortLitteralEtCoupeA64() {
        Component titre = QuestSyncPacket.title(evenement(TITRE), true);
        assertInstanceOf(LiteralContents.class, titre.getContents(), "titre d'événement traduit");
        assertEquals(TITRE.substring(0, QuestSyncPacket.MAX_TITLE), titre.getString());
        assertEquals(64, titre.getString().length());
        // Un titre court, fait d'une seule clé de langue livrée, reste aussi le texte de l'équipe.
        Component cle = QuestSyncPacket.title(evenement("quest.oneforallsurvival.template.fish"), true);
        assertInstanceOf(LiteralContents.class, cle.getContents());
        assertEquals("quest.oneforallsurvival.template.fish", cle.getString());
        // Un modèle, lui, est une clé de langue avec sa quantité.
        Component modele = QuestSyncPacket.title(new Quest("fish", QuestCategory.FISH, null, 6, 15L, 0, false,
                "quest.oneforallsurvival.template.fish"), false);
        assertInstanceOf(TranslatableContents.class, modele.getContents());
    }

    private static QuestRef allerRetour(QuestRef ref) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            QuestActionPacket.writeRef(buf, ref);
            return QuestActionPacket.readRef(buf);
        } finally {
            buf.release();
        }
    }

    @Test
    void uneReferenceDEvenementFaitLAllerRetour() {
        QuestRef evenement = QuestRef.event("fete_du_port", 1_763_467_200_123L);
        assertEquals(evenement, allerRetour(evenement));
        QuestRef attente = new QuestRef(QuestRef.Kind.PENDING, 3, "fete_du_port", LocalDate.of(2026, 11, 18),
                QuestRef.Kind.EVENT, 1_763_467_200_123L);
        assertEquals(attente, allerRetour(attente));
        QuestRef hebdo = new QuestRef(QuestRef.Kind.PENDING, 0, "mine_iron", LocalDate.of(2026, 11, 16),
                QuestRef.Kind.WEEKLY, 0L);
        assertEquals(hebdo, allerRetour(hebdo));
        QuestRef journaliere = new QuestRef(QuestRef.Kind.DAILY, 2, "fish", LocalDate.of(2026, 11, 18));
        assertEquals(journaliere, allerRetour(journaliere));
    }

    @Test
    void uneReferenceMalFormeeNeDesigneRien() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeByte(9);
            buf.writeVarInt(0);
            buf.writeUtf("fete");
            buf.writeLong(Long.MIN_VALUE);
            buf.writeByte(-1);
            buf.writeLong(0L);
            assertNull(QuestActionPacket.readRef(buf), "genre inconnu accepté");
        } finally {
            buf.release();
        }
    }
}
