package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.phone.PhoneItem;
import dev.oneforallsurvival.phone.PhoneRegistry;
import dev.oneforallsurvival.phone.net.PhoneOperator;
import dev.oneforallsurvival.phone.net.PhoneOperators;
import dev.oneforallsurvival.phone.net.PhoneSims;
import dev.oneforallsurvival.phone.net.SimCard;
import dev.oneforallsurvival.phone.net.SimCardItem;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Retours d'essai du 27/11, section 3 : la carte SIM, seule ou logée dans un téléphone, reste reconnue après les gestes
 * de l'inventaire créatif. Le client ne reçoit jamais le jeton de la carte ({@code getShareTag}) et l'inventaire créatif
 * renvoie au serveur la pile qu'il connaît : le serveur rend le jeton qu'il a vu chez ce joueur, jamais celui d'un autre.
 *
 * <p>Chaque geste passe par le vrai paquet de l'inventaire créatif ({@link ServerboundSetCreativeModeSlotPacket}), écrit
 * puis relu comme sur le réseau, depuis la pile telle que le client l'a reçue. Les essais sont synchrones et rangent
 * dans un {@code finally}. Aucun message n'écrit de jeton.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class CreatifSimGameTests {

    private static final String LOT = "sim_creatif";
    private static final Vec3 JOUEUR = new Vec3(4.5, 0, 4.5);
    /** Les cases de la barre (0 à 8) sont les cases 36 à 44 du menu de l'inventaire ; le sac (9 à 35) garde son numéro. */
    private static final int BARRE_DANS_LE_MENU = 36;

    private CreatifSimGameTests() {}

    private static final class Essai implements AutoCloseable {
        private final GameTestHelper helper;
        private final List<JoueurDeTest> joueurs = new ArrayList<>();
        private final List<UUID> fondateurs = new ArrayList<>();

        Essai(GameTestHelper helper) {
            this.helper = helper;
        }

        MinecraftServer serveur() {
            return helper.getLevel().getServer();
        }

        JoueurDeTest createur(String nom) {
            JoueurDeTest j = JoueurDeTest.rejoindre(helper.getLevel(), nom, helper.absoluteVec(JOUEUR));
            joueurs.add(j);
            j.joueur().setGameMode(GameType.CREATIVE);
            helper.assertTrue(j.joueur().isCreative(), "le joueur d'essai doit être en créatif");
            return j;
        }

        PhoneOperator fonder(JoueurDeTest j, String nom) {
            fondateurs.add(j.uuid());
            return OperateurDEssai.fonder(helper, j, nom);
        }

        @Override
        public void close() {
            for (JoueurDeTest j : joueurs) {
                if (j.connecte()) {
                    j.quitter();
                }
            }
            for (UUID f : fondateurs) {
                OperateurDEssai.ranger(helper, f);
            }
            AABB boite = new AABB(helper.absolutePos(new net.minecraft.core.BlockPos(0, 0, 0)),
                    helper.absolutePos(new net.minecraft.core.BlockPos(8, 8, 8)));
            for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, boite)) {
                e.discard();
            }
        }
    }

    /** Ce que le client a reçu d'une pile : écrite puis relue comme dans un paquet du serveur. */
    private static ItemStack vueDuClient(ItemStack pile) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeItem(pile);
            return buf.readItem();
        } finally {
            buf.release();
        }
    }

    /** Le geste de l'inventaire créatif : la case {@code caseDuMenu} reçoit {@code pile} (-1 : jetée). */
    private static void creatif(JoueurDeTest j, int caseDuMenu, ItemStack pile) {
        j.envoyerVanille(new ServerboundSetCreativeModeSlotPacket(caseDuMenu, pile),
                ServerboundSetCreativeModeSlotPacket::new);
    }

    private static int barre(int i) {
        return BARRE_DANS_LE_MENU + i;
    }

    @Nullable
    private static UUID jetonDe(ItemStack carte) {
        return carte.hasTag() && carte.getTag().hasUUID(SimCardItem.TAG_TOKEN)
                ? carte.getTag().getUUID(SimCardItem.TAG_TOKEN) : null;
    }

    private static SimCard auRegistre(GameTestHelper helper, Essai essai, UUID simId) {
        Optional<SimCard> c = PhoneOperators.get(essai.serveur()).card(simId);
        helper.assertTrue(c.isPresent(), "carte absente du registre");
        return c.get();
    }

    /** Une carte émise au nom du joueur, rangée dans la case {@code ou} de sa barre ; rend son identifiant. */
    private static UUID carteEnBarre(GameTestHelper helper, Essai essai, JoueurDeTest j, PhoneOperator op, int ou) {
        UUID simId = SimCardItem.simId(OperateurDEssai.emettre(helper, op, j)).orElseThrow();
        ServerPlayer p = j.joueur();
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            if (SimCardItem.simId(p.getInventory().getItem(i)).filter(simId::equals).isPresent()) {
                ItemStack carte = p.getInventory().removeItemNoUpdate(i);
                p.getInventory().setItem(ou, carte);
                break;
            }
        }
        helper.assertTrue(SimCardItem.simId(p.getInventory().getItem(ou)).filter(simId::equals).isPresent(),
                "la carte doit être dans la case " + ou);
        helper.assertTrue(auRegistre(helper, essai, simId).token().equals(jetonDe(p.getInventory().getItem(ou))),
                "la carte émise porte le jeton du registre");
        return simId;
    }

    /** Un téléphone dans la case {@code ou} de la barre, la carte {@code simId} logée par une vraie insertion. */
    private static ItemStack telephoneAvecCarte(GameTestHelper helper, Essai essai, JoueurDeTest j, UUID simId, int ou) {
        ServerPlayer p = j.joueur();
        p.getInventory().setItem(ou, new ItemStack(PhoneRegistry.PHONE.get()));
        ItemStack phone = p.getInventory().getItem(ou);
        int carte = -1;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            if (SimCardItem.simId(p.getInventory().getItem(i)).filter(simId::equals).isPresent()) {
                carte = i;
            }
        }
        helper.assertTrue(PhoneSims.insert(p, phone, carte) == PhoneSims.Insert.DONE, "insertion de la carte");
        helper.assertTrue(PhoneSims.check(essai.serveur(), phone) == PhoneSims.Validity.ACTIVE, "téléphone ACTIVE");
        return phone;
    }

    @GameTest(template = "test/plancher", batch = LOT)
    public static void deplaceeDansUneAutreCaseLaCarteResteReconnue(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest f = essai.createur("crea_deplace");
            PhoneOperator op = essai.fonder(f, "Crea Deplace");
            ServerPlayer p = f.joueur();
            UUID simId = carteEnBarre(helper, essai, f, op, 2);
            ItemStack client = vueDuClient(p.getInventory().getItem(2));
            helper.assertTrue(jetonDe(client) == null, "témoin : le client n'a pas le jeton");

            // L'inventaire créatif vide la première case, puis pose la pile qu'il tient dans une autre.
            creatif(f, barre(2), ItemStack.EMPTY);
            creatif(f, barre(6), client);
            helper.assertTrue(p.getInventory().getItem(2).isEmpty(), "la première case est vide");
            ItemStack posee = p.getInventory().getItem(6);
            helper.assertTrue(SimCardItem.simId(posee).filter(simId::equals).isPresent(), "la carte est en case 6");
            helper.assertTrue(auRegistre(helper, essai, simId).token().equals(jetonDe(posee)),
                    "la carte posée ailleurs porte de nouveau le jeton du registre");

            // Reconnue : elle se glisse dans un téléphone et donne la ligne.
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PhoneRegistry.PHONE.get()));
            ItemStack phone = p.getItemInHand(InteractionHand.MAIN_HAND);
            helper.assertTrue(PhoneSims.insert(p, phone, 6) == PhoneSims.Insert.DONE, "la carte se glisse");
            helper.assertTrue(PhoneSims.check(essai.serveur(), phone) == PhoneSims.Validity.ACTIVE, "ligne ACTIVE");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT)
    public static void dansLeSacLaCarteResteReconnue(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest f = essai.createur("crea_sac");
            PhoneOperator op = essai.fonder(f, "Crea Sac");
            ServerPlayer p = f.joueur();
            UUID simId = carteEnBarre(helper, essai, f, op, 1);
            ItemStack client = vueDuClient(p.getInventory().getItem(1));
            creatif(f, barre(1), ItemStack.EMPTY);
            creatif(f, 20, client); // case 20 du sac (même numéro dans le menu)
            helper.assertTrue(auRegistre(helper, essai, simId).token().equals(jetonDe(p.getInventory().getItem(20))),
                    "la carte rangée dans le sac porte le jeton");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT)
    public static void jeteeLaCarteResteReconnueAuSol(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest f = essai.createur("crea_jette");
            PhoneOperator op = essai.fonder(f, "Crea Jette");
            ServerPlayer p = f.joueur();
            UUID simId = carteEnBarre(helper, essai, f, op, 3);
            ItemStack client = vueDuClient(p.getInventory().getItem(3));
            creatif(f, barre(3), ItemStack.EMPTY);
            creatif(f, -1, client);
            AABB autour = p.getBoundingBox().inflate(4);
            List<ItemEntity> au = helper.getLevel().getEntitiesOfClass(ItemEntity.class, autour,
                    e -> SimCardItem.simId(e.getItem()).filter(simId::equals).isPresent());
            helper.assertTrue(au.size() == 1, "une carte au sol, vu " + au.size());
            helper.assertTrue(auRegistre(helper, essai, simId).token().equals(jetonDe(au.get(0).getItem())),
                    "la carte jetée porte le jeton : ramassée, elle reste reconnue");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT)
    public static void leTelephoneDeplaceGardeSaCarte(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest f = essai.createur("crea_tel");
            PhoneOperator op = essai.fonder(f, "Crea Tel");
            ServerPlayer p = f.joueur();
            UUID simId = carteEnBarre(helper, essai, f, op, 4);
            ItemStack phone = telephoneAvecCarte(helper, essai, f, simId, 0);
            ItemStack client = vueDuClient(phone);
            helper.assertTrue(jetonDe(PhoneItem.sim(client)) == null, "témoin : la carte logée arrive sans jeton");

            creatif(f, barre(0), ItemStack.EMPTY);
            creatif(f, barre(5), client);
            ItemStack pose = p.getInventory().getItem(5);
            helper.assertTrue(pose.is(PhoneRegistry.PHONE.get()), "le téléphone est en case 5");
            helper.assertTrue(PhoneSims.check(essai.serveur(), pose) == PhoneSims.Validity.ACTIVE,
                    "le téléphone déplacé garde sa ligne, vu " + PhoneSims.check(essai.serveur(), pose));
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT)
    public static void laCaseRenvoyeeParLInventaireOuvertGardeLaCarte(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest f = essai.createur("crea_renvoi");
            PhoneOperator op = essai.fonder(f, "Crea Renvoi");
            ServerPlayer p = f.joueur();
            UUID simId = carteEnBarre(helper, essai, f, op, 4);
            ItemStack phone = telephoneAvecCarte(helper, essai, f, simId, 0);
            // Le serveur met le téléphone à jour (batterie) : l'inventaire créatif ouvert renvoie la case telle qu'il
            // l'a reçue, sans le jeton de la carte.
            PhoneItem.store(phone, 1234);
            creatif(f, barre(0), vueDuClient(phone));
            helper.assertTrue(PhoneSims.check(essai.serveur(), p.getInventory().getItem(0)) == PhoneSims.Validity.ACTIVE,
                    "la case renvoyée garde sa ligne");
            // Et la carte seule, renvoyée sur sa propre case.
            UUID autre = carteEnBarre(helper, essai, f, op, 7);
            creatif(f, barre(7), vueDuClient(p.getInventory().getItem(7)));
            helper.assertTrue(auRegistre(helper, essai, autre).token().equals(jetonDe(p.getInventory().getItem(7))),
                    "la carte renvoyée sur sa case porte le jeton");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT)
    public static void unJoueurQuiNAJamaisEuLaCarteNeRecoitAucunJeton(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest f = essai.createur("crea_proprio");
            JoueurDeTest g = essai.createur("crea_autre");
            PhoneOperator op = essai.fonder(f, "Crea Autre");
            ServerPlayer p = f.joueur();
            UUID simId = carteEnBarre(helper, essai, f, op, 2);
            ItemStack carte = vueDuClient(p.getInventory().getItem(2));
            UUID simTel = carteEnBarre(helper, essai, f, op, 4);
            ItemStack phone = vueDuClient(telephoneAvecCarte(helper, essai, f, simTel, 0));
            // Le propriétaire a vidé ses cases : le serveur a vu les jetons chez lui, pas chez l'autre.
            creatif(f, barre(2), ItemStack.EMPTY);
            creatif(f, barre(0), ItemStack.EMPTY);

            creatif(g, barre(1), carte);
            creatif(g, barre(3), phone);
            ServerPlayer q = g.joueur();
            helper.assertTrue(SimCardItem.simId(q.getInventory().getItem(1)).filter(simId::equals).isPresent(),
                    "l'autre a bien la copie de la carte");
            helper.assertTrue(jetonDe(q.getInventory().getItem(1)) == null, "aucun jeton pour la carte de l'autre");
            helper.assertTrue(jetonDe(PhoneItem.sim(q.getInventory().getItem(3))) == null,
                    "aucun jeton pour la carte logée de l'autre");
            helper.assertTrue(PhoneSims.check(essai.serveur(), q.getInventory().getItem(3)) == PhoneSims.Validity.UNKNOWN,
                    "son téléphone est « non reconnu »");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT)
    public static void laDeconnexionOublieLesJetonsVus(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest f = essai.createur("crea_depart");
            PhoneOperator op = essai.fonder(f, "Crea Depart");
            UUID simId = carteEnBarre(helper, essai, f, op, 2);
            ItemStack client = vueDuClient(f.joueur().getInventory().getItem(2));
            creatif(f, barre(2), ItemStack.EMPTY);
            f.quitter();

            JoueurDeTest revenu = essai.createur("crea_depart");
            creatif(revenu, barre(5), client);
            ItemStack posee = revenu.joueur().getInventory().getItem(5);
            helper.assertTrue(SimCardItem.simId(posee).filter(simId::equals).isPresent(), "la pile est posée");
            helper.assertTrue(jetonDe(posee) == null, "après la déconnexion, le jeton vu n'est plus rendu");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT)
    public static void uneCopieCreativeResteUneCopie(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest f = essai.createur("crea_copie");
            PhoneOperator op = essai.fonder(f, "Crea Copie");
            ServerPlayer p = f.joueur();
            UUID simId = carteEnBarre(helper, essai, f, op, 2);
            // Clic du milieu : une seconde pile de la même carte, posée sans vider la première.
            creatif(f, barre(6), vueDuClient(p.getInventory().getItem(2)));
            helper.assertTrue(SimCardItem.simId(p.getInventory().getItem(6)).filter(simId::equals).isPresent(),
                    "la copie est posée");
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PhoneRegistry.PHONE.get()));
            ItemStack phone = p.getItemInHand(InteractionHand.MAIN_HAND);
            helper.assertTrue(PhoneSims.insert(p, phone, 6) == PhoneSims.Insert.DONE, "une des deux se glisse");
            p.getInventory().setItem(10, new ItemStack(PhoneRegistry.PHONE.get()));
            helper.assertTrue(PhoneSims.insert(p, p.getInventory().getItem(10), 2) == PhoneSims.Insert.UNKNOWN_CARD,
                    "l'autre est refusée : une seule vaut");
            helper.succeed();
        }
    }
}
