package dev.oneforallsurvival.dev;

import com.mojang.authlib.GameProfile;
import dev.oneforallsurvival.bench.BenchCatalog;
import dev.oneforallsurvival.bench.BenchRecipe;
import dev.oneforallsurvival.economy.EconomyClock;
import dev.oneforallsurvival.phone.PhoneItem;
import dev.oneforallsurvival.phone.PhoneRegistry;
import dev.oneforallsurvival.phone.net.PhoneOperator;
import dev.oneforallsurvival.phone.net.PhoneOperators;
import dev.oneforallsurvival.phone.net.PhoneSims;
import dev.oneforallsurvival.phone.net.SimCard;
import dev.oneforallsurvival.phone.net.SimCardItem;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * La carte SIM (lot T2, tâche 5) : émise par le fondateur seul, jamais fabriquée ; glissée dans un téléphone, elle lui
 * donne sa ligne, et son numéro la suit d'un téléphone à l'autre ; un jeton qui tourne à chaque insertion fait d'une
 * copie une carte morte.
 *
 * <p>Tous les essais sont synchrones : ils finissent dans le tic où ils commencent, et rangent dans un {@code finally}.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class CarteSimGameTests {

    private static final String LOT_A = "sim_a";
    private static final String LOT_COPIE = "sim_copie";

    private static final Vec3 JOUEUR = new Vec3(4.5, 0, 4.5);

    private CarteSimGameTests() {}

    // ---------------------------------------------------------------------------------------------
    // Aides
    // ---------------------------------------------------------------------------------------------

    /** Ce qu'un essai a ouvert : joueurs et opérateurs, rendus à la fermeture. */
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

        PhoneOperators registre() {
            return PhoneOperators.get(serveur());
        }

        JoueurDeTest joueur(String nom) {
            JoueurDeTest j = JoueurDeTest.rejoindre(helper.getLevel(), nom, helper.absoluteVec(JOUEUR));
            joueurs.add(j);
            helper.assertTrue(!j.joueur().hasPermissions(2), "le joueur d'essai ne doit pas être de l'équipe");
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
        }
    }

    private static ItemStack telephone() {
        return new ItemStack(PhoneRegistry.PHONE.get());
    }

    /** Le téléphone en main du joueur, tel qu'il est dans son inventaire (la même pile). */
    private static ItemStack enMain(ServerPlayer p, ItemStack phone) {
        p.setItemInHand(InteractionHand.MAIN_HAND, phone);
        return p.getItemInHand(InteractionHand.MAIN_HAND);
    }

    /** La case de l'inventaire qui porte la carte {@code simId}, ou -1. */
    private static int caseDe(ServerPlayer p, UUID simId) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (SimCardItem.simId(inv.getItem(i)).filter(simId::equals).isPresent()) {
                return i;
            }
        }
        return -1;
    }

    private static UUID idDe(GameTestHelper helper, ItemStack carte) {
        Optional<UUID> id = SimCardItem.simId(carte);
        helper.assertTrue(id.isPresent(), "la pile n'est pas une carte : " + carte);
        return id.get();
    }

    private static SimCard carteAuRegistre(GameTestHelper helper, Essai essai, UUID simId) {
        Optional<SimCard> carte = essai.registre().card(simId);
        helper.assertTrue(carte.isPresent(), "carte absente du registre : " + simId);
        return carte.get();
    }

    @Nullable
    private static UUID jetonDe(ItemStack carte) {
        return carte.hasTag() && carte.getTag().hasUUID(SimCardItem.TAG_TOKEN)
                ? carte.getTag().getUUID(SimCardItem.TAG_TOKEN) : null;
    }

    /** Remplit les 36 cases du sac et de la barre (de terre), sauf {@code garder}. */
    private static void remplir(ServerPlayer p, int... garder) {
        Inventory inv = p.getInventory();
        outer:
        for (int i = 0; i < inv.items.size(); i++) {
            for (int g : garder) {
                if (g == i) {
                    continue outer;
                }
            }
            if (inv.items.get(i).isEmpty()) {
                inv.items.set(i, new ItemStack(Items.DIRT, 64));
            }
        }
    }

    /** Pose la carte dans la case {@code slot} du joueur (la case doit être vide). */
    private static void poserEnCase(GameTestHelper helper, ServerPlayer p, ItemStack carte, int slot) {
        int ailleurs = caseDe(p, idDe(helper, carte));
        if (ailleurs >= 0) {
            p.getInventory().setItem(ailleurs, ItemStack.EMPTY);
        }
        helper.assertTrue(p.getInventory().getItem(slot).isEmpty(), "la case " + slot + " devait être vide");
        p.getInventory().setItem(slot, carte);
    }

    private static boolean dit(List<Component> lignes, String cle) {
        for (Component c : lignes) {
            if (c.getContents() instanceof TranslatableContents t && t.getKey().equals(cle)) {
                return true;
            }
        }
        return false;
    }

    private static List<Component> infobulle(ItemStack pile) {
        List<Component> lignes = new ArrayList<>();
        pile.getItem().appendHoverText(pile, null, lignes, TooltipFlag.NORMAL);
        return lignes;
    }

    // ---------------------------------------------------------------------------------------------
    // Lot A : émission, insertion, retrait
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void aucuneRecetteNeDonneDeCarte(GameTestHelper helper) {
        ItemStack carte = new ItemStack(PhoneRegistry.SIM_CARD.get());
        helper.assertTrue(carte.getMaxStackSize() == 1, "une carte par case");
        RegistryAccess acces = helper.getLevel().registryAccess();
        for (Recipe<?> r : helper.getLevel().getRecipeManager().getRecipes()) {
            helper.assertTrue(!r.getResultItem(acces).is(PhoneRegistry.SIM_CARD.get()),
                    "la recette " + r.getId() + " donne une carte SIM");
        }
        for (BenchRecipe r : BenchCatalog.current().recipes()) {
            helper.assertTrue(!r.result().is(PhoneRegistry.SIM_CARD.get()),
                    "la recette d'établi " + r.id() + " donne une carte SIM");
        }
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void uneCarteEmiseArriveChezLeFondateur(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest fondateur = essai.joueur("sim_emet_f");
            JoueurDeTest demandeur = essai.joueur("sim_emet_d");
            PhoneOperator op = essai.fonder(fondateur, "Sim Emission");
            PhoneOperators registre = essai.registre();
            long now = EconomyClock.now();

            // La demande, faite depuis les Réglages (tâche 7) : une par joueur.
            helper.assertTrue(registre.request(op.id(), demandeur.uuid(), "sim_emet_d", now).isPresent(), "demande");
            helper.assertTrue(registre.request(op.id(), demandeur.uuid(), "sim_emet_d", now).isEmpty(),
                    "une seule demande par joueur");

            ItemStack emise = OperateurDEssai.emettre(helper, op, demandeur);
            UUID simId = idDe(helper, emise);
            int ou = caseDe(fondateur.joueur(), simId);
            helper.assertTrue(ou >= 0, "la carte est dans l'inventaire du fondateur");
            helper.assertTrue(caseDe(demandeur.joueur(), simId) < 0, "pas chez le demandeur");
            SimCard carte = carteAuRegistre(helper, essai, simId);
            helper.assertTrue(carte.operatorId().equals(op.id()) && demandeur.uuid().equals(carte.holder())
                    && carte.holderName().equals("sim_emet_d") && carte.state() == SimCard.State.ACTIVE
                    && carte.phoneId() == null, "carte au nom du demandeur : " + carte);
            ItemStack chezLui = fondateur.joueur().getInventory().getItem(ou);
            helper.assertTrue(carte.number().equals(chezLui.getTag().getString(SimCardItem.TAG_NUMBER))
                    && op.name().equals(chezLui.getTag().getString(SimCardItem.TAG_OPERATOR))
                    && "sim_emet_d".equals(chezLui.getTag().getString(SimCardItem.TAG_HOLDER)),
                    "copies d'affichage de la pile");
            helper.assertTrue(carte.token().equals(jetonDe(chezLui)), "la pile porte le jeton du registre");
            List<Component> bulle = infobulle(chezLui);
            helper.assertTrue(dit(bulle, "tooltip.oneforallsurvival.sim_card.number")
                    && dit(bulle, "tooltip.oneforallsurvival.sim_card.operator")
                    && dit(bulle, "tooltip.oneforallsurvival.sim_card.holder"), "infobulle : " + bulle);
            int avant = registre.cardsOf(op.id()).size();

            // Personne d'autre que le fondateur n'émet : ni le demandeur, ni un faux joueur à l'UUID du fondateur.
            helper.assertTrue(PhoneSims.issueTo(demandeur.joueur(), op, demandeur.uuid(), "sim_emet_d", now).isEmpty(),
                    "le demandeur n'émet pas");
            FakePlayer faux = FakePlayerFactory.get(helper.getLevel(), new GameProfile(fondateur.uuid(), "[SimFaux]"));
            faux.getInventory().clearContent();
            helper.assertTrue(PhoneSims.issueTo(faux, op, demandeur.uuid(), "sim_emet_d", now).isEmpty(),
                    "un faux joueur n'émet jamais");
            helper.assertTrue(faux.getInventory().isEmpty(), "rien chez le faux joueur");
            // Un opérateur inconnu du registre : rien, et aucune exception jusqu'au joueur.
            PhoneOperator fantome = new PhoneOperator("simfantome00", "Fantome", fondateur.uuid(), "sim_emet_f", 0L,
                    null, new EnumMap<>(dev.oneforallsurvival.economy.Coin.class), null, 0L);
            helper.assertTrue(PhoneSims.issueTo(fondateur.joueur(), fantome, null, "", now).isEmpty(),
                    "un opérateur inconnu n'émet rien");
            helper.assertTrue(registre.cardsOf(op.id()).size() == avant, "rien n'a été émis");

            // Inventaire plein : rien d'émis, aucun numéro réservé (le registre n'a pas une carte de plus).
            remplir(fondateur.joueur());
            helper.assertTrue(fondateur.joueur().getInventory().getFreeSlot() < 0, "inventaire plein");
            helper.assertTrue(PhoneSims.issueTo(fondateur.joueur(), op, demandeur.uuid(), "sim_emet_d", now).isEmpty(),
                    "inventaire plein : rien d'émis");
            helper.assertTrue(registre.cardsOf(op.id()).size() == avant, "aucune carte de plus au registre");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void glisserLaCarteDonneLaLigne(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest f = essai.joueur("sim_glisse");
            PhoneOperator op = essai.fonder(f, "Sim Glisse");
            ServerPlayer p = f.joueur();
            ItemStack phone = enMain(p, telephone());
            UUID simId = idDe(helper, OperateurDEssai.emettre(helper, op, f));
            UUID jetonEmis = carteAuRegistre(helper, essai, simId).token();
            int slot = caseDe(p, simId);
            MinecraftServer srv = essai.serveur();

            // Ce qui n'est pas une carte, ou pas un téléphone du joueur, ne fait rien.
            p.getInventory().setItem(20, new ItemStack(Items.DIRT));
            helper.assertTrue(PhoneSims.insert(p, phone, 20) == PhoneSims.Insert.NOT_A_SIM, "terre : NOT_A_SIM");
            helper.assertTrue(PhoneSims.insert(p, phone, 99) == PhoneSims.Insert.NOT_A_SIM, "case hors inventaire");
            helper.assertTrue(PhoneSims.insert(p, new ItemStack(Items.DIRT), slot) == PhoneSims.Insert.NO_PHONE,
                    "pas un téléphone");
            helper.assertTrue(PhoneSims.insert(p, telephone(), slot) == PhoneSims.Insert.NO_PHONE,
                    "un téléphone qui n'est pas dans l'inventaire du joueur");
            helper.assertTrue(PhoneSims.check(srv, phone) == PhoneSims.Validity.NONE, "rien glissé encore");

            // Un faux joueur n'insère rien, même avec le téléphone et la carte dans son inventaire.
            FakePlayer faux = FakePlayerFactory.get(helper.getLevel(), new GameProfile(f.uuid(), "[SimFauxInsert]"));
            faux.getInventory().clearContent();
            ItemStack phoneFaux = telephone();
            faux.getInventory().setItem(0, phoneFaux);
            faux.getInventory().setItem(1, p.getInventory().getItem(slot).copy());
            helper.assertTrue(PhoneSims.insert(faux, faux.getInventory().getItem(0), 1) == PhoneSims.Insert.REFUSED,
                    "un faux joueur n'insère rien");
            helper.assertTrue(PhoneItem.sim(faux.getInventory().getItem(0)).isEmpty()
                    && carteAuRegistre(helper, essai, simId).token().equals(jetonEmis)
                    && carteAuRegistre(helper, essai, simId).phoneId() == null, "rien n'a bougé");
            faux.getInventory().clearContent();

            helper.assertTrue(PhoneSims.insert(p, phone, slot) == PhoneSims.Insert.DONE, "insertion");
            helper.assertTrue(p.getInventory().getItem(slot).isEmpty(), "la case est vide");
            ItemStack logee = PhoneItem.sim(phone);
            helper.assertTrue(SimCardItem.simId(logee).equals(Optional.of(simId)), "la carte est dans le téléphone");
            helper.assertTrue(PhoneSims.check(srv, phone) == PhoneSims.Validity.ACTIVE,
                    "ACTIVE attendu, vu " + PhoneSims.check(srv, phone));
            SimCard carte = carteAuRegistre(helper, essai, simId);
            Optional<SimCard> ligne = PhoneSims.line(srv, phone);
            helper.assertTrue(ligne.isPresent() && ligne.get().number().equals(carte.number()), "la ligne : " + ligne);
            helper.assertTrue(PhoneItem.phoneId(phone).isPresent() && PhoneItem.phoneId(phone).get().equals(carte.phoneId()),
                    "le registre note le téléphone");
            helper.assertTrue(!carte.token().equals(jetonEmis) && carte.token().equals(jetonDe(logee)),
                    "le jeton a tourné et la pile logée porte le nouveau");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void retirerLaCarteLaRendALInventaire(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest f = essai.joueur("sim_retire");
            PhoneOperator op = essai.fonder(f, "Sim Retire");
            ServerPlayer p = f.joueur();
            MinecraftServer srv = essai.serveur();
            ItemStack phone = enMain(p, telephone());
            UUID simId = idDe(helper, OperateurDEssai.emettre(helper, op, f));
            helper.assertTrue(PhoneSims.insert(p, phone, caseDe(p, simId)) == PhoneSims.Insert.DONE, "insertion");

            // Inventaire plein : refusé, la carte reste dans le téléphone et la ligne tient.
            remplir(p);
            helper.assertTrue(PhoneSims.remove(p, phone) == PhoneSims.Remove.INVENTORY_FULL, "inventaire plein");
            helper.assertTrue(SimCardItem.simId(PhoneItem.sim(phone)).equals(Optional.of(simId)), "la carte reste");
            helper.assertTrue(PhoneSims.check(srv, phone) == PhoneSims.Validity.ACTIVE, "toujours active");
            helper.assertTrue(caseDe(p, simId) < 0, "aucune copie dans l'inventaire");

            p.getInventory().setItem(30, ItemStack.EMPTY);
            helper.assertTrue(PhoneSims.remove(p, phone) == PhoneSims.Remove.DONE, "retrait");
            helper.assertTrue(caseDe(p, simId) == 30, "la carte est revenue dans l'inventaire");
            helper.assertTrue(PhoneItem.sim(phone).isEmpty() && PhoneSims.check(srv, phone) == PhoneSims.Validity.NONE,
                    "le téléphone n'a plus de carte");
            helper.assertTrue(carteAuRegistre(helper, essai, simId).phoneId() == null, "le registre la délie");
            helper.assertTrue(PhoneSims.remove(p, phone) == PhoneSims.Remove.NO_SIM, "plus rien à retirer");

            // La carte retirée garde son jeton : elle se reglisse.
            helper.assertTrue(PhoneSims.insert(p, phone, 30) == PhoneSims.Insert.DONE
                    && PhoneSims.check(srv, phone) == PhoneSims.Validity.ACTIVE, "reglissée");
            List<PhoneSims.SimChoice> choix = PhoneSims.choices(p);
            helper.assertTrue(choix.isEmpty(), "plus aucune carte dans l'inventaire : " + choix);
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void changerDeCarteRendLAncienneASaCase(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest f = essai.joueur("sim_change");
            PhoneOperator op = essai.fonder(f, "Sim Change");
            ServerPlayer p = f.joueur();
            MinecraftServer srv = essai.serveur();
            ItemStack phone = enMain(p, telephone());
            ItemStack x = OperateurDEssai.emettre(helper, op, f);
            ItemStack y = OperateurDEssai.emettre(helper, op, f);
            UUID idX = idDe(helper, x);
            UUID idY = idDe(helper, y);
            poserEnCase(helper, p, p.getInventory().getItem(caseDe(p, idX)), 3);
            poserEnCase(helper, p, p.getInventory().getItem(caseDe(p, idY)), 5);

            List<PhoneSims.SimChoice> choix = PhoneSims.choices(p);
            helper.assertTrue(choix.size() == 2 && choix.stream().allMatch(c -> c.validity() == PhoneSims.Validity.ACTIVE
                    && c.operator().equals(op.name())), "deux cartes au choix : " + choix);
            helper.assertTrue(choix.stream().anyMatch(c -> c.slot() == 3) && choix.stream().anyMatch(c -> c.slot() == 5),
                    "cases 3 et 5 : " + choix);

            helper.assertTrue(PhoneSims.insert(p, phone, 3) == PhoneSims.Insert.DONE, "X glissée");
            helper.assertTrue(PhoneSims.insert(p, phone, 5) == PhoneSims.Insert.DONE, "Y glissée à la place");
            helper.assertTrue(SimCardItem.simId(p.getInventory().getItem(5)).equals(Optional.of(idX)),
                    "X est revenue dans la case de Y");
            helper.assertTrue(SimCardItem.simId(PhoneItem.sim(phone)).equals(Optional.of(idY)), "Y est dans le téléphone");
            helper.assertTrue(carteAuRegistre(helper, essai, idX).phoneId() == null, "X déliée");
            helper.assertTrue(PhoneItem.phoneId(phone).get().equals(carteAuRegistre(helper, essai, idY).phoneId()),
                    "Y liée au téléphone");
            helper.assertTrue(PhoneSims.line(srv, phone).map(SimCard::simId).equals(Optional.of(idY)), "la ligne est Y");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void leNumeroSuitLaCarte(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest f = essai.joueur("sim_suit");
            PhoneOperator op = essai.fonder(f, "Sim Suit");
            ServerPlayer p = f.joueur();
            MinecraftServer srv = essai.serveur();
            ItemStack a = enMain(p, telephone());
            UUID simId = idDe(helper, OperateurDEssai.emettre(helper, op, f));
            String numero = carteAuRegistre(helper, essai, simId).number();
            p.getInventory().setItem(10, telephone());
            ItemStack b = p.getInventory().getItem(10);

            helper.assertTrue(PhoneSims.insert(p, a, caseDe(p, simId)) == PhoneSims.Insert.DONE, "dans A");
            helper.assertTrue(PhoneSims.line(srv, a).map(SimCard::number).equals(Optional.of(numero)), "A a le numéro");
            helper.assertTrue(PhoneSims.remove(p, a) == PhoneSims.Remove.DONE, "retirée de A");
            helper.assertTrue(PhoneSims.insert(p, b, caseDe(p, simId)) == PhoneSims.Insert.DONE, "dans B");

            SimCard carte = carteAuRegistre(helper, essai, simId);
            helper.assertTrue(PhoneSims.line(srv, b).map(SimCard::number).equals(Optional.of(numero)),
                    "B a le même numéro");
            helper.assertTrue(PhoneItem.phoneId(b).get().equals(carte.phoneId())
                    && !PhoneItem.phoneId(a).get().equals(carte.phoneId()), "le registre note B");
            helper.assertTrue(PhoneSims.check(srv, a) == PhoneSims.Validity.NONE && PhoneSims.line(srv, a).isEmpty(),
                    "A n'a plus de ligne");
            helper.assertTrue(dit(infobulle(a), "tooltip.oneforallsurvival.phone.no_sim"), "A : « Aucune carte SIM »");
            helper.assertTrue(dit(infobulle(b), "tooltip.oneforallsurvival.phone.sim"), "B montre sa carte");
            helper.assertTrue(numero.equals(PhoneItem.sim(b).getTag().getString(SimCardItem.TAG_NUMBER)),
                    "numéro sur la pile logée");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void uneCarteSuspendueResteSuspendue(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest f = essai.joueur("sim_suspend");
            PhoneOperator op = essai.fonder(f, "Sim Suspend");
            ServerPlayer p = f.joueur();
            MinecraftServer srv = essai.serveur();
            PhoneOperators registre = essai.registre();
            ItemStack phone = enMain(p, telephone());
            UUID simId = idDe(helper, OperateurDEssai.emettre(helper, op, f));
            helper.assertTrue(PhoneSims.insert(p, phone, caseDe(p, simId)) == PhoneSims.Insert.DONE, "insertion");

            helper.assertTrue(registre.setState(simId, SimCard.State.SUSPENDED), "suspension");
            helper.assertTrue(PhoneSims.check(srv, phone) == PhoneSims.Validity.SUSPENDED, "SUSPENDED attendu");
            helper.assertTrue(PhoneSims.line(srv, phone).isPresent(), "une suspendue garde son numéro");
            // Retirée puis reglissée : toujours suspendue (l'état vit au registre, pas dans la pile).
            helper.assertTrue(PhoneSims.remove(p, phone) == PhoneSims.Remove.DONE, "retrait");
            helper.assertTrue(PhoneSims.choices(p).stream().anyMatch(c -> c.validity() == PhoneSims.Validity.SUSPENDED),
                    "au choix, la carte se dit suspendue");
            helper.assertTrue(PhoneSims.insert(p, phone, caseDe(p, simId)) == PhoneSims.Insert.DONE, "reglissée");
            helper.assertTrue(PhoneSims.check(srv, phone) == PhoneSims.Validity.SUSPENDED, "toujours suspendue");

            helper.assertTrue(registre.setState(simId, SimCard.State.ACTIVE), "réactivation");
            helper.assertTrue(PhoneSims.check(srv, phone) == PhoneSims.Validity.ACTIVE, "ACTIVE après réactivation");

            // Désactivée : pour toujours, et sans ligne.
            helper.assertTrue(registre.setState(simId, SimCard.State.DEACTIVATED), "désactivation");
            helper.assertTrue(PhoneSims.check(srv, phone) == PhoneSims.Validity.DEACTIVATED, "DEACTIVATED attendu");
            helper.assertTrue(PhoneSims.line(srv, phone).isEmpty(), "une désactivée n'a pas de ligne");
            helper.assertTrue(!registre.setState(simId, SimCard.State.ACTIVE)
                    && PhoneSims.check(srv, phone) == PhoneSims.Validity.DEACTIVATED, "elle ne revient pas");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void unTelephoneDuLotT1SansCarte(GameTestHelper helper) {
        MinecraftServer srv = helper.getLevel().getServer();
        ItemStack neuf = telephone();
        helper.assertTrue(PhoneSims.check(srv, neuf) == PhoneSims.Validity.NONE, "téléphone neuf : NONE");
        ItemStack t1 = telephone();
        PhoneItem.ensurePhoneId(t1);
        PhoneItem.store(t1, 1000);
        CompoundTag avant = t1.getTag().copy();
        helper.assertTrue(!avant.contains("sim"), "un téléphone du lot T1 n'a pas de NBT sim");
        helper.assertTrue(PhoneSims.check(srv, t1) == PhoneSims.Validity.NONE && PhoneSims.line(srv, t1).isEmpty(),
                "téléphone du lot T1 : NONE, sans ligne");
        helper.assertTrue(PhoneItem.sim(t1).isEmpty(), "aucune carte logée");
        helper.assertTrue(avant.equals(t1.getShareTag()), "le NBT envoyé au client est celui du lot T1");
        helper.assertTrue(avant.equals(t1.getTag()), "rien n'a changé");
        helper.assertTrue(dit(infobulle(t1), "tooltip.oneforallsurvival.phone.no_sim"), "« Aucune carte SIM »");
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------
    // Lot copie : le jeton contre les copies
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_COPIE)
    public static void uneCopieDeLaCarteNeDonnePasDeuxLignes(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest f = essai.joueur("sim_copie_a");
            JoueurDeTest g = essai.joueur("sim_copie_b");
            PhoneOperator op = essai.fonder(f, "Sim Copie");
            MinecraftServer srv = essai.serveur();
            ItemStack a = enMain(f.joueur(), telephone());
            ItemStack b = enMain(g.joueur(), telephone());
            UUID simId = idDe(helper, OperateurDEssai.emettre(helper, op, f));
            helper.assertTrue(PhoneSims.insert(f.joueur(), a, caseDe(f.joueur(), simId)) == PhoneSims.Insert.DONE,
                    "la première pile dans A");

            // La copie de la pile logée (même NBT, jeton à jour), glissée dans B : B prend la ligne, A la perd.
            ItemStack copie = PhoneItem.sim(a).copy();
            g.joueur().getInventory().setItem(7, copie);
            helper.assertTrue(PhoneSims.insert(g.joueur(), b, 7) == PhoneSims.Insert.DONE, "la copie dans B");
            helper.assertTrue(PhoneSims.check(srv, b) == PhoneSims.Validity.ACTIVE, "B ACTIVE");
            helper.assertTrue(PhoneSims.check(srv, a) == PhoneSims.Validity.UNKNOWN,
                    "A UNKNOWN, vu " + PhoneSims.check(srv, a));
            helper.assertTrue(PhoneSims.line(srv, a).isEmpty() && PhoneSims.line(srv, b).isPresent(), "une seule ligne");

            // La pile restée dans A est morte : retirée puis reglissée ailleurs, elle est refusée.
            helper.assertTrue(PhoneSims.remove(f.joueur(), a) == PhoneSims.Remove.DONE, "retrait de la pile morte");
            helper.assertTrue(PhoneItem.phoneId(b).get().equals(carteAuRegistre(helper, essai, simId).phoneId()),
                    "retirer la pile morte ne délie pas B");
            helper.assertTrue(PhoneSims.check(srv, b) == PhoneSims.Validity.ACTIVE, "B garde sa ligne");
            helper.assertTrue(PhoneSims.insert(f.joueur(), a, caseDe(f.joueur(), simId)) == PhoneSims.Insert.UNKNOWN_CARD,
                    "la pile morte est refusée");
            helper.assertTrue(PhoneSims.choices(f.joueur()).stream()
                    .allMatch(c -> c.validity() == PhoneSims.Validity.UNKNOWN), "au choix, elle se dit inconnue");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_COPIE)
    public static void uneCopieFaiteAvantLaPremiereInsertionEstRefusee(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest f = essai.joueur("sim_avant_a");
            JoueurDeTest g = essai.joueur("sim_avant_b");
            PhoneOperator op = essai.fonder(f, "Sim Avant");
            MinecraftServer srv = essai.serveur();
            ItemStack a = enMain(f.joueur(), telephone());
            ItemStack b = enMain(g.joueur(), telephone());
            UUID simId = idDe(helper, OperateurDEssai.emettre(helper, op, f));
            // Copiée tout de suite après l'émission, avant toute insertion : même jeton que l'original.
            ItemStack copie = f.joueur().getInventory().getItem(caseDe(f.joueur(), simId)).copy();
            g.joueur().getInventory().setItem(4, copie);

            helper.assertTrue(PhoneSims.insert(f.joueur(), a, caseDe(f.joueur(), simId)) == PhoneSims.Insert.DONE,
                    "l'original dans A");
            helper.assertTrue(PhoneSims.insert(g.joueur(), b, 4) == PhoneSims.Insert.UNKNOWN_CARD,
                    "la copie faite avant la première insertion est refusée");
            helper.assertTrue(SimCardItem.simId(g.joueur().getInventory().getItem(4)).equals(Optional.of(simId)),
                    "la copie refusée reste dans sa case");
            helper.assertTrue(PhoneSims.check(srv, b) == PhoneSims.Validity.NONE, "B sans carte");
            helper.assertTrue(PhoneSims.check(srv, a) == PhoneSims.Validity.ACTIVE, "A garde sa ligne");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_COPIE)
    public static void uneCopieDuTelephoneAvecSaCarte(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest f = essai.joueur("sim_tel_a");
            JoueurDeTest g = essai.joueur("sim_tel_b");
            PhoneOperator op = essai.fonder(f, "Sim Telephone");
            MinecraftServer srv = essai.serveur();
            ItemStack a = enMain(f.joueur(), telephone());
            UUID simId = idDe(helper, OperateurDEssai.emettre(helper, op, f));
            helper.assertTrue(PhoneSims.insert(f.joueur(), a, caseDe(f.joueur(), simId)) == PhoneSims.Insert.DONE,
                    "insertion");
            ItemStack a2 = enMain(g.joueur(), a.copy());
            helper.assertTrue(PhoneItem.phoneId(a).equals(PhoneItem.phoneId(a2)), "même phone_id");

            helper.assertTrue(PhoneSims.check(srv, a) == PhoneSims.Validity.ACTIVE
                    && PhoneSims.check(srv, a2) == PhoneSims.Validity.ACTIVE, "les deux copies partagent la ligne (lot T1)");
            Set<UUID> lignes = new HashSet<>();
            PhoneSims.line(srv, a).ifPresent(c -> lignes.add(c.simId()));
            PhoneSims.line(srv, a2).ifPresent(c -> lignes.add(c.simId()));
            helper.assertTrue(lignes.equals(Set.of(simId)), "une seule ligne : " + lignes);
            helper.assertTrue(PhoneItem.phoneId(a).get().equals(carteAuRegistre(helper, essai, simId).phoneId()),
                    "portée par ce phone_id");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_COPIE)
    public static void uneCarteForgeeEstRefusee(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest f = essai.joueur("sim_forge");
            PhoneOperator op = essai.fonder(f, "Sim Forge");
            ServerPlayer p = f.joueur();
            MinecraftServer srv = essai.serveur();
            ItemStack phone = enMain(p, telephone());
            UUID simId = idDe(helper, OperateurDEssai.emettre(helper, op, f));
            SimCard vraie = carteAuRegistre(helper, essai, simId);

            // Une carte vierge, puis un sim_id inventé, puis le vrai sim_id avec un faux jeton.
            p.getInventory().setItem(11, new ItemStack(PhoneRegistry.SIM_CARD.get()));
            helper.assertTrue(PhoneSims.insert(p, phone, 11) == PhoneSims.Insert.UNKNOWN_CARD, "carte vierge refusée");
            ItemStack inventee = new ItemStack(PhoneRegistry.SIM_CARD.get());
            inventee.getOrCreateTag().putUUID(SimCardItem.TAG_ID, UUID.randomUUID());
            inventee.getTag().putUUID(SimCardItem.TAG_TOKEN, UUID.randomUUID());
            p.getInventory().setItem(12, inventee);
            helper.assertTrue(PhoneSims.insert(p, phone, 12) == PhoneSims.Insert.UNKNOWN_CARD, "sim_id inventé refusé");
            ItemStack fauxJeton = new ItemStack(PhoneRegistry.SIM_CARD.get());
            fauxJeton.getOrCreateTag().putUUID(SimCardItem.TAG_ID, simId);
            fauxJeton.getTag().putUUID(SimCardItem.TAG_TOKEN, UUID.randomUUID());
            fauxJeton.getTag().putString(SimCardItem.TAG_NUMBER, vraie.number());
            p.getInventory().setItem(13, fauxJeton);
            helper.assertTrue(PhoneSims.insert(p, phone, 13) == PhoneSims.Insert.UNKNOWN_CARD, "faux jeton refusé");
            ItemStack sansJeton = new ItemStack(PhoneRegistry.SIM_CARD.get());
            sansJeton.getOrCreateTag().putUUID(SimCardItem.TAG_ID, simId);
            p.getInventory().setItem(14, sansJeton);
            helper.assertTrue(PhoneSims.insert(p, phone, 14) == PhoneSims.Insert.UNKNOWN_CARD, "sans jeton refusé");
            helper.assertTrue(PhoneSims.check(srv, phone) == PhoneSims.Validity.NONE, "rien n'est entré");
            helper.assertTrue(carteAuRegistre(helper, essai, simId).token().equals(vraie.token()), "le jeton n'a pas bougé");

            // Logée par NBT, sans passer par l'insertion : faux jeton, puis vrai jeton mais pas ce téléphone.
            PhoneItem.ensurePhoneId(phone);
            PhoneItem.setSim(phone, fauxJeton.copy());
            helper.assertTrue(PhoneSims.check(srv, phone) == PhoneSims.Validity.UNKNOWN, "faux jeton logé : UNKNOWN");
            helper.assertTrue(PhoneSims.line(srv, phone).isEmpty(), "aucune ligne");
            ItemStack vraiJeton = fauxJeton.copy();
            vraiJeton.getTag().putUUID(SimCardItem.TAG_TOKEN, vraie.token());
            PhoneItem.setSim(phone, vraiJeton);
            helper.assertTrue(PhoneSims.check(srv, phone) == PhoneSims.Validity.UNKNOWN,
                    "vrai jeton, mais carte jamais liée à ce téléphone : UNKNOWN");
            PhoneItem.setSim(phone, ItemStack.EMPTY);
            helper.assertTrue(PhoneSims.check(srv, phone) == PhoneSims.Validity.NONE, "vidé");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_COPIE)
    public static void leJetonNeVaPasAuClient(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest f = essai.joueur("sim_client");
            PhoneOperator op = essai.fonder(f, "Sim Client");
            ServerPlayer p = f.joueur();
            ItemStack phone = enMain(p, telephone());
            UUID simId = idDe(helper, OperateurDEssai.emettre(helper, op, f));
            ItemStack carte = p.getInventory().getItem(caseDe(p, simId));
            helper.assertTrue(jetonDe(carte) != null, "le serveur garde le jeton sur la pile");

            CompoundTag partage = carte.getShareTag();
            helper.assertTrue(partage != null && partage.hasUUID(SimCardItem.TAG_ID)
                    && !partage.contains(SimCardItem.TAG_TOKEN + "Most") && !partage.contains(SimCardItem.TAG_TOKEN)
                    && partage.contains(SimCardItem.TAG_NUMBER), "carte envoyée, clés : " + (partage == null ? null : partage.getAllKeys()));
            ItemStack recue = parLeReseau(carte);
            helper.assertTrue(SimCardItem.simId(recue).equals(Optional.of(simId)) && jetonDe(recue) == null,
                    "carte relue côté client sans jeton");
            helper.assertTrue(jetonDe(carte) != null, "getShareTag ne retire rien de la pile du serveur");

            helper.assertTrue(PhoneSims.insert(p, phone, caseDe(p, simId)) == PhoneSims.Insert.DONE, "insertion");
            CompoundTag tel = phone.getShareTag();
            helper.assertTrue(tel != null && tel.contains("sim", Tag.TAG_COMPOUND), "le téléphone envoie sa carte");
            CompoundTag simEnvoyee = tel.getCompound("sim").getCompound("tag");
            helper.assertTrue(simEnvoyee.hasUUID(SimCardItem.TAG_ID) && !simEnvoyee.contains(SimCardItem.TAG_TOKEN),
                    "carte logée envoyée, clés : " + simEnvoyee.getAllKeys());
            ItemStack telRecu = parLeReseau(phone);
            ItemStack simRecue = PhoneItem.sim(telRecu);
            helper.assertTrue(SimCardItem.simId(simRecue).equals(Optional.of(simId)) && jetonDe(simRecue) == null,
                    "téléphone relu côté client : carte sans jeton");
            helper.assertTrue(jetonDe(PhoneItem.sim(phone)) != null, "le serveur garde le jeton logé");
            helper.assertTrue(!carteAuRegistre(helper, essai, simId).toString()
                    .contains(carteAuRegistre(helper, essai, simId).token().toString()), "toString sans jeton");
            helper.succeed();
        }
    }

    /** Ce que le client reçoit d'une pile : écrite puis relue comme dans un paquet. */
    private static ItemStack parLeReseau(ItemStack pile) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeItem(pile);
            return buf.readItem();
        } finally {
            buf.release();
        }
    }
}
