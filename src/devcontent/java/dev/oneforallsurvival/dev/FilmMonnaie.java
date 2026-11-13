package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinDropEntity;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.EconomyLedger;
import dev.oneforallsurvival.economy.EconomyRegistry;
import dev.oneforallsurvival.economy.MoneySink;
import dev.oneforallsurvival.economy.MoneySource;
import dev.oneforallsurvival.economy.Purse;
import dev.oneforallsurvival.economy.SafeAccounts;
import dev.oneforallsurvival.economy.SafeMenu;
import dev.oneforallsurvival.economy.PurseMenu;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import dev.oneforallsurvival.economy.client.CoinAccountScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
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
 * Film de démonstration du lot E1, la monnaie (développement seulement, jamais empaqueté). Un client qui se pilote seul dans
 * un monde intégré avec le pack complet : les pièces et le change à la table (de vrais clics sur la grille), la bourse qui
 * ramasse les pièces au sol et son écran, un coffre qui refuse la pièce, le coffre-fort posé et rempli, puis
 * {@code /eco stats} et {@code /eco ledger}.
 *
 * <p>Le temps du monde n'est jamais accéléré et aucune durée n'est abrégée. Mêmes images que {@link FilmExploration} :
 * 1280 x 656, un bandeau de légende, 10 images par seconde, dans {@code frames-monnaie}. Activé par
 * {@code -Doneforallsurvival.demo.monnaie}.
 */
@Mod.EventBusSubscriber(modid = "oneforallsurvival", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FilmMonnaie {

    private static final Logger LOG = LogManager.getLogger("oneforallsurvival-film");
    private static final boolean ACTIF = System.getProperty("oneforallsurvival.demo.monnaie") != null;
    private static final String MONDE = "film_monnaie";
    private static final double GARDE_S = 420;

    private enum Phase { TITRE, CREATION, ATTENTE_MONDE, PREPARATION, FILM, FIN }

    private record Etape(String nom, String legende, Runnable action, BooleanSupplier pret, long reposMs, Supplier<String> valeurs) {}

    /** Ce que le serveur sait, recopié à chaque tick pour le bandeau. */
    private record Instantane(long coffre, long creeDepart, long creeEquipe, long detruitEquipe, long circulation) {
        static final Instantane VIDE = new Instantane(0, 0, 0, 0, 0);
    }

    private static Phase phase = Phase.TITRE;
    private static boolean dansUneEtape;
    private static long debutPhaseNs;
    private static final long DEBUT_NS = System.nanoTime();
    private static final List<Etape> ETAPES = new ArrayList<>();
    private static int indexEtape = -1;
    private static boolean actionFaite;
    private static long debutAction;
    private static long debutRepos;
    private static FilmCapture capture;
    private static String echec;
    private static volatile boolean preparationFaite;
    private static volatile Instantane instantane = Instantane.VIDE;
    private static int origine;
    private static String dernierChat = "";

    private static BlockPos o;

    static {
        if (ACTIF) {
            Thread chien = new Thread(() -> {
                try {
                    Thread.sleep((long) ((GARDE_S + 40) * 1000));
                    LOG.error("[film] delai de garde de la JVM depasse : arret force");
                    Runtime.getRuntime().halt(2);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "film-garde");
            chien.setDaemon(true);
            chien.start();
        }
    }

    private FilmMonnaie() {}

    // ================================================================================================
    // Boucle
    // ================================================================================================

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (!ACTIF || phase == Phase.FIN || event.phase != TickEvent.Phase.END || dansUneEtape) {
            return;
        }
        dansUneEtape = true;
        Minecraft mc = Minecraft.getInstance();
        mc.getToasts().clear();
        try {
            if ((System.nanoTime() - DEBUT_NS) / 1e9 > GARDE_S) {
                throw new IllegalStateException("delai de garde du film depasse en phase " + phase);
            }
            avancer(mc);
            if (phase == Phase.FILM && indexEtape >= 1 && indexEtape < ETAPES.size()) {
                Etape e = ETAPES.get(indexEtape);
                String valeurs;
                try {
                    valeurs = e.valeurs().get();
                } catch (RuntimeException ex) {
                    valeurs = "(valeur indisponible : " + ex.getClass().getSimpleName() + ")";
                }
                capture.capturer(mc, e.legende(), valeurs);
            }
        } catch (RuntimeException | IOException e) {
            LOG.error("[film] échec en phase {}", phase, e);
            echec = e.toString();
            terminer(mc);
        } finally {
            dansUneEtape = false;
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (!ACTIF || event.phase != TickEvent.Phase.END || phase == Phase.FIN) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.screen instanceof PauseScreen) {
            mc.setScreen(null);
        }
    }

    @SubscribeEvent
    public static void onChat(ClientChatReceivedEvent event) {
        if (ACTIF) {
            dernierChat = event.getMessage().getString();
            LOG.info("[film] message : {}", dernierChat);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (!ACTIF || event.phase != TickEvent.Phase.END || !preparationFaite) {
            return;
        }
        MinecraftServer serveur = event.getServer();
        EconomyLedger registre = EconomyLedger.get(serveur);
        instantane = new Instantane(SafeAccounts.get(serveur).totalMass(), registre.totalCreated(MoneySource.STARTING),
                registre.totalCreated(MoneySource.STAFF), registre.totalDestroyed(MoneySink.STAFF), registre.inCirculation());
    }

    private static void avancer(Minecraft mc) throws IOException {
        long maintenant = System.nanoTime();
        switch (phase) {
            case TITRE -> {
                if (mc.getOverlay() == null && mc.level == null && mc.screen != null) {
                    reglerLesOptions(mc);
                    phase = Phase.CREATION;
                }
            }
            case CREATION -> {
                phase = Phase.ATTENTE_MONDE;
                creerLeMonde(mc);
                debutPhaseNs = maintenant;
            }
            case ATTENTE_MONDE -> {
                if (mc.level != null && mc.player != null && mc.getSingleplayerServer() != null
                        && mc.getSingleplayerServer().isReady() && maintenant - debutPhaseNs > 6_000_000_000L) {
                    mc.setScreen(null);
                    phase = Phase.PREPARATION;
                    MinecraftServer serveur = mc.getSingleplayerServer();
                    serveur.execute(() -> preparer(serveur));
                }
            }
            case PREPARATION -> {
                if (preparationFaite) {
                    construireScenario(mc);
                    capture = new FilmCapture(mc.gameDirectory.toPath().resolve("frames-monnaie"),
                            "One For All Survival | lot E1 | monnaie");
                    phase = Phase.FILM;
                    LOG.info("[film] scénario prêt : {} étapes", ETAPES.size());
                }
            }
            case FILM -> jouer(mc);
            default -> {
            }
        }
    }

    private static void reglerLesOptions(Minecraft mc) {
        var opt = mc.options;
        opt.pauseOnLostFocus = false;
        opt.onboardAccessibility = false;
        opt.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
        opt.renderDistance().set(10);
        opt.simulationDistance().set(8);
        opt.guiScale().set(2);
        opt.enableVsync().set(false);
        opt.framerateLimit().set(60);
        opt.hideGui = false;
        opt.gamma().set(1.0);
        mc.getToasts().clear();
        mc.mouseHandler.releaseMouse();
    }

    private static void creerLeMonde(Minecraft mc) {
        GameRules regles = new GameRules();
        regles.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        regles.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        regles.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        regles.getRule(GameRules.RULE_ANNOUNCE_ADVANCEMENTS).set(false, null);
        LevelSettings reglages = new LevelSettings(MONDE, GameType.SURVIVAL, false, Difficulty.PEACEFUL, true, regles,
                WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(MONDE, reglages, new WorldOptions(0L, false, false),
                acces -> acces.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value()
                        .createWorldDimensions());
    }

    // ================================================================================================
    // Disposition
    // ================================================================================================

    private static BlockPos p(int dx, int dy, int dz) {
        return o.offset(dx, dy, dz);
    }

    private static BlockPos table() {
        return p(0, 0, 0);
    }

    private static BlockPos coffre() {
        return p(4, 0, 0);
    }

    private static BlockPos coffreFort() {
        return p(8, 0, 0);
    }

    private static void preparer(MinecraftServer serveur) {
        ServerLevel monde = serveur.overworld();
        ServerPlayer joueur = serveur.getPlayerList().getPlayers().get(0);
        BlockPos ici = joueur.blockPosition();
        int y = monde.getHeight(Heightmap.Types.MOTION_BLOCKING, ici.getX(), ici.getZ() + 12);
        o = new BlockPos(ici.getX(), y, ici.getZ() + 12);
        monde.setDayTime(6000);
        monde.setBlock(table(), Blocks.CRAFTING_TABLE.defaultBlockState(), 3);
        monde.setBlock(coffre(), Blocks.CHEST.defaultBlockState(), 3);
        // Le joueur est neuf : il a reçu sa somme de départ à l'entrée dans le monde. Rien d'autre dans son inventaire.
        long libre = 0;
        for (ItemStack s : joueur.getInventory().items) {
            libre += CoinMath.valueOf(s);
        }
        LOG.info("[film] somme de départ reçue : {} cuivres", libre);
        preparationFaite = true;
    }

    // ================================================================================================
    // Outils du scénario
    // ================================================================================================

    private static void aller(Minecraft mc, double x, double y, double z, float yaw, float pitch) {
        MinecraftServer serveur = mc.getSingleplayerServer();
        serveur.execute(() -> serveur.getPlayerList().getPlayers().get(0).teleportTo(serveur.overworld(), x, y, z, yaw, pitch));
    }

    private static void devant(Minecraft mc, BlockPos bloc) {
        aller(mc, bloc.getX() + 0.5, o.getY(), bloc.getZ() + 2.5, 180, 30);
    }

    private static void etape(String nom, String legende, Runnable action, BooleanSupplier pret, long reposMs, Supplier<String> valeurs) {
        ETAPES.add(new Etape(nom, legende, action, pret, reposMs, valeurs));
    }

    private static void sur(Minecraft mc, Runnable travail) {
        mc.getSingleplayerServer().execute(travail);
    }

    private static void commande(Minecraft mc, String texte) {
        sur(mc, () -> {
            MinecraftServer serveur = mc.getSingleplayerServer();
            serveur.getCommands().performPrefixedCommand(serveur.getPlayerList().getPlayers().get(0).createCommandSourceStack().withPermission(4),
                    texte);
        });
    }

    private static void clicDroit(Minecraft mc, BlockPos pos, Direction face) {
        Vec3 point = Vec3.atCenterOf(pos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        var res = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(point, face, pos, false));
        LOG.info("[film] clic droit en {} : {}", pos, res);
    }

    private static void tenirVide(Minecraft mc) {
        for (int i = 8; i >= 0; i--) {
            if (mc.player.getInventory().getItem(i).isEmpty()) {
                mc.player.getInventory().selected = i;
                return;
            }
        }
    }

    private static void tenir(Minecraft mc, Predicate<ItemStack> voulu) {
        for (int i = 0; i < 9; i++) {
            if (voulu.test(mc.player.getInventory().getItem(i))) {
                mc.player.getInventory().selected = i;
                return;
            }
        }
        throw new IllegalStateException("objet absent de la barre d'action");
    }

    private static void clicMenu(Minecraft mc, int index, int bouton, ClickType type) {
        mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId, index, bouton, type, mc.player);
    }

    private static int caseJoueur(Minecraft mc, Predicate<ItemStack> voulu) {
        for (Slot s : mc.player.containerMenu.slots) {
            if (s.container == mc.player.getInventory() && voulu.test(s.getItem())) {
                return s.index;
            }
        }
        return -1;
    }

    /** Le bouton de la souris qui retire, sur une ligne de l'écran du coffre-fort ou de la bourse. */
    private static final int RETIRER = 0;

    /** Un vrai clic sur un bouton de l'écran du coffre-fort ou de la bourse, au centre de l'élément nommé. */
    private static void clicElement(Minecraft mc, String id, int bouton) {
        CoinAccountScreen<?> s = (CoinAccountScreen<?>) mc.screen;
        int[] c = s.elementCenter(id).orElseThrow(() -> new IllegalStateException("aucun element " + id));
        s.mouseClicked(c[0], c[1], bouton);
    }

    private static PurseMenu menuBourse(Minecraft mc) {
        return (PurseMenu) ((AbstractContainerScreen<?>) mc.screen).getMenu();
    }

    private static boolean menuOuvert(Minecraft mc, Class<?> menu) {
        return mc.screen instanceof AbstractContainerScreen<?> s && menu.isInstance(s.getMenu());
    }

    private static long libre(Minecraft mc) {
        long n = 0;
        for (ItemStack s : mc.player.getInventory().items) {
            n += CoinMath.valueOf(s);
        }
        return n;
    }

    private static long bourse(Minecraft mc) {
        long n = 0;
        for (ItemStack s : mc.player.getInventory().items) {
            n += Purse.total(s);
        }
        return n;
    }

    private static long pieces(Minecraft mc, Coin coin) {
        long n = 0;
        for (ItemStack s : mc.player.getInventory().items) {
            if (s.is(coin.item())) {
                n += s.getCount();
            }
        }
        return n;
    }

    private static ItemStack laBourse(Minecraft mc) {
        for (ItemStack s : mc.player.getInventory().items) {
            if (Purse.isPurse(s)) {
                return s;
            }
        }
        return ItemStack.EMPTY;
    }

    private static String fmt(long cuivres) {
        return CoinMath.format(cuivres).getString();
    }

    private static String valeursJoueur(Minecraft mc) {
        return "pieces libres " + libre(mc) + " cuivres (" + fmt(libre(mc)) + ") | bourse " + bourse(mc) + " | coffre-fort " + instantane.coffre();
    }

    private static String valeursRegistre(Minecraft mc) {
        Instantane i = instantane;
        return "cree : depart " + i.creeDepart() + ", equipe " + i.creeEquipe() + " | detruit equipe " + i.detruitEquipe() + " | coffres " + i.coffre()
                + " | en circulation " + i.circulation();
    }

    // ================================================================================================
    // Scénario
    // ================================================================================================

    private static void construireScenario(Minecraft mc) {
        etape("cadrage", "", () -> devant(mc, table()), () -> true, 2500, () -> "");

        // --- Scène 1 : les pièces, le change à la table
        etape("depart", "Un joueur neuf : 500 cuivres (5 pièces d'or) donnés à sa première connexion, une seule fois",
                () -> mc.options.setCameraType(CameraType.FIRST_PERSON), () -> libre(mc) == 500, 4000, () -> valeursJoueur(mc));
        etape("table", "Clic droit sur la table de fabrication : le change se fait ici", () -> {
            tenirVide(mc);
            clicDroit(mc, table(), Direction.NORTH);
        }, () -> mc.screen instanceof CraftingScreen, 1500, () -> valeursJoueur(mc));
        etape("or_grille", "Les 5 pièces d'or dans la grille : la table propose 10 pièces d'argent", () -> {
            origine = caseJoueur(mc, s -> s.is(Coin.GOLD.item()));
            clicMenu(mc, origine, 0, ClickType.PICKUP);
        }, () -> true, 800, () -> valeursJoueur(mc));
        etape("or_pose", "", () -> clicMenu(mc, 1, 0, ClickType.PICKUP),
                () -> !mc.player.containerMenu.getSlot(0).getItem().isEmpty(), 3000, () -> valeursJoueur(mc));
        etape("change", "Clic majuscule sur le résultat : 50 pièces d'argent, à valeur égale (500 cuivres)",
                () -> clicMenu(mc, 0, 0, ClickType.QUICK_MOVE), () -> pieces(mc, Coin.SILVER) == 50, 3000, () -> valeursJoueur(mc));
        etape("table_fermee", "", () -> mc.player.closeContainer(), () -> mc.screen == null, 800, () -> valeursJoueur(mc));

        // --- Scène 2 : la bourse ramasse les pièces au sol
        etape("bourse_donnee", "Une bourse (donnée ici ; sa fabrication à la table est dans l'essai de bout en bout)", () -> {
            sur(mc, () -> {
                ServerPlayer j = mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                j.getInventory().add(new ItemStack(EconomyRegistry.PURSE.get()));
            });
        }, () -> !laBourse(mc).isEmpty(), 2000, () -> valeursJoueur(mc));
        etape("sol", "Des pièces tombent au sol : la bourse les ramasse, sans les laisser à l'inventaire", () -> {
            sur(mc, () -> {
                ServerLevel monde = mc.getSingleplayerServer().overworld();
                BlockPos c = p(2, 0, 4);
                monde.addFreshEntity(CoinDropEntity.at(monde, c.getX() + 0.5, c.getY() + 0.2, c.getZ() + 0.5, Coin.GOLD, 3));
                MinecraftServer s = mc.getSingleplayerServer();
                EconomyLedger.get(s).created(MoneySource.STAFF, 300, null, "film : pieces posees au sol");
            });
            aller(mc, o.getX() + 2.5, o.getY(), o.getZ() + 6.5, 180, 40);
        }, () -> true, 600, () -> valeursJoueur(mc));
        etape("ramasse", "Il avance sur les pièces : 300 cuivres entrent dans la bourse", () -> aller(mc, o.getX() + 2.5, o.getY(), o.getZ() + 4.5, 180, 40),
                () -> Purse.total(laBourse(mc)) == 300, 3000, () -> valeursJoueur(mc));
        etape("bourse_ecran", "Clic droit, la bourse en main : son écran (le total, une ligne par pièce)", () -> {
            tenir(mc, Purse::isPurse);
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        }, () -> menuOuvert(mc, PurseMenu.class), 2500, () -> valeursJoueur(mc));
        etape("deposer", "« Déposer tout » : les 50 pièces d'argent entrent dans la bourse", () -> clicElement(mc, "deposit_all", 0),
                () -> menuBourse(mc).count(Coin.SILVER) == 50 && menuBourse(mc).total() == 800, 2500, () -> valeursJoueur(mc));
        etape("regrouper", "« Regrouper » : 10 pièces d'une coupure deviennent 1 de la suivante, à valeur égale",
                () -> clicElement(mc, "group", 0), () -> menuBourse(mc).count(Coin.SILVER) == 0 && menuBourse(mc).count(Coin.GOLD) == 8, 4000,
                () -> valeursJoueur(mc));
        etape("bourse_fermee", "", () -> mc.player.closeContainer(), () -> mc.screen == null && Purse.count(laBourse(mc), Coin.GOLD) == 8, 800,
                () -> valeursJoueur(mc));

        // --- Scène 3 : un coffre refuse la pièce
        etape("coffre_retrait", "Un peu d'argent à la main pour l'essai du coffre (retiré de la bourse)", () -> {
            tenir(mc, Purse::isPurse);
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        }, () -> menuOuvert(mc, PurseMenu.class), 800, () -> valeursJoueur(mc));
        etape("retirer", "« Retirer 1 » sur la ligne de l'or", () -> clicElement(mc, "row.gold.1", RETIRER),
                () -> menuBourse(mc).count(Coin.GOLD) == 7, 1800, () -> valeursJoueur(mc));
        etape("retirer_fermer", "", () -> mc.player.closeContainer(), () -> mc.screen == null && pieces(mc, Coin.GOLD) == 1, 600, () -> valeursJoueur(mc));
        etape("coffre_vue", "Un coffre ordinaire", () -> aller(mc, coffre().getX() + 0.5, o.getY(), coffre().getZ() + 2.5, 180, 30),
                () -> true, 1500, () -> valeursJoueur(mc));
        etape("coffre_ouvert", "Clic droit sur le coffre", () -> {
            tenirVide(mc);
            clicDroit(mc, coffre(), Direction.NORTH);
        }, () -> mc.screen instanceof AbstractContainerScreen<?> s && s.getMenu() != mc.player.inventoryMenu, 1500, () -> valeursJoueur(mc));
        etape("coffre_prend", "La pièce d'or est prise au curseur...", () -> {
            origine = caseJoueur(mc, s -> s.is(Coin.GOLD.item()));
            clicMenu(mc, origine, 0, ClickType.PICKUP);
        }, () -> !mc.player.containerMenu.getCarried().isEmpty(), 1800, () -> valeursJoueur(mc) + " | curseur : pièce d'or");
        etape("coffre_refuse", "...et refusée : elle reste au curseur, le coffre n'a rien reçu", () -> clicMenu(mc, 0, 0, ClickType.PICKUP),
                () -> !mc.player.containerMenu.getCarried().isEmpty() && mc.player.containerMenu.getSlot(0).getItem().isEmpty(), 3500,
                () -> valeursJoueur(mc) + " | case 0 du coffre vide, piece au curseur");
        etape("coffre_rend", "Elle est remise dans l'inventaire", () -> clicMenu(mc, origine, 0, ClickType.PICKUP),
                () -> mc.player.containerMenu.getCarried().isEmpty(), 1200, () -> valeursJoueur(mc));
        etape("coffre_ferme", "", () -> mc.player.closeContainer(), () -> mc.screen == null, 800, () -> valeursJoueur(mc));

        // --- Scène 4 : le coffre-fort
        etape("fort_donne", "Un coffre-fort (donné ici ; sa recette est dans l'essai de bout en bout) : un par joueur", () -> {
            sur(mc, () -> mc.getSingleplayerServer().getPlayerList().getPlayers().get(0).getInventory()
                    .add(new ItemStack(EconomyRegistry.SAFE_ITEM.get())));
            aller(mc, coffreFort().getX() + 0.5, o.getY(), coffreFort().getZ() + 2.5, 180, 40);
        }, () -> caseJoueur0(mc, EconomyRegistry.SAFE_ITEM.get()), 1500, () -> valeursJoueur(mc));
        etape("fort_pose", "Il le pose : le coffre-fort lui appartient", () -> {
            tenir(mc, s -> s.is(EconomyRegistry.SAFE_ITEM.get()));
            clicDroit(mc, coffreFort().below(), Direction.UP);
        }, () -> mc.level.getBlockState(coffreFort()).is(EconomyRegistry.SAFE.get()), 2500, () -> valeursJoueur(mc));
        etape("fort_ouvert", "Clic droit : son écran (le compte est gardé par le serveur, pas par le bloc)", () -> {
            tenirVide(mc);
            clicDroit(mc, coffreFort(), Direction.NORTH);
        }, () -> menuOuvert(mc, SafeMenu.class), 2500, () -> valeursJoueur(mc));
        etape("fort_depose", "« Déposer tout » : l'or de l'inventaire et de la bourse entre au compte", () -> clicElement(mc, "deposit_all", 0),
                () -> menuOuvert(mc, SafeMenu.class) && ((SafeMenu) ((AbstractContainerScreen<?>) mc.screen).getMenu()).total() == 800, 4000,
                () -> valeursJoueur(mc));
        etape("fort_regroupe", "« Retirer 1 » sur l'or : la pièce revient dans la bourse (elle prend les pièces avant l'inventaire)", () -> clicElement(mc, "row.gold.1", RETIRER),
                () -> ((SafeMenu) ((AbstractContainerScreen<?>) mc.screen).getMenu()).total() == 700, 3500, () -> valeursJoueur(mc));
        etape("fort_ferme", "", () -> mc.player.closeContainer(), () -> mc.screen == null && Purse.total(laBourse(mc)) == 100, 800, () -> valeursJoueur(mc));
        etape("fort_second", "Un second coffre-fort est refusé : un par joueur", () -> {
            sur(mc, () -> mc.getSingleplayerServer().getPlayerList().getPlayers().get(0).getInventory()
                    .add(new ItemStack(EconomyRegistry.SAFE_ITEM.get())));
            aller(mc, coffreFort().getX() + 3.5, o.getY(), coffreFort().getZ() + 2.5, 180, 40);
        }, () -> caseJoueur0(mc, EconomyRegistry.SAFE_ITEM.get()), 1200, () -> valeursJoueur(mc));
        etape("fort_refuse", "Clic droit sur le sol avec le second : rien n'est posé", () -> {
            tenir(mc, s -> s.is(EconomyRegistry.SAFE_ITEM.get()));
            clicDroit(mc, coffreFort().east(3).below(), Direction.UP);
        }, () -> true, 3500, () -> "second coffre-fort pose : "
                + (mc.level.getBlockState(coffreFort().east(3)).is(EconomyRegistry.SAFE.get()) ? "OUI (anomalie)" : "non, il reste dans l'inventaire"));

        // --- Scène 5 : l'équipe
        etape("equipe", "L'équipe injecte 1 rubis (/eco give), puis retire 50 cuivres (/eco take)", () -> {
            commande(mc, "eco give @s 10000 injection d'un rubis");
            commande(mc, "eco take @s 50 retrait de controle");
            tenirVide(mc);
        }, () -> instantane.detruitEquipe() == 50, 3000, () -> valeursRegistre(mc));
        etape("stats", "/eco stats : créé par source, détruit par cause, masse des coffres-forts, en circulation", () -> {
            mc.setScreen(new ChatScreen(""));
            commande(mc, "eco stats");
        }, () -> dernierChat.contains("circulation"), 6000, () -> valeursRegistre(mc));
        etape("ledger", "/eco ledger : chaque création et chaque destruction, avec l'auteur et le motif", () -> commande(mc, "eco ledger"),
                () -> dernierChat.contains("injection") || dernierChat.contains("retrait") || dernierChat.contains("Starting"), 7000,
                () -> valeursRegistre(mc));
        etape("fin", "Fin du lot E1 (temps réel, aucune durée abrégée)", () -> mc.setScreen(null), () -> true, 2500, () -> valeursRegistre(mc));
    }

    private static boolean caseJoueur0(Minecraft mc, net.minecraft.world.item.Item item) {
        for (ItemStack s : mc.player.getInventory().items) {
            if (s.is(item)) {
                return true;
            }
        }
        return false;
    }

    // ================================================================================================
    // Déroulement
    // ================================================================================================

    private static void jouer(Minecraft mc) {
        if (indexEtape >= ETAPES.size()) {
            return;
        }
        long maintenant = System.nanoTime();
        if (indexEtape < 0) {
            indexEtape = 0;
            actionFaite = false;
        }
        Etape e = ETAPES.get(indexEtape);
        if (!actionFaite) {
            LOG.info("[film] étape : {} | {}", e.nom(), instantane);
            e.action().run();
            actionFaite = true;
            debutAction = maintenant;
            debutRepos = 0;
            return;
        }
        if (!e.pret().getAsBoolean()) {
            if (maintenant - debutAction > 60_000_000_000L) {
                throw new IllegalStateException("étape sans issue : " + e.nom() + " | " + instantane + " | " + valeursJoueur(mc));
            }
            return;
        }
        if (debutRepos == 0) {
            debutRepos = maintenant;
        }
        if ((maintenant - debutRepos) / 1_000_000L < e.reposMs()) {
            return;
        }
        LOG.info("[film] étape finie : {} | {}", e.nom(), instantane);
        indexEtape++;
        actionFaite = false;
        if (indexEtape >= ETAPES.size()) {
            terminer(mc);
        }
    }

    private static void terminer(Minecraft mc) {
        phase = Phase.FIN;
        int n = 0;
        if (capture != null) {
            capture.terminer(echec);
            n = capture.images();
        }
        LOG.info("[film] terminé : {} images, {}", n, echec == null ? "sans erreur" : echec);
        mc.stop();
    }
}
