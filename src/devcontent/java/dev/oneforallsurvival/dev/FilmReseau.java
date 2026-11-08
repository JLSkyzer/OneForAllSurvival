package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.food.FoodDecay;
import dev.oneforallsurvival.food.FoodRegistry;
import dev.oneforallsurvival.food.FoodStamp;
import dev.oneforallsurvival.food.FridgeBlock;
import dev.oneforallsurvival.food.FridgeBlockEntity;
import dev.oneforallsurvival.grid.BreakerBlock;
import dev.oneforallsurvival.grid.BreakerBlockEntity;
import dev.oneforallsurvival.grid.GridRegistry;
import dev.oneforallsurvival.grid.LoadShedderBlock;
import dev.oneforallsurvival.grid.LoadShedderBlockEntity;
import dev.oneforallsurvival.grid.OutputStatus;
import dev.oneforallsurvival.grid.WeatherStationBlock;
import dev.oneforallsurvival.grid.client.LoadShedderScreen;
import dev.oneforallsurvival.grid.client.SubscriberNameScreen;
import dev.oneforallsurvival.machine.ElectricMachineBlock;
import dev.oneforallsurvival.machine.ElectricMachineBlockEntity;
import dev.oneforallsurvival.machine.InlineEnergyBlock;
import dev.oneforallsurvival.repair.RepairRegistry;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
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
import org.lwjgl.glfw.GLFW;

/**
 * Film de démonstration du lot 4 (développement seulement, jamais empaqueté). Un client qui se pilote seul dans un monde
 * intégré avec le pack complet : vrais clics droits, vrais boutons de l'écran du tableau, vraie saisie au clavier, vrais
 * éclairs (non visuels), vraie commande {@code /weather}. Le temps n'est jamais accéléré. Mêmes images que
 * {@link FilmVivres} : 1280 x 656, un bandeau de légende, 10 images par seconde, dans {@code frames-reseau}. Activé par
 * {@code -Doneforallsurvival.demo.reseau}.
 */
@Mod.EventBusSubscriber(modid = "oneforallsurvival", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FilmReseau {

    private static final Logger LOG = LogManager.getLogger("oneforallsurvival-film");
    private static final boolean ACTIF = System.getProperty("oneforallsurvival.demo.reseau") != null;
    private static final String MONDE = "film_reseau";
    private static final double GARDE_S = 480;
    private static final int H_TESTEUR = 0;
    private static final int H_SCELLE = 1;
    private static final int H_VIDE = 8;
    private static final String ABONNE = "Ferme Nord";
    private static final int RPM_PLEIN = 256;
    /** L'alternateur exige la vitesse moyenne de Create (30 tr/min) : en dessous, il ne produit rien. */
    private static final int RPM_REDUIT = 32;
    private static final String[] SORTIES = {"devant", "gauche", "droite", "dessus"};
    private static final String ALARME = "light_bulb_addon_create_crafts_and__addions:alarm_lamp";

    private enum Phase { TITRE, CREATION, ATTENTE_MONDE, PREPARATION, FILM, FIN }

    private record Etape(String nom, String legende, Runnable action, BooleanSupplier pret, long reposMs, Supplier<String> valeurs) {}

    /** Ce que le serveur sait, recopié à chaque tick pour le bandeau. */
    private record Instantane(String tableau, String disjoncteur, double temoin, String temoinEtat, double protegee,
            double parafoudre, String station, boolean lampe, String alarme) {
        static final Instantane VIDE = new Instantane("-", "-", 0, "-", 0, 0, "-", false, "-");
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
    private static String dernierMessage = "";
    private static volatile boolean preparationFaite;
    private static volatile Instantane instantane = Instantane.VIDE;
    private static int lettres;
    private static int clics;
    private static long dernierClic;

    // Scène du tableau (S : le tableau, face à l'est ; entrée à l'ouest).
    private static BlockPos tableau;
    private static BlockPos alternateur;
    private static BlockPos moteur;
    private static BlockPos compteur;
    private static BlockPos disjoncteur;
    private static BlockPos[] armoiresDuTableau;
    // Scène de la foudre : un groupe sans parafoudre, un groupe avec, à 40 blocs l'un de l'autre et du reste.
    private static BlockPos temoin;
    private static BlockPos eclairSans;
    private static BlockPos protegee;
    private static BlockPos parafoudre;
    private static BlockPos eclairAvec;
    // Scène de la météo.
    private static BlockPos station;
    private static BlockPos lampe;
    private static BlockPos alarme;

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

    private FilmReseau() {}

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
            dernierMessage = event.getMessage().getString();
            LOG.info("[film] message : {}", dernierMessage);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (!ACTIF || event.phase != TickEvent.Phase.END || !preparationFaite) {
            return;
        }
        ServerLevel monde = event.getServer().overworld();
        StringBuilder t = new StringBuilder();
        if (monde.getBlockEntity(tableau) instanceof LoadShedderBlockEntity s) {
            int[] ordre = s.priorities();
            for (int rang = 0; rang < ordre.length; rang++) {
                OutputStatus st = s.status(ordre[rang]);
                t.append(rang == 0 ? "" : " | ").append(rang + 1).append(" ").append(SORTIES[ordre[rang]]).append(" ")
                        .append(libelle(st)).append(" ").append(s.lastDelivered(ordre[rang])).append(" FE");
            }
        }
        BlockState d = monde.getBlockState(disjoncteur);
        String disj = d.is(GridRegistry.BREAKER.get())
                ? (d.getValue(BreakerBlock.CLOSED) ? "enclenché" : "coupé") + (d.getValue(BreakerBlock.SEALED) ? ", scellé" : ", sans scellé") : "-";
        BlockState st = monde.getBlockState(station);
        String meteo = st.is(GridRegistry.WEATHER_STATION.get()) ? st.getValue(WeatherStationBlock.TREND).name() + " / "
                + st.getValue(ElectricMachineBlock.STATE).name() : "-";
        BlockState lp = monde.getBlockState(lampe);
        instantane = new Instantane(t.toString(), disj, etatDe(monde, temoin), panneDe(monde, temoin), etatDe(monde, protegee),
                etatDe(monde, parafoudre), meteo, lp.is(Blocks.REDSTONE_LAMP) && lp.getValue(RedstoneLampBlock.LIT),
                "redstone " + propriete(monde.getBlockState(alarme), "powered") + ", allumée " + propriete(monde.getBlockState(alarme), "lit"));
    }

    private static String libelle(OutputStatus s) {
        return switch (s) {
            case FED -> "ALIMENTÉE";
            case PARTIAL -> "PARTIELLE";
            case CUT -> "COUPÉE";
            default -> "non branchée";
        };
    }

    private static double etatDe(ServerLevel monde, BlockPos p) {
        return monde.getBlockEntity(p) instanceof ElectricMachineBlockEntity m ? m.condition() : -1;
    }

    private static String panneDe(ServerLevel monde, BlockPos p) {
        return monde.getBlockEntity(p) instanceof ElectricMachineBlockEntity m && m.fault() != null ? "panne " + m.fault().name() : "sans panne";
    }

    private static Boolean propriete(BlockState s, String nom) {
        for (Property<?> p : s.getProperties()) {
            if (p.getName().equals(nom) && p instanceof BooleanProperty b) {
                return s.getValue(b);
            }
        }
        return null;
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
                    capture = new FilmCapture(mc.gameDirectory.toPath().resolve("frames-reseau"),
                            "One For All Survival | lot 4 | réseau");
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
        var o = mc.options;
        o.pauseOnLostFocus = false;
        o.onboardAccessibility = false;
        o.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
        o.renderDistance().set(8);
        o.simulationDistance().set(6);
        o.guiScale().set(2);
        o.enableVsync().set(false);
        o.framerateLimit().set(60);
        o.hideGui = false;
        mc.getToasts().clear();
        mc.mouseHandler.releaseMouse();
    }

    private static void creerLeMonde(Minecraft mc) {
        GameRules regles = new GameRules();
        regles.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        regles.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        regles.getRule(GameRules.RULE_ANNOUNCE_ADVANCEMENTS).set(false, null);
        // Le cycle de la météo reste actif : la station lit les minuteries de pluie et d'orage du monde.
        LevelSettings reglages = new LevelSettings(MONDE, GameType.SURVIVAL, false, Difficulty.PEACEFUL, true, regles,
                WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(MONDE, reglages, new WorldOptions(0L, false, false),
                acces -> acces.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value()
                        .createWorldDimensions());
    }

    // ================================================================================================
    // Préparation côté serveur
    // ================================================================================================

    private static Block bloc(String id) {
        Block b = BuiltInRegistries.BLOCK.get(new ResourceLocation(id));
        if (b == Blocks.AIR) {
            throw new IllegalStateException(id + " absent : lancer avec -PwithPack");
        }
        return b;
    }

    private static void moteurA(ServerLevel monde, int rpm) {
        monde.setBlock(moteur, Blocks.AIR.defaultBlockState(), 3);
        monde.setBlock(moteur, bloc("create:creative_motor").defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, Direction.EAST), 3);
        if (monde.getBlockEntity(moteur) != null) {
            CompoundTag tag = monde.getBlockEntity(moteur).saveWithoutMetadata();
            tag.putInt("ScrollValue", rpm);
            monde.getBlockEntity(moteur).load(tag);
        }
    }

    /** Une armoire alimentée qui travaille : un steak frais dedans, tournée vers l'ouest (la caméra). */
    private static void armoire(ServerLevel monde, BlockPos p) {
        monde.setBlock(p, FoodRegistry.FRIDGE.get().defaultBlockState().setValue(FridgeBlock.FACING, Direction.WEST), 3);
        ItemStack steak = new ItemStack(Items.COOKED_BEEF);
        FoodStamp.setStamp(steak, FoodDecay.roundDown(monde.getGameTime()));
        FridgeBlockEntity f = (FridgeBlockEntity) monde.getBlockEntity(p);
        f.inventory().setStackInSlot(0, steak);
        reserve(f, FridgeBlockEntity.CAPACITY);
    }

    private static void reserve(ElectricMachineBlockEntity m, int fe) {
        CompoundTag tag = m.saveWithoutMetadata();
        tag.putInt("energy", fe);
        m.load(tag);
    }

    private static void preparer(MinecraftServer serveur) {
        ServerLevel monde = serveur.overworld();
        ServerPlayer joueur = serveur.getPlayerList().getPlayers().get(0);
        BlockPos ici = joueur.blockPosition();
        int y = monde.getHeight(Heightmap.Types.MOTION_BLOCKING, ici.getX() + 6, ici.getZ());
        BlockPos s = new BlockPos(ici.getX() + 6, y, ici.getZ());
        monde.setDayTime(6000);
        monde.setWeatherParameters(1_000_000, 0, false, false);

        tableau = s;
        alternateur = s.west();
        moteur = s.west(2);
        compteur = s.east();
        disjoncteur = s.north();
        monde.setBlock(alternateur, bloc("createaddition:alternator").defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, Direction.WEST), 3);
        moteurA(monde, RPM_PLEIN);
        monde.setBlock(compteur, GridRegistry.METER.get().defaultBlockState().setValue(InlineEnergyBlock.FACING, Direction.EAST), 3);
        monde.setBlock(disjoncteur, GridRegistry.BREAKER.get().defaultBlockState().setValue(InlineEnergyBlock.FACING, Direction.NORTH), 3);
        // Un disjoncteur ne se scelle qu'inscrit.
        if (monde.getBlockEntity(disjoncteur) instanceof BreakerBlockEntity b) {
            b.setSubscriber(ABONNE);
        }
        armoiresDuTableau = new BlockPos[] {s.east(2), s.south(), s.north(2), s.above()};
        for (BlockPos p : armoiresDuTableau) {
            armoire(monde, p);
        }
        monde.setBlock(tableau, GridRegistry.LOAD_SHEDDER.get().defaultBlockState().setValue(LoadShedderBlock.FACING, Direction.EAST), 3);

        // Foudre : deux groupes à 40 blocs du tableau et l'un de l'autre (rayon de la foudre 16, du parafoudre 24).
        temoin = s.offset(2, 0, 40);
        eclairSans = s.offset(5, 0, 40);
        armoire(monde, temoin);
        protegee = s.offset(2, 0, 80);
        parafoudre = s.offset(2, 0, 82);
        eclairAvec = s.offset(5, 0, 80);
        armoire(monde, protegee);
        monde.setBlock(parafoudre, GridRegistry.SURGE_ARRESTER.get().defaultBlockState(), 3);
        // Les deux armoires de la foudre restent sans courant : elles ne s'usent pas, leur état ne bouge que par l'éclair.

        // Météo : station alimentée, lampe et alarme d'Advanced Electricity contre elle, à 40 blocs au nord.
        station = s.offset(2, 0, -40);
        lampe = station.south();
        alarme = station.north();
        Block creative = bloc("createaddition:creative_energy");
        monde.setBlock(station.east(), creative.defaultBlockState(), 3);
        monde.setBlock(station, GridRegistry.WEATHER_STATION.get().defaultBlockState()
                .setValue(WeatherStationBlock.FACING, Direction.WEST), 3);
        monde.setBlock(lampe, Blocks.REDSTONE_LAMP.defaultBlockState(), 3);
        monde.setBlock(alarme, bloc(ALARME).defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, Direction.UP), 3);
        monde.setBlock(alarme.east(), creative.defaultBlockState(), 3);

        joueur.getInventory().clearContent();
        joueur.getInventory().setItem(H_TESTEUR, new ItemStack(RepairRegistry.TESTER.get()));
        joueur.getInventory().setItem(H_SCELLE, new ItemStack(GridRegistry.SEAL.get(), 2));
        preparationFaite = true;
    }

    // ================================================================================================
    // Outils du scénario
    // ================================================================================================

    /** Place le joueur à l'œil {@code oeil}, le regard sur le centre de {@code cible}. */
    private static void regarder(Minecraft mc, BlockPos cible, Vec3 oeil) {
        MinecraftServer serveur = mc.getSingleplayerServer();
        Vec3 centre = Vec3.atCenterOf(cible);
        double dx = centre.x - oeil.x;
        double dy = centre.y - oeil.y;
        double dz = centre.z - oeil.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        serveur.execute(() -> serveur.getPlayerList().getPlayers().get(0)
                .teleportTo(serveur.overworld(), oeil.x, oeil.y - 1.62, oeil.z, yaw, pitch));
    }

    private static Vec3 depuis(BlockPos cible, double dx, double dy, double dz) {
        return Vec3.atCenterOf(cible).add(dx, dy, dz);
    }

    private static void clicDroit(Minecraft mc, BlockPos pos, int fente) {
        mc.player.getInventory().selected = fente;
        var res = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.WEST, pos, false));
        LOG.info("[film] clic droit en {} avec {} : {}", pos, mc.player.getMainHandItem(), res);
    }

    private static void etape(String nom, String legende, Runnable action, BooleanSupplier pret, long reposMs, Supplier<String> valeurs) {
        ETAPES.add(new Etape(nom, legende, action, pret, reposMs, valeurs));
    }

    private static void souris(Minecraft mc, double xGui, double yGui) {
        double echelle = mc.getWindow().getGuiScale();
        try {
            for (String nom : new String[] {"xpos", "ypos"}) {
                Field f = net.minecraft.client.MouseHandler.class.getDeclaredField(nom);
                f.setAccessible(true);
                f.setDouble(mc.mouseHandler, nom.equals("xpos") ? xGui * echelle : yGui * echelle);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean ecranTableau(Minecraft mc) {
        return mc.screen instanceof LoadShedderScreen;
    }

    /** Le curseur sur le bouton « monter » de la ligne de cette sortie. */
    private static double[] boutonMonter(Minecraft mc, int sortie) {
        LoadShedderScreen e = (LoadShedderScreen) mc.screen;
        int ligne = e.getMenu().rank(sortie);
        return new double[] {e.getGuiLeft() + 206, e.getGuiTop() + 46 + ligne * 18 + 6};
    }

    private static void grosPlanEcran(Minecraft mc) {
        double e = mc.getWindow().getGuiScale();
        capture.grosPlan(mc.getWindow().getGuiScaledWidth() / 2.0 * e, mc.getWindow().getGuiScaledHeight() / 2.0 * e, 1.9);
    }

    private static MinecraftServer serveur(Minecraft mc) {
        return mc.getSingleplayerServer();
    }

    private static String tableauValeurs() {
        return instantane.tableau();
    }

    private static String disjValeurs() {
        return "disjoncteur : " + instantane.disjoncteur() + " | scellés en main : " + Minecraft.getInstance().player.getInventory().getItem(H_SCELLE).getCount();
    }

    private static String foudreValeurs() {
        Instantane i = instantane;
        return String.format(Locale.FRANCE, "sans parafoudre : armoire %.0f %% (%s) | avec : armoire %.0f %%, parafoudre %.0f %%",
                i.temoin(), i.temoinEtat(), i.protegee(), i.parafoudre());
    }

    private static String meteoValeurs() {
        Instantane i = instantane;
        Minecraft mc = Minecraft.getInstance();
        return "station " + i.station() + " | lampe " + (i.lampe() ? "ALLUMÉE" : "éteinte") + " | alarme " + i.alarme()
                + " | pluie " + mc.level.isRaining() + ", orage " + mc.level.isThundering();
    }

    private static void eclair(Minecraft mc, BlockPos ou) {
        MinecraftServer s = serveur(mc);
        s.execute(() -> {
            LightningBolt b = EntityType.LIGHTNING_BOLT.create(s.overworld());
            b.moveTo(Vec3.atBottomCenterOf(ou));
            s.overworld().addFreshEntity(b);
        });
    }

    // ================================================================================================
    // Scénario
    // ================================================================================================

    private static void construireScenario(Minecraft mc) {
        Vec3 vueTableau = depuis(tableau, -3.2, 1.6, -3.4);
        etape("cadrage", "", () -> regarder(mc, tableau, vueTableau), () -> true, 2500, () -> "");

        // --- Scène 1 : la pénurie
        etape("vue", "Un alternateur C&A (moteur créatif, " + RPM_PLEIN + " tr/min) alimente le tableau de délestage", () -> {
            mc.player.getInventory().selected = H_VIDE;
        }, () -> true, 3000, FilmReseau::tableauValeurs);
        etape("sorties", "Quatre armoires sur ses sorties : devant (par un compteur), gauche, droite (par un disjoncteur), dessus",
                () -> {}, () -> true, 3500, FilmReseau::tableauValeurs);
        etape("ouvre", "Clic droit : l'écran du tableau, une ligne par sortie dans l'ordre de service", () -> clicDroit(mc, tableau, H_VIDE),
                () -> ecranTableau(mc) && instantane.tableau().chars().filter(c -> c == 'É').count() >= 0, 500, FilmReseau::tableauValeurs);
        etape("plein", "Courant plein : les quatre sorties sont alimentées", () -> grosPlanEcran(mc),
                () -> !instantane.tableau().contains("COUPÉE") && !instantane.tableau().contains("PARTIELLE")
                        && instantane.tableau().split("ALIMENTÉE", -1).length == 5, 3500, FilmReseau::tableauValeurs);
        etape("baisse", "Source réduite (" + RPM_REDUIT + " tr/min, réserves vidées en dev) : les derniers rangs sont coupés", () -> {
            serveur(mc).execute(() -> {
                ServerLevel monde = serveur(mc).overworld();
                // L'alternateur reposé neuf : sa réserve interne pleine couvrirait sinon la demande.
                monde.setBlock(moteur, Blocks.AIR.defaultBlockState(), 3);
                monde.setBlock(alternateur, Blocks.AIR.defaultBlockState(), 3);
                monde.setBlock(alternateur, bloc("createaddition:alternator").defaultBlockState()
                        .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, Direction.WEST), 3);
                moteurA(monde, RPM_REDUIT);
                for (BlockPos p : armoiresDuTableau) {
                    reserve((ElectricMachineBlockEntity) monde.getBlockEntity(p), 0);
                }
            });
        }, () -> instantane.tableau().contains("4 dessus COUPÉE"), 2500, FilmReseau::tableauValeurs);
        etape("rang_survol", "On remonte la sortie du dessus (réserves revidées, dev) : bouton « monter »", () -> {
            // La pénurie ne dure que le temps de remplir les réserves (2 000 FE à 45 FE par tick, quelques secondes) :
            // elles sont vidées de nouveau pour que le réglage du rang se voie pendant la pénurie.
            serveur(mc).execute(() -> {
                for (BlockPos p : armoiresDuTableau) {
                    reserve((ElectricMachineBlockEntity) serveur(mc).overworld().getBlockEntity(p), 0);
                }
            });
            double[] b = boutonMonter(mc, LoadShedderBlockEntity.OUTPUT_TOP);
            souris(mc, b[0], b[1]);
            clics = 0;
            dernierClic = System.nanoTime();
        }, () -> true, 800, FilmReseau::tableauValeurs);
        etape("rang_clics", "Trois clics : le dessus passe au rang 1, la droite descend au rang 4", () -> {}, () -> {
            LoadShedderScreen e = (LoadShedderScreen) mc.screen;
            int rang = e.getMenu().rank(LoadShedderBlockEntity.OUTPUT_TOP);
            if (rang > 0 && System.nanoTime() - dernierClic > 600_000_000L) {
                double[] b = boutonMonter(mc, LoadShedderBlockEntity.OUTPUT_TOP);
                souris(mc, b[0], b[1]);
                e.mouseClicked(b[0], b[1], 0);
                clics++;
                dernierClic = System.nanoTime();
            }
            return rang == 0 && instantane.tableau().startsWith("1 dessus") && !instantane.tableau().startsWith("1 dessus COUPÉE")
                    && instantane.tableau().contains("4 droite COUPÉE");
        }, 3500, FilmReseau::tableauValeurs);
        etape("ferme1", "", () -> { capture.pleinCadre(); mc.player.closeContainer(); }, () -> mc.screen == null, 300,
                FilmReseau::tableauValeurs);

        // --- Scène 2 : le compteur, l'abonné et le relevé
        etape("compteur_vue", "Le compteur, en ligne sur la sortie de devant", () -> regarder(mc, compteur, depuis(compteur, -1.2, 1.4, -2.6)),
                () -> true, 2500, () -> "abonné : aucun");
        etape("compteur_ouvre", "Clic droit main vide de l'ingénieur : l'écran d'inscription", () -> clicDroit(mc, compteur, H_VIDE),
                () -> mc.screen instanceof SubscriberNameScreen, 1000, () -> "abonné : aucun");
        etape("compteur_nom", "Il tape le nom de l'abonné", () -> {
            lettres = 0;
            dernierClic = System.nanoTime();
            grosPlanEcran(mc);
        }, () -> {
            if (lettres < ABONNE.length() && System.nanoTime() - dernierClic > 150_000_000L) {
                mc.screen.charTyped(ABONNE.charAt(lettres++), 0);
                dernierClic = System.nanoTime();
            }
            return lettres == ABONNE.length();
        }, 1500, () -> "saisie : " + ABONNE.substring(0, lettres));
        etape("compteur_inscrit", "Entrée : le serveur inscrit l'abonné", () -> mc.screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0),
                () -> mc.screen == null && dernierMessage.contains(ABONNE), 2500, () -> "message : " + dernierMessage);
        etape("releve", "Le testeur sur le compteur : le relevé", () -> {
            capture.pleinCadre();
            clicDroit(mc, compteur, H_TESTEUR);
        }, () -> mc.player.getInventory().countItem(GridRegistry.METER_READING.get()) >= 1, 2500, () -> "message : " + dernierMessage);
        etape("releve_inventaire", "Le relevé dans l'inventaire", () -> mc.setScreen(new InventoryScreen(mc.player)),
                () -> mc.screen instanceof InventoryScreen, 800, () -> "message : " + dernierMessage);
        etape("releve_infobulle", "Son infobulle : abonné, index, consommation, jour de jeu, date réelle, anomalies", () -> {
            AbstractContainerScreen<?> s = (AbstractContainerScreen<?>) mc.screen;
            for (Slot sl : s.getMenu().slots) {
                if (sl.getItem().is(GridRegistry.METER_READING.get())) {
                    souris(mc, s.getGuiLeft() + sl.x + 8, s.getGuiTop() + sl.y + 8);
                    double e = mc.getWindow().getGuiScale();
                    capture.grosPlan((s.getGuiLeft() + sl.x + 8) * e + 160, (s.getGuiTop() + sl.y + 8) * e + 20, 2.2);
                    return;
                }
            }
            throw new IllegalStateException("releve absent de l'inventaire");
        }, () -> true, 5000, () -> "relevé de " + ABONNE);
        etape("ferme2", "", () -> { capture.pleinCadre(); mc.player.closeContainer(); }, () -> mc.screen == null, 300, () -> "");

        // --- Scène 3 : le disjoncteur et le scellé
        Vec3 vueDisj = depuis(disjoncteur, -1.6, 0.9, -1.6);
        etape("disj_vue", "Le disjoncteur de la sortie de droite : enclenché", () -> regarder(mc, disjoncteur, vueDisj),
                () -> true, 2500, FilmReseau::disjValeurs);
        etape("disj_coupe", "Clic droit main vide : coupé", () -> clicDroit(mc, disjoncteur, H_VIDE),
                () -> instantane.disjoncteur().startsWith("coupé"), 2500, FilmReseau::disjValeurs);
        etape("disj_scelle", "L'ingénieur pose un scellé sur le disjoncteur coupé", () -> clicDroit(mc, disjoncteur, H_SCELLE),
                () -> instantane.disjoncteur().endsWith(", scellé"), 3500, FilmReseau::disjValeurs);
        etape("disj_refus", "Un clic pour le réenclencher : refusé, il reste coupé", () -> clicDroit(mc, disjoncteur, H_VIDE),
                () -> dernierMessage.contains("scellé") && instantane.disjoncteur().startsWith("coupé"), 3500, FilmReseau::disjValeurs);

        // --- Scène 4 : la foudre, sans puis avec parafoudre
        etape("foudre_sans_vue", "Une armoire (sans courant : la foudre frappe quand même), sans parafoudre à portée", () -> {
            mc.player.getInventory().selected = H_VIDE;
            regarder(mc, temoin, depuis(temoin, -3.0, 2.0, -5.0));
        }, () -> true, 2500, FilmReseau::foudreValeurs);
        etape("foudre_sans", "Un vrai éclair tombe à 3 blocs : l'armoire est touchée", () -> eclair(mc, eclairSans),
                () -> instantane.temoin() < 100, 2500, FilmReseau::foudreValeurs);
        etape("foudre_sans_testeur", "Le testeur le confirme", () -> {
            regarder(mc, temoin, depuis(temoin, -2.0, 1.2, -1.0));
        }, () -> true, 500, FilmReseau::foudreValeurs);
        etape("foudre_sans_lecture", "Le testeur le confirme", () -> clicDroit(mc, temoin, H_TESTEUR), () -> true, 3500,
                FilmReseau::foudreValeurs);
        etape("foudre_avec_vue", "La même scène, avec un parafoudre à 2 blocs de l'armoire", () -> {
            mc.player.getInventory().selected = H_VIDE;
            regarder(mc, protegee, depuis(protegee, -3.0, 2.0, -4.0));
        }, () -> true, 2500, FilmReseau::foudreValeurs);
        etape("foudre_avec", "Un vrai éclair : l'armoire n'a rien, le parafoudre encaisse le coup", () -> eclair(mc, eclairAvec),
                () -> instantane.parafoudre() < 100, 3000, FilmReseau::foudreValeurs);
        etape("foudre_avec_testeur", "Le parafoudre au testeur : -10 points d'un coup", () -> {
            regarder(mc, parafoudre, depuis(parafoudre, -2.0, 1.2, -1.0));
        }, () -> true, 500, FilmReseau::foudreValeurs);
        etape("foudre_avec_lecture", "Le parafoudre au testeur : -10 points d'un coup", () -> clicDroit(mc, parafoudre, H_TESTEUR),
                () -> true, 3500, FilmReseau::foudreValeurs);

        // --- Scène 5 : la station météo
        etape("meteo_vue", "La station météo (alimentée), une lampe et une alarme d'Advanced Electricity contre elle", () -> {
            mc.player.getInventory().selected = H_VIDE;
            regarder(mc, station, depuis(station, -3.6, 1.4, 0.4));
        }, () -> instantane.station().startsWith("STABLE / RUNNING"), 3500, FilmReseau::meteoValeurs);
        etape("meteo_orage", "Commande réelle : /weather thunder. La station voit l'orage, la lampe et l'alarme s'allument", () -> {
            mc.player.connection.sendCommand("weather thunder");
        }, () -> instantane.station().startsWith("STORM_SOON") && instantane.lampe(), 5000, FilmReseau::meteoValeurs);
        etape("fin", "Fin du lot 4 : délestage, compteur, disjoncteur, parafoudre, station météo (temps réel, jamais accéléré)",
                () -> {}, () -> true, 2500, FilmReseau::meteoValeurs);
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
                throw new IllegalStateException("étape sans issue : " + e.nom() + " | " + instantane + " | message " + dernierMessage);
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
