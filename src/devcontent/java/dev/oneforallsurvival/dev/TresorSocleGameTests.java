package dev.oneforallsurvival.dev;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.EconomyLedger;
import dev.oneforallsurvival.economy.MoneySink;
import dev.oneforallsurvival.economy.MoneySource;
import dev.oneforallsurvival.economy.MoneyTransfer;
import dev.oneforallsurvival.economy.TreasuryAccount;
import dev.oneforallsurvival.economy.town.TreasuryLedger;
import dev.oneforallsurvival.economy.town.TreasuryLedger.Kind;
import dev.oneforallsurvival.role.RoleSavedData;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Le socle du trésor (lot E3, tâche 1) : registre, dépense, injection et retrait de l'équipe, relecture. Les
 * comptes d'essai sont des instances détachées ({@code load(new CompoundTag())}) ; seuls les essais de commande
 * touchent le trésor du serveur, et ils le rendent comme ils l'ont trouvé (la somme injectée est retirée).
 * La taxe vers le registre est prouvée dans {@code EtalVenteGameTests.laTaxeArriveAuTresorEtAuRegistre}.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class TresorSocleGameTests {

    private static final String LOT = "tresor_socle";

    private TresorSocleGameTests() {}

    // ---------------------------------------------------------------------------------------------
    // Aides
    // ---------------------------------------------------------------------------------------------

    /** Une source de commande qui garde ce qu'on lui dit. */
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

        /** La clé du premier message (sendFailure enveloppe le texte), puis oubli. */
        String cle() {
            Component c = messages.isEmpty() ? Component.empty() : messages.get(0);
            messages.clear();
            if (!(c.getContents() instanceof TranslatableContents) && !c.getSiblings().isEmpty()) {
                c = c.getSiblings().get(0);
            }
            return c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
        }
    }

    /** Exécute une commande ; rend son résultat (0 si elle a échoué). */
    private static int lancer(MinecraftServer serveur, CommandSourceStack source, String commande) {
        try {
            return serveur.getCommands().getDispatcher().execute(commande, source);
        } catch (CommandSyntaxException e) {
            return -1;
        }
    }

    private static CommandSourceStack equipe(MinecraftServer serveur, Ecoute ecoute) {
        return serveur.createCommandSourceStack().withSource(ecoute).withPermission(2);
    }

    private static TreasuryLedger.Line derniere(TreasuryAccount compte) {
        List<TreasuryLedger.Line> page = compte.ledger().page(0);
        return page.isEmpty() ? null : page.get(0);
    }

    // ---------------------------------------------------------------------------------------------
    // Dépense
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT)
    public static void payValueCasseSesPiecesEtNeDonneJamaisPlus(GameTestHelper helper) {
        TreasuryAccount compte = TreasuryAccount.load(new CompoundTag());
        compte.add(Coin.GOLD, 1L);
        helper.assertTrue(compte.payValue(25L), "payer 25 sur 1 or");
        helper.assertTrue(compte.total() == 75L, "75 restent, vu " + compte.total());
        helper.assertTrue(compte.count(Coin.GOLD) == 0L && compte.count(Coin.SILVER) == 7L
                && compte.count(Coin.COPPER) == 5L, "reste 7 argent 5 cuivre, vu " + compte.balance());

        TreasuryAccount autre = TreasuryAccount.load(new CompoundTag());
        autre.add(Coin.GOLD, 1L);
        Map<Coin, Long> avant = autre.balance();
        helper.assertTrue(!autre.payValue(200L), "200 sur 100 doit être refusé");
        helper.assertTrue(autre.balance().equals(avant), "rien ne bouge après un refus : " + autre.balance());
        helper.assertTrue(!autre.payValue(0L) && !autre.payValue(-5L), "un montant nul ou négatif est refusé");
        helper.assertTrue(autre.balance().equals(avant), "rien ne bouge non plus");
        helper.assertTrue(autre.payValue(100L) && autre.total() == 0L, "payer exactement tout le solde");
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------
    // Injection et retrait de l'équipe
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT)
    public static void injectionDeLEquipeAuTresor(GameTestHelper helper) {
        MinecraftServer serveur = helper.getLevel().getServer();
        TreasuryAccount tresor = TreasuryAccount.get(serveur);
        EconomyLedger global = EconomyLedger.get(serveur);
        long avant = tresor.total();
        long cree = global.totalCreated(MoneySource.STAFF);
        long detruit = global.totalDestroyed(MoneySink.STAFF);
        int lignes = tresor.ledger().size();
        Ecoute ecoute = new Ecoute();
        try {
            helper.assertTrue(lancer(serveur, equipe(serveur, ecoute), "eco treasury give 500 relance") == 1,
                    "give : " + ecoute.cle());
            helper.assertTrue(tresor.total() - avant == 500L, "trésor + 500, vu " + (tresor.total() - avant));
            helper.assertTrue(global.totalCreated(MoneySource.STAFF) - cree == 500L, "créé (équipe) + 500");
            helper.assertTrue(tresor.ledger().size() == lignes + 1, "une ligne de plus");
            TreasuryLedger.Line ligne = derniere(tresor);
            helper.assertTrue(ligne.kind() == Kind.INJECTION && ligne.amount() == 500L
                    && "relance".equals(ligne.reason()), "ligne INJECTION : " + ligne);
            helper.assertTrue(tresor.takeFundsArrived() && !tresor.takeFundsArrived(),
                    "le drapeau des fonds arrivés se lève puis se baisse");
            // Un motif trop long, ou une somme nulle, ne bougent rien.
            int avantRefus = tresor.ledger().size();
            helper.assertTrue(lancer(serveur, equipe(serveur, ecoute), "eco treasury give 5 " + "x".repeat(81)) == 0,
                    "motif de 81 caractères refusé");
            helper.assertTrue(tresor.ledger().size() == avantRefus && tresor.total() - avant == 500L,
                    "le refus ne change rien");
        } finally {
            // Rendre le trésor et les totaux comme on les a trouvés.
            lancer(serveur, equipe(serveur, new Ecoute()), "eco treasury take 500 fin-de-l-essai");
            tresor.takeFundsArrived();
        }
        helper.assertTrue(tresor.total() == avant, "trésor rendu, vu " + tresor.total() + " pour " + avant);
        helper.assertTrue(global.totalDestroyed(MoneySink.STAFF) - detruit == 500L, "détruit (équipe) + 500 au retrait");
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT)
    public static void retraitDeLEquipeRefuseSiLeSoldeManque(GameTestHelper helper) {
        MinecraftServer serveur = helper.getLevel().getServer();
        TreasuryAccount tresor = TreasuryAccount.get(serveur);
        EconomyLedger global = EconomyLedger.get(serveur);
        Ecoute ecoute = new Ecoute();
        helper.assertTrue(lancer(serveur, equipe(serveur, ecoute), "eco treasury give 300 essai-retrait") == 1,
                "give : " + ecoute.cle());
        try {
            long solde = tresor.total();
            long detruit = global.totalDestroyed(MoneySink.STAFF);
            int lignes = tresor.ledger().size();
            helper.assertTrue(lancer(serveur, equipe(serveur, ecoute), "eco treasury take " + (solde + 1) + " trop") == 0,
                    "un retrait au-dessus du solde doit être refusé");
            helper.assertTrue(tresor.total() == solde && tresor.ledger().size() == lignes
                    && global.totalDestroyed(MoneySink.STAFF) == detruit, "rien n'a bougé après le refus");

            helper.assertTrue(lancer(serveur, equipe(serveur, ecoute), "eco treasury take 25 ouvrier") == 1,
                    "retrait de 25");
            helper.assertTrue(tresor.total() == solde - 25L, "trésor - 25, vu " + tresor.total());
            helper.assertTrue(global.totalDestroyed(MoneySink.STAFF) - detruit == 25L, "détruit (équipe) + 25");
            TreasuryLedger.Line ligne = derniere(tresor);
            helper.assertTrue(ligne.kind() == Kind.STAFF_TAKE && ligne.amount() == 25L
                    && "ouvrier".equals(ligne.reason()), "ligne STAFF_TAKE : " + ligne);
        } finally {
            lancer(serveur, equipe(serveur, new Ecoute()), "eco treasury take 275 fin-de-l-essai");
            tresor.takeFundsArrived();
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------
    // Sauvegarde
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT)
    public static void leRegistreEtLEmplacementSurviventAuRechargement(GameTestHelper helper) {
        TreasuryAccount compte = TreasuryAccount.load(new CompoundTag());
        UUID maire = UUID.fromString("00000000-0000-0000-0000-00000000beef");
        compte.credit(Map.of(Coin.GOLD, 3L, Coin.COPPER, 5L), Kind.DEPOSIT, maire, "Maire", "caisse du marche");
        compte.note(new TreasuryLedger.Line(42L, null, "", Kind.PLACED, 0L, "pose"));
        GlobalPos place = GlobalPos.of(Level.OVERWORLD, new BlockPos(12, 64, -7));
        compte.setPosition(place);
        TreasuryAccount relu = TreasuryAccount.load(compte.save(new CompoundTag()));
        helper.assertTrue(relu.balance().equals(compte.balance()) && relu.total() == 305L, "solde relu : " + relu.balance());
        helper.assertTrue(relu.ledger().size() == 2, "deux lignes relues : " + relu.ledger().size());
        helper.assertTrue(relu.ledger().page(0).equals(compte.ledger().page(0)), "lignes relues à l'identique");
        helper.assertTrue(relu.position().equals(Optional.of(place)), "position relue : " + relu.position());
        // L'emplacement ne s'efface que s'il est le bon.
        relu.clearPosition(GlobalPos.of(Level.OVERWORLD, new BlockPos(0, 0, 0)));
        helper.assertTrue(relu.position().isPresent(), "un autre emplacement n'efface rien");
        relu.clearPosition(place);
        helper.assertTrue(relu.position().isEmpty(), "le bon emplacement s'efface");
        helper.assertTrue(TreasuryAccount.load(relu.save(new CompoundTag())).position().isEmpty(), "effacé à la relecture");
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT)
    public static void unAncienFichierDuTresorSeRelit(GameTestHelper helper) {
        // Le format d'E2 : seulement « coins », par clé de coupure.
        CompoundTag coins = new CompoundTag();
        coins.putLong("gold", 4L);
        coins.putLong("copper", 5L);
        CompoundTag ancien = new CompoundTag();
        ancien.put("coins", coins);
        TreasuryAccount compte = TreasuryAccount.load(ancien);
        helper.assertTrue(compte.total() == 405L && compte.count(Coin.GOLD) == 4L, "solde relu : " + compte.balance());
        helper.assertTrue(compte.ledger().size() == 0, "registre vide");
        helper.assertTrue(compte.position().isEmpty(), "sans emplacement");
        helper.assertTrue(!compte.takeFundsArrived(), "aucun fonds arrivé à la relecture");
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------
    // /treasury
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT)
    public static void treasuryLogReserveAuMaireEtALEquipe(GameTestHelper helper) {
        MinecraftServer serveur = helper.getLevel().getServer();
        RoleSavedData roles = RoleSavedData.get(serveur);
        Optional<UUID> ancienMaire = roles.book().mayor();
        JoueurDeTest citoyen = null;
        try {
            citoyen = JoueurDeTest.rejoindre(helper.getLevel(), "TresorCitoyen", helper.absoluteVec(new Vec3(2.5, 1.0, 2.5)));
            roles.book().removeMayor();
            Ecoute ecoute = new Ecoute();
            CommandSourceStack source = citoyen.joueur().createCommandSourceStack().withSource(ecoute);
            helper.assertTrue(lancer(serveur, source, "treasury log") == 0, "un citoyen ne lit pas le registre");
            String refus = ecoute.cle();
            helper.assertTrue("command.oneforallsurvival.treasury.mayor_only".equals(refus), "refus : " + refus);
            helper.assertTrue(lancer(serveur, source, "treasury") == 1, "le solde est public");
            String solde = ecoute.cle();
            helper.assertTrue("command.oneforallsurvival.treasury.balance".equals(solde), "solde : " + solde);

            roles.book().appointMayor(citoyen.uuid());
            helper.assertTrue(lancer(serveur, source, "treasury log") == 1, "le maire lit le registre");
            helper.assertTrue("command.oneforallsurvival.treasury.log.title".equals(ecoute.cle()), "titre du registre");
            helper.assertTrue(lancer(serveur, source, "treasury log 999") == 0, "une page absente est refusée");
            ecoute.cle();

            roles.book().removeMayor();
            helper.assertTrue(lancer(serveur, source, "treasury log") == 0, "l'ancien maire ne lit plus");
            ecoute.cle();
            helper.assertTrue(lancer(serveur, equipe(serveur, ecoute), "treasury log") == 1, "l'équipe lit le registre");
            ecoute.cle();
            helper.assertTrue(lancer(serveur, equipe(serveur, ecoute), "eco treasury log") == 1, "/eco treasury log");
            ecoute.cle();
            helper.assertTrue(lancer(serveur, equipe(serveur, ecoute), "eco treasury") == 1, "/eco treasury");
            helper.assertTrue("command.oneforallsurvival.eco.treasury.balance".equals(ecoute.cle()), "état du trésor");
            helper.assertTrue(lancer(serveur, source, "eco treasury") != 1, "/eco reste réservé à l'équipe");
            helper.succeed();
        } finally {
            if (ancienMaire.isPresent()) {
                roles.book().appointMayor(ancienMaire.get());
            } else {
                roles.book().removeMayor();
            }
            roles.setDirty();
            if (citoyen != null && citoyen.connecte()) {
                citoyen.quitter();
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // /eco stats
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT)
    public static void ecoStatsMontreLesNouveauxTransferts(GameTestHelper helper) throws IOException {
        MinecraftServer serveur = helper.getLevel().getServer();
        EconomyLedger global = EconomyLedger.get(serveur);
        Ecoute ecoute = new Ecoute();
        helper.assertTrue(lancer(serveur, equipe(serveur, ecoute), "eco stats") == 1, "eco stats");
        helper.assertTrue(ecoute.messages.size() == 1, "une réponse attendue : " + ecoute.messages.size());
        String texte = ecoute.messages.get(0).getString();
        for (MoneyTransfer genre : new MoneyTransfer[] {MoneyTransfer.TREASURY_DEPOSIT, MoneyTransfer.TREASURY_SPEND,
                MoneyTransfer.SALARY}) {
            String ligne = Component.translatable("command.oneforallsurvival.eco.stats.line",
                    Component.translatable(genre.langKey()), CoinMath.format(global.totalTransferred(genre))).getString();
            helper.assertTrue(texte.contains(ligne), "ligne absente : " + ligne + " dans " + texte);
        }
        // Les libellés lus tels qu'un joueur les voit, dans les deux langues.
        for (String langue : new String[] {"fr_fr", "en_us"}) {
            String json;
            try (InputStream in = TresorSocleGameTests.class
                    .getResourceAsStream("/assets/oneforallsurvival/lang/" + langue + ".json")) {
                helper.assertTrue(in != null, "fichier de langue " + langue);
                json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            for (MoneyTransfer genre : new MoneyTransfer[] {MoneyTransfer.TREASURY_DEPOSIT,
                    MoneyTransfer.TREASURY_SPEND, MoneyTransfer.SALARY}) {
                helper.assertTrue(json.contains("\"" + genre.langKey() + "\""), langue + " : " + genre.langKey());
            }
        }
        String fr;
        try (InputStream in = TresorSocleGameTests.class.getResourceAsStream("/assets/oneforallsurvival/lang/fr_fr.json")) {
            fr = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        helper.assertTrue(fr.contains("\"Dépôt au trésor\"") && fr.contains("\"Dépense publique\"")
                && fr.contains("\"Salaire versé\""), "libellés français");
        helper.succeed();
    }
}
