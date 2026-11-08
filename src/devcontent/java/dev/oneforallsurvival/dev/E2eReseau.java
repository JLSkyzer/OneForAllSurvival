package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.grid.BreakerBlock;
import dev.oneforallsurvival.grid.GridRegistry;
import dev.oneforallsurvival.grid.LoadShedderBlockEntity;
import dev.oneforallsurvival.grid.LoadShedderMenu;
import dev.oneforallsurvival.grid.OutputStatus;
import dev.oneforallsurvival.grid.WeatherForecast.Trend;
import dev.oneforallsurvival.grid.WeatherStationBlock;
import dev.oneforallsurvival.grid.client.LoadShedderScreen;
import dev.oneforallsurvival.grid.client.SubscriberNameScreen;
import dev.oneforallsurvival.machine.ElectricMachineBlock;
import dev.oneforallsurvival.machine.MachineState;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;

/**
 * Étape « réseau » de {@link E2eClient} (développement seulement, jamais empaquetée) : le lot 4 vu par un vrai client contre
 * un serveur dédié, avec le pack. Un alternateur de Create Crafts & Additions, entraîné par un moteur créatif de Create,
 * alimente un tableau de délestage et quatre machines ; la vitesse du moteur réduite, les dernières sorties sont coupées
 * et l'écran du tableau le montre ; un rang est changé par les vrais boutons de l'écran. Un compteur est inscrit par
 * l'écran d'inscription, relevé au testeur ; un Citoyen sans droits d'opérateur est refusé ; un disjoncteur est coupé,
 * scellé, et sa bascule refusée.
 *
 * <p>Le joueur d'essai est opérateur du serveur, et un opérateur garde la main d'ingénieur ({@code RoleGate}). Pour un vrai
 * refus de Citoyen, il perd son statut d'opérateur le temps du contrôle, par la console RCON du serveur (le seul chemin
 * d'administration hors jeu de {@code runServer}, qui ne lit pas son entrée standard), puis le retrouve de même.
 *
 * <p>{@link #relance}, après un vrai arrêt et une vraie relance du serveur : rangs, index, registre et scellé relus, puis un
 * vrai éclair près des machines (parafoudre usé d'un coup, une machine témoin hors de sa portée abîmée), puis la station
 * météo qui voit l'orage de {@code /weather thunder} et allume une lampe et une alarme d'Advanced Electricity.
 *
 * <p>L'aire est à 48 blocs du point d'apparition, hors de la protection du spawn (16 blocs) qui refuserait tout clic d'un
 * joueur non opérateur, en hauteur sur une dalle de pierre : rien du terrain ne gêne.
 */
final class E2eReseau {

    private static final Logger LOG = LogManager.getLogger("oneforallsurvival-e2e");
    private static final String FICHIER = "e2e-reseau.txt";
    private static final double DELAI = 25;
    private static final int ECHECS_MAX = 8;
    private static final int Y_DALLE = 150;
    /** Un nom neuf à chaque essai : le registre des abonnés du monde garde les relevés des essais précédents. */
    private static String abonne = "Ferme " + (System.currentTimeMillis() / 1000 % 10000);

    /**
     * L'inventaire n'est jamais vidé : la relance des étapes précédentes y relit le poste chargé, le ragoût et la ration.
     * Le testeur est celui de la réparation (case 1) ; le scellé et la main vide prennent des cases libres de la barre.
     */
    private static final int H_TESTEUR = 1;
    private static int hScelle = 7;
    private static int hVide = 8;

    private static final String MOTEUR = "create:creative_motor";
    private static final String ALTERNATEUR = "createaddition:alternator";
    private static final String ALARME = "light_bulb_addon_create_crafts_and__addions:alarm_lamp";
    private static final String CREATIVE = "createaddition:creative_energy";
    private static final int RPM_PLEIN = 256;
    /** L'alternateur exige la vitesse moyenne de Create (30 tr/min) : en dessous, il ne produit rien. */
    private static final int RPM_REDUIT = 32;

    private static final String P_COMPTEUR = "message.oneforallsurvival.meter.";
    private static final String P_DISJ = "message.oneforallsurvival.breaker.";
    private static final String P_RELEVE = "tooltip.oneforallsurvival.meter_reading.";
    private static final String CLE_INSCRIT = "message.oneforallsurvival.subscriber.set";
    private static final String CLE_REFUS = "message.oneforallsurvival.engineer_only";
    private static final String CLE_DATA = "commands.data.block.query";

    // Disposition du tableau (repères relatifs à l'origine O de l'aire).
    private static final String[] NOMS_SORTIES = {"devant", "gauche", "droite", "dessus"};

    private record Recu(String cle, List<String> args, String texte) {}

    private record Pas(String legende, Runnable action, BooleanSupplier pret, Supplier<String> echec, Supplier<String> succes,
            double delai, boolean critique) {}

    private static final List<Recu> RECUS = new ArrayList<>();
    private static final List<Pas> PAS = new ArrayList<>();
    private static int index;
    private static boolean actionFaite;
    private static long debut;
    private static int echecsDuPas;

    private static Minecraft mc;
    private static BlockPos o;
    /** Où était le joueur avant l'étape : il y revient, les machines des étapes précédentes y sont. */
    private static BlockPos depart;
    private static BlockPos joueur;
    private static BlockPos moteur;
    private static BlockPos alternateur;
    private static BlockPos tableau;
    private static BlockPos compteur;
    private static BlockPos m1;
    private static BlockPos m2;
    private static BlockPos disjoncteur;
    private static BlockPos m3;
    private static BlockPos m4;
    private static BlockPos parafoudre;
    private static BlockPos temoin;
    private static BlockPos eclair;
    private static BlockPos station;
    private static BlockPos lampe;
    private static BlockPos alarme;
    private static BlockPos sourceAlarme;

    // Ce que les pas se transmettent.
    private static int clicsRang;
    private static long index1;
    private static int relevesAvant;
    private static boolean ecranVu;
    private static boolean citoyen;
    private static final Map<String, Double> ETATS = new LinkedHashMap<>();
    private static long ticMeteo;
    private static String rangsAvantArret = "";

    private E2eReseau() {}

    static String legende() {
        return index < PAS.size() ? PAS.get(index).legende() : "Fin de l'étape";
    }

    static void recevoir(Component message) {
        Recu r = lire(message);
        synchronized (RECUS) {
            RECUS.add(r);
        }
    }

    private static Recu lire(Component message) {
        // sendFailure enveloppe le message : un composant vide, avec le vrai message en enfant.
        if (!(message.getContents() instanceof TranslatableContents) && !message.getSiblings().isEmpty()) {
            return lire(message.getSiblings().get(0));
        }
        if (message.getContents() instanceof TranslatableContents t) {
            return new Recu(t.getKey(), Arrays.stream(t.getArgs()).map(E2eReseau::argument).toList(), message.getString());
        }
        return new Recu("", List.of(), message.getString());
    }

    private static String argument(Object a) {
        return a instanceof Component c ? c.getString() : String.valueOf(a);
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
        Pas p = PAS.get(index);
        if (!actionFaite) {
            LOG.info("[e2e] pas : {}", p.legende());
            p.action().run();
            actionFaite = true;
            debut = System.nanoTime();
            return false;
        }
        if (p.pret().getAsBoolean()) {
            if (p.succes() != null) {
                E2eClient.pass(p.succes().get());
            }
            index++;
            actionFaite = false;
        } else if (ecoule() > p.delai()) {
            String detail = p.legende() + " : " + p.echec().get() + " ; recus : " + recus();
            if (p.critique()) {
                E2eClient.fail(detail);
            } else {
                E2eClient.failSoft(detail);
                echecsDuPas++;
                index++;
                actionFaite = false;
                if (echecsDuPas >= ECHECS_MAX) {
                    E2eClient.fail("trop d'echecs dans l'etape du reseau : arret");
                }
            }
        }
        return false;
    }

    private static double ecoule() {
        return (System.nanoTime() - debut) / 1e9;
    }

    private static String recus() {
        synchronized (RECUS) {
            List<String> t = RECUS.stream().map(r -> r.cle().isEmpty() ? r.texte() : r.cle() + r.args()).toList();
            return t.size() > 6 ? t.subList(t.size() - 6, t.size()).toString() : t.toString();
        }
    }

    private static void pas(String legende, Runnable action, BooleanSupplier pret, Supplier<String> echec, Supplier<String> succes) {
        PAS.add(new Pas(legende, action, pret, echec, succes, DELAI, false));
    }

    private static void pasD(double delai, String legende, Runnable action, BooleanSupplier pret, Supplier<String> echec,
            Supplier<String> succes) {
        PAS.add(new Pas(legende, action, pret, echec, succes, delai, false));
    }

    private static void pasC(String legende, Runnable action, BooleanSupplier pret, Supplier<String> echec, Supplier<String> succes) {
        PAS.add(new Pas(legende, action, pret, echec, succes, DELAI, true));
    }

    private static void controle(String legende, BooleanSupplier ok, Supplier<String> detail) {
        pas(legende, () -> {}, ok, detail, detail);
    }

    private static void commande(String texte) {
        LOG.info("[e2e] commande : /{}", texte);
        mc.player.connection.sendCommand(texte);
    }

    private static String nom() {
        return mc.player.getGameProfile().getName();
    }

    private static String xyz(BlockPos p) {
        return p.getX() + " " + p.getY() + " " + p.getZ();
    }

    private static void vider() {
        synchronized (RECUS) {
            RECUS.clear();
        }
    }

    private static Recu trouve(String cle) {
        synchronized (RECUS) {
            for (Recu r : RECUS) {
                if (r.cle().equals(cle)) {
                    return r;
                }
            }
        }
        return null;
    }

    private static void clic(BlockPos pos) {
        var res = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false));
        LOG.info("[e2e] clic droit en {}, main {} : {}", pos, mc.player.getMainHandItem(), res);
    }

    private static void tenir(int fente) {
        mc.player.getInventory().selected = fente;
    }

    private static Block bloc(String id) {
        return BuiltInRegistries.BLOCK.get(new ResourceLocation(id));
    }

    private static BlockState etat(BlockPos p) {
        return mc.level.getBlockState(p);
    }

    private static MachineState etatMachine(BlockPos pos) {
        BlockState s = etat(pos);
        return s.hasProperty(ElectricMachineBlock.STATE) ? s.getValue(ElectricMachineBlock.STATE) : null;
    }

    /** Une propriété booléenne d'un bloc d'un autre mod, lue par son nom ; {@code null} si elle n'existe pas. */
    private static Boolean propriete(BlockPos pos, String nomPropriete) {
        BlockState s = etat(pos);
        for (Property<?> p : s.getProperties()) {
            if (p.getName().equals(nomPropriete) && p instanceof BooleanProperty b) {
                return s.getValue(b);
            }
        }
        return null;
    }

    private static int releves() {
        int n = 0;
        for (ItemStack s : mc.player.getInventory().items) {
            if (s.is(GridRegistry.METER_READING.get())) {
                n++;
            }
        }
        return n;
    }

    /** Le dernier relevé de l'inventaire (le plus loin dans les cases), ou une pile vide. */
    private static ItemStack dernierReleve() {
        ItemStack trouve = ItemStack.EMPTY;
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.items.size(); i++) {
            if (inv.items.get(i).is(GridRegistry.METER_READING.get())) {
                trouve = inv.items.get(i);
            }
        }
        return trouve;
    }

    /** Les lignes de l'infobulle d'un relevé : clé (suffixe) vers son premier argument, lues comme le joueur les voit. */
    private static Map<String, String> infobulle(ItemStack s) {
        Map<String, String> m = new LinkedHashMap<>();
        for (Component ligne : s.getTooltipLines(mc.player, TooltipFlag.Default.NORMAL)) {
            if (ligne.getContents() instanceof TranslatableContents t && t.getKey().startsWith(P_RELEVE)) {
                m.put(t.getKey().substring(P_RELEVE.length()), t.getArgs().length > 0 ? argument(t.getArgs()[0]) : "");
            }
        }
        return m;
    }

    // ---------------------------------------------------------------------------------------------- tableau

    private static LoadShedderMenu menuTableau() {
        return mc.player.containerMenu instanceof LoadShedderMenu m ? m : null;
    }

    private static boolean tableauOuvert() {
        return menuTableau() != null && mc.screen instanceof LoadShedderScreen;
    }

    private static OutputStatus statut(int sortie) {
        LoadShedderMenu m = menuTableau();
        return m == null ? null : m.status(sortie);
    }

    /** Le tableau tel que l'écran le montre, ligne par ligne dans l'ordre des rangs. */
    private static String lectureTableau() {
        LoadShedderMenu m = menuTableau();
        if (m == null) {
            return "(ecran du tableau ferme : " + mc.screen + ")";
        }
        StringBuilder sb = new StringBuilder("etat " + MachineState.values()[Math.floorMod(m.stateOrdinal(), MachineState.values().length)]);
        for (int rang = 0; rang < LoadShedderBlockEntity.OUTPUTS; rang++) {
            for (int s = 0; s < LoadShedderBlockEntity.OUTPUTS; s++) {
                if (m.rank(s) == rang) {
                    sb.append(" | rang ").append(rang + 1).append(" ").append(NOMS_SORTIES[s]).append(" ").append(m.status(s))
                            .append(" ").append(m.delivered(s)).append(" FE");
                }
            }
        }
        return sb.toString();
    }

    /** La sortie qui a ce rang à l'écran, ou -1. */
    private static int aLeRang(int rang) {
        LoadShedderMenu m = menuTableau();
        for (int s = 0; m != null && s < LoadShedderBlockEntity.OUTPUTS; s++) {
            if (m.rank(s) == rang) {
                return s;
            }
        }
        return -1;
    }

    private static String rangs() {
        LoadShedderMenu m = menuTableau();
        if (m == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int s = 0; s < LoadShedderBlockEntity.OUTPUTS; s++) {
            sb.append(m.rank(s));
        }
        return sb.toString();
    }

    /** Un vrai clic de souris sur le bouton « monter » de la ligne de cette sortie, aux coordonnées de l'écran. */
    private static void monter(int sortie) {
        if (!(mc.screen instanceof LoadShedderScreen ecran) || menuTableau() == null) {
            throw new IllegalStateException("ecran du tableau ferme : " + mc.screen);
        }
        int ligne = menuTableau().rank(sortie);
        // Repères de LoadShedderScreen : boutons de 12 px en x = 200, lignes de 18 px à partir de y = 46.
        double x = ecran.getGuiLeft() + 200 + 6;
        double y = ecran.getGuiTop() + 46 + ligne * 18 + 6;
        LOG.info("[e2e] clic sur « monter » de la sortie {} (ligne {}) en {} {}", NOMS_SORTIES[sortie], ligne + 1, x, y);
        ecran.mouseClicked(x, y, 0);
    }

    private static void ouvrirTableau(String legende) {
        pasC(legende, () -> {
            tenir(hVide);
            clic(tableau);
        }, E2eReseau::tableauOuvert, () -> "ecran du tableau non ouvert : " + mc.screen,
                () -> "ecran du tableau de delestage ouvert par un vrai clic droit : " + lectureTableau());
    }

    private static void fermer(String legende) {
        pas(legende, () -> {
            if (mc.player.containerMenu != mc.player.inventoryMenu) {
                mc.player.closeContainer();
            }
            mc.setScreen(null);
        }, () -> mc.screen == null && mc.player.containerMenu == mc.player.inventoryMenu, () -> "ecran encore ouvert : " + mc.screen, null);
    }

    // ---------------------------------------------------------------------------------------------- serveur

    /** {@code /data get block} d'une valeur numérique : on attend la réponse de cette position, puis on la garde. */
    private static void lireDonnee(String legende, String cle, Supplier<BlockPos> ou, String champ) {
        pas(legende, () -> {
            vider();
            commande("data get block " + xyz(ou.get()) + " " + champ);
        }, () -> valeurDe(ou.get()) != null, () -> "pas de reponse /data pour " + ou.get(), () -> {
            ETATS.put(cle, valeurDe(ou.get()));
            return "/data get block " + ou.get().toShortString() + " " + champ + " : " + ETATS.get(cle) + " (" + cle + ")";
        });
    }

    private static final Pattern NOMBRE = Pattern.compile("-?\\d+(\\.\\d+)?");

    private static Double valeurDe(BlockPos p) {
        synchronized (RECUS) {
            for (Recu r : RECUS) {
                if (r.cle().equals(CLE_DATA) && r.args().size() >= 4 && r.args().get(0).equals(String.valueOf(p.getX()))
                        && r.args().get(1).equals(String.valueOf(p.getY())) && r.args().get(2).equals(String.valueOf(p.getZ()))) {
                    Matcher m = NOMBRE.matcher(r.args().get(3));
                    return m.find() ? Double.parseDouble(m.group()) : null;
                }
            }
        }
        return null;
    }

    /**
     * Une commande par la console RCON du serveur dédié (protocole Source RCON, sur 127.0.0.1). Le mot de passe est lu dans
     * {@code run-server/server.properties}, jamais écrit ici. Renvoie la réponse de la console.
     */
    static String rcon(String commandeConsole) {
        Path proprietes = mc.gameDirectory.toPath().resolve("../run-server/server.properties").normalize();
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(proprietes)) {
            p.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("server.properties illisible : " + proprietes, e);
        }
        if (!"true".equals(p.getProperty("enable-rcon")) || p.getProperty("rcon.password", "").isEmpty()) {
            throw new IllegalStateException("RCON non active dans " + proprietes);
        }
        int port = Integer.parseInt(p.getProperty("rcon.port", "25575"));
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", port), 5000);
            s.setSoTimeout(10000);
            OutputStream out = s.getOutputStream();
            DataInputStream in = new DataInputStream(s.getInputStream());
            envoyerRcon(out, 1, 3, p.getProperty("rcon.password"));
            int[] entete = new int[2];
            lireRcon(in, entete);
            if (entete[0] == -1) {
                throw new IllegalStateException("RCON : mot de passe refuse");
            }
            envoyerRcon(out, 2, 2, commandeConsole);
            String reponse = lireRcon(in, entete);
            LOG.info("[e2e] rcon : {} -> {}", commandeConsole, reponse);
            return reponse;
        } catch (IOException e) {
            throw new IllegalStateException("RCON injoignable sur le port " + port, e);
        }
    }

    private static void envoyerRcon(OutputStream out, int id, int type, String corps) throws IOException {
        byte[] b = corps.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(4 + 4 + 4 + b.length + 2).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(4 + 4 + b.length + 2).putInt(id).putInt(type).put(b).put((byte) 0).put((byte) 0);
        out.write(buf.array());
        out.flush();
    }

    private static String lireRcon(DataInputStream in, int[] entete) throws IOException {
        byte[] l = new byte[4];
        in.readFully(l);
        int longueur = ByteBuffer.wrap(l).order(ByteOrder.LITTLE_ENDIAN).getInt();
        byte[] reste = new byte[longueur];
        in.readFully(reste);
        ByteBuffer b = ByteBuffer.wrap(reste).order(ByteOrder.LITTLE_ENDIAN);
        entete[0] = b.getInt();
        entete[1] = b.getInt();
        return new String(reste, 8, Math.max(0, longueur - 10), StandardCharsets.UTF_8);
    }

    private static boolean operateur() {
        return mc.player.hasPermissions(2);
    }

    // ================================================================================================
    // Disposition
    // ================================================================================================

    private static void placer(BlockPos origine) {
        o = origine;
        joueur = o.offset(1, 0, -4);
        moteur = o.offset(-1, 0, 0);
        alternateur = o;
        tableau = o.offset(1, 0, 0);
        compteur = o.offset(2, 0, 0);
        m1 = o.offset(3, 0, 0);
        m2 = o.offset(1, 0, 1);
        disjoncteur = o.offset(1, 0, -1);
        m3 = o.offset(1, 0, -2);
        m4 = o.offset(1, 1, 0);
        parafoudre = o.offset(-3, 0, 2);
        eclair = o.offset(13, 0, 0);
        temoin = o.offset(28, 0, 0);
        station = o.offset(0, 0, 1);
        lampe = o.offset(0, 0, 2);
        alarme = o.offset(-1, 0, 1);
        sourceAlarme = o.offset(-2, 0, 1);
    }

    private static void tp() {
        commande("tp " + nom() + " " + (joueur.getX() + 0.5) + " " + joueur.getY() + " " + (joueur.getZ() + 0.5) + " 0 30");
    }

    private static String moteurA(int rpm) {
        return MOTEUR + "[facing=east]{ScrollValue:" + rpm + "}";
    }

    // ================================================================================================
    // Première phase
    // ================================================================================================

    static void premierePhase() {
        preparation();
        penurie();
        compteur();
        citoyen();
        disjoncteur();
        persistance();
    }

    private static void preparation() {
        pasC("Aire du lot 4 hors du spawn, sur une dalle, et outils par de vraies commandes", () -> {
            if (mc.player.containerMenu != mc.player.inventoryMenu) {
                mc.player.closeContainer();
            }
            mc.setScreen(null);
            if (bloc(ALTERNATEUR) == Blocks.AIR || bloc(MOTEUR) == Blocks.AIR) {
                throw new IllegalStateException("alternateur ou moteur creatif absent : lancer avec -PwithPack");
            }
            // Les étapes précédentes ont posé leurs machines autour du joueur : l'aire du réseau est à 64 blocs de lui,
            // et à plus de 24 blocs du point d'apparition (protection du spawn : 16 blocs).
            depart = mc.player.blockPosition();
            BlockPos spawn = mc.level.getSharedSpawnPos();
            int x = depart.getX() + 64;
            if (Math.abs(x - spawn.getX()) <= 24 && Math.abs(depart.getZ() - spawn.getZ()) <= 24) {
                x += 48;
            }
            placer(new BlockPos(x, Y_DALLE, depart.getZ()));
            commande("fill " + xyz(o.offset(-8, 0, -8)) + " " + xyz(o.offset(30, 6, 8)) + " air");
            commande("fill " + xyz(o.offset(-8, -1, -8)) + " " + xyz(o.offset(30, -1, 8)) + " stone");
            tp();
            choisirCases();
            commande("gamemode survival " + nom());
            commande("item replace entity " + nom() + " hotbar." + H_TESTEUR + " with oneforallsurvival:tester");
            commande("item replace entity " + nom() + " hotbar." + hScelle + " with oneforallsurvival:seal 2");
        }, () -> mc.player.blockPosition().equals(joueur) && etat(o.below()).is(Blocks.STONE)
                && mc.player.getInventory().getItem(hScelle).is(GridRegistry.SEAL.get())
                && mc.player.getInventory().getItem(H_TESTEUR).getItem() != net.minecraft.world.item.Items.AIR,
                () -> "joueur en " + mc.player.blockPosition() + " (attendu " + joueur + "), dalle " + etat(o.below()),
                () -> "/fill, /tp, /clear, /item replace : aire en " + o.toShortString() + " (joueur en " + depart.toShortString()
                        + ", spawn " + mc.level.getSharedSpawnPos().toShortString() + "), testeur et 2 scelles recus");
        pasC("Moteur créatif à " + RPM_PLEIN + " tr/min, alternateur, tableau, compteur, disjoncteur, quatre machines", () -> {
            commande("setblock " + xyz(moteur) + " " + moteurA(RPM_PLEIN));
            commande("setblock " + xyz(alternateur) + " " + ALTERNATEUR + "[facing=west]");
            commande("setblock " + xyz(compteur) + " oneforallsurvival:meter[facing=east]");
            // Un disjoncteur ne se scelle qu'inscrit : il est posé au nom de l'abonné.
            commande("setblock " + xyz(disjoncteur) + " oneforallsurvival:breaker[facing=north]{subscriber:\"" + abonne + "\"}");
            for (BlockPos m : new BlockPos[] {m1, m2, m3, m4}) {
                commande("setblock " + xyz(m) + " oneforallsurvival:test_machine");
            }
            // Le tableau en dernier : ses voisins existent déjà quand il les cherche.
            commande("setblock " + xyz(tableau) + " oneforallsurvival:load_shedder[facing=east]");
        }, () -> etat(tableau).is(GridRegistry.LOAD_SHEDDER.get()) && etat(compteur).is(GridRegistry.METER.get())
                && etat(disjoncteur).is(GridRegistry.BREAKER.get()) && etat(alternateur).is(bloc(ALTERNATEUR))
                && etat(moteur).is(bloc(MOTEUR)) && etat(m4).is(bloc("oneforallsurvival:test_machine")),
                () -> "blocs absents : tableau " + etat(tableau) + ", alternateur " + etat(alternateur) + ", m4 " + etat(m4),
                () -> "setblock : moteur creatif " + RPM_PLEIN + " tr/min -> alternateur Crafts & Additions -> tableau (entree a l'arriere, ouest) ; "
                        + "devant : compteur puis machine 1 ; gauche : machine 2 ; droite : disjoncteur puis machine 3 ; dessus : machine 4");
    }

    private static void penurie() {
        ouvrirTableau("Ouverture du tableau de délestage");
        pasD(40, "Courant plein : les quatre sorties sont alimentées", () -> {}, () -> tableauOuvert()
                        && statut(0) == OutputStatus.FED && statut(1) == OutputStatus.FED && statut(2) == OutputStatus.FED
                        && statut(3) == OutputStatus.FED && menuTableau().delivered(0) > 0 && ecoule() >= 2,
                E2eReseau::lectureTableau, () -> "alternateur a " + RPM_PLEIN + " tr/min, l'ecran du tableau : " + lectureTableau());
        fermer("Fermeture du tableau");
        // Réserves pleines, quatre machines ne prennent que 40 FE par tick, moins que l'alternateur au plus lent qu'il
        // tolère (45 FE à 32 tr/min) : les réserves sont vidées, chaque machine redemande 100 FE par tick. L'alternateur
        // est reposé neuf : sa réserve interne (5 000 FE, pleine) couvrirait sinon la demande pendant des secondes.
        pas("Source réduite : moteur à " + RPM_REDUIT + " tr/min, alternateur reposé, réserves des machines vidées (/data merge)", () -> {
            vider();
            commande("setblock " + xyz(moteur) + " air");
            commande("setblock " + xyz(alternateur) + " air");
            commande("setblock " + xyz(alternateur) + " " + ALTERNATEUR + "[facing=west]");
            commande("setblock " + xyz(moteur) + " " + moteurA(RPM_REDUIT));
            for (BlockPos m : new BlockPos[] {m1, m2, m3, m4}) {
                commande("data merge block " + xyz(m) + " {energy:0}");
            }
        }, () -> etat(moteur).is(bloc(MOTEUR)) && etat(alternateur).is(bloc(ALTERNATEUR)) && ecoule() >= 1,
                () -> "moteur " + etat(moteur) + ", alternateur " + etat(alternateur), null);
        ouvrirTableau("Le tableau rouvert après la baisse");
        // La réserve interne de l'alternateur (5 000 FE) se vide d'abord : quelques secondes de plein service.
        pasD(60, "Pénurie : le rang 1 reste servi, les deux derniers rangs sont coupés", () -> {}, () -> tableauOuvert()
                        && statut(aLeRang(0)) != OutputStatus.CUT && menuTableau().delivered(aLeRang(0)) > 0
                        && statut(aLeRang(2)) == OutputStatus.CUT
                        && statut(aLeRang(3)) == OutputStatus.CUT,
                E2eReseau::lectureTableau, () -> "alternateur a " + RPM_REDUIT + " tr/min, l'ecran du tableau : " + lectureTableau()
                        + " (ordre d'usine : devant, gauche, droite, dessus)");
        pas("Réglage d'un rang : « monter » sur la sortie du dessus, par un vrai clic", () -> {
            clicsRang = 0;
            monter(LoadShedderBlockEntity.OUTPUT_TOP);
            clicsRang++;
        }, () -> {
            LoadShedderMenu m = menuTableau();
            if (m == null) {
                return false;
            }
            int rang = m.rank(LoadShedderBlockEntity.OUTPUT_TOP);
            // Un clic à la fois : on attend que le serveur ait rendu le rang avant de cliquer encore.
            if (rang == 3 - clicsRang && rang > 0 && ecoule() > 0.3 * clicsRang) {
                monter(LoadShedderBlockEntity.OUTPUT_TOP);
                clicsRang++;
            }
            return rang == 0;
        }, () -> "rangs (devant, gauche, droite, dessus) : " + rangs() + " apres " + clicsRang + " clic(s)",
                () -> "3 clics reels sur « monter » : la sortie du dessus passe du rang 4 au rang 1 ; rangs (devant, gauche, droite, dessus) "
                        + rangs());
        pasD(30, "Le dessus est servi en premier, la droite (rang 4) est coupée", () -> {},
                () -> tableauOuvert() && statut(LoadShedderBlockEntity.OUTPUT_TOP) != OutputStatus.CUT
                        && menuTableau().delivered(LoadShedderBlockEntity.OUTPUT_TOP) > 0
                        && statut(LoadShedderBlockEntity.OUTPUT_RIGHT) == OutputStatus.CUT && ecoule() >= 1.5,
                E2eReseau::lectureTableau, () -> "apres le reglage, l'ecran : " + lectureTableau());
        pas("Rangs retenus pour la relance", () -> rangsAvantArret = rangs(), () -> true, () -> "", null);
        fermer("Fermeture du tableau");
    }

    private static void compteur() {
        pas("Clic droit main vide sur le compteur : l'ingénieur ouvre l'écran d'inscription", () -> {
            vider();
            tenir(hVide);
            clic(compteur);
        }, () -> mc.screen instanceof SubscriberNameScreen, () -> "ecran : " + mc.screen,
                () -> "ecran d'inscription de l'abonne ouvert chez l'ingenieur (paquet du serveur)");
        pas("Nom de l'abonné tapé au clavier, Entrée", () -> {
            vider();
            for (char c : abonne.toCharArray()) {
                mc.screen.charTyped(c, 0);
            }
            mc.screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        }, () -> trouve(CLE_INSCRIT) != null && mc.screen == null, () -> "ecran " + mc.screen,
                () -> "nom tape et envoye : le serveur repond " + trouve(CLE_INSCRIT).texte());
        pasD(10, "Le compteur compte : du courant le traverse", () -> {}, () -> ecoule() >= 3, () -> "", null);
        pas("Relevé au testeur par l'ingénieur", () -> {
            vider();
            relevesAvant = releves();
            tenir(H_TESTEUR);
            clic(compteur);
        }, () -> releves() == relevesAvant + 1 && trouve(P_COMPTEUR + "reading_done") != null,
                () -> "releves " + relevesAvant + " -> " + releves(), () -> "testeur sur le compteur : un releve dans l'inventaire ; "
                        + trouve(P_COMPTEUR + "reading_done").texte());
        controle("Le relevé lu dans son infobulle", () -> {
            Map<String, String> i = infobulle(dernierReleve());
            long idx = Long.parseLong(i.getOrDefault("index", "0"));
            long conso = Long.parseLong(i.getOrDefault("consumption", "-1"));
            return abonne.equals(i.get("subscriber")) && idx > 0 && i.containsKey("day")
                    && i.containsKey("date") && i.containsKey("no_anomaly")
                    && conso > 0 && conso <= idx;
        }, () -> {
            Map<String, String> i = infobulle(dernierReleve());
            index1 = Long.parseLong(i.getOrDefault("index", "0"));
            return "infobulle du releve : " + i + " (premier releve : consommation depuis l'inscription, pas tout l'index)";
        });
    }

    private static void citoyen() {
        pasC("Le joueur devient Citoyen et perd l'opérateur (/role set, puis deop par la console RCON)", () -> {
            vider();
            commande("role set " + nom() + " citoyen");
        }, () -> ecoule() >= 1.0 && retirerOperateur(), () -> "toujours operateur : " + operateur(),
                () -> "role citoyen, et plus operateur cote client (hasPermissions(2) = " + operateur() + ")");
        pas("Citoyen : testeur sur le compteur, relevé refusé", () -> {
            vider();
            relevesAvant = releves();
            tenir(H_TESTEUR);
            clic(compteur);
        }, () -> trouve(CLE_REFUS) != null && ecoule() >= 1.0 && releves() == relevesAvant,
                () -> "releves " + relevesAvant + " -> " + releves(),
                () -> "Citoyen non operateur : releve refuse (« " + trouve(CLE_REFUS).texte() + " »), aucun releve donne");
        pas("Citoyen : main vide sur le compteur, pas d'écran, le nom de l'abonné", () -> {
            vider();
            ecranVu = false;
            tenir(hVide);
            clic(compteur);
        }, () -> {
            ecranVu |= mc.screen instanceof SubscriberNameScreen;
            return trouve(P_COMPTEUR + "owner") != null && ecoule() >= 1.0 && !ecranVu;
        }, () -> "ecran vu " + ecranVu, () -> "Citoyen : aucun ecran d'inscription, il lit « " + trouve(P_COMPTEUR + "owner").texte() + " »");
        pas("Citoyen : le scellé en main sur le disjoncteur, refusé", () -> {
            vider();
            tenir(hScelle);
            clic(disjoncteur);
        }, () -> trouve(CLE_REFUS) != null && ecoule() >= 1.0 && !etat(disjoncteur).getValue(BreakerBlock.SEALED)
                && mc.player.getInventory().getItem(hScelle).getCount() == 2,
                () -> "scelle " + etat(disjoncteur).getValue(BreakerBlock.SEALED) + ", scelles en main " + mc.player.getInventory().getItem(hScelle),
                () -> "Citoyen : scelle refuse, disjoncteur non scelle, les 2 scelles gardes");
        pasC("Le joueur redevient opérateur (console RCON) et Ingénieur", () -> {
            rcon("op " + nom());
            citoyen = false;
        }, () -> {
            if (operateur() && !citoyen) {
                citoyen = true;
                vider();
                commande("role set " + nom() + " ingenieur");
            }
            return operateur() && citoyen && ecoule() >= 1.5;
        }, () -> "operateur " + operateur(), () -> "op par RCON, /role set ingenieur");
    }

    /** Une seule fois par pas : retire l'opérateur par RCON ; vrai quand le client l'a vu. */
    private static boolean retirerOperateur() {
        if (!citoyen) {
            citoyen = true;
            rcon("deop " + nom());
        }
        return !operateur();
    }

    private static void disjoncteur() {
        pas("Clic droit main vide sur le disjoncteur : coupé", () -> {
            vider();
            tenir(hVide);
            clic(disjoncteur);
        }, () -> !etat(disjoncteur).getValue(BreakerBlock.CLOSED) && trouve(P_DISJ + "cut") != null,
                () -> "disjoncteur " + etat(disjoncteur), () -> "bascule par un vrai clic : disjoncteur coupe (" + trouve(P_DISJ + "cut").texte() + ")");
        pas("L'ingénieur pose un scellé sur le disjoncteur coupé", () -> {
            vider();
            tenir(hScelle);
            clic(disjoncteur);
        }, () -> etat(disjoncteur).getValue(BreakerBlock.SEALED) && mc.player.getInventory().getItem(hScelle).getCount() == 1
                && trouve(P_DISJ + "seal_done") != null,
                () -> "disjoncteur " + etat(disjoncteur) + ", scelles " + mc.player.getInventory().getItem(hScelle),
                () -> "scelle pose : disjoncteur coupe et scelle, un scelle consomme (2 -> 1)");
        pas("Bascule d'un disjoncteur scellé : refusée", () -> {
            vider();
            tenir(hVide);
            clic(disjoncteur);
        }, () -> trouve(P_DISJ + "sealed") != null && ecoule() >= 1.0 && !etat(disjoncteur).getValue(BreakerBlock.CLOSED),
                () -> "disjoncteur " + etat(disjoncteur),
                () -> "clic main vide sur le disjoncteur scelle : refuse (« " + trouve(P_DISJ + "sealed").texte() + " »), il reste coupe");
    }

    private static void persistance() {
        pasC("Positions, rangs et index écrits pour la relance", () -> {
            try {
                List<String> lignes = new ArrayList<>();
                lignes.add("origine=" + xyz(o));
                lignes.add("rangs=" + rangsAvantArret);
                lignes.add("index=" + index1);
                lignes.add("abonne=" + abonne);
                Files.write(fichier(), lignes, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            retour();
        }, () -> true, () -> "", () -> "a retrouver apres relance : tableau " + tableau.toShortString() + " rangs (devant, gauche, droite, dessus) "
                + rangsAvantArret + ", releve a l'index " + index1 + " pour " + abonne + ", disjoncteur " + disjoncteur.toShortString()
                + " coupe et scelle");
    }

    /** Deux cases vides de la barre d'action (de la droite vers la gauche, à partir de la case 3) : main vide, scellé. */
    private static void choisirCases() {
        List<Integer> libres = new ArrayList<>();
        for (int i = 8; i >= 3; i--) {
            if (mc.player.getInventory().getItem(i).isEmpty()) {
                libres.add(i);
            }
        }
        if (libres.size() < 2) {
            throw new IllegalStateException("moins de deux cases libres dans la barre d'action");
        }
        hVide = libres.get(0);
        hScelle = libres.get(1);
        LOG.info("[e2e] cases du reseau : main vide {}, scelle {}", hVide, hScelle);
    }

    /** Le joueur revient où il était avant l'étape. */
    private static void retour() {
        if (depart != null) {
            commande("tp " + nom() + " " + (depart.getX() + 0.5) + " " + depart.getY() + " " + (depart.getZ() + 0.5));
        }
    }

    private static Path fichier() {
        return mc.gameDirectory.toPath().resolve(FICHIER);
    }

    /** Avant le {@code /stop}, même après un échec : le joueur est rendu opérateur et Ingénieur. */
    static void rangerAvantArret(Minecraft minecraft) {
        if (minecraft.player == null || o == null) {
            return;
        }
        mc = minecraft;
        if (!operateur()) {
            try {
                rcon("op " + nom());
            } catch (RuntimeException e) {
                LOG.error("[e2e] rcon impossible avant l'arret", e);
            }
        }
        commande("role set " + nom() + " ingenieur");
    }

    // ================================================================================================
    // Après un arrêt et une relance du serveur
    // ================================================================================================

    static void relance() {
        Map<String, String> lu = new LinkedHashMap<>();
        pasC("Relecture des positions, des rangs et de l'index", () -> {
            try {
                for (String l : Files.readAllLines(fichier(), StandardCharsets.UTF_8)) {
                    int i = l.indexOf('=');
                    lu.put(l.substring(0, i), l.substring(i + 1));
                }
            } catch (IOException e) {
                throw new IllegalStateException("premiere phase absente : " + fichier(), e);
            }
            String[] t = lu.get("origine").split(" ");
            placer(new BlockPos(Integer.parseInt(t[0]), Integer.parseInt(t[1]), Integer.parseInt(t[2])));
            rangsAvantArret = lu.get("rangs");
            index1 = Long.parseLong(lu.get("index"));
            abonne = lu.get("abonne");
            if (mc.player.containerMenu != mc.player.inventoryMenu) {
                mc.player.closeContainer();
            }
            mc.setScreen(null);
            depart = mc.player.blockPosition();
            for (int i = 8; i >= 0; i--) {
                if (mc.player.getInventory().getItem(i).isEmpty()) {
                    hVide = i;
                    break;
                }
            }
            tp();
        }, () -> mc.player.blockPosition().equals(joueur) && etat(tableau).is(GridRegistry.LOAD_SHEDDER.get())
                && etat(compteur).is(GridRegistry.METER.get()) && etat(disjoncteur).is(GridRegistry.BREAKER.get()),
                () -> "blocs absents : " + etat(tableau) + " " + etat(compteur) + " " + etat(disjoncteur),
                () -> "relance : tableau, compteur et disjoncteur relus du disque en " + o.toShortString());
        controle("Relance : le disjoncteur est toujours coupé et scellé", () -> !etat(disjoncteur).getValue(BreakerBlock.CLOSED)
                && etat(disjoncteur).getValue(BreakerBlock.SEALED), () -> "relance : disjoncteur " + etat(disjoncteur));
        pas("Relance : bascule du disjoncteur scellé, refusée", () -> {
            vider();
            tenir(hVide);
            clic(disjoncteur);
        }, () -> trouve(P_DISJ + "sealed") != null && ecoule() >= 1.0 && !etat(disjoncteur).getValue(BreakerBlock.CLOSED),
                () -> "disjoncteur " + etat(disjoncteur), () -> "relance : bascule refusee, le scelle tient (« " + trouve(P_DISJ + "sealed").texte() + " »)");
        ouvrirTableau("Relance : ouverture du tableau");
        controle("Relance : les rangs sont ceux d'avant l'arrêt", () -> tableauOuvert() && rangs().equals(rangsAvantArret),
                () -> "relance : rangs (devant, gauche, droite, dessus) " + rangs() + ", avant l'arret " + rangsAvantArret + " ; " + lectureTableau());
        fermer("Relance : fermeture du tableau");
        pas("Relance : second relevé au testeur", () -> {
            vider();
            relevesAvant = releves();
            tenir(H_TESTEUR);
            clic(compteur);
        }, () -> releves() == relevesAvant + 1 && trouve(P_COMPTEUR + "reading_done") != null,
                () -> "releves " + relevesAvant + " -> " + releves(), null);
        controle("Relance : index gardé, consommation comptée depuis le relevé d'avant l'arrêt", () -> {
            Map<String, String> i = infobulle(dernierReleve());
            long idx = Long.parseLong(i.getOrDefault("index", "-1"));
            return abonne.equals(i.get("subscriber")) && idx >= index1 && i.containsKey("no_anomaly")
                    && Long.parseLong(i.getOrDefault("consumption", "-1")) == idx - index1;
        }, () -> "relance : infobulle du second releve " + infobulle(dernierReleve()) + " ; index du premier " + index1
                + " (consommation attendue = index - " + index1 + " : le registre de l'abonne a garde le releve precedent)");
        foudre();
        meteo();
        rangement();
    }

    private static void foudre() {
        pas("Un parafoudre près des machines, une machine témoin à 31 blocs de lui", () -> {
            commande("setblock " + xyz(parafoudre) + " oneforallsurvival:surge_arrester");
            commande("setblock " + xyz(temoin) + " oneforallsurvival:test_machine");
        }, () -> etat(parafoudre).is(GridRegistry.SURGE_ARRESTER.get()) && etat(temoin).is(bloc("oneforallsurvival:test_machine")),
                () -> "parafoudre " + etat(parafoudre) + ", temoin " + etat(temoin),
                () -> "parafoudre en " + parafoudre.toShortString() + " ; machine temoin en " + temoin.toShortString()
                        + ", a 15 blocs de l'eclair et hors de portee du parafoudre (24 blocs)");
        lireDonnee("État du parafoudre avant l'éclair", "parafoudre_avant", () -> parafoudre, "condition");
        lireDonnee("État de la machine 1 avant l'éclair", "m1_avant", () -> m1, "condition");
        lireDonnee("État du tableau avant l'éclair", "tableau_avant", () -> tableau, "condition");
        lireDonnee("État de la machine témoin avant l'éclair", "temoin_avant", () -> temoin, "condition");
        pas("Un vrai éclair (/summon) à 10 blocs des machines", () -> commande("summon minecraft:lightning_bolt "
                + (eclair.getX() + 0.5) + " " + eclair.getY() + " " + (eclair.getZ() + 0.5)), () -> ecoule() >= 2.0, () -> "", null);
        lireDonnee("État du parafoudre après l'éclair", "parafoudre_apres", () -> parafoudre, "condition");
        lireDonnee("État de la machine 1 après l'éclair", "m1_apres", () -> m1, "condition");
        lireDonnee("État du tableau après l'éclair", "tableau_apres", () -> tableau, "condition");
        lireDonnee("État de la machine témoin après l'éclair", "temoin_apres", () -> temoin, "condition");
        controle("Le parafoudre a encaissé l'éclair d'un coup, les machines protégées n'ont rien, le témoin est touché", () -> {
            double pa = ETATS.getOrDefault("parafoudre_avant", -1.0);
            double pp = ETATS.getOrDefault("parafoudre_apres", -1.0);
            double ta = ETATS.getOrDefault("temoin_avant", -1.0);
            double tp = ETATS.getOrDefault("temoin_apres", -1.0);
            return Math.abs((pa - pp) - 10.0) < 1e-6
                    && ETATS.getOrDefault("m1_apres", -1.0) >= ETATS.getOrDefault("m1_avant", 999.0) - 1.0
                    && ETATS.getOrDefault("tableau_apres", -1.0) >= ETATS.getOrDefault("tableau_avant", 999.0) - 1.0
                    && ta >= 99.0 && tp <= ta - 25.0 + 1e-6;
        }, () -> "eclair en " + eclair.toShortString() + " : parafoudre " + ETATS.get("parafoudre_avant") + " -> " + ETATS.get("parafoudre_apres")
                + " (-10 attendu, un coup) ; machine 1 " + ETATS.get("m1_avant") + " -> " + ETATS.get("m1_apres") + ", tableau "
                + ETATS.get("tableau_avant") + " -> " + ETATS.get("tableau_apres") + " (proteges) ; temoin " + ETATS.get("temoin_avant") + " -> "
                + ETATS.get("temoin_apres") + ", etat du bloc " + etatMachine(temoin) + " (-25 ou fusible : l'eclair etait reel)");
    }

    private static void meteo() {
        pas("Station météo contre l'alternateur, une lampe et une alarme d'Advanced Electricity contre elle", () -> {
            if (bloc(ALARME) == Blocks.AIR) {
                throw new IllegalStateException(ALARME + " absente du pack");
            }
            commande("setblock " + xyz(station) + " oneforallsurvival:weather_station[facing=north]");
            commande("setblock " + xyz(lampe) + " minecraft:redstone_lamp");
            commande("setblock " + xyz(alarme) + " " + ALARME + "[facing=up]");
            commande("setblock " + xyz(sourceAlarme) + " " + CREATIVE);
            commande("weather clear 1000000");
        }, () -> etat(station).is(GridRegistry.WEATHER_STATION.get()) && etat(alarme).is(bloc(ALARME)) && etat(lampe).is(Blocks.REDSTONE_LAMP),
                () -> "station " + etat(station) + ", alarme " + etat(alarme), () -> "station meteo alimentee par l'alternateur, lampe de redstone "
                        + "et lampe d'alarme (" + ALARME + ", alimentee par une source Create) contre elle ; /weather clear 1000000");
        pasD(40, "Ciel dégagé pour longtemps : station en marche, tendance stable, rien d'allumé", () -> {},
                () -> etatMachine(station) == MachineState.RUNNING && etat(station).getValue(WeatherStationBlock.TREND) == Trend.STABLE
                        && !etat(lampe).getValue(RedstoneLampBlock.LIT) && Boolean.FALSE.equals(propriete(alarme, "powered")) && ecoule() >= 2,
                E2eReseau::lectureMeteo, () -> "avant l'orage : " + lectureMeteo());
        pasD(30, "/weather thunder : la station passe à l'orage, la lampe et l'alarme s'allument", () -> {
            ticMeteo = mc.level.getGameTime();
            commande("weather thunder");
        }, () -> etat(station).getValue(WeatherStationBlock.TREND) == Trend.STORM_SOON && etat(lampe).getValue(RedstoneLampBlock.LIT)
                && Boolean.TRUE.equals(propriete(alarme, "powered")) && Boolean.TRUE.equals(propriete(alarme, "lit")),
                E2eReseau::lectureMeteo, () -> "orage : " + lectureMeteo() + ", " + (mc.level.getGameTime() - ticMeteo)
                        + " ticks apres /weather thunder (vus par le client)");
        pas("Ciel rendu", () -> commande("weather clear"), () -> ecoule() >= 1.0, () -> "", null);
    }

    private static String lectureMeteo() {
        BlockState s = etat(station);
        return "station " + (s.hasProperty(WeatherStationBlock.TREND) ? s.getValue(WeatherStationBlock.TREND) : "?") + " / " + etatMachine(station)
                + ", lampe de redstone allumee " + (etat(lampe).hasProperty(RedstoneLampBlock.LIT) && etat(lampe).getValue(RedstoneLampBlock.LIT))
                + ", alarme alimentee en redstone " + propriete(alarme, "powered") + " et allumee " + propriete(alarme, "lit")
                + ", pluie client " + mc.level.isRaining() + " orage " + mc.level.isThundering();
    }

    private static void rangement() {
        pas("Rangement de l'aire du réseau", () -> {
            commande("fill " + xyz(o.offset(-8, 0, -8)) + " " + xyz(o.offset(30, 6, 8)) + " air");
            commande("clear " + nom() + " oneforallsurvival:meter_reading");
            retour();
        }, () -> etat(tableau).isAir() && etat(temoin).isAir() && etat(station).isAir(), () -> "blocs encore la",
                () -> "aire du reseau videe, releves retires");
    }
}
