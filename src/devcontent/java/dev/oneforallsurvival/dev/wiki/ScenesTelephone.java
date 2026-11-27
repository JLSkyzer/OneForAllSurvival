package dev.oneforallsurvival.dev.wiki;

import dev.oneforallsurvival.bench.BenchRegistry;
import dev.oneforallsurvival.bench.EngineerBenchBlock;
import dev.oneforallsurvival.bench.client.BenchClientState;
import dev.oneforallsurvival.bench.client.BenchStockScreen;
import dev.oneforallsurvival.bench.client.EngineerBenchScreen;
import dev.oneforallsurvival.dev.JoueurDeTest;
import dev.oneforallsurvival.dev.OperateurDEssai;
import dev.oneforallsurvival.dev.ReseauxDev;
import dev.oneforallsurvival.economy.EconomyClock;
import dev.oneforallsurvival.machine.ElectricMachineBlock;
import dev.oneforallsurvival.machine.ElectricMachineBlockEntity;
import dev.oneforallsurvival.machine.FaultType;
import dev.oneforallsurvival.machine.MachineState;
import dev.oneforallsurvival.phone.PhoneConfig;
import dev.oneforallsurvival.phone.PhoneItem;
import dev.oneforallsurvival.phone.PhoneRegistry;
import dev.oneforallsurvival.phone.WallSocketBlock;
import dev.oneforallsurvival.phone.WallSocketBlockEntity;
import dev.oneforallsurvival.phone.call.CallService;
import dev.oneforallsurvival.phone.call.EmergencyService;
import dev.oneforallsurvival.phone.net.AntennaBlock;
import dev.oneforallsurvival.phone.net.AntennaBlockEntity;
import dev.oneforallsurvival.phone.net.OperatorActionPacket;
import dev.oneforallsurvival.phone.net.PhoneOperator;
import dev.oneforallsurvival.phone.net.PhoneOperators;
import dev.oneforallsurvival.phone.net.client.OperatorPanelLayout;
import dev.oneforallsurvival.phone.net.client.OperatorPanelScreen;
import dev.oneforallsurvival.role.RoleSavedData;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Le chapitre Téléphone du wiki : l'antenne (en marche, sans courant, en panne), l'écran « Antenne à fonder » et le
 * panneau de l'opérateur, l'antenne reliée par un fil (connecteurs et bobine de Crafts &amp; Additions), l'alternateur
 * contre l'antenne, la prise murale en charge, l'antenne fabriquée à l'établi et l'alerte reçue par un policier. Chaque
 * scène se joue sur un bout d'île : herbe, plage, mer au nord, deux palmiers.
 *
 * <p>Les gestes passent par le vrai chemin du jeu : clic droit du client ({@link Scene#clicDroit}), ou
 * {@code ServerPlayerGameMode.useItemOn} pour un objet tenu (accroupi ou non), comme un paquet de clic du joueur. Les
 * constats en jeu (mode des connecteurs, alternateur à 32 tr/min) sont écrits au journal ({@code [wiki] constat}).
 */
public final class ScenesTelephone implements Chapitre {

    private static final String OPERATEUR = "Mairie";
    private static final BlockPos A = new BlockPos(0, 0, 0);
    @Nullable private static volatile JoueurDeTest appelante;
    private static final List<String> RELEVES = java.util.Collections.synchronizedList(new ArrayList<>());

    @Override
    public String id() {
        return "telephone";
    }

    @Override
    public void scenes(Atelier atelier) {
        antenne(atelier);
        fonder(atelier);
        fil(atelier);
        alternateur(atelier);
        prise(atelier);
        etabli(atelier);
        urgence(atelier);
    }

    // --- l'antenne et ses trois états --------------------------------------------------------------------------------

    private static final BlockPos ETEINTE = new BlockPos(-12, 0, 1);
    private static final BlockPos PANNE = new BlockPos(12, 0, 1);

    private void antenne(Atelier atelier) {
        atelier.scene("antenne")
                .serveur("île et antennes", lieu -> {
                    ile(lieu);
                    fonder(lieu, A);
                    alimenter(lieu, A);
                    poserAntenne(lieu, ETEINTE);
                    poserAntenne(lieu, PANNE);
                    alimenter(lieu, PANNE);
                })
                .attendre("antenne en marche", () -> etat(A) == MachineState.RUNNING)
                .attendre("seconde sans courant", () -> etat(ETEINTE) == MachineState.NO_POWER)
                .attendre("troisième en marche", () -> etat(PANNE) == MachineState.RUNNING)
                .serveur("troisième en panne", lieu -> {
                    if (lieu.monde().getBlockEntity(lieu.p(12, 0, 1)) instanceof ElectricMachineBlockEntity m) {
                        m.breakDown(FaultType.FUSE);
                    }
                })
                .attendre("troisième en panne vue", () -> etat(PANNE) == MachineState.FAULT)
                .camera(new Vec3(1.45, 0.95, 2.25), new Vec3(0.5, 0.5, 0.5))
                .repereBloc("voyant", A, 0.5, 1.12, 0.5)
                .repereBloc("mat", A, 0.82, 0.38, 0.5)
                .repereBloc("socle", A, 0.12, 0.06, 0.88)
                .prise("antenne-hero")
                .regarder(A, new Vec3(0.55, 0.3, 1.55))
                .prise("etat-en-marche")
                .regarder(ETEINTE, new Vec3(0.55, 0.3, 1.55))
                .prise("etat-sans-courant")
                .regarder(PANNE, new Vec3(0.55, 0.3, 1.55))
                .pause(1500)
                .prise("etat-en-panne");
    }

    // --- « Antenne à fonder », puis le panneau --------------------------------------------------------------------------

    private void fonder(Atelier atelier) {
        atelier.scene("fonder")
                .serveur("île et antenne neuve", lieu -> {
                    ile(lieu);
                    OperateurDEssai.oublier(lieu.serveur(), lieu.joueur().getUUID());
                    poserAntenne(lieu, A);
                    alimenter(lieu, A);
                })
                .regarder(A, new Vec3(0.3, 0.6, 2.4))
                .ouvrirBloc(A, Direction.SOUTH, OperatorPanelScreen.class)
                .attendre("champ du nom", () -> panneau() != null && disposition(panneau()).name() != null)
                .client("nom tapé", mc -> {
                    for (char c : OPERATEUR.toCharArray()) {
                        mc.screen.charTyped(c, 0);
                    }
                })
                .pause(400)
                .repereInterface("champ", mc -> {
                    var n = disposition(panneau()).name();
                    // Dans la partie vide du champ, pour ne pas cacher le nom tapé.
                    return n == null ? null : new double[] {n.field().x() + n.field().w() * 0.72,
                            n.field().y() + n.field().h() / 2.0};
                })
                .repereInterface("fonder", mc -> {
                    var n = disposition(panneau()).name();
                    // Sous le bouton, pour ne pas cacher son libellé.
                    return n == null ? null : new double[] {n.button().x() + n.button().w() / 2.0,
                            n.button().y() + n.button().h() + 9};
                })
                .prise("antenne-a-fonder")
                .client("Fonder", mc -> {
                    double[] b = centre(disposition(panneau()).name().button());
                    mc.screen.mouseClicked(b[0], b[1], 0);
                })
                .attendre("opérateur fondé, en marche", () -> panneau() != null
                        && !panneau().view().operatorId().isEmpty() && etat(A) == MachineState.RUNNING)
                .serveur("cartes et demandes", lieu -> {
                    PhoneOperators registre = PhoneOperators.get(lieu.serveur());
                    PhoneOperator op = registre.byFounder(lieu.joueur().getUUID()).orElseThrow();
                    long now = EconomyClock.now();
                    registre.issue(op.id(), UUID.randomUUID(), "Jonas Mercier", RandomSource.create(), now);
                    registre.issue(op.id(), UUID.randomUUID(), "Inès Lebrun", RandomSource.create(), now);
                    registre.request(op.id(), UUID.randomUUID(), "Maëlle Rivière", now);
                })
                .attendre("panneau à jour", () -> panneau() != null && !panneau().view().requests().isEmpty()
                        && panneau().view().cards().size() >= 2, 10_000)
                .pause(1500)
                .repereInterface("onglets", mc -> {
                    // Entre les onglets Réseau et Cartes, sans cacher les libellés.
                    var r = disposition(panneau()).tabs().get(1);
                    return new double[] {r.x() - 2, r.y() + r.h() / 2.0};
                })
                .repereInterface("etat", mc -> texte(panneau(), "En marche"))
                .repereInterface("principale", mc -> texte(panneau(), "Principale"))
                .prise("panneau-reseau")
                .client("onglet Cartes", mc -> cliquer(mc, panneau().centreOf(OperatorPanelLayout.Tab.CARDS, null, "")))
                .attendre("onglet Cartes", () -> panneau() != null && panneau().tab() == OperatorPanelLayout.Tab.CARDS)
                .pause(600)
                .repereInterface("emettre", mc -> {
                    double[] c = panneau().centreOf(null, OperatorActionPacket.Action.ISSUE, "");
                    // À gauche du bouton, pour ne pas cacher « Émettre ».
                    return c == null ? null : new double[] {c[0] - 40, c[1]};
                })
                .prise("panneau-cartes")
                .client("onglet Tarif", mc -> cliquer(mc, panneau().centreOf(OperatorPanelLayout.Tab.TARIFF, null, "")))
                .attendre("onglet Tarif", () -> panneau() != null && panneau().tab() == OperatorPanelLayout.Tab.TARIFF)
                .pause(600)
                .prise("panneau-tarif")
                .fermer();
    }

    // --- le fil : connecteurs et bobine ---------------------------------------------------------------------------

    private static final BlockPos SOURCE = new BlockPos(4, 0, 0);

    private void fil(Atelier atelier) {
        atelier.scene("fil")
                .serveur("île, antenne fondée sans courant, source", lieu -> {
                    ile(lieu);
                    fonder(lieu, A);
                    lieu.poser(4, 0, 0, bloc("createaddition:creative_energy").defaultBlockState());
                })
                .attendre("antenne sans courant", () -> etat(A) == MachineState.NO_POWER)
                .camera(new Vec3(2.6, 1.9, 4.3), new Vec3(2.3, 0.45, 0.4))
                // Constat : un clic droit sur l'antenne, connecteur en main, sans s'accroupir.
                .serveur("connecteur sur l'antenne, debout", lieu -> {
                    InteractionResult r = utiliser(lieu, objet("createaddition:connector"), A, Direction.EAST, false);
                    constat("connecteur tenu, clic droit debout sur l'antenne : " + r + ", bloc à l'est : "
                            + nomBloc(lieu, lieu.p(1, 0, 0)));
                })
                .pause(1200)
                .client("constat de l'écran", mc -> constat("écran ouvert après ce clic : "
                        + (mc.screen == null ? "aucun" : mc.screen.getClass().getSimpleName())))
                .fermer()
                .pause(500)
                .sequence("fil-pose", 7000, 100, lieu -> {
                    int[] t = {0};
                    // Une seule bobine : le premier clic y garde le connecteur, le second tire le fil.
                    ItemStack bobine = objet("createaddition:copper_spool");
                    lieu.tenir(monde -> {
                        int n = t[0]++;
                        if (n == 15) {
                            InteractionResult r = utiliser(lieu, objet("createaddition:connector"), SOURCE,
                                    Direction.UP, false);
                            constat("connecteur posé sur la source (clic debout) : " + r + ", mode « "
                                    + mode(lieu, lieu.p(4, 1, 0)) + " »");
                        } else if (n == 35) {
                            InteractionResult r = utiliser(lieu, objet("createaddition:connector"), A, Direction.EAST,
                                    true);
                            constat("connecteur posé sur l'antenne (accroupi) : " + r + ", mode « "
                                    + mode(lieu, lieu.p(1, 0, 0)) + " »");
                        } else if (n == 60) {
                            utiliser(lieu, bobine, lieu.p(4, 1, 0), Direction.UP, false, true);
                        } else if (n == 80) {
                            utiliser(lieu, bobine, lieu.p(1, 0, 0), Direction.SOUTH, false, true);
                        } else if (n == 100) {
                            constat("après le fil : connecteur de la source « " + mode(lieu, lieu.p(4, 1, 0))
                                    + " », connecteur de l'antenne « " + mode(lieu, lieu.p(1, 0, 0)) + " »");
                        }
                    });
                })
                .attendre("antenne en marche par le fil", () -> etat(A) == MachineState.RUNNING, 15_000)
                .serveur("constat final", lieu -> constat("antenne par le fil : " + etatServeur(lieu, A)
                        + ", connecteur de la source « " + mode(lieu, lieu.p(4, 1, 0)) + " », connecteur de l'antenne « "
                        + mode(lieu, lieu.p(1, 0, 0)) + " »"))
                .pause(800)
                .repereBloc("connecteur-source", new BlockPos(4, 1, 0), 0.5, 0.35, 0.5)
                .repereBloc("connecteur-antenne", new BlockPos(1, 0, 0), 0.2, 0.5, 0.5)
                .repereMonde("fil", new Vec3(2.7, 1.0, 0.5))
                .repereBloc("voyant", A, 0.5, 0.9, 0.5)
                .prise("fil");
    }

    // --- l'alternateur contre l'antenne (constat à 32 tr/min, témoin à 16) ------------------------------------------

    private static final BlockPos B = new BlockPos(0, 0, 9);

    private void alternateur(Atelier atelier) {
        atelier.scene("alternateur")
                .serveur("île, deux antennes, deux alternateurs", lieu -> {
                    ile(lieu);
                    fonder(lieu, A);
                    poserAntenne(lieu, B);
                    RELEVES.clear();
                    alternateurEt(lieu, new BlockPos(1, 0, 0), 32);
                    alternateurEt(lieu, new BlockPos(1, 0, 9), 16);
                    int[] t = {0};
                    lieu.tenir(monde -> {
                        if (t[0]++ % 20 == 0) {
                            RELEVES.add(String.format(Locale.ROOT, "t=%ds 32tr/min:%s %dFE 16tr/min:%s %dFE", t[0] / 20,
                                    etatServeur(lieu, A), energie(lieu, A), etatServeur(lieu, B), energie(lieu, B)));
                        }
                    });
                })
                .pause(40_000)
                .serveur("constat", lieu -> {
                    synchronized (RELEVES) {
                        constat("alternateur contre l'antenne, relevés une fois par seconde : " + String.join(" | ",
                                RELEVES));
                    }
                })
                .regarder(A, new Vec3(-1.4, 0.9, 2.6))
                .repereBloc("antenne", A, 0.5, 0.55, 0.5)
                .repereBloc("alternateur", new BlockPos(1, 0, 0), 0.5, 0.5, 0.5)
                .repereBloc("moteur", new BlockPos(2, 0, 0), 0.5, 0.5, 0.5)
                .prise("alternateur");
    }

    /** Un alternateur en {@code pos} tourné vers l'est (le moteur), un moteur créatif à {@code tours} tr/min derrière. */
    private static void alternateurEt(Lieu lieu, BlockPos pos, int tours) {
        ServerLevel monde = lieu.monde();
        BlockPos alt = lieu.p(pos.getX(), pos.getY(), pos.getZ());
        BlockPos mot = alt.east();
        monde.setBlock(alt, bloc("createaddition:alternator").defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.EAST), 3);
        monde.setBlock(mot, bloc("create:creative_motor").defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.WEST), 3);
        if (monde.getBlockEntity(mot) != null) {
            CompoundTag tag = monde.getBlockEntity(mot).saveWithoutMetadata();
            tag.putInt("ScrollValue", tours);
            monde.getBlockEntity(mot).load(tag);
        }
    }

    // --- la prise murale en charge ----------------------------------------------------------------------------------

    private static final BlockPos MUR = new BlockPos(0, 1, -1);
    private static final BlockPos PRISE = new BlockPos(0, 1, 0);

    private void prise(Atelier atelier) {
        atelier.scene("prise")
                .serveur("île, mur, prise, fil", lieu -> {
                    ile(lieu);
                    for (int x = -2; x <= 1; x++) {
                        for (int y = 0; y <= 1; y++) {
                            lieu.poser(x, y, -1, Blocks.OAK_PLANKS.defaultBlockState());
                        }
                    }
                    lieu.poser(5, 0, -1, bloc("createaddition:creative_energy").defaultBlockState());
                    InteractionResult r = utiliser(lieu, new ItemStack(PhoneRegistry.WALL_SOCKET_ITEM.get()),
                            lieu.p(0, 1, -1), Direction.SOUTH, false, true);
                    constat("prise posée sur le mur : " + r + ", bloc : " + nomBloc(lieu, lieu.p(0, 1, 0)));
                    utiliser(lieu, objet("createaddition:connector"), lieu.p(5, 0, -1), Direction.UP, false, true);
                    utiliser(lieu, objet("createaddition:connector"), lieu.p(0, 1, -1), Direction.UP, false, true);
                    constat("connecteur posé sur le mur : mode « " + mode(lieu, lieu.p(0, 2, -1)) + " »");
                    ItemStack bobine = objet("createaddition:copper_spool");
                    utiliser(lieu, bobine, lieu.p(5, 1, -1), Direction.UP, false, true);
                    utiliser(lieu, bobine, lieu.p(0, 2, -1), Direction.UP, false, true);
                })
                .regarder(PRISE, new Vec3(1.1, 0.35, 2.4))
                .sequence("prise-branchement", 4000, 100, lieu -> {
                    int[] t = {0};
                    lieu.tenir(monde -> {
                        if (t[0]++ == 20) {
                            ItemStack phone = new ItemStack(PhoneRegistry.PHONE.get());
                            PhoneItem.store(phone, PhoneConfig.capacity() / 5);
                            InteractionResult r = utiliser(lieu, phone, lieu.p(0, 1, 0), Direction.SOUTH, false,
                                    true);
                            constat("téléphone branché : " + r);
                        }
                    });
                })
                .attendre("prise en charge", () -> etat(PRISE) == MachineState.RUNNING, 15_000)
                .serveur("charge relevée", lieu -> {
                    if (lieu.monde().getBlockEntity(lieu.p(0, 1, 0)) instanceof WallSocketBlockEntity s) {
                        constat("prise : téléphone " + PhoneItem.stored(s.phone()) + " FE, connecteur du mur « "
                                + mode(lieu, lieu.p(0, 2, -1)) + " », état " + s.state());
                    }
                })
                .pause(500)
                .camera(new Vec3(2.9, 2.1, 5.2), new Vec3(2.5, 0.9, -0.5))
                .repereBloc("prise", PRISE, 0.5, -0.12, 0.1)
                .repereBloc("connecteur-mur", new BlockPos(0, 2, -1), 0.5, 0.3, 0.5)
                .repereBloc("connecteur-source", new BlockPos(5, 1, -1), 0.5, 0.3, 0.5)
                .prise("prise-en-charge")
                .regarder(PRISE, new Vec3(0.45, 0.15, 1.25))
                .repereBloc("voyant", PRISE, 0.1, 0.78, 0.05)
                .prise("prise-gros-plan");
    }

    // --- l'antenne fabriquée à l'établi ---------------------------------------------------------------------------

    private static final BlockPos ETABLI = new BlockPos(0, 0, 0);
    private static final ResourceLocation RECETTE = new ResourceLocation("oneforallsurvival", "antenna");

    private void etabli(Atelier atelier) {
        boolean[] fait = new boolean[8];
        atelier.scene("etabli")
                .serveur("établi, rôle et ingrédients", lieu -> {
                    ile(lieu);
                    lieu.poser(0, 0, 0, BenchRegistry.ENGINEER_BENCH.get().defaultBlockState()
                            .setValue(EngineerBenchBlock.FACING, Direction.SOUTH));
                    // Alimenté (Générateur Créatif collé derrière) : l'écran dit « Alimenté », la fabrication va vite.
                    lieu.poser(0, 0, -1, bloc("createaddition:creative_energy").defaultBlockState());
                    ServerPlayer j = lieu.joueur();
                    RoleSavedData roles = RoleSavedData.get(lieu.serveur());
                    roles.book().assign(j.getUUID(), "ingenieur");
                    roles.setDirty();
                    j.getInventory().add(new ItemStack(Items.LIGHTNING_ROD));
                    j.getInventory().add(new ItemStack(Items.COPPER_INGOT, 2));
                    j.getInventory().add(new ItemStack(Items.REDSTONE_BLOCK));
                    j.getInventory().add(new ItemStack(Items.IRON_INGOT, 2));
                    j.getInventory().add(new ItemStack(Items.IRON_BARS));
                })
                .regarder(ETABLI, new Vec3(0.0, 0.7, 2.4))
                .ouvrirBloc(ETABLI, Direction.SOUTH, EngineerBenchScreen.class)
                .attendre("catalogue reçu", () -> !BenchClientState.catalog().isEmpty())
                .attendre("établi alimenté", () -> etat(ETABLI) == MachineState.RUNNING
                        || etat(ETABLI) == MachineState.STOPPED, 10_000)
                .client("recherche vide, objets du mod", mc -> {
                    EngineerBenchScreen e = (EngineerBenchScreen) mc.screen;
                    e.setSearchText("");
                    // La catégorie du mod (comme un clic sur « One For All ») : la liste reste en français.
                    for (var r : BenchClientState.catalog()) {
                        if (r.id().equals(RECETTE)) {
                            e.setCategory(BenchClientState.modOf(r));
                        }
                    }
                })
                .pause(800)
                .sequence("etabli-antenne", 19_000, 200, null, (mc, ms) -> {
                    if (mc.screen instanceof EngineerBenchScreen e) {
                        String mot = "ante";
                        for (int k = 1; k <= mot.length(); k++) {
                            if (ms >= 700 + 280L * k && !fait[k - 1]) {
                                fait[k - 1] = true;
                                e.setSearchText(mot.substring(0, k));
                            }
                        }
                        if (ms >= 2600 && !fait[4]) {
                            fait[4] = true;
                            if (!e.selectById(RECETTE)) {
                                throw new IllegalStateException("recette de l'antenne absente de la liste");
                            }
                        }
                        if (ms >= 3800 && !fait[5]) {
                            fait[5] = true;
                            int[] p = e.hoverPoint("crafter");
                            e.mouseClicked(p[0], p[1], 0);
                        }
                        if (ms >= 4800 && !fait[6] && BenchClientState.queue().isEmpty()
                                && BenchClientState.stockItems() >= 1) {
                            fait[6] = true;
                            int[] p = e.hoverPoint("stock");
                            e.mouseClicked(p[0], p[1], 0);
                        }
                    }
                })
                .attendre("stock ouvert", () -> Minecraft.getInstance().screen instanceof BenchStockScreen, 5_000)
                .client("constat de l'établi", mc -> constat("établi : file " + BenchClientState.queue().size()
                        + ", objets en stock " + BenchClientState.stockItems()))
                .fermer();
    }

    // --- l'alerte chez le policier -----------------------------------------------------------------------------------

    private void urgence(Atelier atelier) {
        atelier.scene("urgence")
                .serveur("île, antenne, policier, appelante", lieu -> {
                    ile(lieu);
                    fonder(lieu, A);
                    alimenter(lieu, A);
                    ServerPlayer j = lieu.joueur();
                    RoleSavedData roles = RoleSavedData.get(lieu.serveur());
                    roles.book().assign(j.getUUID(), "policier");
                    roles.setDirty();
                    ItemStack tel = new ItemStack(PhoneRegistry.PHONE.get());
                    PhoneItem.store(tel, PhoneConfig.capacity());
                    PhoneItem.ensurePhoneId(tel);
                    j.getInventory().selected = 0;
                    j.getInventory().setItem(0, tel);
                    JoueurDeTest vieille = appelante;
                    if (vieille != null && vieille.connecte()) {
                        vieille.quitter();
                    }
                    Vec3 ou = lieu.v(-3.5, 0, -5.5);
                    JoueurDeTest m = JoueurDeTest.rejoindre(lieu.monde(), "Maelle", ou);
                    m.joueur().teleportTo(lieu.monde(), ou.x, ou.y, ou.z, 160F, 0F);
                    ItemStack telM = new ItemStack(PhoneRegistry.PHONE.get());
                    PhoneItem.store(telM, PhoneConfig.capacity());
                    PhoneItem.ensurePhoneId(telM);
                    m.joueur().setItemInHand(InteractionHand.MAIN_HAND, telM);
                    appelante = m;
                })
                // Maelle à gauche de l'image, le chat en dessous ; ni réticule ni main (rien de parasite).
                .camera(new Vec3(1.5, 1.62, 4.0), new Vec3(1.0, 1.1, -5.0))
                .client("chat visible et vidé", mc -> {
                    mc.gui.getChat().clearMessages(false);
                    mc.options.chatVisibility().set(ChatVisiblity.FULL);
                    mc.options.broadcastOptions();
                    mc.player.getInventory().selected = 0;
                    WikiStudio.ath(mc, true);
                    WikiStudio.reticule(false);
                    mc.gameRenderer.setRenderHand(false);
                })
                .pause(800)
                .serveur("l'appelante compose le 17", lieu -> {
                    EmergencyService.forgetCooldown();
                    ServerPlayer c = appelante.joueur();
                    CallService.Dial d = CallService.dial(c, c.getMainHandItem(), false, "17");
                    constat("Maelle compose le 17 : " + d.outcome());
                })
                .pause(2500)
                // Échelle 3 : le message du chat reste lisible une fois l'image réduite pour le wiki.
                .echelle(3)
                .pause(400)
                .prise("urgence-policier")
                .serveur("rangement", lieu -> {
                    JoueurDeTest m = appelante;
                    if (m != null && m.connecte()) {
                        m.quitter();
                    }
                    appelante = null;
                    RoleSavedData roles = RoleSavedData.get(lieu.serveur());
                    roles.book().assign(lieu.joueur().getUUID(), "citoyen");
                    roles.setDirty();
                })
                .client("chat masqué", mc -> {
                    mc.gui.getChat().clearMessages(false);
                    mc.options.chatVisibility().set(ChatVisiblity.HIDDEN);
                    mc.options.broadcastOptions();
                    WikiStudio.ath(mc, false);
                });
    }

    // --- décor ---------------------------------------------------------------------------------------------------------

    /**
     * Un bout d'île autour de l'origine : la mer au nord (z ≤ -13, rive ondulée), une plage de sable, l'herbe du monde
     * plat au sud, deux palmiers (troncs de jungle, palmes de feuilles persistantes).
     */
    static void ile(Lieu lieu) {
        ServerLevel monde = lieu.monde();
        BlockState eau = Blocks.WATER.defaultBlockState();
        BlockState sable = Blocks.SAND.defaultBlockState();
        for (int x = -56; x <= 56; x++) {
            int rive = -13 + (int) Math.round(1.6 * Math.sin(x / 4.0) + Math.sin(x / 9.0));
            for (int z = -110; z <= rive + 5; z++) {
                BlockPos sol = lieu.p(x, -1, z);
                if (z <= rive) {
                    monde.setBlock(sol, eau, 2);
                    monde.setBlock(sol.below(), sable, 2);
                } else {
                    monde.setBlock(sol, sable, 2);
                }
            }
        }
        palmier(lieu, -6, -8, 5);
        palmier(lieu, 8, -9, 6);
    }

    private static void palmier(Lieu lieu, int x, int z, int hauteur) {
        BlockState tronc = Blocks.JUNGLE_LOG.defaultBlockState();
        BlockState palme = Blocks.JUNGLE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (int y = 0; y < hauteur; y++) {
            lieu.poser(x, y, z, tronc);
        }
        int h = hauteur;
        lieu.poser(x, h, z, palme);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            for (int k = 1; k <= 3; k++) {
                lieu.poser(x + d.getStepX() * k, h - (k == 3 ? 1 : 0), z + d.getStepZ() * k, palme);
            }
        }
        lieu.poser(x + 1, h, z + 1, palme);
        lieu.poser(x - 1, h, z - 1, palme);
        lieu.poser(x + 1, h, z - 1, palme);
        lieu.poser(x - 1, h, z + 1, palme);
    }

    // --- outils --------------------------------------------------------------------------------------------------------

    private static Block bloc(String id) {
        Block b = BuiltInRegistries.BLOCK.get(new ResourceLocation(id));
        if (b == Blocks.AIR) {
            throw new IllegalStateException(id + " absent (pack non chargé ?)");
        }
        return b;
    }

    private static ItemStack objet(String id) {
        Item i = BuiltInRegistries.ITEM.get(new ResourceLocation(id));
        if (i == Items.AIR) {
            throw new IllegalStateException(id + " absent (pack non chargé ?)");
        }
        return new ItemStack(i, 16);
    }

    private static void constat(String texte) {
        WikiStudio.journal("constat : " + texte);
    }

    /** Pose une antenne neuve par un clic du joueur sur le bloc du dessous. */
    private static BlockPos poserAntenne(Lieu lieu, BlockPos rel) {
        BlockPos p = lieu.p(rel.getX(), rel.getY(), rel.getZ());
        InteractionResult r = utiliser(lieu, new ItemStack(PhoneRegistry.ANTENNA_ITEM.get()), p.below(), Direction.UP,
                false, true);
        if (!(lieu.monde().getBlockEntity(p) instanceof AntennaBlockEntity)) {
            throw new IllegalStateException("antenne non posée en " + p.toShortString() + " : " + r);
        }
        return p;
    }

    /** L'opérateur du joueur oublié, puis une antenne posée et nommée {@link #OPERATEUR}. */
    private static void fonder(Lieu lieu, BlockPos rel) {
        OperateurDEssai.oublier(lieu.serveur(), lieu.joueur().getUUID());
        BlockPos p = poserAntenne(lieu, rel);
        Optional<PhoneOperators.Result> r = AntennaBlock.found(lieu.joueur(), p, OPERATEUR);
        if (r.isEmpty() || !r.get().founded()) {
            throw new IllegalStateException("fondation de " + OPERATEUR + " : " + r);
        }
    }

    /** 100 FE par tic dans la machine jusqu'à la fin de la scène (source invisible : l'image ne montre que l'état). */
    private static void alimenter(Lieu lieu, BlockPos rel) {
        BlockPos p = lieu.p(rel.getX(), rel.getY(), rel.getZ());
        lieu.tenir(monde -> {
            if (monde.getBlockEntity(p) instanceof ElectricMachineBlockEntity m) {
                m.energy().receiveEnergy(100, false);
            }
        });
    }

    private static InteractionResult utiliser(Lieu lieu, ItemStack pile, BlockPos rel, Direction face,
            boolean accroupi) {
        return utiliser(lieu, pile, lieu.p(rel.getX(), rel.getY(), rel.getZ()), face, accroupi, true);
    }

    /**
     * Un clic droit du joueur avec {@code pile} en main sur la face {@code face} du bloc {@code pos} (absolu), par le
     * chemin du serveur ({@code ServerPlayerGameMode.useItemOn}) qu'emprunte le paquet du client ; {@code accroupi} : le
     * joueur se baisse le temps du clic. La main reprend ce qu'elle tenait.
     */
    private static InteractionResult utiliser(Lieu lieu, ItemStack pile, BlockPos pos, Direction face,
            boolean accroupi, boolean absolu) {
        ServerPlayer j = lieu.joueur();
        ItemStack avant = j.getItemInHand(InteractionHand.MAIN_HAND);
        j.setItemInHand(InteractionHand.MAIN_HAND, pile);
        j.setShiftKeyDown(accroupi);
        try {
            Vec3 point = Vec3.atCenterOf(pos).add(face.getStepX() * 0.45, face.getStepY() * 0.45,
                    face.getStepZ() * 0.45);
            return j.gameMode.useItemOn(j, lieu.monde(), pile, InteractionHand.MAIN_HAND,
                    new BlockHitResult(point, face, pos, false));
        } finally {
            j.setShiftKeyDown(false);
            j.setItemInHand(InteractionHand.MAIN_HAND, avant);
        }
    }

    private static String mode(Lieu lieu, BlockPos pos) {
        BlockState s = lieu.monde().getBlockState(pos);
        String id = String.valueOf(BuiltInRegistries.BLOCK.getKey(s.getBlock()));
        if (!id.startsWith("createaddition:")) {
            return "pas de connecteur (" + id + ")";
        }
        return ReseauxDev.propriete(s, "mode").toLowerCase(Locale.ROOT);
    }

    private static String nomBloc(Lieu lieu, BlockPos pos) {
        return String.valueOf(BuiltInRegistries.BLOCK.getKey(lieu.monde().getBlockState(pos).getBlock()));
    }

    private static String etatServeur(Lieu lieu, BlockPos rel) {
        BlockState s = lieu.monde().getBlockState(lieu.p(rel.getX(), rel.getY(), rel.getZ()));
        return s.hasProperty(ElectricMachineBlock.STATE) ? s.getValue(ElectricMachineBlock.STATE).name() : "?";
    }

    private static int energie(Lieu lieu, BlockPos rel) {
        return lieu.monde().getBlockEntity(lieu.p(rel.getX(), rel.getY(), rel.getZ()))
                instanceof ElectricMachineBlockEntity m ? m.energy().getEnergyStored() : -1;
    }

    /** L'état de la machine en {@code rel} vu par le client, ou {@code null}. */
    private static MachineState etat(BlockPos rel) {
        Minecraft mc = Minecraft.getInstance();
        BlockPos o = WikiStudio.origineEnCours();
        if (o == null || mc.level == null) {
            return null;
        }
        BlockState s = mc.level.getBlockState(o.offset(rel));
        return s.hasProperty(ElectricMachineBlock.STATE) ? s.getValue(ElectricMachineBlock.STATE) : null;
    }

    @Nullable
    private static OperatorPanelScreen panneau() {
        return Minecraft.getInstance().screen instanceof OperatorPanelScreen p ? p : null;
    }

    private static Method methodeDisposition;

    /** La mise en page du panneau ouvert (méthode privée, lue pour placer les repères et les clics). */
    private static OperatorPanelLayout.Layout disposition(OperatorPanelScreen s) {
        try {
            if (methodeDisposition == null) {
                methodeDisposition = OperatorPanelScreen.class.getDeclaredMethod("currentLayout");
                methodeDisposition.setAccessible(true);
            }
            return (OperatorPanelLayout.Layout) methodeDisposition.invoke(s);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static double[] centre(OperatorPanelLayout.Rect r) {
        return new double[] {r.x() + r.w() / 2.0, r.y() + r.h() / 2.0};
    }

    /** Le milieu du premier texte du volet montré qui contient {@code mot}, ou {@code null}. */
    @Nullable
    private static double[] texte(OperatorPanelScreen s, String mot) {
        var l = disposition(s);
        if (l.panes().isEmpty()) {
            return null;
        }
        for (var row : l.panes().get(s.tab().ordinal()).rows()) {
            for (var t : row.texts()) {
                if (t.shown().contains(mot)) {
                    int debut = t.shown().indexOf(mot);
                    var font = Minecraft.getInstance().font;
                    // Juste à gauche du mot, pour ne pas le cacher.
                    double x = t.x() + font.width(t.shown().substring(0, debut)) * t.scale() - 20;
                    return new double[] {x, t.y() + 4 * t.scale()};
                }
            }
            for (var b : row.buttons()) {
                if (b.label().shown().contains(mot)) {
                    return centre(b.box());
                }
            }
        }
        return null;
    }

    private static void cliquer(Minecraft mc, @Nullable double[] p) {
        if (p == null) {
            throw new IllegalStateException("cible absente du panneau");
        }
        mc.screen.mouseClicked(p[0], p[1], 0);
    }
}
