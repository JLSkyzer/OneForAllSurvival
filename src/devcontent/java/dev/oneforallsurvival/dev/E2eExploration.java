package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.explore.DetectorItem;
import dev.oneforallsurvival.explore.DetectorRules;
import dev.oneforallsurvival.explore.ExploreRegistry;
import dev.oneforallsurvival.explore.HeadlampItem;
import dev.oneforallsurvival.explore.MechanismBlock;
import dev.oneforallsurvival.explore.ReceiverBlock;
import dev.oneforallsurvival.explore.ReceiverMenu;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Étape « exploration » de {@link E2eClient} (développement seulement, jamais empaquetée) : le lot 7 vu par un vrai client
 * contre un serveur dédié, avec le pack complet.
 *
 * <p>Un joueur qui n'est <b>pas opérateur</b> (console RCON : {@code deop}) fait toute la séquence : il recharge le
 * détecteur et la lampe sur une source créative, cherche une cache (les bips, relevés par l'événement de son du client,
 * s'accélèrent), la casse à la main et ramasse son butin, porte la lampe dans une grotte close (niveau de lumière lu à sa
 * tête, puis disparu quand il la retire), reçoit un message de l'équipe sur le seul récepteur alimenté et réglé sur la
 * bonne fréquence, câble un mécanisme ancien avec un vrai câble de Create Crafts & Additions (deux connecteurs, une
 * bobine de cuivre) qui s'active et ouvre une porte de fer, et ne peut pas le casser. Tout ce qui change le monde passe
 * par la console RCON (le joueur sans droits ne peut pas envoyer de commandes) ; les gestes du joueur sont de vrais
 * clics et de vrais coups.
 *
 * <p>Avant l'arrêt, {@link #armerLaLampe} fait porter la lampe : au {@code /stop} une lumière est posée. À la relance, la
 * lampe est retirée en premier ({@link #relanceLampe}), puis l'étape relit l'archive du récepteur, le mécanisme (toujours
 * actif, la porte ouverte) et vérifie qu'aucun bloc de lumière n'est resté autour des deux derniers points de la lampe.
 *
 * <p>L'aire est une dalle à 80 blocs à l'est et 60 au nord du joueur, en hauteur.
 */
final class E2eExploration {

    private static final Logger LOG = LogManager.getLogger("oneforallsurvival-e2e");
    private static final String FICHIER = "e2e-exploration.txt";
    private static final double DELAI = 25;
    private static final int ECHECS_MAX = 8;
    private static final int Y_DALLE = 150;
    private static final String CREATIVE = "createaddition:creative_energy";
    private static final String CONNECTEUR = "createaddition:connector";
    private static final String BOBINE = "createaddition:copper_spool";
    private static final String TEXTE = "Essai de reception depuis la base";
    private static final String TEXTE_SEPT = "Second essai sur la frequence sept";
    private static final Pattern NOMBRE = Pattern.compile("data: (-?[0-9]+(?:[.][0-9]+)?)");
    private static final Pattern ENTIER = Pattern.compile("(\\d+)");
    private static final double PORTEE = 24;

    private record Pas(String legende, Runnable action, BooleanSupplier pret, Supplier<String> echec, Supplier<String> succes,
            double delai, boolean critique) {}

    private static final List<Pas> PAS = new ArrayList<>();
    private static int index;
    private static boolean actionFaite;
    private static long debut;
    private static int echecsDuPas;

    private static Minecraft mc;
    private static BlockPos depart;
    private static BlockPos o;

    /** Les bips reçus par le client : heure d'arrivée (ns) et hauteur. */
    private static final List<double[]> BIPS = new ArrayList<>();
    private static final java.util.Set<String> SONS_VUS = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final List<Double> MOYENNES = new ArrayList<>();
    private static final List<Double> HAUTEURS = new ArrayList<>();
    private static int butin;
    private static BlockPos lampeCave;
    private static BlockPos lampeArret;
    private static boolean lampeArmee;
    private static long debutLampeArret;
    private static int progressVu = -1;
    /** Vrai quand la dalle est posée et le joueur dessus : dès lors, le joueur doit rester en vie, sur le sol. */
    private static boolean airePrete;
    private static boolean balaye;
    private static boolean lampeRetireeAuDemarrage;
    private static long debutLampeRelance;

    private E2eExploration() {}

    static String legende() {
        return index < PAS.size() ? PAS.get(index).legende() : "Fin de l'étape";
    }

    // ================================================================================================
    // Disposition (relative à l'origine O : le coin ouest de la source de recharge, au niveau du sol)
    // ================================================================================================

    private static BlockPos p(int dx, int dy, int dz) {
        return o.offset(dx, dy, dz);
    }

    private static BlockPos sourceRecharge() {
        return p(0, 0, 0);
    }

    private static BlockPos cache() {
        return p(46, 0, 0);
    }

    private static BlockPos recepteur(int i) {
        return p(8 + 4 * i, 0, -8);
    }

    private static BlockPos sourceRecepteur(int i) {
        return recepteur(i).west();
    }

    private static BlockPos sourceMecanisme() {
        return p(24, 0, -8);
    }

    private static BlockPos mecanisme() {
        return p(30, 0, -8);
    }

    private static BlockPos connecteurSource() {
        return sourceMecanisme().east();
    }

    private static BlockPos connecteurMecanisme() {
        return mecanisme().west();
    }

    private static BlockPos porteBas() {
        return mecanisme().south();
    }

    private static BlockPos grotteCentre() {
        return p(53, 0, 11);
    }

    // ================================================================================================
    // Déroulement
    // ================================================================================================

    /** Avance d'un pas ; vrai quand la séquence est finie. */
    static boolean avancer(Minecraft minecraft) {
        mc = minecraft;
        if (index >= PAS.size()) {
            return true;
        }
        Pas pas = PAS.get(index);
        if (airePrete && (mc.player.isDeadOrDying() || mc.player.getY() < o.getY() - 3)) {
            E2eClient.fail("le joueur est mort ou tombe dans le vide : vie " + mc.player.getHealth() + ", position " + mc.player.position()
                    + ", pas " + pas.legende());
            return false;
        }
        if (!actionFaite) {
            LOG.info("[e2e] pas : {}", pas.legende());
            pas.action().run();
            actionFaite = true;
            debut = System.nanoTime();
            return false;
        }
        boolean pret;
        try {
            pret = pas.pret().getAsBoolean();
        } catch (RuntimeException e) {
            pret = false;
            if (ecoule() > pas.delai()) {
                LOG.error("[e2e] exception en attendant : {}", pas.legende(), e);
            }
        }
        if (pret) {
            if (pas.succes() != null) {
                String texte;
                try {
                    texte = pas.succes().get();
                } catch (RuntimeException e) {
                    E2eClient.fail(pas.legende() + " : " + e.getMessage());
                    return false;
                }
                E2eClient.pass(texte);
            }
            index++;
            actionFaite = false;
        } else if (ecoule() > pas.delai()) {
            String detail = pas.legende() + " : " + pas.echec().get();
            if (pas.critique()) {
                E2eClient.fail(detail);
            } else {
                E2eClient.failSoft(detail);
                echecsDuPas++;
                index++;
                actionFaite = false;
                if (echecsDuPas >= ECHECS_MAX) {
                    E2eClient.fail("trop d'echecs dans l'etape de l'exploration : arret");
                }
            }
        }
        return false;
    }

    private static double ecoule() {
        return (System.nanoTime() - debut) / 1e9;
    }

    private static void pasC(String legende, Runnable action, BooleanSupplier pret, Supplier<String> echec, Supplier<String> succes) {
        PAS.add(new Pas(legende, action, pret, echec, succes, DELAI, true));
    }

    private static void pasCD(double delai, String legende, Runnable action, BooleanSupplier pret, Supplier<String> echec,
            Supplier<String> succes) {
        PAS.add(new Pas(legende, action, pret, echec, succes, delai, true));
    }

    private static String nom() {
        return mc.player.getGameProfile().getName();
    }

    private static String xyz(BlockPos p) {
        return p.getX() + " " + p.getY() + " " + p.getZ();
    }

    private static boolean operateur() {
        return mc.player.hasPermissions(2);
    }

    private static String rcon(String commandeConsole) {
        return E2eReseau.rcon(commandeConsole);
    }

    private static Path fichier() {
        return mc.gameDirectory.toPath().resolve(FICHIER);
    }

    private static void placerJoueur(double x, double z, float yaw, float pitch) {
        rcon("tp " + nom() + " " + x + " " + o.getY() + " " + z + " " + yaw + " " + pitch);
    }

    private static boolean pres(double x, double z) {
        return Math.abs(mc.player.getX() - x) < 1.0 && Math.abs(mc.player.getZ() - z) < 1.0 && Math.abs(mc.player.getY() - o.getY()) < 2.5;
    }

    private static Item objet(String id) {
        return BuiltInRegistries.ITEM.get(new ResourceLocation(id));
    }

    private static int fente(Item item) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).is(item)) {
                return i;
            }
        }
        // Dans le sac : un échange avec une case de la barre qui ne porte ni détecteur ni lampe (rien ne se perd).
        for (int i = 9; i < 36; i++) {
            if (mc.player.getInventory().getItem(i).is(item)) {
                int cible = 8;
                for (int h = 8; h >= 0; h--) {
                    ItemStack st = mc.player.getInventory().getItem(h);
                    if (st.isEmpty()) {
                        cible = h;
                        break;
                    }
                    if (!st.is(ExploreRegistry.METAL_DETECTOR.get()) && !st.is(ExploreRegistry.HEADLAMP.get()) && cible == 8) {
                        cible = h;
                    }
                }
                mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, i, cible, ClickType.SWAP, mc.player);
                return mc.player.getInventory().getItem(cible).is(item) ? cible : -1;
            }
        }
        return -1;
    }

    private static ItemStack pile(Item item) {
        int f = fente(item);
        return f < 0 ? ItemStack.EMPTY : mc.player.getInventory().getItem(f);
    }

    private static void tenir(Item item) {
        int f = fente(item);
        if (f < 0) {
            throw new IllegalStateException("objet absent de la barre d'action : " + item);
        }
        mc.player.getInventory().selected = f;
    }

    private static void tenirVide() {
        for (int i = 8; i >= 0; i--) {
            if (mc.player.getInventory().getItem(i).isEmpty()) {
                mc.player.getInventory().selected = i;
                return;
            }
        }
        mc.player.getInventory().selected = 8;
    }

    private static void clic(BlockPos pos, Direction face) {
        Vec3 point = Vec3.atCenterOf(pos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        var res = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(point, face, pos, false));
        LOG.info("[e2e] clic droit en {} face {}, main {} : {}", pos, face, mc.player.getMainHandItem(), res);
    }

    private static int entier(BlockPos pos, String chemin) {
        Matcher m = NOMBRE.matcher(rcon("data get block " + xyz(pos) + " " + chemin));
        return m.find() ? (int) Double.parseDouble(m.group(1)) : -1;
    }

    private static int lumiereTete() {
        return mc.level.getBrightness(LightLayer.BLOCK, BlockPos.containing(mc.player.getEyePosition()));
    }

    private static int lumieres(BlockPos centre, int rayon) {
        int n = 0;
        for (int dx = -rayon; dx <= rayon; dx++) {
            for (int dy = -rayon; dy <= rayon; dy++) {
                for (int dz = -rayon; dz <= rayon; dz++) {
                    if (mc.level.getBlockState(centre.offset(dx, dy, dz)).is(Blocks.LIGHT)) {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    private static Slot fenteLampe() {
        for (Slot s : mc.player.inventoryMenu.slots) {
            if (s.getItem().is(ExploreRegistry.HEADLAMP.get())) {
                return s;
            }
        }
        return null;
    }

    /** Un déplacement rapide de la pile de la lampe (vers la tête, ou, si elle y est, vers l'inventaire). */
    private static void deplacerLaLampe() {
        Slot s = fenteLampe();
        if (s == null) {
            throw new IllegalStateException("aucune lampe dans l'inventaire du joueur");
        }
        mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, s.index, 0, ClickType.QUICK_MOVE, mc.player);
    }

    private static boolean lampePortee() {
        return mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(ExploreRegistry.HEADLAMP.get());
    }

    /** Appelé par {@link E2eClient} à chaque son joué par le client. */
    static void sonRecu(PlaySoundEvent event) {
        var son = event.getOriginalSound();
        if (son != null && SONS_VUS.add(son.getLocation().toString())) {
            LOG.info("[e2e] son vu pour la premiere fois : {}", son.getLocation());
        }
        if (son != null && son.getLocation().getPath().equals("block.note_block.bit")) {
            synchronized (BIPS) {
                BIPS.add(new double[] {System.nanoTime(), hauteurDe(son)});
            }
        }
    }

    /**
     * La hauteur demandée au son. {@code getPitch()} lit la hauteur du son résolu, qui n'existe pas encore quand
     * {@link PlaySoundEvent} est publié (NullPointerException) : on lit le champ de l'instance.
     */
    static float hauteurDe(net.minecraft.client.resources.sounds.SoundInstance son) {
        try {
            java.lang.reflect.Field champ = net.minecraft.client.resources.sounds.AbstractSoundInstance.class.getDeclaredField("pitch");
            champ.setAccessible(true);
            return champ.getFloat(son);
        } catch (ReflectiveOperationException | ClassCastException e) {
            return -1F;
        }
    }

    private static List<double[]> bips() {
        synchronized (BIPS) {
            return new ArrayList<>(BIPS);
        }
    }

    private static void viderLesBips() {
        synchronized (BIPS) {
            BIPS.clear();
        }
    }

    /**
     * Intervalle moyen entre le deuxième bip et le dernier, en ticks de 50 ms : la moyenne sur toute la durée, donc sans
     * souci d'un paquet qui arrive avec le suivant (le client traite les paquets une fois par tick).
     */
    static double intervalleMoyen(List<double[]> b) {
        int n = b.size();
        return (b.get(n - 1)[0] - b.get(1)[0]) / 50e6 / (n - 2);
    }

    private static int butinTotal() {
        int n = 0;
        for (ItemStack s : mc.player.getInventory().items) {
            if (!s.isEmpty() && !s.is(ExploreRegistry.METAL_DETECTOR.get()) && !s.is(ExploreRegistry.HEADLAMP.get())) {
                n += s.getCount();
            }
        }
        return n;
    }

    // ================================================================================================
    // Première phase
    // ================================================================================================

    static void premierePhase() {
        preparation();
        recharge();
        detecteur();
        casseDeLaCache();
        lampe();
        recepteurs();
        etapeMecanisme();
        persistance();
    }

    private static void preparation() {
        pasC("Le joueur, en spectateur, se rend au centre de l'aire de l'exploration (les chunks se chargent)", () -> {
            if (mc.player.containerMenu != mc.player.inventoryMenu) {
                mc.player.closeContainer();
            }
            mc.setScreen(null);
            depart = mc.player.blockPosition();
            BlockPos spawn = mc.level.getSharedSpawnPos();
            int x = depart.getX() + 80;
            int z = depart.getZ() - 60;
            if (Math.abs(x - spawn.getX()) <= 40 && Math.abs(z - spawn.getZ()) <= 40) {
                x = depart.getX() - 160;
            }
            o = new BlockPos(x, Y_DALLE, z);
            // Spectateur : sans pesanteur, le joueur ne tombe pas dans le vide en attendant la dalle.
            rcon("gamemode spectator " + nom());
            rcon("tp " + nom() + " " + (o.getX() + 30.5) + " " + (o.getY() + 5) + " " + (o.getZ() + 6.5));
        }, () -> ecoule() >= 4 && mc.level.getChunkSource().hasChunk((o.getX() + 30) >> 4, (o.getZ() + 6) >> 4)
                && mc.level.getChunkSource().hasChunk((o.getX() + 60) >> 4, (o.getZ() + 20) >> 4)
                && mc.level.getChunkSource().hasChunk((o.getX() - 3) >> 4, (o.getZ() - 12) >> 4),
                () -> "chunks de l'aire non recus, joueur en " + mc.player.blockPosition(), null);
        pasC("Aire de l'exploration sur une dalle, grotte close, joueur sans droits (console RCON : deop)", () -> {
            rcon("difficulty peaceful");
            rcon("gamerule doMobSpawning false");
            rcon("fill " + xyz(p(-4, 0, -12)) + " " + xyz(p(62, 9, 24)) + " air");
            rcon("fill " + xyz(p(-4, -1, -12)) + " " + xyz(p(62, -1, 24)) + " stone");
            // La grotte : sept blocs de large et de long, quatre de haut, close de pierre de toutes parts, rien qui éclaire.
            rcon("fill " + xyz(p(49, -1, 7)) + " " + xyz(p(57, 4, 15)) + " stone");
            rcon("fill " + xyz(p(50, 0, 8)) + " " + xyz(p(56, 3, 14)) + " air");
            // Pas de /clear général : les étapes des lots 2 à 5 gardent des objets dans l'inventaire jusqu'à la relance.
            rcon("effect clear " + nom());
            rcon("tp " + nom() + " " + (o.getX() + 2.5) + " " + o.getY() + " " + (o.getZ() + 0.5) + " 90 0");
            rcon("gamemode survival " + nom());
            rcon("deop " + nom());
        }, () -> mc.level.getBlockState(o.below()).is(Blocks.STONE) && !operateur() && mc.player.getHealth() > 0 && pres(o.getX() + 2.5, o.getZ() + 0.5) && ecoule() >= 2,
                () -> "operateur " + operateur() + ", joueur en " + mc.player.blockPosition() + ", dalle " + mc.level.getBlockState(o.below()),
                () -> {
                    airePrete = true;
                    return "dalle en " + o.toShortString() + " (joueur parti de " + depart.toShortString() + "), deop par RCON : hasPermissions(2) cote client = "
                        + operateur() + ", gamemode " + mc.gameMode.getPlayerMode() + ", vie " + mc.player.getHealth();
                });
    }

    private static void recharge() {
        pasC("Une source créative de Create Crafts & Additions, le détecteur et la lampe donnés (console)", () -> {
            rcon("setblock " + xyz(sourceRecharge()) + " " + CREATIVE);
            rcon("give " + nom() + " oneforallsurvival:metal_detector");
            rcon("give " + nom() + " oneforallsurvival:headlamp");
        }, () -> mc.level.getBlockState(sourceRecharge()).getBlock() == bloc(CREATIVE) && fente(ExploreRegistry.METAL_DETECTOR.get()) >= 0
                && fente(ExploreRegistry.HEADLAMP.get()) >= 0 && ecoule() >= 1.5,
                () -> "source " + mc.level.getBlockState(sourceRecharge()) + ", detecteur " + fente(ExploreRegistry.METAL_DETECTOR.get()) + ", lampe "
                        + fente(ExploreRegistry.HEADLAMP.get()),
                () -> "source creative posee, detecteur et lampe dans la barre d'action, vides : " + DetectorItem.stored(pile(ExploreRegistry.METAL_DETECTOR.get()))
                        + " et " + HeadlampItem.stored(pile(ExploreRegistry.HEADLAMP.get())) + " FE");
        pasC("Clic droit du détecteur sur la source : il se recharge", () -> {
            tenir(ExploreRegistry.METAL_DETECTOR.get());
            clic(sourceRecharge(), Direction.EAST);
        }, () -> DetectorItem.stored(pile(ExploreRegistry.METAL_DETECTOR.get())) > 0,
                () -> "charge " + DetectorItem.stored(pile(ExploreRegistry.METAL_DETECTOR.get())),
                () -> "detecteur recharge par un vrai clic droit sur une source d'un autre mod : " + DetectorItem.stored(pile(ExploreRegistry.METAL_DETECTOR.get()))
                        + " FE (lue sur la pile du client)");
        pasC("Clic droit de la lampe sur la source : elle se recharge", () -> {
            tenir(ExploreRegistry.HEADLAMP.get());
            clic(sourceRecharge(), Direction.EAST);
        }, () -> HeadlampItem.stored(pile(ExploreRegistry.HEADLAMP.get())) > 0,
                () -> "charge " + HeadlampItem.stored(pile(ExploreRegistry.HEADLAMP.get())),
                () -> "lampe rechargee par un vrai clic droit sur la meme source : " + HeadlampItem.stored(pile(ExploreRegistry.HEADLAMP.get())) + " FE");
    }

    private static net.minecraft.world.level.block.Block bloc(String id) {
        return BuiltInRegistries.BLOCK.get(new ResourceLocation(id));
    }

    // ---------------------------------------------------------------------------------------------- détecteur

    private static void detecteur() {
        pasC("Une cache posée par l'équipe (console), inscrite à l'index", () -> {
            rcon("setblock " + xyz(cache()) + " oneforallsurvival:buried_cache");
        }, () -> mc.level.getBlockState(cache()).is(ExploreRegistry.CACHE.get()) && ecoule() >= 1.5
                && rcon("cache list").contains(cache().toShortString()),
                () -> "bloc " + mc.level.getBlockState(cache()), () -> "cache en " + cache().toShortString() + ", presente dans /cache list");
        double[] distances = {40, 20, 12, 6, 1.6};
        MOYENNES.clear();
        HAUTEURS.clear();
        for (double dx : distances) {
            boolean silence = dx > PORTEE;
            pasCD(silence ? 12 : 40, "Le détecteur en main à " + (int) Math.round(dx) + " blocs de la cache", () -> {
                viderLesBips();
                placerJoueur(cache().getX() + 0.5 - dx, cache().getZ() + 0.5, -90, 0);
                tenir(ExploreRegistry.METAL_DETECTOR.get());
            }, () -> {
                if (!pres(cache().getX() + 0.5 - dx, cache().getZ() + 0.5) || ecoule() < 1.0) {
                    return false;
                }
                return silence ? ecoule() >= 6 : bips().size() >= 7;
            }, () -> "bips " + bips().size() + " apres " + String.format("%.1f", ecoule()) + " s",
                    () -> {
                        double distance = mc.player.position().distanceTo(Vec3.atCenterOf(cache()));
                        if (silence) {
                            if (!bips().isEmpty()) {
                                throw new IllegalStateException(bips().size() + " bip(s) a " + String.format("%.1f", distance) + " blocs, au-dela de la portee " + PORTEE);
                            }
                            return "a " + String.format("%.1f", distance) + " blocs (portee " + (int) PORTEE + ") : aucun bip en 6 s";
                        }
                        List<double[]> b = bips();
                        double moyen = intervalleMoyen(b);
                        int attendu = DetectorRules.beepInterval(distance, PORTEE);
                        double hauteur = b.get(b.size() - 1)[1];
                        StringBuilder sb = new StringBuilder();
                        for (int i = 1; i < b.size(); i++) {
                            sb.append(Math.round((b.get(i)[0] - b.get(i - 1)[0]) / 50e6)).append(' ');
                        }
                        if (Math.abs(moyen - attendu) > Math.max(3.0, 0.15 * attendu)) {
                            throw new IllegalStateException("intervalle moyen " + String.format("%.1f", moyen) + " ticks pour " + attendu + " attendus a "
                                    + String.format("%.1f", distance) + " blocs (" + sb + ")");
                        }
                        MOYENNES.add(moyen);
                        HAUTEURS.add(hauteur);
                        return "bips a " + String.format("%.1f", distance) + " blocs : intervalles releves dans le journal du client [" + sb.toString().trim()
                                + "] ticks, moyenne du 2e au dernier bip " + String.format("%.1f", moyen) + " (attendu " + attendu + "), hauteur "
                                + String.format("%.2f", hauteur);
                    });
        }
        pasC("Les bips s'accélèrent et montent en hauteur à mesure qu'on approche", () -> {}, () -> true, () -> "",
                () -> {
                    for (int i = 1; i < MOYENNES.size(); i++) {
                        if (MOYENNES.get(i) >= MOYENNES.get(i - 1) || HAUTEURS.get(i) <= HAUTEURS.get(i - 1)) {
                            throw new IllegalStateException("intervalles " + MOYENNES + ", hauteurs " + HAUTEURS);
                        }
                    }
                    return "de loin a pres, intervalle moyen " + MOYENNES.stream().map(d -> String.format("%.1f", d)).toList() + " ticks (strictement decroissant), "
                            + "hauteur " + HAUTEURS.stream().map(d -> String.format("%.2f", d)).toList() + " (strictement croissante)";
                });
    }

    private static void casseDeLaCache() {
        pasCD(30, "Le joueur casse la cache à la main (survie) et ramasse son butin", () -> {
            rcon("gamemode survival " + nom());
            // Debout contre la cache, côté ouest, pour ramasser ce qui tombe.
            placerJoueur(cache().getX() - 0.35, cache().getZ() + 0.5, -90, 0);
            tenirVide();
            butin = butinTotal();
        }, () -> {
            if (!pres(cache().getX() - 0.35, cache().getZ() + 0.5) || ecoule() < 1.0) {
                return false;
            }
            if (mc.level.getBlockState(cache()).isAir()) {
                return true;
            }
            mc.gameMode.continueDestroyBlock(cache(), Direction.WEST);
            return false;
        }, () -> "bloc " + mc.level.getBlockState(cache()) + " apres " + String.format("%.1f", ecoule()) + " s",
                () -> "cache cassee a la main en survie, en " + String.format("%.1f", ecoule()) + " s");
        pasCD(25, "Le butin tombe et le joueur le ramasse", () -> balaye = false, () -> {
            int auSol = mc.level.getEntitiesOfClass(ItemEntity.class, new AABB(cache()).inflate(6)).size();
            if (auSol > 0 && ecoule() > 3 && !balaye) {
                // Une pile a roulé hors de portée : le joueur se place sur la case de la cache, où tout tombe.
                balaye = true;
                placerJoueur(cache().getX() + 0.5, cache().getZ() + 0.5, -90, 0);
            }
            return auSol == 0 && butinTotal() >= butin + 2;
        },
                () -> "butin ramasse " + (butinTotal() - butin) + " (objets au sol " + mc.level.getEntitiesOfClass(ItemEntity.class,
                        new AABB(cache()).inflate(4)).size() + ")",
                () -> "butin ramasse : " + (butinTotal() - butin) + " objet(s) dans l'inventaire, " + inventaireDuButin());
        pasC("Une seule fois : rien de plus après 4 s, rien au sol, la cache sort de l'index", () -> {
            butin = butinTotal();
        }, () -> ecoule() >= 4, () -> "", () -> {
            int au_sol = mc.level.getEntitiesOfClass(ItemEntity.class, new AABB(cache()).inflate(6)).size();
            String liste = rcon("cache list");
            if (butinTotal() != butin || au_sol != 0 || liste.contains(cache().toShortString())) {
                throw new IllegalStateException("butin " + butin + " -> " + butinTotal() + ", objets au sol " + au_sol + ", /cache list : " + liste);
            }
            return "butin unique : " + butin + " objet(s) apres 4 s encore, aucun objet au sol, /cache list ne la montre plus (" + liste + ")";
        });
        pasC("Le détecteur, toujours chargé en main, se tait : plus aucune cache dans l'index", () -> {
            tenir(ExploreRegistry.METAL_DETECTOR.get());
            viderLesBips();
        }, () -> ecoule() >= 5, () -> "", () -> {
            if (!bips().isEmpty() || DetectorItem.stored(pile(ExploreRegistry.METAL_DETECTOR.get())) <= 0) {
                throw new IllegalStateException(bips().size() + " bip(s), charge " + DetectorItem.stored(pile(ExploreRegistry.METAL_DETECTOR.get())));
            }
            return "detecteur en main a 0,85 bloc de l'ancienne cache pendant 5 s : aucun bip, charge " + DetectorItem.stored(pile(ExploreRegistry.METAL_DETECTOR.get()))
                    + " FE (sans cache dans la dimension il ne depense rien non plus)";
        });
    }

    private static String inventaireDuButin() {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (ItemStack s : mc.player.getInventory().items) {
            if (!s.isEmpty() && !s.is(ExploreRegistry.METAL_DETECTOR.get()) && !s.is(ExploreRegistry.HEADLAMP.get())) {
                m.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).getPath(), s.getCount(), Integer::sum);
            }
        }
        return m.toString();
    }

    // ---------------------------------------------------------------------------------------------- lampe

    private static void lampe() {
        pasC("Le joueur entre dans la grotte close, sans lumière", () -> {
            placerJoueur(grotteCentre().getX() + 0.5, grotteCentre().getZ() + 0.5, 0, 0);
            rcon("time set midnight");
        }, () -> pres(grotteCentre().getX() + 0.5, grotteCentre().getZ() + 0.5) && ecoule() >= 2 && lumiereTete() == 0 && !lampePortee(),
                () -> "lumiere a la tete " + lumiereTete() + ", joueur en " + mc.player.position(),
                () -> "dans la grotte close, lumiere de bloc a la tete (" + BlockPos.containing(mc.player.getEyePosition()).toShortString() + ") : "
                        + lumiereTete() + " (avant la lampe)");
        pasC("Il porte la lampe (clic droit : elle se met sur la tête)", () -> {
            tenir(ExploreRegistry.HEADLAMP.get());
            var res = mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            LOG.info("[e2e] clic droit de la lampe en main : {}", res);
        }, () -> lampePortee() && lumiereTete() >= 12,
                () -> "lampe portee " + lampePortee() + ", lumiere a la tete " + lumiereTete(),
                () -> {
                    lampeCave = BlockPos.containing(mc.player.getEyePosition());
                    String serveur = rcon("execute if block " + xyz(lampeCave) + " minecraft:light");
                    return "lampe sur la tete : lumiere de bloc a la tete " + lumiereTete() + " (de 0 sans la lampe), bloc " + mc.level.getBlockState(lampeCave)
                            + " en " + lampeCave.toShortString() + ", serveur : " + serveur + ", charge " + HeadlampItem.stored(mc.player.getItemBySlot(
                            net.minecraft.world.entity.EquipmentSlot.HEAD)) + " FE";
                });
        pasC("Il retire la lampe : la lumière disparaît à sa tête", () -> deplacerLaLampe(),
                () -> !lampePortee() && lumiereTete() == 0 && ecoule() >= 1.5,
                () -> "lampe portee " + lampePortee() + ", lumiere a la tete " + lumiereTete(),
                () -> "lampe retiree (deplacement rapide de l'armure) : lumiere a la tete " + lumiereTete() + ", serveur : "
                        + rcon("execute if block " + xyz(lampeCave) + " minecraft:light"));
        pasC("Aucun bloc de lumière dans un rayon de 3 autour du dernier point de la lampe", () -> {}, () -> ecoule() >= 1,
                () -> "", () -> {
                    int n = lumieres(lampeCave, 3);
                    if (n != 0) {
                        throw new IllegalStateException(n + " bloc(s) minecraft:light autour de " + lampeCave.toShortString());
                    }
                    return "0 bloc minecraft:light dans le cube de rayon 3 autour de " + lampeCave.toShortString() + " (343 positions lues cote client)";
                });
    }

    // ---------------------------------------------------------------------------------------------- récepteurs

    private static void recepteurs() {
        pasC("Trois récepteurs : le premier alimenté, le deuxième coupé, le troisième alimenté sur la fréquence 7", () -> {
            placerJoueur(recepteur(1).getX() + 0.5, recepteur(1).getZ() + 3.5, 0, 0);
            for (int i = 0; i < 3; i++) {
                rcon("setblock " + xyz(recepteur(i)) + " oneforallsurvival:signal_receiver");
            }
            rcon("setblock " + xyz(sourceRecepteur(0)) + " " + CREATIVE);
            rcon("setblock " + xyz(sourceRecepteur(2)) + " " + CREATIVE);
            rcon("data merge block " + xyz(recepteur(2)) + " {Frequency:7}");
        }, () -> ecoule() >= 3 && ecoute(0) && ecoute(2) && !ecoute(1),
                () -> "ecoute " + ecoute(0) + " " + ecoute(1) + " " + ecoute(2),
                () -> "recepteur 1 (courant, frequence " + entier(recepteur(0), "Frequency") + ") et 3 (courant, frequence " + entier(recepteur(2), "Frequency")
                        + ") a l'ecoute, recepteur 2 coupe (courant 0, " + entier(recepteur(1), "Energy") + " FE)");
        pasC("L'équipe envoie /signal send 1 : seul le premier récepteur le reçoit, et la commande dit combien", () -> {
            reponse = rcon("signal send 1 5000 " + TEXTE);
        }, () -> ecoule() >= 1 && compteDe(reponse) >= 0, () -> "reponse " + reponse, () -> {
            if (compteDe(reponse) != 1) {
                throw new IllegalStateException("reponse de la commande : " + reponse);
            }
            return "/signal send 1 : \"" + reponse.trim() + "\" (alimente et sur 1 : un ; coupe et mauvaise frequence : rien)";
        });
        pasC("Le voyant « non lu » du premier récepteur s'allume, pas celui des autres", () -> {},
                () -> mc.level.getBlockState(recepteur(0)).getValue(ReceiverBlock.UNREAD) && ecoule() >= 0.5,
                () -> "non lu " + mc.level.getBlockState(recepteur(0)).getValue(ReceiverBlock.UNREAD),
                () -> {
                    if (mc.level.getBlockState(recepteur(1)).getValue(ReceiverBlock.UNREAD) || mc.level.getBlockState(recepteur(2)).getValue(ReceiverBlock.UNREAD)) {
                        throw new IllegalStateException("un autre recepteur a un message non lu");
                    }
                    return "voyant non lu : recepteur 1 oui, recepteurs 2 et 3 non";
                });
        pasC("Clic droit sur le premier récepteur : l'écran montre le message, le voyant s'éteint", () -> {
            tenirVide();
            clic(recepteur(0), Direction.NORTH);
        }, () -> mc.screen instanceof AbstractContainerScreen<?> s && s.getMenu() instanceof ReceiverMenu m && !m.archive().isEmpty(),
                () -> "ecran " + mc.screen, () -> {
                    ReceiverMenu m = (ReceiverMenu) ((AbstractContainerScreen<?>) mc.screen).getMenu();
                    if (m.archive().size() != 1 || !m.archive().get(0).text().equals(TEXTE) || m.frequency() != 1) {
                        throw new IllegalStateException("archive " + m.archive() + ", frequence " + m.frequency());
                    }
                    return "ecran du recepteur : archive d'un message \"" + m.archive().get(0).text() + "\" sur la frequence " + m.frequency()
                            + ", ecoute " + m.listening() + ", reserve " + m.energy() + " FE";
                });
        pasC("L'écran se ferme ; le voyant est éteint ; les autres archives sont vides", () -> mc.player.closeContainer(),
                () -> mc.screen == null && ecoute(0) && !mc.level.getBlockState(recepteur(0)).getValue(ReceiverBlock.UNREAD) && ecoule() >= 1,
                () -> "ecran " + mc.screen + ", non lu " + mc.level.getBlockState(recepteur(0)).getValue(ReceiverBlock.UNREAD), () -> {
                    String a2 = rcon("data get block " + xyz(recepteur(1)) + " Archive");
                    String a3 = rcon("data get block " + xyz(recepteur(2)) + " Archive");
                    if (a2.contains("Essai") || a3.contains("Essai")) {
                        throw new IllegalStateException("archives " + a2 + " | " + a3);
                    }
                    return "voyant eteint apres ouverture ; archive du recepteur coupe : " + a2.trim() + " ; du recepteur sur la frequence 7 : " + a3.trim();
                });
        pasC("L'équipe envoie /signal send 7 : le troisième le reçoit, /signal send 2 : personne", () -> {
            reponse = rcon("signal send 7 5000 " + TEXTE_SEPT);
            reponse2 = rcon("signal send 2 5000 Personne ne devrait recevoir ceci");
        }, () -> ecoule() >= 1, () -> "", () -> {
            String a3 = rcon("data get block " + xyz(recepteur(2)) + " Archive");
            if (compteDe(reponse) != 1 || compteDe(reponse2) != 0 || !a3.contains(TEXTE_SEPT)) {
                throw new IllegalStateException("send 7 : " + reponse + " | send 2 : " + reponse2 + " | archive 3 : " + a3);
            }
            return "/signal send 7 : \"" + reponse.trim() + "\" (le troisieme), /signal send 2 : \"" + reponse2.trim() + "\" (zero, sans erreur)";
        });
    }

    private static String reponse = "";
    private static String reponse2 = "";

    private static boolean ecoute(int i) {
        return mc.level.getBlockState(recepteur(i)).getValue(ReceiverBlock.LISTENING);
    }

    /** Le nombre annoncé par la réponse de {@code /signal send} ; -1 si elle n'en contient pas. */
    private static int compteDe(String texte) {
        Matcher m = ENTIER.matcher(texte == null ? "" : texte);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    // ---------------------------------------------------------------------------------------------- mécanisme

    private static void etapeMecanisme() {
        pasC("Un mécanisme ancien, une porte de fer contre lui, une source créative à 6 blocs ; réglé à 20 FE par tick pendant 5 s", () -> {
            placerJoueur(sourceMecanisme().getX() + 3.5, sourceMecanisme().getZ() + 3.5, 0, 0);
            rcon("setblock " + xyz(sourceMecanisme()) + " " + CREATIVE);
            rcon("setblock " + xyz(mecanisme()) + " oneforallsurvival:ancient_mechanism");
            rcon("setblock " + xyz(porteBas()) + " minecraft:iron_door[half=lower,facing=south,hinge=left,open=false]");
            rcon("setblock " + xyz(porteBas().above()) + " minecraft:iron_door[half=upper,facing=south,hinge=left,open=false]");
            rcon("mechanism set " + xyz(mecanisme()) + " 20 5");
            // Le butin de la cache libère la barre d'action pour les connecteurs et la bobine ; détecteur et lampe restent.
            for (String loot : new String[] {"scrap_metal", "aviation_wire", "instrument_core", "battery_cell"}) {
                rcon("clear " + nom() + " oneforallsurvival:" + loot);
            }
            rcon("clear " + nom() + " minecraft:iron_nugget");
            rcon("give " + nom() + " " + CONNECTEUR + " 2");
            rcon("give " + nom() + " " + BOBINE + " 2");
        }, () -> ecoule() >= 2 && mc.level.getBlockState(mecanisme()).is(ExploreRegistry.MECHANISM.get())
                && mc.level.getBlockState(porteBas()).getBlock() == Blocks.IRON_DOOR && fente(objet(CONNECTEUR)) >= 0 && fente(objet(BOBINE)) >= 0,
                () -> "mecanisme " + mc.level.getBlockState(mecanisme()) + ", porte " + mc.level.getBlockState(porteBas()),
                () -> "mecanisme en " + mecanisme().toShortString() + " (" + rcon("mechanism info " + xyz(mecanisme())).trim() + "), porte de fer fermee : "
                        + !mc.level.getBlockState(porteBas()).getValue(DoorBlock.OPEN));
        pasC("Les deux connecteurs posés par de vrais clics droits : sur la source, puis contre le mécanisme", () -> {
            tenir(objet(CONNECTEUR));
            clic(sourceMecanisme(), Direction.EAST);
        }, () -> mc.level.getBlockState(connecteurSource()).getBlock() == bloc(CONNECTEUR), () -> "bloc " + mc.level.getBlockState(connecteurSource()),
                () -> "connecteur sur la source en " + connecteurSource().toShortString() + " : " + mc.level.getBlockState(connecteurSource()));
        pasC("Le second connecteur contre le mécanisme", () -> {
            tenir(objet(CONNECTEUR));
            clic(mecanisme(), Direction.WEST);
        }, () -> mc.level.getBlockState(connecteurMecanisme()).getBlock() == bloc(CONNECTEUR), () -> "bloc " + mc.level.getBlockState(connecteurMecanisme()),
                () -> "connecteur contre le mecanisme en " + connecteurMecanisme().toShortString() + " : " + mc.level.getBlockState(connecteurMecanisme()));
        pasCD(10, "Sans câble, 4 s : le mécanisme ne reçoit rien (progression 0), la porte reste fermée", () -> {},
                () -> ecoule() >= 4, () -> "", () -> {
                    int progression = entier(mecanisme(), "Progress");
                    if (progression != 0 || mc.level.getBlockState(porteBas()).getValue(DoorBlock.OPEN)
                            || mc.level.getBlockState(mecanisme()).getValue(MechanismBlock.ACTIVE)) {
                        throw new IllegalStateException("progression " + progression + ", porte ouverte " + mc.level.getBlockState(porteBas()).getValue(DoorBlock.OPEN));
                    }
                    return "sans cable, apres 4 s : progression " + progression + ", mecanisme inactif, porte fermee (la source creative n'est pas contre lui)";
                });
        pasC("Le fil de cuivre est tiré d'un connecteur à l'autre (bobine, deux clics droits)", () -> {
            tenir(objet(BOBINE));
            clic(connecteurSource(), Direction.UP);
        }, () -> ecoule() >= 1, () -> "", () -> "premier clic de la bobine sur le connecteur de la source");
        pasC("Second clic de la bobine sur le connecteur du mécanisme", () -> {
            clic(connecteurMecanisme(), Direction.UP);
            progressVu = -1;
        }, () -> ecoule() >= 1, () -> "", () -> "second clic de la bobine sur le connecteur du mecanisme");
        pasCD(30, "Le courant arrive par le câble : la progression avance, puis le mécanisme s'active", () -> {},
                () -> {
                    int p = entier(mecanisme(), "Progress");
                    if (p > progressVu) {
                        progressVu = p;
                    }
                    return mc.level.getBlockState(mecanisme()).getValue(MechanismBlock.ACTIVE);
                }, () -> "progression maximale vue " + progressVu + ", etat " + mc.level.getBlockState(mecanisme()) + ", info "
                        + rcon("mechanism info " + xyz(mecanisme())).trim(),
                () -> "mecanisme actif, alimente par un cable de Create Crafts & Additions : progression maximale vue " + progressVu + " ticks (duree 100), "
                        + rcon("mechanism info " + xyz(mecanisme())).trim());
        pasCD(10, "La porte de fer s'ouvre par le signal de redstone du mécanisme", () -> {},
                () -> mc.level.getBlockState(porteBas()).getValue(DoorBlock.OPEN),
                () -> "porte " + mc.level.getBlockState(porteBas()),
                () -> "porte de fer ouverte (open=true) par le signal du mecanisme actif, sans levier ni bouton");
        pasCD(20, "Le joueur essaie de casser le mécanisme avec une pioche de netherite pendant 6 s", () -> {
            rcon("give " + nom() + " minecraft:netherite_pickaxe");
            placerJoueur(mecanisme().getX() + 0.5, mecanisme().getZ() + 3.5, 0, 20);
        }, () -> {
            if (fente(objet("minecraft:netherite_pickaxe")) < 0 || ecoule() < 1.5) {
                return false;
            }
            tenir(objet("minecraft:netherite_pickaxe"));
            mc.gameMode.continueDestroyBlock(mecanisme(), Direction.SOUTH);
            return ecoule() >= 8;
        }, () -> "", () -> {
            if (!mc.level.getBlockState(mecanisme()).is(ExploreRegistry.MECHANISM.get()) || !rcon("mechanism info " + xyz(mecanisme())).contains(mecanisme().getX() + "")) {
                throw new IllegalStateException("bloc " + mc.level.getBlockState(mecanisme()));
            }
            mc.gameMode.stopDestroyBlock();
            return "6 s a miner le mecanisme en survie avec une pioche de netherite (non operateur : " + !operateur() + ") : il est toujours la, " + mc.level.getBlockState(mecanisme());
        });
    }

    // ---------------------------------------------------------------------------------------------- persistance

    private static void persistance() {
        pasC("Positions et textes écrits ; le joueur rentre à son point de départ (la zone se décharge)", () -> {
            try {
                List<String> lignes = new ArrayList<>();
                lignes.add("origine=" + xyz(o));
                lignes.add("depart=" + xyz(depart));
                lignes.add("texte=" + TEXTE);
                lignes.add("lampeCave=" + xyz(lampeCave));
                Files.write(fichier(), lignes, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            tenirVide();
            // Seule la lampe reste (portée juste avant l'arrêt) : les étapes suivantes veulent une case libre dans la barre d'action.
            for (String objet : new String[] {CONNECTEUR, BOBINE, "minecraft:netherite_pickaxe", "oneforallsurvival:metal_detector",
                    "minecraft:iron_nugget", "oneforallsurvival:scrap_metal", "oneforallsurvival:aviation_wire", "oneforallsurvival:instrument_core",
                    "oneforallsurvival:battery_cell"}) {
                rcon("clear " + nom() + " " + objet);
            }
            rcon("tp " + nom() + " " + (depart.getX() + 0.5) + " " + depart.getY() + " " + (depart.getZ() + 0.5));
        }, () -> ecoule() >= 1.5 && mc.player.blockPosition().distSqr(depart) < 9, () -> "joueur en " + mc.player.blockPosition(), null);
        pasC("Le joueur redevient opérateur (console RCON : op), pour les étapes qui suivent", () -> rcon("op " + nom()),
                () -> operateur() && ecoule() >= 1.5 && mc.player.getHealth() > 0, () -> "operateur " + operateur(),
                () -> "op par RCON, hasPermissions(2) = " + operateur() + ", vie " + mc.player.getHealth());
    }

    // ---------------------------------------------------------------------------------------------- arrêt

    /** Avant le {@code /stop}, même après un échec : joueur rendu opérateur, règles du monde rendues. */
    static void rangerAvantArret(Minecraft minecraft) {
        if (minecraft.player == null) {
            return;
        }
        mc = minecraft;
        if (o != null) {
            try {
                rcon("op " + nom());
                rcon("gamerule doMobSpawning true");
            } catch (RuntimeException e) {
                LOG.error("[e2e] rcon impossible avant l'arret", e);
            }
        }
    }

    /**
     * Juste avant l'arrêt (premier passage seulement) : le joueur porte la lampe, donc une lumière est posée à sa tête quand
     * le serveur s'arrête. Renvoie vrai quand c'est fait (ou impossible), faux tant qu'il faut attendre.
     */
    static boolean armerLaLampe(Minecraft minecraft) {
        mc = minecraft;
        if (o == null || mc.player == null) {
            return true;
        }
        if (!lampeArmee) {
            lampeArmee = true;
            debutLampeArret = System.nanoTime();
            if (fenteLampe() == null && !lampePortee()) {
                E2eClient.failSoft("aucune lampe a porter avant l'arret");
                return true;
            }
            if (!lampePortee()) {
                deplacerLaLampe();
            }
            return false;
        }
        if (lampePortee() && lumiereTete() >= 12) {
            lampeArret = BlockPos.containing(mc.player.getEyePosition());
            try {
                Files.write(fichier(), List.of("lampeArret=" + xyz(lampeArret)), StandardCharsets.UTF_8, StandardOpenOption.APPEND);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            E2eClient.pass("lampe portee au moment de l'arret : lumiere de bloc a la tete " + lumiereTete() + " en " + lampeArret.toShortString()
                    + " (bloc " + mc.level.getBlockState(lampeArret) + ")");
            return true;
        }
        if ((System.nanoTime() - debutLampeArret) / 1e9 > 8) {
            E2eClient.failSoft("lampe portee avant l'arret : lumiere " + lumiereTete() + ", portee " + lampePortee());
            return true;
        }
        return false;
    }

    // ================================================================================================
    // Après un arrêt et une relance du serveur
    // ================================================================================================

    /**
     * Tout de suite après la connexion, avant l'étape du lot 5 : la lampe, portée à l'arrêt, est retirée, puis on cherche les
     * blocs de lumière restés autour du point où elle brillait. Vrai quand c'est fini.
     */
    static boolean relanceLampe(Minecraft minecraft) {
        mc = minecraft;
        if (!lampeRetireeAuDemarrage) {
            lampeRetireeAuDemarrage = true;
            debutLampeRelance = System.nanoTime();
            try {
                for (String l : Files.readAllLines(Minecraft.getInstance().gameDirectory.toPath().resolve(FICHIER), StandardCharsets.UTF_8)) {
                    if (l.startsWith("lampeArret=")) {
                        String[] t = l.substring("lampeArret=".length()).split(" ");
                        lampeArret = new BlockPos(Integer.parseInt(t[0]), Integer.parseInt(t[1]), Integer.parseInt(t[2]));
                    }
                }
            } catch (IOException e) {
                E2eClient.failSoft("relance : fichier de l'exploration illisible : " + e);
                return true;
            }
            if (lampeArret == null) {
                E2eClient.failSoft("relance : pas de position de lampe a l'arret dans " + FICHIER);
                return true;
            }
            LOG.info("[e2e] relance : lampe portee {}, lumiere a la tete {}", lampePortee(), lumiereTete());
            if (lampePortee()) {
                deplacerLaLampe();
            }
            return false;
        }
        if ((System.nanoTime() - debutLampeRelance) / 1e9 < 2.5) {
            return false;
        }
        int n = lumieres(lampeArret, 3);
        // La lampe rendue à l'inventaire est retirée : les étapes qui suivent veulent une case libre dans la barre d'action.
        rcon("clear " + nom() + " oneforallsurvival:headlamp");
        if (lampePortee() || n != 0) {
            E2eClient.failSoft("relance : lampe portee " + lampePortee() + ", " + n + " bloc(s) de lumiere autour de " + lampeArret.toShortString());
        } else {
            E2eClient.pass("relance : lampe retiree, aucun bloc minecraft:light dans un rayon de 3 autour de " + lampeArret.toShortString()
                    + " (point ou elle brillait a l'arret du serveur, 343 positions lues)");
        }
        return true;
    }

    static void relance() {
        Map<String, String> lu = new LinkedHashMap<>();
        pasC("Relecture des positions écrites avant l'arrêt", () -> {
            try {
                for (String l : Files.readAllLines(fichier(), StandardCharsets.UTF_8)) {
                    int i = l.indexOf('=');
                    lu.put(l.substring(0, i), l.substring(i + 1));
                }
            } catch (IOException e) {
                throw new IllegalStateException("premiere phase absente : " + fichier(), e);
            }
            String[] t = lu.get("origine").split(" ");
            o = new BlockPos(Integer.parseInt(t[0]), Integer.parseInt(t[1]), Integer.parseInt(t[2]));
            String[] d = lu.get("depart").split(" ");
            depart = new BlockPos(Integer.parseInt(d[0]), Integer.parseInt(d[1]), Integer.parseInt(d[2]));
            String[] c = lu.get("lampeCave").split(" ");
            lampeCave = new BlockPos(Integer.parseInt(c[0]), Integer.parseInt(c[1]), Integer.parseInt(c[2]));
            if (mc.player.containerMenu != mc.player.inventoryMenu) {
                mc.player.closeContainer();
            }
            mc.setScreen(null);
            placerJoueur(mecanisme().getX() + 0.5, mecanisme().getZ() + 3.5, 0, 20);
        }, () -> pres(mecanisme().getX() + 0.5, mecanisme().getZ() + 3.5) && ecoule() >= 3
                && mc.level.getBlockState(mecanisme()).is(ExploreRegistry.MECHANISM.get()),
                () -> "joueur en " + mc.player.blockPosition() + ", bloc " + mc.level.getBlockState(mecanisme()),
                () -> "relance : zone relue du disque en " + o.toShortString() + ", mecanisme relu en " + mecanisme().toShortString());
        pasC("Relance : le mécanisme est toujours actif, la porte de fer ouverte", () -> {}, () -> ecoule() >= 1,
                () -> "", () -> {
                    String info = rcon("mechanism info " + xyz(mecanisme())).trim();
                    int actif = entier(mecanisme(), "Active");
                    boolean etat = mc.level.getBlockState(mecanisme()).getValue(MechanismBlock.ACTIVE);
                    boolean porte = mc.level.getBlockState(porteBas()).getValue(DoorBlock.OPEN);
                    if (actif != 1 || !etat || !porte) {
                        throw new IllegalStateException("Active " + actif + ", etat " + etat + ", porte ouverte " + porte + ", info " + info);
                    }
                    return "relance : mecanisme toujours actif (Active=1b, etat actif cote client), porte de fer ouverte, " + info;
                });
        pasC("Relance : le joueur retourne devant les récepteurs", () -> placerJoueur(recepteur(1).getX() + 0.5, recepteur(1).getZ() + 3.5, 0, 0),
                () -> pres(recepteur(1).getX() + 0.5, recepteur(1).getZ() + 3.5) && ecoule() >= 2.5
                        && mc.level.getBlockState(recepteur(0)).is(ExploreRegistry.RECEIVER.get()),
                () -> "joueur en " + mc.player.blockPosition() + ", bloc " + mc.level.getBlockState(recepteur(0)), null);
        pasC("Relance : l'archive du premier récepteur est relue à l'identique (console et écran)", () -> {
            tenirVide();
            clic(recepteur(0), Direction.NORTH);
        }, () -> mc.screen instanceof AbstractContainerScreen<?> s && s.getMenu() instanceof ReceiverMenu m && ecoule() >= 1.0,
                () -> "ecran " + mc.screen, () -> {
                    ReceiverMenu m = (ReceiverMenu) ((AbstractContainerScreen<?>) mc.screen).getMenu();
                    String console = rcon("data get block " + xyz(recepteur(0)) + " Archive");
                    if (m.archive().size() != 1 || !m.archive().get(0).text().equals(lu.get("texte")) || !console.contains(lu.get("texte"))
                            || m.frequency() != 1) {
                        throw new IllegalStateException("archive " + m.archive() + ", console " + console);
                    }
                    return "relance : archive du recepteur relue du disque : \"" + m.archive().get(0).text() + "\" (frequence " + m.frequency()
                            + ", reserve " + m.energy() + " FE, console : " + console.trim() + ")";
                });
        pasC("Relance : fermeture de l'écran ; l'archive du troisième récepteur", () -> mc.player.closeContainer(),
                () -> mc.screen == null && ecoule() >= 0.5, () -> "ecran " + mc.screen, () -> {
                    String a3 = rcon("data get block " + xyz(recepteur(2)) + " Archive");
                    if (!a3.contains(TEXTE_SEPT)) {
                        throw new IllegalStateException("archive du troisieme recepteur : " + a3);
                    }
                    return "relance : archive du recepteur sur la frequence 7 relue : " + a3.trim();
                });
        pasC("Relance : le joueur retourne dans la grotte", () -> placerJoueur(grotteCentre().getX() + 0.5, grotteCentre().getZ() + 0.5, 0, 0),
                () -> pres(grotteCentre().getX() + 0.5, grotteCentre().getZ() + 0.5) && ecoule() >= 2.5
                        && mc.level.getBlockState(grotteCentre().below()).is(Blocks.STONE),
                () -> "joueur en " + mc.player.blockPosition() + ", sol " + mc.level.getBlockState(grotteCentre().below()), null);
        pasC("Relance : aucun bloc de lumière orphelin dans la grotte", () -> {}, () -> ecoule() >= 1, () -> "", () -> {
            int n = lumieres(lampeCave, 3);
            int tout = lumieres(grotteCentre().above(), 4);
            if (n != 0 || tout != 0) {
                throw new IllegalStateException(n + " / " + tout + " bloc(s) minecraft:light autour de " + lampeCave.toShortString());
            }
            return "relance : 0 bloc minecraft:light dans un rayon de 3 autour du dernier point de la lampe dans la grotte " + lampeCave.toShortString()
                    + " (et 0 dans le cube de rayon 4 autour du centre de la grotte)";
        });
        pasC("Relance : rangement de l'aire de l'exploration", () -> {
            rcon("fill " + xyz(p(-4, 0, -12)) + " " + xyz(p(62, 9, 24)) + " air");
            for (String objet : new String[] {"oneforallsurvival:metal_detector", "oneforallsurvival:headlamp", "minecraft:iron_nugget"}) {
                rcon("clear " + nom() + " " + objet);
            }
            rcon("tp " + nom() + " " + (depart.getX() + 0.5) + " " + depart.getY() + " " + (depart.getZ() + 0.5));
            rcon("gamerule doMobSpawning true");
        }, () -> ecoule() >= 2 && mc.level.getBlockState(mecanisme()).isAir(), () -> "bloc " + mc.level.getBlockState(mecanisme()),
                () -> "aire videe, regles du monde rendues");
    }
}
