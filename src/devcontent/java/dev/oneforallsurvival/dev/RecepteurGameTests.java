package dev.oneforallsurvival.dev;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.oneforallsurvival.explore.ExploreCommands;
import dev.oneforallsurvival.explore.ExploreConfig;
import dev.oneforallsurvival.explore.ExploreRegistry;
import dev.oneforallsurvival.explore.ReceiverBlock;
import dev.oneforallsurvival.explore.ReceiverBlockEntity;
import dev.oneforallsurvival.explore.ReceiverFrequencyPacket;
import dev.oneforallsurvival.explore.ReceiverFrequencyPacket.Result;
import dev.oneforallsurvival.explore.ReceiverIndex;
import dev.oneforallsurvival.explore.SignalMessage;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Le récepteur de signaux et {@code /signal send} : réception alimenté, message manqué sans courant (rien en file),
 * fréquence, zone, dimension, chunk non chargé, nombre de récepteurs annoncé, archive bornée et sauvegardée, bornes de la
 * fréquence côté serveur. L'écran ne se teste pas ici : seul le côté serveur l'est. Chaque test a sa fréquence et retire
 * ses récepteurs avant de rendre la main, car les tests d'un lot tournent ensemble.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class RecepteurGameTests {

    private static final String LOT_A = "recepteur";
    private static final String LOT_B = "recepteur2";
    private static final BlockPos A = new BlockPos(1, 1, 1);
    private static final BlockPos B = new BlockPos(3, 1, 1);
    private static final BlockPos C = new BlockPos(1, 1, 3);
    private static final BlockPos D = new BlockPos(3, 1, 3);
    private static final BlockPos JOUEUR = new BlockPos(5, 1, 5);

    private RecepteurGameTests() {}

    /** Source de commande qui garde les messages renvoyés. */
    private static final class Ecoute implements CommandSource {
        final List<Component> messages = new ArrayList<>();

        @Override
        public void sendSystemMessage(Component message) {
            messages.add(message);
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }
    }

    private static ReceiverBlockEntity poser(GameTestHelper helper, BlockPos pos, int frequence, int energie) {
        helper.setBlock(pos, ExploreRegistry.RECEIVER.get());
        helper.assertTrue(helper.getBlockEntity(pos) instanceof ReceiverBlockEntity, "un ReceiverBlockEntity était attendu");
        ReceiverBlockEntity be = (ReceiverBlockEntity) helper.getBlockEntity(pos);
        helper.assertTrue(be.setFrequency(frequence), "la fréquence " + frequence + " devait être acceptée");
        be.energy().setStored(energie);
        return be;
    }

    private static void retirer(GameTestHelper helper, BlockPos... positions) {
        for (BlockPos pos : positions) {
            helper.setBlock(pos, Blocks.AIR);
        }
    }

    private static CommandSourceStack source(GameTestHelper helper, ServerLevel level, Vec3 position, Ecoute ecoute) {
        return helper.getLevel().getServer().createCommandSourceStack().withLevel(level).withPosition(position)
                .withPermission(2).withSource(ecoute);
    }

    private static CommandSourceStack source(GameTestHelper helper) {
        return source(helper, helper.getLevel(), helper.absoluteVec(Vec3.atCenterOf(A)), new Ecoute());
    }

    private static BlockState etat(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockState(pos);
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void alimenteIlRecoit(GameTestHelper helper) {
        ReceiverBlockEntity be = poser(helper, A, 11, 1000);
        try {
            helper.assertTrue(be.listening(), "un récepteur chargé écoute");
            int n = ExploreCommands.send(source(helper), 11, 10, "Bonjour");
            helper.assertTrue(n == 1, "1 récepteur attendu, vu " + n);
            helper.assertTrue(be.archive().size() == 1 && be.archive().get(0).text().equals("Bonjour")
                    && be.archive().get(0).frequency() == 11, "archive : " + be.archive());
            helper.assertTrue(etat(helper, A).getValue(ReceiverBlock.UNREAD), "le voyant non lu devait s'allumer");
            be.markRead();
            helper.assertTrue(!etat(helper, A).getValue(ReceiverBlock.UNREAD), "ouvrir l'écran éteint le voyant");
            helper.assertTrue(be.archive().size() == 1, "marquer lu ne touche pas l'archive");
            helper.succeed();
        } finally {
            retirer(helper, A);
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void sansCourantIlManque(GameTestHelper helper) {
        ReceiverBlockEntity be = poser(helper, A, 12, 0);
        try {
            helper.assertTrue(!be.listening(), "sans courant il n'écoute pas");
            helper.assertTrue(ExploreCommands.send(source(helper), 12, 10, "Perdu") == 0, "aucun récepteur sans courant");
            be.energy().setStored(ExploreConfig.receiverEnergy() - 1);
            helper.assertTrue(ExploreCommands.send(source(helper), 12, 10, "Encore perdu") == 0,
                    "un tampon qui ne couvre pas un tick n'écoute pas");
            helper.assertTrue(be.archive().isEmpty(), "rien ne devait être archivé : " + be.archive());
            helper.assertTrue(!etat(helper, A).getValue(ReceiverBlock.UNREAD), "pas de voyant non lu sans message");
            be.energy().setStored(1000);
            helper.assertTrue(ExploreCommands.send(source(helper), 12, 10, "Reçu") == 1, "alimenté, il reçoit");
            helper.assertTrue(be.archive().size() == 1 && be.archive().get(0).text().equals("Reçu"),
                    "seul le message d'après le courant devait être là, rien en file : " + be.archive());
            helper.succeed();
        } finally {
            retirer(helper, A);
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void mauvaiseFrequenceRien(GameTestHelper helper) {
        ReceiverBlockEntity be = poser(helper, A, 13, 1000);
        try {
            helper.assertTrue(ExploreCommands.send(source(helper), 14, 10, "Autre") == 0, "mauvaise fréquence : personne");
            helper.assertTrue(be.archive().isEmpty(), "rien d'archivé : " + be.archive());
            helper.assertTrue(be.deliver(new SignalMessage(0, 14, "direct")) == false,
                    "deliver refuse une autre fréquence");
            helper.succeed();
        } finally {
            retirer(helper, A);
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void horsZoneRien(GameTestHelper helper) {
        ReceiverBlockEntity be = poser(helper, A, 15, 1000);
        try {
            Vec3 recepteur = helper.absoluteVec(Vec3.atCenterOf(A));
            Vec3 loin = recepteur.add(40, 0, 0);
            helper.assertTrue(ExploreCommands.send(source(helper, helper.getLevel(), loin, new Ecoute()), 15, 10, "x") == 0,
                    "à 40 blocs avec un rayon de 10 : personne");
            helper.assertTrue(be.archive().isEmpty(), "rien d'archivé hors zone");
            helper.assertTrue(ExploreCommands.send(source(helper, helper.getLevel(), loin, new Ecoute()), 15, 100, "x") == 1,
                    "à 40 blocs avec un rayon de 100 : reçu");
            // La hauteur ne compte pas : le rayon est horizontal.
            Vec3 haut = recepteur.add(0, 150, 0);
            helper.assertTrue(ExploreCommands.send(source(helper, helper.getLevel(), haut, new Ecoute()), 15, 5, "y") == 1,
                    "150 blocs plus haut, rayon 5 : reçu (rayon horizontal)");
            helper.succeed();
        } finally {
            retirer(helper, A);
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void autreDimensionRien(GameTestHelper helper) {
        ReceiverBlockEntity be = poser(helper, A, 16, 1000);
        try {
            ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
            helper.assertTrue(nether != null, "le Nether devait exister sur le serveur d'essai");
            Vec3 memeXZ = helper.absoluteVec(Vec3.atCenterOf(A));
            Ecoute ecoute = new Ecoute();
            int n = ExploreCommands.send(source(helper, nether, memeXZ, ecoute), 16, 100, "Depuis le Nether");
            helper.assertTrue(n == 0, "une commande du Nether n'atteint pas l'Overworld, vu " + n);
            helper.assertTrue(be.archive().isEmpty(), "rien d'archivé : " + be.archive());
            helper.assertTrue(ReceiverIndex.get(nether).within(helper.absolutePos(A), 100).isEmpty(),
                    "l'index du Nether ne connaît pas ce récepteur");
            helper.assertTrue(ReceiverIndex.get(helper.getLevel()).contains(helper.absolutePos(A)),
                    "l'index de l'Overworld le connaît");
            helper.succeed();
        } finally {
            retirer(helper, A);
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void chunkNonChargeJamaisCharge(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos source = helper.absolutePos(A);
        // Une entrée d'index à 20 000 blocs, dans un chunk que personne ne charge : la commande doit la sauter.
        BlockPos loin = new BlockPos(source.getX() + 20000, 70, source.getZ());
        ReceiverIndex index = ReceiverIndex.get(level);
        helper.assertTrue(!level.isLoaded(loin), "le chunk lointain ne devait pas être chargé au départ");
        index.add(loin);
        try {
            int n = ExploreCommands.send(source(helper), 17, 30000, "Dans le vide");
            helper.assertTrue(n == 0, "personne dans un chunk déchargé, vu " + n);
            helper.assertTrue(!level.isLoaded(loin), "la commande ne devait pas charger le chunk");
            helper.assertTrue(index.contains(loin), "l'entrée d'un chunk déchargé reste dans l'index");
            helper.succeed();
        } finally {
            index.remove(loin);
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void leTexteEstNettoye(GameTestHelper helper) {
        ReceiverBlockEntity be = poser(helper, A, 18, 1000);
        try {
            String grand = "x".repeat(300);
            ExploreCommands.send(source(helper), 18, 10, "§cRouge§r et §lgras\nligne");
            ExploreCommands.send(source(helper), 18, 10, grand);
            String propre = be.archive().get(1).text();
            helper.assertTrue(propre.equals("cRouger et lgras ligne"), "texte nettoyé : " + propre);
            helper.assertTrue(!propre.contains("§"), "aucun § ne doit rester");
            helper.assertTrue(be.archive().get(0).text().length() == SignalMessage.MAX_LENGTH,
                    "256 caractères au plus, vu " + be.archive().get(0).text().length());
            helper.succeed();
        } finally {
            retirer(helper, A);
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void laCommandeCompte(GameTestHelper helper) throws CommandSyntaxException {
        poser(helper, A, 21, 1000);
        poser(helper, B, 21, 1000);
        poser(helper, C, 21, 0); // sans courant
        poser(helper, D, 22, 1000); // autre fréquence
        try {
            Ecoute ecoute = new Ecoute();
            CommandSourceStack source = source(helper, helper.getLevel(), helper.absoluteVec(Vec3.atCenterOf(A)), ecoute);
            var serveur = helper.getLevel().getServer();
            int n = serveur.getCommands().getDispatcher().execute("signal send 21 20 Bonjour à tous", source);
            helper.assertTrue(n == 2, "2 récepteurs attendus, vu " + n);
            Component dernier = ecoute.messages.get(ecoute.messages.size() - 1);
            helper.assertTrue(dernier.getContents() instanceof TranslatableContents t
                            && t.getKey().equals("command.oneforallsurvival.signal.sent")
                            && t.getArgs().length == 1 && Integer.valueOf(2).equals(t.getArgs()[0]),
                    "la réponse devait annoncer 2 récepteurs : " + dernier);
            helper.assertTrue(((ReceiverBlockEntity) helper.getBlockEntity(A)).archive().get(0).text()
                    .equals("Bonjour à tous"), "le message complet devait être livré");
            // Personne : 0, sans erreur.
            int zero = serveur.getCommands().getDispatcher().execute("signal send 9000 20 Personne", source);
            helper.assertTrue(zero == 0, "0 récepteur sans erreur, vu " + zero);
            Component reponse = ecoute.messages.get(ecoute.messages.size() - 1);
            helper.assertTrue(reponse.getContents() instanceof TranslatableContents t2
                    && Integer.valueOf(0).equals(t2.getArgs()[0]), "la réponse devait annoncer 0 : " + reponse);
            // Niveau de permission 2 exigé, bornes de la fréquence et du rayon.
            CommandSourceStack simple = source.withPermission(0);
            boolean refuse = false;
            try {
                serveur.getCommands().getDispatcher().execute("signal send 21 20 Pirate", simple);
            } catch (CommandSyntaxException e) {
                refuse = true;
            }
            helper.assertTrue(refuse, "sans permission 2 la commande est refusée");
            for (String ligne : new String[] {"signal send 0 20 x", "signal send 10000 20 x", "signal send 21 0 x",
                    "signal send 21 30001 x"}) {
                boolean borne = false;
                try {
                    serveur.getCommands().getDispatcher().execute(ligne, source);
                } catch (CommandSyntaxException e) {
                    borne = true;
                }
                helper.assertTrue(borne, "hors bornes, refusé : " + ligne);
            }
            helper.succeed();
        } finally {
            retirer(helper, A, B, C, D);
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void archiveBorneeACinquante(GameTestHelper helper) {
        ReceiverBlockEntity be = poser(helper, A, 23, 1000);
        try {
            for (int i = 0; i < 60; i++) {
                helper.assertTrue(be.deliver(new SignalMessage(i, 23, "m" + i)), "livraison " + i);
            }
            List<SignalMessage> archive = be.archive();
            helper.assertTrue(archive.size() == ReceiverBlockEntity.MAX_ARCHIVE, "50 au plus, vu " + archive.size());
            helper.assertTrue(archive.get(0).text().equals("m59"), "le plus récent en tête : " + archive.get(0));
            helper.assertTrue(archive.get(49).text().equals("m10"), "les 10 plus anciens sont tombés : " + archive.get(49));
            helper.succeed();
        } finally {
            retirer(helper, A);
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void archiveEtFrequenceSurviventAuRechargement(GameTestHelper helper) {
        ReceiverBlockEntity be = poser(helper, A, 777, 1500);
        try {
            be.deliver(new SignalMessage(100, 777, "premier"));
            be.deliver(new SignalMessage(200, 777, "second"));
            CompoundTag tag = be.saveWithoutMetadata();
            BlockPos pos = helper.absolutePos(A);
            ReceiverBlockEntity relu = new ReceiverBlockEntity(pos, ExploreRegistry.RECEIVER.get().defaultBlockState());
            relu.load(tag);
            helper.assertTrue(relu.frequency() == 777, "fréquence relue : " + relu.frequency());
            helper.assertTrue(relu.archive().equals(be.archive()) && relu.archive().size() == 2
                    && relu.archive().get(0).text().equals("second") && relu.archive().get(0).gameTime() == 200,
                    "archive relue : " + relu.archive());
            helper.assertTrue(relu.energy().getEnergyStored() == be.energy().getEnergyStored(),
                    "la réserve se relit aussi");
            // Une fréquence abîmée dans le fichier retombe sur le défaut au lieu de sortir des bornes.
            tag.putInt("Frequency", 123456);
            ReceiverBlockEntity abime = new ReceiverBlockEntity(pos, ExploreRegistry.RECEIVER.get().defaultBlockState());
            abime.load(tag);
            helper.assertTrue(abime.frequency() == ReceiverBlockEntity.MIN_FREQUENCY,
                    "fréquence hors bornes ramenée à 1, vu " + abime.frequency());
            helper.succeed();
        } finally {
            retirer(helper, A);
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void frequenceHorsBornesRefusee(GameTestHelper helper) {
        ReceiverBlockEntity be = poser(helper, A, 31, 1000);
        JoueurDeTest j = null;
        try {
            for (int mauvaise : new int[] {0, -5, 10000, Integer.MAX_VALUE, Integer.MIN_VALUE}) {
                helper.assertTrue(!be.setFrequency(mauvaise), "fréquence refusée : " + mauvaise);
            }
            helper.assertTrue(be.frequency() == 31, "la fréquence ne devait pas bouger : " + be.frequency());
            helper.assertTrue(be.setFrequency(1) && be.setFrequency(9999) && be.frequency() == 9999, "bornes incluses");
            helper.assertTrue(be.setFrequency(31), "retour à 31");

            Vec3 pres = helper.absoluteVec(Vec3.atBottomCenterOf(JOUEUR));
            j = JoueurDeTest.rejoindre(helper.getLevel(), "Radio31", pres);
            // Le nom d'un joueur d'essai déjà passé le replace à sa dernière position.
            j.joueur().moveTo(pres);
            BlockPos pos = helper.absolutePos(A);
            helper.assertTrue(ReceiverFrequencyPacket.apply(j.joueur(), new ReceiverFrequencyPacket(pos, 4321)) == Result.SET
                    && be.frequency() == 4321, "un joueur à portée règle la fréquence");
            Result trop = ReceiverFrequencyPacket.apply(j.joueur(), new ReceiverFrequencyPacket(pos, 0));
            helper.assertTrue(trop == Result.OUT_OF_RANGE && be.frequency() == 4321, "0 refusé : " + trop);
            trop = ReceiverFrequencyPacket.apply(j.joueur(), new ReceiverFrequencyPacket(pos, 10000));
            helper.assertTrue(trop == Result.OUT_OF_RANGE && be.frequency() == 4321, "10000 refusé : " + trop);
            helper.assertTrue(ReceiverFrequencyPacket.apply(j.joueur(),
                    new ReceiverFrequencyPacket(helper.absolutePos(new BlockPos(6, 1, 6)), 5)) == Result.NOT_A_RECEIVER,
                    "pas un récepteur");
            Vec3 loin = pres.add(30, 0, 0);
            j.joueur().moveTo(loin);
            Result distance = ReceiverFrequencyPacket.apply(j.joueur(), new ReceiverFrequencyPacket(pos, 77));
            helper.assertTrue(distance == Result.TOO_FAR && be.frequency() == 4321, "trop loin : " + distance);
            helper.succeed();
        } finally {
            if (j != null && j.connecte()) {
                j.quitter();
            }
            retirer(helper, A);
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void lePaquetPasseParLeCanal(GameTestHelper helper) {
        ReceiverBlockEntity be = poser(helper, A, 41, 1000);
        Vec3 pres = helper.absoluteVec(Vec3.atBottomCenterOf(JOUEUR));
        JoueurDeTest j = JoueurDeTest.rejoindre(helper.getLevel(), "Radio41", pres);
        j.joueur().moveTo(pres);
        BlockPos pos = helper.absolutePos(A);
        try {
            j.envoyer(new ReceiverFrequencyPacket(pos, 555));
        } catch (RuntimeException e) {
            j.quitter();
            retirer(helper, A);
            throw e;
        }
        helper.runAfterDelay(3, () -> {
            try {
                helper.assertTrue(be.frequency() == 555, "le paquet devait régler 555, vu " + be.frequency());
                j.envoyer(new ReceiverFrequencyPacket(pos, 99999));
            } catch (RuntimeException e) {
                j.quitter();
                retirer(helper, A);
                throw e;
            }
        });
        helper.runAfterDelay(6, () -> {
            try {
                helper.assertTrue(be.frequency() == 555, "99999 devait être refusé, vu " + be.frequency());
                helper.assertTrue(j.messagesRecus().contains(ReceiverFrequencyPacket.MESSAGE + "out_of_range"),
                        "le joueur devait être prévenu");
                helper.succeed();
            } finally {
                j.quitter();
                retirer(helper, A);
            }
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void lEcouteConsommeEtLeVoyantSuit(GameTestHelper helper) {
        int cout = ExploreConfig.receiverEnergy();
        ReceiverBlockEntity be = poser(helper, A, 51, 3 * cout);
        helper.runAfterDelay(10, () -> {
            try {
                helper.assertTrue(be.energy().getEnergyStored() == 0,
                        "la réserve de 3 ticks devait être vide, vu " + be.energy().getEnergyStored());
                helper.assertTrue(!etat(helper, A).getValue(ReceiverBlock.LISTENING), "sans réserve le voyant s'éteint");
                be.energy().setStored(100 * cout);
            } catch (RuntimeException e) {
                retirer(helper, A);
                throw e;
            }
        });
        helper.runAfterDelay(16, () -> {
            try {
                int reste = be.energy().getEnergyStored();
                helper.assertTrue(etat(helper, A).getValue(ReceiverBlock.LISTENING), "rechargé, le voyant s'allume");
                helper.assertTrue(reste < 100 * cout && (100 * cout - reste) % cout == 0,
                        "la consommation est de " + cout + " FE par tick, réserve " + reste);
                helper.assertTrue(be.energy().extractEnergy(1000, false) == 0, "rien ne ressort de la réserve");
                helper.succeed();
            } finally {
                retirer(helper, A);
            }
        });
    }
}
