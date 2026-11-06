package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.bench.BenchRecipe;
import dev.oneforallsurvival.bench.BenchRegistry;
import dev.oneforallsurvival.bench.client.BenchClientState;
import dev.oneforallsurvival.bench.client.BenchStockScreen;
import dev.oneforallsurvival.bench.client.EngineerBenchScreen;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Preuve de bout en bout contre un serveur dédié (développement seulement, jamais empaqueté). Un vrai
 * client se connecte à {@code -Doneforallsurvival.e2e=<hôte>}, se fait Ingénieur par la vraie commande
 * {@code /role}, pose un établi, l'ouvre par un vrai clic droit, commande un craft court, attend le stock,
 * le retire, puis arrête le serveur par {@code /stop}. Chaque contrôle écrit une ligne {@code PASS} ou
 * {@code FAIL} dans le journal ; le client quitte à la fin.
 */
@Mod.EventBusSubscriber(modid = "oneforallsurvival", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class E2eClient {

    private static final Logger LOG = LogManager.getLogger("oneforallsurvival-e2e");
    private static final String HOTE = System.getProperty("oneforallsurvival.e2e");
    private static final boolean ACTIF = HOTE != null && !HOTE.isEmpty();
    private static final int COPIES = 2;

    private enum Phase { TITRE, CONNEXION, CATALOGUE, PROVISIONS, POSER, OUVRIR, COMMANDER, STOCK, OUVRIR_STOCK, PRENDRE,
        ARRET, FIN }

    private static Phase phase = Phase.TITRE;
    private static long debutPhase = System.nanoTime();
    private static boolean actionFaite;
    private static int echecs;
    private static BenchRecipe recette;
    private static List<ItemStack> cout = new ArrayList<>();
    private static BlockPos banc;
    private static int avantInventaire;
    private static long debutCraft;
    private static int dernierSecondes = -1;
    /** Le stock persiste d'un essai à l'autre : on mesure ce qu'il gagne, pas sa valeur. */
    private static int stockAvant;
    private static int attendusRetrait;

    private static FilmCapture film;
    private static String derniereLigne = "";

    private E2eClient() {}

    private static String legende() {
        return switch (phase) {
            case TITRE -> "Client réel : menu principal";
            case CONNEXION -> "Connexion au serveur dédié (localhost, pas de serveur intégré)";
            case CATALOGUE -> "Le catalogue de l'établi arrive par le réseau, dès la connexion";
            case PROVISIONS -> "Commandes réelles : /role set, /clear, /give";
            case POSER -> "Pose de l'établi par un clic droit";
            case OUVRIR -> "Clic droit sur l'établi : le serveur ouvre l'écran";
            case COMMANDER -> "Commande de 2 crafts à l'établi";
            case STOCK -> "Le serveur fabrique : la file avance, le stock grandit";
            case OUVRIR_STOCK -> "Ouverture du stock";
            case PRENDRE -> "Récupération de la pile fabriquée";
            case ARRET -> "Arrêt du serveur par /stop";
            default -> "Fin";
        };
    }

    private static String valeurs() {
        return "catalogue " + BenchClientState.catalog().size() + " recettes (gén. " + BenchClientState.generation()
                + ") | file " + BenchClientState.queue().size() + " | stock " + BenchClientState.stockItems()
                + " | vitesses serveur x" + BenchClientState.manualSpeed() + " / x" + BenchClientState.poweredSpeed()
                + (derniereLigne.isEmpty() ? "" : " | " + derniereLigne);
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (!ACTIF || event.phase != TickEvent.Phase.END || film == null) {
            return;
        }
        film.capturer(Minecraft.getInstance(), legende(), valeurs());
    }

    private static void passer(Phase suivante) {
        phase = suivante;
        debutPhase = System.nanoTime();
        actionFaite = false;
    }

    private static double secondes() {
        return (System.nanoTime() - debutPhase) / 1e9;
    }

    private static void pass(String texte) {
        LOG.info("[e2e] PASS {}", texte);
    }

    private static void fail(String texte) {
        echecs++;
        LOG.error("[e2e] FAIL {}", texte);
        passer(Phase.ARRET);
    }

    @SubscribeEvent
    public static void onChat(ClientChatReceivedEvent event) {
        if (ACTIF) {
            LOG.info("[e2e] chat : {}", event.getMessage().getString());
            String m = event.getMessage().getString();
            derniereLigne = m.length() > 70 ? m.substring(0, 70) : m;
        }
    }

    private static int compter(Minecraft mc, Item item) {
        int n = 0;
        for (ItemStack s : mc.player.getInventory().items) {
            if (s.is(item)) {
                n += s.getCount();
            }
        }
        return n;
    }

    private static String cle(Ingredient ingredient) {
        StringBuilder sb = new StringBuilder();
        for (ItemStack s : ingredient.getItems()) {
            sb.append(BuiltInRegistries.ITEM.getKey(s.getItem())).append(',');
        }
        return sb.toString();
    }

    /** La recette la plus courte, sans étiquette, d'au plus quatre ingrédients distincts. */
    private static BenchRecipe choisir() {
        BenchRecipe meilleure = null;
        for (BenchRecipe r : BenchClientState.catalog()) {
            boolean simple = r.ingredients().stream().noneMatch(i -> i.getItems().length != 1);
            Map<String, Integer> distincts = new LinkedHashMap<>();
            r.ingredients().forEach(i -> distincts.merge(cle(i), 1, Integer::sum));
            if (!simple || distincts.size() > 4 || r.ingredients().size() > 6
                    || BuiltInRegistries.ITEM.getKey(r.result().getItem()).getNamespace().equals("oneforallsurvival")) {
                continue;
            }
            if (meilleure == null || r.seconds() < meilleure.seconds()) {
                meilleure = r;
            }
        }
        return meilleure;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (!ACTIF || event.phase != TickEvent.Phase.END || phase == Phase.FIN) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        try {
            avancer(mc);
        } catch (RuntimeException e) {
            LOG.error("[e2e] exception en phase {}", phase, e);
            fail("exception en phase " + phase + " : " + e);
        }
        if (phase != Phase.TITRE && phase != Phase.FIN && phase != Phase.ARRET && secondes() > 180) {
            fail("delai depasse en phase " + phase);
        }
    }

    private static void avancer(Minecraft mc) {
        switch (phase) {
            case TITRE -> {
                debutPhase = phase == Phase.TITRE ? debutPhase : System.nanoTime();
                // Premier lancement d'un dossier neuf : l'écran d'accessibilité précède le menu.
                if (mc.getOverlay() == null && mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) {
                    mc.setScreen(new TitleScreen());
                    return;
                }
                if (mc.getOverlay() == null && mc.level == null && mc.screen instanceof TitleScreen) {
                    mc.options.pauseOnLostFocus = false;
                    mc.options.onboardAccessibility = false;
                    mc.options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
                    LOG.info("[e2e] connexion a {}", HOTE);
                    try {
                        film = new FilmCapture(mc.gameDirectory.toPath().resolve("frames-e2e"),
                                "One For All Survival | serveur dédié");
                    } catch (java.io.IOException e) {
                        throw new IllegalStateException(e);
                    }
                    ConnectScreen.startConnecting(mc.screen, mc, ServerAddress.parseString(HOTE),
                            new ServerData("e2e", HOTE, false), false);
                    passer(Phase.CONNEXION);
                }
            }
            case CONNEXION -> {
                if (mc.player != null && mc.level != null) {
                    if (mc.getSingleplayerServer() == null) {
                        pass("connecte a un serveur distant (aucun serveur integre) : " + mc.getCurrentServer().ip);
                    } else {
                        fail("serveur integre present : ce n'est pas un serveur dedie");
                        return;
                    }
                    passer(Phase.CATALOGUE);
                }
            }
            case CATALOGUE -> {
                // Le catalogue doit arriver a la connexion, sans qu'aucun etabli n'ait ete ouvert.
                if (!BenchClientState.catalog().isEmpty()) {
                    pass("catalogue recu par le reseau a la connexion : " + BenchClientState.catalog().size()
                            + " recettes, generation " + BenchClientState.generation() + ", apres " + String.format("%.1f", secondes()) + " s");
                    passer(Phase.PROVISIONS);
                } else if (secondes() > 40) {
                    fail("aucun catalogue recu 40 s apres la connexion");
                }
            }
            case PROVISIONS -> {
                String nom = mc.player.getGameProfile().getName();
                if (!actionFaite) {
                    recette = choisir();
                    if (recette == null) {
                        fail("aucune recette simple au catalogue");
                        return;
                    }
                    LOG.info("[e2e] recette choisie : {} ({} s), resultat {}", recette.id(), recette.seconds(),
                            recette.result());
                    mc.player.connection.sendCommand("role set " + nom + " ingenieur");
                    mc.player.connection.sendCommand("clear " + nom);
                    mc.player.connection.sendCommand("give " + nom + " oneforallsurvival:engineer_bench");
                    cout = new ArrayList<>();
                    Map<String, ItemStack> lignes = new LinkedHashMap<>();
                    for (Ingredient i : recette.ingredients()) {
                        ItemStack s = i.getItems()[0];
                        lignes.merge(cle(i), s.copyWithCount(COPIES), (a, b) -> {
                            a.grow(COPIES);
                            return a;
                        });
                    }
                    for (ItemStack s : lignes.values()) {
                        cout.add(s);
                        mc.player.connection.sendCommand("give " + nom + " "
                                + BuiltInRegistries.ITEM.getKey(s.getItem()) + " " + s.getCount());
                    }
                    actionFaite = true;
                    return;
                }
                boolean pret = compter(mc, BenchRegistry.ENGINEER_BENCH_ITEM.get()) >= 1;
                for (ItemStack s : cout) {
                    pret &= compter(mc, s.getItem()) >= s.getCount();
                }
                if (pret) {
                    pass("commandes /role et /give executees par le serveur, provisions recues");
                    passer(Phase.POSER);
                } else if (secondes() > 30) {
                    fail("provisions non recues");
                }
            }
            case POSER -> {
                if (secondes() < 1.5) {
                    return;
                }
                var joueur = mc.player;
                int fente = -1;
                for (int i = 0; i < 9; i++) {
                    if (joueur.getInventory().getItem(i).is(BenchRegistry.ENGINEER_BENCH_ITEM.get())) {
                        fente = i;
                    }
                }
                if (fente < 0) {
                    fail("l'etabli n'est pas dans la barre d'action");
                    return;
                }
                joueur.getInventory().selected = fente;
                BlockPos pieds = joueur.blockPosition();
                BlockPos cible = null;
                outer:
                for (int d = 2; d <= 6; d++) {
                    for (Direction dir : Direction.Plane.HORIZONTAL) {
                        BlockPos p = pieds.relative(dir, d);
                        if (mc.level.getBlockState(p.below()).isSolidRender(mc.level, p.below())
                                && mc.level.getBlockState(p).isAir() && mc.level.getBlockState(p.above()).isAir()) {
                            cible = p;
                            break outer;
                        }
                    }
                }
                if (cible == null) {
                    fail("aucun emplacement libre autour du joueur " + pieds);
                    return;
                }
                BlockPos sol = cible.below();
                var res = mc.gameMode.useItemOn(joueur, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(sol).add(0, 0.5, 0), Direction.UP, sol, false));
                banc = cible;
                LOG.info("[e2e] pose en {} : {}", cible, res);
                passer(Phase.OUVRIR);
            }
            case OUVRIR -> {
                if (!actionFaite) {
                    if (!mc.level.getBlockState(banc).is(BenchRegistry.ENGINEER_BENCH.get())) {
                        if (secondes() > 10) {
                            fail("l'etabli n'a pas ete pose en " + banc);
                        }
                        return;
                    }
                    pass("etabli pose par un clic droit reel en " + banc);
                    var res = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(banc), Direction.UP, banc, false));
                    LOG.info("[e2e] clic droit sur l'etabli : {}", res);
                    actionFaite = true;
                    debutPhase = System.nanoTime();
                    return;
                }
                if (mc.screen instanceof EngineerBenchScreen) {
                    pass("ecran de l'etabli ouvert par clic droit reel (role Ingenieur accepte par le serveur)");
                    passer(Phase.COMMANDER);
                } else if (secondes() > 15) {
                    fail("l'ecran ne s'est pas ouvert, ecran actuel : " + mc.screen);
                }
            }
            case COMMANDER -> {
                if (secondes() < 1.0 || !(mc.screen instanceof EngineerBenchScreen ecran)) {
                    return;
                }
                avantInventaire = compter(mc, cout.get(0).getItem());
                stockAvant = BenchClientState.stockItems();
                boolean trouvee = ecran.selectById(recette.id());
                ecran.setQuantity(COPIES);
                LOG.info("[e2e] selection de la recette : {} ; {} recettes montrees", trouvee, ecran.shownRecipes().size());
                ecran.order();
                debutCraft = System.nanoTime();
                LOG.info("[e2e] commande de {} x {} ; {} : {} avant", COPIES, recette.id(), cout.get(0).getItem(),
                        avantInventaire);
                passer(Phase.STOCK);
            }
            case STOCK -> {
                if (!actionFaite) {
                    if (secondes() < 2) {
                        return;
                    }
                    int apres = compter(mc, cout.get(0).getItem());
                    if ((int) secondes() != dernierSecondes) {
                        dernierSecondes = (int) secondes();
                        LOG.info("[e2e] attente : {} {} en inventaire, file {}", apres, cout.get(0).getItem(),
                                BenchClientState.queue().size());
                        if (dernierSecondes == 3) {
                            StringBuilder sb = new StringBuilder();
                            for (int i = 0; i < mc.player.getInventory().items.size(); i++) {
                                ItemStack st = mc.player.getInventory().items.get(i);
                                if (!st.isEmpty()) {
                                    sb.append(i).append('=').append(st).append(' ');
                                }
                            }
                            LOG.info("[e2e] inventaire client : {}", sb);
                            mc.player.connection.sendCommand("data get entity " + mc.player.getGameProfile().getName() + " Inventory");
                        }
                    }
                    if (apres >= avantInventaire || BenchClientState.queue().isEmpty()) {
                        if (secondes() > 15) {
                            fail("commande non prise en compte : " + apres + " apres, file " + BenchClientState.queue().size());
                        }
                        return;
                    }
                    pass("commande acceptee : ingredients preleves (" + avantInventaire + " -> " + apres
                            + "), file de " + BenchClientState.queue().size() + " fabrication(s), vitesses serveur main x"
                            + BenchClientState.manualSpeed() + " / alimente x" + BenchClientState.poweredSpeed());
                    actionFaite = true;
                }
                int attendus = recette.result().getCount() * COPIES;
                if (BenchClientState.stockItems() >= stockAvant + attendus) {
                    pass("craft termine cote serveur : stock " + stockAvant + " -> " + BenchClientState.stockItems() + " objet(s) apres "
                            + String.format("%.1f", (System.nanoTime() - debutCraft) / 1e9) + " s");
                    passer(Phase.OUVRIR_STOCK);
                } else if (secondes() > 120) {
                    fail("stock toujours a " + BenchClientState.stockItems() + " apres 120 s");
                }
            }
            case OUVRIR_STOCK -> {
                if (!actionFaite) {
                    if (!(mc.screen instanceof EngineerBenchScreen ecran)) {
                        fail("ecran de l'etabli ferme : " + mc.screen);
                        return;
                    }
                    int[] p = ecran.hoverPoint("stock");
                    mc.screen.mouseClicked(p[0], p[1], 0);
                    actionFaite = true;
                    return;
                }
                if (mc.screen instanceof BenchStockScreen s && s.getMenu().totalStacks() >= 1) {
                    pass("ecran de stock ouvert : " + s.getMenu().totalStacks() + " pile(s)");
                    passer(Phase.PRENDRE);
                } else if (secondes() > 15) {
                    fail("stock non ouvert, ecran : " + mc.screen);
                }
            }
            case PRENDRE -> {
                if (!(mc.screen instanceof BenchStockScreen s)) {
                    return;
                }
                if (!actionFaite) {
                    int fente = -1;
                    for (int i = 0; i < s.getMenu().slots.size() && fente < 0; i++) {
                        if (s.getMenu().slots.get(i).getItem().is(recette.result().getItem())) {
                            fente = i;
                        }
                    }
                    if (fente < 0) {
                        fail("aucune pile de " + recette.result().getItem() + " dans le stock");
                        return;
                    }
                    attendusRetrait = s.getMenu().slots.get(fente).getItem().getCount();
                    avantInventaire = compter(mc, recette.result().getItem());
                    LOG.info("[e2e] retrait de la pile {} : {}", fente, s.getMenu().slots.get(fente).getItem());
                    mc.gameMode.handleInventoryMouseClick(s.getMenu().containerId, fente, 0, ClickType.QUICK_MOVE, mc.player);
                    actionFaite = true;
                    return;
                }
                int gagne = compter(mc, recette.result().getItem()) - avantInventaire;
                if (gagne >= attendusRetrait && gagne >= recette.result().getCount() * COPIES) {
                    pass("stock retire : +" + gagne + " " + recette.result().getItem() + " dans l'inventaire");
                    passer(Phase.ARRET);
                } else if (secondes() > 15) {
                    fail("retrait : +" + gagne + " au lieu de " + attendusRetrait);
                }
            }
            case ARRET -> {
                if (!actionFaite) {
                    LOG.info("[e2e] bilan : {}", echecs == 0 ? "TOUT PASSE" : echecs + " ECHEC(S)");
                    if (mc.player != null) {
                        mc.setScreen(null);
                        mc.player.connection.sendCommand("stop");
                        LOG.info("[e2e] /stop envoye");
                    }
                    actionFaite = true;
                    return;
                }
                // Le serveur ferme la connexion ; sinon, on quitte au bout de 30 s.
                if (mc.level == null || secondes() > 30) {
                    phase = Phase.FIN;
                    if (film != null) {
                        film.terminer(echecs == 0 ? null : echecs + " echec(s)");
                    }
                    LOG.info("[e2e] fin du client");
                    mc.stop();
                }
            }
            default -> {
            }
        }
    }
}
