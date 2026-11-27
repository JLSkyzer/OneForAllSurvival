package dev.oneforallsurvival.dev.wiki;

import static dev.oneforallsurvival.dev.wiki.ScenesElectricite.alternateur;
import static dev.oneforallsurvival.dev.wiki.ScenesElectricite.be;
import static dev.oneforallsurvival.dev.wiki.ScenesElectricite.face;
import static dev.oneforallsurvival.dev.wiki.ScenesElectricite.gui;
import static dev.oneforallsurvival.dev.wiki.ScenesElectricite.nomEcran;
import static dev.oneforallsurvival.dev.wiki.ScenesTelephone.constat;
import static dev.oneforallsurvival.dev.wiki.ScenesTelephone.etat;
import static dev.oneforallsurvival.dev.wiki.ScenesTelephone.etatServeur;
import static dev.oneforallsurvival.dev.wiki.ScenesTelephone.ile;

import dev.oneforallsurvival.dev.FilmTournage;
import dev.oneforallsurvival.dev.JoueurDeTest;
import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinDropEntity;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.EconomyCalendar;
import dev.oneforallsurvival.economy.EconomyClock;
import dev.oneforallsurvival.economy.EconomyConfig;
import dev.oneforallsurvival.economy.EconomyRegistry;
import dev.oneforallsurvival.economy.Purse;
import dev.oneforallsurvival.economy.SafeAccounts;
import dev.oneforallsurvival.economy.SafeBlockEntity;
import dev.oneforallsurvival.economy.TreasuryAccount;
import dev.oneforallsurvival.economy.client.CoinAccountScreen;
import dev.oneforallsurvival.economy.client.InvoiceFormScreen;
import dev.oneforallsurvival.economy.client.InvoiceScreen;
import dev.oneforallsurvival.economy.client.PurseScreen;
import dev.oneforallsurvival.economy.client.SafeScreen;
import dev.oneforallsurvival.economy.client.StallCashScreen;
import dev.oneforallsurvival.economy.client.StallOwnerScreen;
import dev.oneforallsurvival.economy.client.StallShopScreen;
import dev.oneforallsurvival.economy.client.TradeScreen;
import dev.oneforallsurvival.economy.client.TreasuryReasonScreen;
import dev.oneforallsurvival.economy.client.TreasuryScreen;
import dev.oneforallsurvival.economy.invoice.InvoiceService;
import dev.oneforallsurvival.economy.quest.QuestRef;
import dev.oneforallsurvival.economy.quest.QuestSyncPacket;
import dev.oneforallsurvival.economy.quest.client.QuestClientCache;
import dev.oneforallsurvival.economy.quest.client.QuestKeys;
import dev.oneforallsurvival.economy.quest.client.QuestScreen;
import dev.oneforallsurvival.economy.stall.MarketStallBlock;
import dev.oneforallsurvival.economy.stall.MarketStallBlockEntity;
import dev.oneforallsurvival.economy.stall.StallBuyback;
import dev.oneforallsurvival.economy.stall.StallOffer;
import dev.oneforallsurvival.economy.stall.StallShopMenu;
import dev.oneforallsurvival.economy.stall.StallTrade;
import dev.oneforallsurvival.economy.town.TownBoardBlock;
import dev.oneforallsurvival.economy.trade.TradeActionPacket;
import dev.oneforallsurvival.economy.trade.TradeMenu;
import dev.oneforallsurvival.food.DryerBlockEntity;
import dev.oneforallsurvival.food.FoodRegistry;
import dev.oneforallsurvival.grid.BreakerBlock;
import dev.oneforallsurvival.grid.BreakerBlockEntity;
import dev.oneforallsurvival.grid.GridRegistry;
import dev.oneforallsurvival.grid.MeterBlockEntity;
import dev.oneforallsurvival.grid.client.SubscriberNameScreen;
import dev.oneforallsurvival.grid.supplier.PowerClient;
import dev.oneforallsurvival.grid.supplier.PowerPairing;
import dev.oneforallsurvival.grid.supplier.PowerPanelPacket;
import dev.oneforallsurvival.grid.supplier.PowerStationBlock;
import dev.oneforallsurvival.grid.supplier.PowerSupplier;
import dev.oneforallsurvival.grid.supplier.PowerSuppliers;
import dev.oneforallsurvival.grid.supplier.client.PowerPanelLayout;
import dev.oneforallsurvival.grid.supplier.client.PowerPanelScreen;
import dev.oneforallsurvival.machine.MachineState;
import dev.oneforallsurvival.repair.RepairRegistry;
import dev.oneforallsurvival.role.RoleEvents;
import dev.oneforallsurvival.role.RoleSavedData;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Le chapitre « Économie » du wiki. Chaque scène joue un parcours de joueur sur le bout d'île du téléphone
 * ({@link ScenesTelephone#ile}) : le joueur de l'atelier (Robin) clique, tape et ouvre les écrans par le vrai chemin du
 * client (clic droit, clic d'écran, touche J, lien du chat) ; l'autre joueur est simulé ({@link JoueurDeTest}) et joue
 * ses gestes par le chemin du serveur (sa commande, son paquet, la règle du jeu en son nom). Les montants sont petits et
 * réalistes, jamais des cas extrêmes. Le passage à minuit (paie, prélèvement) avance l'horloge de l'économie
 * ({@link EconomyClock#setClock}) comme l'essai de bout en bout ; le temps du monde ne bouge pas.
 *
 * <p>Robin est opérateur (monde solo avec commandes) : pour l'étal d'un autre, il s'accroupit, main vide, pour voir la
 * vitrine comme un client ; pour la gestion, il passe par son propre étal.
 */
@Mod.EventBusSubscriber(modid = "oneforallsurvival", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ScenesEconomie implements Chapitre {

    private static final BlockPos A = new BlockPos(0, 0, 0);
    private static final List<JoueurDeTest> FIGURANTS = new ArrayList<>();
    /** Les messages reçus par le client (chat et système), liens compris, depuis le dernier « oubli ». */
    private static final List<Component> CHAT = Collections.synchronizedList(new ArrayList<>());
    private static final ItemStack[] PILE = new ItemStack[1];
    /** Une valeur relevée par le serveur pour une condition du client (une condition ne lit jamais le monde du serveur). */
    private static volatile long releve;
    private static volatile boolean drapeau;

    @Override
    public String id() {
        return "economie";
    }

    @Override
    public void scenes(Atelier atelier) {
        couverture(atelier);
        pieces(atelier);
        changer(atelier);
        ranger(atelier);
        bourse(atelier);
        coffre(atelier);
        echange(atelier);
        etal(atelier);
        facture(atelier);
        tresor(atelier);
        fournisseur(atelier);
        butin(atelier);
        quetes(atelier);
        minuit(atelier);
        equipe(atelier);
    }

    @SubscribeEvent
    public static void onChat(ClientChatReceivedEvent event) {
        if (WikiStudio.ACTIF) {
            CHAT.add(event.getMessage());
        }
    }

    // --- couverture : la place du marché -------------------------------------------------------------------------------

    private void couverture(Atelier atelier) {
        atelier.scene("couverture")
                .serveur("place du marché", lieu -> {
                    ile(lieu);
                    quitterFigurants();
                    JoueurDeTest maelle = figurant(lieu, "Maelle", new Vec3(0.5, 0, -1.0), 0F, true);
                    etalGarni(lieu, A, maelle.joueur());
                    lieu.poser(-3, 0, -1, face(EconomyRegistry.SAFE.get()));
                    lieu.poser(3, 0, -1, face(EconomyRegistry.TREASURY.get()));
                    for (int x = -4; x <= 4; x++) {
                        lieu.poser(x, 0, -2, Blocks.STONE_BRICKS.defaultBlockState());
                        lieu.poser(x, 1, -2, Blocks.STONE_BRICKS.defaultBlockState());
                    }
                    lieu.poser(-4, 0, 1, Blocks.LANTERN.defaultBlockState());
                    lieu.poser(4, 0, 1, Blocks.LANTERN.defaultBlockState());
                    sol(lieu, -1.4, 1.7, Coin.GOLD, 3);
                    sol(lieu, -0.7, 2.1, Coin.SILVER, 5);
                    sol(lieu, 1.0, 2.0, Coin.COPPER, 4);
                })
                .camera(new Vec3(0.5, 1.7, 4.6), new Vec3(0.5, 0.7, -0.4))
                .prise("couverture")
                .serveur("rangement", lieu -> quitterFigurants());
    }

    // --- les pièces ---------------------------------------------------------------------------------------------------

    private void pieces(Atelier atelier) {
        String[] noms = {"cuivre", "argent", "or", "emeraude", "rubis"};
        Scene s = atelier.scene("pieces")
                .serveur("les cinq pièces au sol", lieu -> {
                    ile(lieu);
                    for (Coin c : Coin.values()) {
                        sol(lieu, c.ordinal() - 2 + 0.5, 0.5, c, 1);
                    }
                })
                .pause(1500)
                .camera(new Vec3(0.5, 1.25, 2.6), new Vec3(0.5, 0.15, 0.5));
        for (Coin c : Coin.values()) {
            s.repereMonde(noms[c.ordinal()], new Vec3(c.ordinal() - 2 + 0.5, 0.0, 1.0));
        }
        s.prise("pieces-sol");
        for (String suite : new String[] {"b", "c", "d"}) {
            s.pause(230);
            for (Coin c : Coin.values()) {
                s.repereMonde(noms[c.ordinal()], new Vec3(c.ordinal() - 2 + 0.5, 0.0, 1.0));
            }
            s.prise("pieces-sol-" + suite);
        }
        s
                .echelle(3)
                .ouvrir("infobulle d'une pièce d'or", () -> new ScenesElectricite.Infobulle(new ItemStack(Coin.GOLD.item())))
                .pause(500)
                .prise("pieces-valeur")
                .ouvrir("infobulle d'une pile", () -> new ScenesElectricite.Infobulle(new ItemStack(Coin.SILVER.item(), 23)))
                .pause(500)
                .prise("pieces-pile")
                .fermer()
                .echelle(2)
                // La somme de départ : un joueur neuf entre ; ce qu'il porte est relevé (preuve du montant).
                .serveur("un joueur neuf entre", lieu -> {
                    quitterFigurants();
                    JoueurDeTest noah = figurant(lieu, "Noah", new Vec3(3.5, 0, -3.5), 0F, false);
                    long porte = 0;
                    for (ItemStack st : noah.joueur().getInventory().items) {
                        porte += CoinMath.valueOf(st) + Purse.total(st);
                    }
                    constat("somme de départ reçue par un joueur neuf (Noah) : " + porte + " cuivres ; registre : "
                            + noah.joueur().getInventory().items.stream().filter(st -> !st.isEmpty())
                                    .map(st -> st.getCount() + " " + st.getItem()).toList());
                    quitterFigurants();
                    lieu.joueur().getInventory().setItem(0, new ItemStack(Coin.GOLD.item(), (int) (porte / 100)));
                })
                .client("barre seule", mc -> {
                    mc.player.getInventory().selected = 4;
                    WikiStudio.ath(mc, true);
                    WikiStudio.elements(VanillaGuiOverlay.HOTBAR);
                    WikiStudio.reticule(false);
                    mc.gameRenderer.setRenderHand(false);
                })
                .echelle(3)
                .attendre("pièces d'or dans la barre", () -> Minecraft.getInstance().player.getInventory().getItem(0)
                        .is(Coin.GOLD.item()))
                .pause(600)
                .prise("depart-barre")
                .echelle(2)
                .client("interface masquée", mc -> WikiStudio.ath(mc, false));
    }

    // --- le change : la table, puis la bourse --------------------------------------------------------------------------

    private void changer(Atelier atelier) {
        atelier.scene("changer")
                .serveur("table et bourse", lieu -> {
                    ile(lieu);
                    lieu.poser(0, 0, 0, Blocks.CRAFTING_TABLE.defaultBlockState());
                    ServerPlayer j = lieu.joueur();
                    j.getInventory().setItem(0, new ItemStack(Coin.GOLD.item(), 3));
                    j.getInventory().setItem(1, bourse(0, 3, 14, 26));
                })
                .regarder(A, new Vec3(0.0, 0.9, 2.2))
                .ouvrirBloc(A, Direction.SOUTH, CraftingScreen.class)
                // Une pièce d'or posée dans la grille par de vrais clics : prise, un clic droit dans la grille, le reste rendu.
                .client("prendre l'or", mc -> clicMenu(mc, caseDe(mc, Coin.GOLD), 0, ClickType.PICKUP))
                .attendre("or au curseur", () -> !Minecraft.getInstance().player.containerMenu.getCarried().isEmpty())
                .client("une pièce dans la grille", mc -> clicMenu(mc, 1, 1, ClickType.PICKUP))
                .attendre("pièce posée", () -> !Minecraft.getInstance().player.containerMenu.getSlot(1).getItem().isEmpty())
                .client("le reste rendu", mc -> clicMenu(mc, caseVide(mc), 0, ClickType.PICKUP))
                .attendre("dix pièces d'argent proposées", () -> {
                    ItemStack r = Minecraft.getInstance().player.containerMenu.getSlot(0).getItem();
                    return r.is(Coin.SILVER.item()) && r.getCount() == 10
                            && Minecraft.getInstance().player.containerMenu.getCarried().isEmpty();
                })
                .pause(500)
                .repereCase("grille", 5)
                .repereInterface("resultat", mc -> slotCentre(mc, 0, 26, 0))
                .prise("changer-table")
                .client("dix pièces d'argent prises", mc -> clicMenu(mc, 0, 0, ClickType.QUICK_MOVE))
                .attendre("argent dans l'inventaire", () -> compter(Minecraft.getInstance(), Coin.SILVER) == 10)
                .client("constat de la table", mc -> constat("table : 1 or posé, " + compter(mc, Coin.SILVER)
                        + " argent reçus, or restant " + compter(mc, Coin.GOLD)))
                .fermer()
                .client("la bourse en main", mc -> mc.player.getInventory().selected = 1)
                .attendre("bourse en main", () -> Minecraft.getInstance().player.getMainHandItem().is(EconomyRegistry.PURSE.get()))
                .client("clic droit avec la bourse", mc -> mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND))
                .attendre("écran de la bourse", () -> Minecraft.getInstance().screen instanceof PurseScreen)
                .pause(600)
                .client("bulle « casser »", mc -> ((CoinAccountScreen<?>) mc.screen).pinHover("break.gold"))
                .pause(400)
                .prise("changer-casser")
                .client("bulle grisée", mc -> ((CoinAccountScreen<?>) mc.screen).pinHover("break.emerald"))
                .pause(400)
                .prise("changer-grise")
                .client("sans bulle", mc -> ((CoinAccountScreen<?>) mc.screen).pinHover(null))
                .fermer();
    }

    // --- où ranger : l'entonnoir et le coffre --------------------------------------------------------------------------

    private void ranger(Atelier atelier) {
        atelier.scene("ranger")
                .serveur("entonnoir et coffre", lieu -> {
                    ile(lieu);
                    lieu.poser(0, 0, 0, Blocks.HOPPER.defaultBlockState());
                    lieu.poser(0, 0, 1, Blocks.CHEST.defaultBlockState()
                            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
                    // Des pièces jetées sur l'entonnoir (délai de ramassage ordinaire) : il ne les avale pas.
                    Vec3 a = lieu.v(0.35, 1.3, 0.4);
                    Vec3 b = lieu.v(0.7, 1.3, 0.6);
                    for (CoinDropEntity p : List.of(CoinDropEntity.at(lieu.monde(), a.x, a.y, a.z, Coin.GOLD, 2),
                            CoinDropEntity.at(lieu.monde(), b.x, b.y, b.z, Coin.SILVER, 3))) {
                        p.setDeltaMovement(Vec3.ZERO);
                        lieu.monde().addFreshEntity(p);
                    }
                    lieu.joueur().getInventory().setItem(0, new ItemStack(Coin.GOLD.item(), 4));
                })
                .pause(4000)
                .serveur("constat de l'entonnoir", lieu -> {
                    HopperBlockEntity h = be(lieu, A, HopperBlockEntity.class);
                    int dedans = 0;
                    for (int i = 0; i < h.getContainerSize(); i++) {
                        dedans += h.getItem(i).getCount();
                    }
                    long dessus = lieu.monde().getEntitiesOfClass(CoinDropEntity.class, new AABB(lieu.p(0, 0, 0)).inflate(1))
                            .stream().mapToLong(CoinDropEntity::value).sum();
                    constat("entonnoir après 4 s : " + dedans + " objets dedans, " + dessus + " cuivres de pièces encore dessus");
                })
                .camera(new Vec3(1.3, 1.7, -1.4), new Vec3(0.5, 0.95, 0.5))
                .prise("ranger-entonnoir")
                .pause(230)
                .prise("ranger-entonnoir-b")
                .pause(230)
                .prise("ranger-entonnoir-c")
                .pause(230)
                .prise("ranger-entonnoir-d")
                .regarder(new BlockPos(0, 0, 1), new Vec3(0.0, 0.8, 2.3))
                .ouvrirBloc(new BlockPos(0, 0, 1), Direction.SOUTH, net.minecraft.client.gui.screens.inventory.ContainerScreen.class)
                .client("Maj + clic sur les pièces", mc -> clicMenu(mc, caseDe(mc, Coin.GOLD), 0, ClickType.QUICK_MOVE))
                .pause(1200)
                .client("constat du coffre", mc -> {
                    int dansLeCoffre = 0;
                    for (int i = 0; i < 27; i++) {
                        dansLeCoffre += mc.player.containerMenu.getSlot(i).getItem().getCount();
                    }
                    constat("coffre après Maj + clic : " + dansLeCoffre + " objets dedans, or porté " + compter(mc, Coin.GOLD));
                })
                .repereInterface("piece", mc -> centreCase(mc, caseDe(mc, Coin.GOLD), 10))
                .repereInterface("coffre", mc -> gui(mc, -9, 30))
                .prise("ranger-coffre")
                .fermer();
    }

    // --- la bourse ----------------------------------------------------------------------------------------------------

    private void bourse(Atelier atelier) {
        atelier.scene("bourse")
                .serveur("une bourse et des pièces en vrac", lieu -> {
                    ile(lieu);
                    ServerPlayer j = lieu.joueur();
                    j.getInventory().setItem(0, bourse(0, 2, 7, 15));
                    j.getInventory().setItem(1, new ItemStack(Coin.SILVER.item(), 4));
                    j.getInventory().setItem(2, new ItemStack(Coin.COPPER.item(), 12));
                    PILE[0] = j.getInventory().getItem(0).copy();
                })
                .echelle(3)
                .ouvrir("infobulle de la bourse", () -> new ScenesElectricite.Infobulle(PILE[0]))
                .pause(500)
                .prise("bourse-infobulle")
                .fermer()
                .echelle(2)
                .client("la bourse en main", mc -> mc.player.getInventory().selected = 0)
                .attendre("bourse en main", () -> Minecraft.getInstance().player.getMainHandItem().is(EconomyRegistry.PURSE.get()))
                .client("clic droit avec la bourse", mc -> mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND))
                .attendre("écran de la bourse", () -> Minecraft.getInstance().screen instanceof PurseScreen)
                .pause(700)
                .repereInterface("lignes", mc -> element(mc, "row.gold.1", -24, 0))
                .repereInterface("tout", mc -> element(mc, "deposit_all", -43, 0))
                .repereInterface("regrouper", mc -> element(mc, "group", 43, 0))
                .repereInterface("change", mc -> element(mc, "group.ruby", 0, -19))
                .prise("bourse-ecran")
                .client("bulle d'une ligne", mc -> ((CoinAccountScreen<?>) mc.screen).pinHover("row.silver.10"))
                .pause(400)
                .prise("bourse-bulle")
                .client("sans bulle", mc -> ((CoinAccountScreen<?>) mc.screen).pinHover(null))
                .fermer();
    }

    // --- le coffre-fort -----------------------------------------------------------------------------------------------

    private void coffre(Atelier atelier) {
        atelier.scene("coffre")
                .serveur("le coffre-fort à poser, des pièces", lieu -> {
                    ile(lieu);
                    for (int x = -2; x <= 2; x++) {
                        for (int y = 0; y <= 2; y++) {
                            lieu.poser(x, y, -1, Blocks.SPRUCE_PLANKS.defaultBlockState());
                        }
                    }
                    ServerPlayer j = lieu.joueur();
                    viderCoffreFort(lieu.serveur(), j.getUUID());
                    SafeAccounts.get(lieu.serveur()).forgetSafePos(j.getUUID());
                    j.getInventory().setItem(0, new ItemStack(EconomyRegistry.SAFE_ITEM.get()));
                    j.getInventory().setItem(1, new ItemStack(Coin.GOLD.item(), 2));
                    j.getInventory().setItem(2, new ItemStack(Coin.SILVER.item(), 15));
                    j.getInventory().setItem(3, new ItemStack(Coin.COPPER.item(), 7));
                    j.getInventory().setItem(4, bourse(0, 1, 5, 0));
                })
                .camera(new Vec3(0.5, 1.7, 2.6), new Vec3(0.5, 0.0, 0.5))
                .client("le coffre-fort en main", mc -> mc.player.getInventory().selected = 0)
                .attendre("coffre-fort en main", () -> Minecraft.getInstance().player.getMainHandItem()
                        .is(EconomyRegistry.SAFE_ITEM.get()))
                .client("clic droit sur le sol", mc -> FilmTournage.clicDroit(mc, origine(0, -1, 0), Direction.UP))
                .attendre("coffre-fort posé", () -> blocClient(A).is(EconomyRegistry.SAFE.get()))
                .camera(new Vec3(-0.9, 1.5, 3.0), new Vec3(0.5, 0.45, 0.5))
                .repereBloc("face", A, 0.5, 1.0, 0.55)
                .prise("coffre-hero")
                .client("main vide", mc -> mc.player.getInventory().selected = 8)
                .regarder(A, new Vec3(0.0, 0.8, 2.3))
                .ouvrirBloc(A, Direction.SOUTH, SafeScreen.class)
                .pause(500)
                .client("« Déposer tout »", mc -> cliquerElement(mc, "deposit_all"))
                .attendre("tout déposé", () -> Minecraft.getInstance().screen instanceof SafeScreen s
                        && s.getMenu().total() > 0 && libre(Minecraft.getInstance()) == 0)
                .pause(600)
                .client("constat du coffre-fort", mc -> constat("coffre-fort après « Déposer tout » : "
                        + ((SafeScreen) mc.screen).getMenu().total() + " cuivres"))
                .repereInterface("total", mc -> element(mc, "row.ruby.64", 0, -18))
                .repereInterface("lignes", mc -> element(mc, "row.gold.1", -24, 0))
                .repereInterface("tout", mc -> element(mc, "deposit_all", -43, 0))
                .repereInterface("change", mc -> element(mc, "group.ruby", 0, -19))
                .prise("coffre-ecran")
                .client("bulle d'une ligne", mc -> ((CoinAccountScreen<?>) mc.screen).pinHover("row.gold.10"))
                .pause(400)
                .prise("coffre-bulle")
                .client("sans bulle", mc -> ((CoinAccountScreen<?>) mc.screen).pinHover(null))
                .fermer()
                // Un second coffre-fort : la pose est refusée, le message s'affiche au-dessus de la barre.
                .serveur("un second coffre-fort", lieu -> lieu.joueur().getInventory().setItem(0,
                        new ItemStack(EconomyRegistry.SAFE_ITEM.get())))
                .camera(new Vec3(2.5, 1.7, 2.6), new Vec3(2.5, 0.0, 0.5))
                .client("barre et message", mc -> {
                    mc.player.getInventory().selected = 0;
                    WikiStudio.ath(mc, true);
                    WikiStudio.elements(VanillaGuiOverlay.HOTBAR, VanillaGuiOverlay.RECORD_OVERLAY);
                    WikiStudio.reticule(false);
                    mc.gameRenderer.setRenderHand(false);
                })
                .attendre("coffre-fort en main", () -> Minecraft.getInstance().player.getMainHandItem()
                        .is(EconomyRegistry.SAFE_ITEM.get()))
                .client("clic droit sur le sol", mc -> FilmTournage.clicDroit(mc, origine(2, -1, 0), Direction.UP))
                .pause(900)
                .client("constat du second", mc -> constat("second coffre-fort : bloc posé ? "
                        + blocClient(new BlockPos(2, 0, 0)).is(EconomyRegistry.SAFE.get())))
                .prise("coffre-second")
                .client("interface masquée", mc -> WikiStudio.ath(mc, false));
    }

    // --- l'échange de main à main -------------------------------------------------------------------------------------

    private void echange(Atelier atelier) {
        atelier.scene("echange")
                .serveur("Maelle face au joueur", lieu -> {
                    ile(lieu);
                    quitterFigurants();
                    JoueurDeTest maelle = figurant(lieu, "Maelle", new Vec3(0.5, 0, -0.5), 0F, true);
                    maelle.joueur().getInventory().setItem(0, new ItemStack(Items.BREAD, 5));
                    maelle.joueur().getInventory().setItem(1, new ItemStack(Items.BREAD, 7));
                    ServerPlayer j = lieu.joueur();
                    j.getInventory().setItem(0, bourse(0, 2, 8, 0));
                    j.getInventory().setItem(1, new ItemStack(Coin.GOLD.item(), 1));
                })
                .camera(new Vec3(0.5, 1.62, 3.0), new Vec3(0.5, 1.2, -0.5))
                .client("chat visible", ScenesEconomie::chatVisible)
                .serveur("Maelle propose un échange (/trade Robin)", lieu -> commandeDe(figurant("Maelle"),
                        "trade " + lieu.joueur().getGameProfile().getName()))
                .attendre("lien [Accepter] reçu", () -> lien("/trade accept") != null)
                .echelle(3)
                .pause(700)
                .prise("echange-demande")
                .echelle(2)
                .client("clic sur [Accepter]", mc -> cliquerLien(mc, lien("/trade accept")))
                .attendre("fenêtre d'échange", () -> Minecraft.getInstance().screen instanceof TradeScreen)
                .client("chat masqué", ScenesEconomie::chatMasque)
                .serveur("Maelle pose cinq pains", lieu -> {
                    ServerPlayer m = figurant("Maelle").joueur();
                    ((TradeMenu) m.containerMenu).clicked(TradeMenu.slotOfInventory(0), 0, ClickType.QUICK_MOVE, m);
                })
                .attendre("pains de Maelle visibles", () -> dansEchange(Minecraft.getInstance(), false, Items.BREAD) == 5)
                .client("+1 or", mc -> cliquerElement(mc, "coins.gold.1"))
                .attendre("une pièce d'or versée", () -> dansEchange(Minecraft.getInstance(), true, Coin.GOLD.item()) == 1)
                .client("reçu coché", mc -> cliquerElement(mc, "receipt"))
                .pause(500)
                .client("« Confirmer »", mc -> cliquerElement(mc, "confirm"))
                .attendre("confirmé de son côté", () -> Minecraft.getInstance().screen instanceof TradeScreen t
                        && t.getMenu().selfConfirmed())
                .pause(500)
                .repereInterface("autre", mc -> slotCentre(mc, TradeMenu.OTHER_START + 6, 0, 0))
                .repereInterface("toi", mc -> slotCentre(mc, TradeMenu.OWN_START + 6, 0, 0))
                .repereInterface("pieces", mc -> element(mc, "coins.gold.10", 18, 0))
                .repereInterface("recu", mc -> element(mc, "receipt", -42, 0))
                .repereInterface("confirmer", mc -> element(mc, "confirm", 44, 0))
                .prise("echange-fenetre")
                .serveur("Maelle confirme", lieu -> {
                    ServerPlayer m = figurant("Maelle").joueur();
                    figurant("Maelle").envoyer(TradeActionPacket.confirm(((TradeMenu) m.containerMenu).version()));
                })
                .attendre("échange conclu", () -> Minecraft.getInstance().screen == null
                        && compter(Minecraft.getInstance(), Items.BREAD) == 5)
                .attendre("reçu reçu", () -> premiere(Minecraft.getInstance(), st -> st.is(EconomyRegistry.RECEIPT.get())) != null)
                .client("le reçu", mc -> {
                    PILE[0] = premiere(mc, st -> st.is(EconomyRegistry.RECEIPT.get())).copy();
                    constat("échange : pains reçus " + compter(mc, Items.BREAD) + ", reçu " + PILE[0].getHoverName().getString());
                })
                .echelle(3)
                .ouvrir("infobulle du reçu", () -> new ScenesElectricite.Infobulle(PILE[0]))
                .pause(500)
                .prise("echange-recu")
                .fermer()
                .echelle(2)
                .serveur("Maelle part", lieu -> quitterFigurants());
    }

    // --- l'étal de marché ---------------------------------------------------------------------------------------------

    private static final BlockPos ETAL_ROBIN = new BlockPos(7, 0, 0);

    private void etal(Atelier atelier) {
        atelier.scene("etal")
                .serveur("l'étal de Maelle, celui de Robin", lieu -> {
                    ile(lieu);
                    quitterFigurants();
                    JoueurDeTest maelle = figurant(lieu, "Maelle", new Vec3(0.5, 0, -1.0), 0F, true);
                    maelle.joueur().getInventory().setItem(1, new ItemStack(Coin.GOLD.item(), 2));
                    etalGarni(lieu, A, maelle.joueur());
                    // L'étal de Robin : carottes et blé, une offre de rachat, un peu de caisse.
                    ServerPlayer j = lieu.joueur();
                    lieu.poser(ETAL_ROBIN.getX(), 0, 0, face(EconomyRegistry.MARKET_STALL.get()));
                    MarketStallBlockEntity e = be(lieu, ETAL_ROBIN, MarketStallBlockEntity.class);
                    e.setOwner(j.getUUID(), j.getGameProfile().getName());
                    e.stock().setStackInSlot(0, new ItemStack(Items.CARROT, 48));
                    e.stock().setStackInSlot(1, new ItemStack(Items.WHEAT, 40));
                    e.stock().setStackInSlot(2, new ItemStack(Items.PUMPKIN_PIE, 6));
                    e.setOffer(0, new StallOffer(new ItemStack(Items.CARROT), 8, 20L));
                    e.setOffer(1, new StallOffer(new ItemStack(Items.WHEAT), 16, 25L));
                    e.setOffer(2, new StallOffer(new ItemStack(Items.PUMPKIN_PIE), 1, 12L));
                    e.setBuyback(0, new StallBuyback(new ItemStack(Items.POTATO), 16, 20L, 10));
                    e.addCash(Coin.SILVER, 6);
                    // Robin : une pièce d'or et une petite bourse pour acheter chez Maelle.
                    j.getInventory().setItem(0, new ItemStack(Coin.GOLD.item(), 1));
                    j.getInventory().setItem(1, bourse(0, 0, 4, 0));
                })
                .camera(new Vec3(0.9, 0.95, 1.9), new Vec3(0.5, 0.6, 0.35))
                .repereBloc("comptoir", A, 0.5, 0.3, 0.0)
                .repereBloc("auvent", A, 0.05, 1.0, 0.5)
                .prise("etal-hero")
                // La vitrine de Maelle : Robin (opérateur) s'accroupit, main vide, pour la voir comme un client.
                .regarder(A, new Vec3(0.0, 0.7, 2.3))
                .client("accroupi, main vide", mc -> {
                    mc.player.getInventory().selected = 8;
                    accroupir(mc, true);
                })
                .attendre("accroupi", () -> Minecraft.getInstance().player.isShiftKeyDown())
                .pause(400)
                .clicDroit(A, Direction.SOUTH)
                .attendre("vitrine", () -> Minecraft.getInstance().screen instanceof StallShopScreen)
                .client("debout", mc -> accroupir(mc, false))
                .pause(700)
                .repereInterface("onglets", mc -> gui(mc, 172, 22))
                .repereInterface("ligne", mc -> gui(mc, 3, StallShopMenu.LIST_Y + 10))
                .repereInterface("acheter", mc -> gui(mc, 172, StallShopMenu.LIST_Y + 10))
                .prise("etal-vitrine")
                .client("« Acheter » : quatre pains", mc -> {
                    int[] b = boutonVitrine(mc, 0);
                    mc.screen.mouseClicked(b[0], b[1], 0);
                })
                .attendre("pains achetés", () -> compter(Minecraft.getInstance(), Items.BREAD) == 4)
                .pause(600)
                .client("constat de l'achat", mc -> constat("achat chez Maelle : " + compter(mc, Items.BREAD)
                        + " pains, porté " + valeurPortee(mc) + " cuivres (avant : 140)"))
                .prise("etal-achat")
                .fermer()
                // Maelle achète à l'étal de Robin (son achat, par la règle du jeu) : une vente au registre, de la caisse.
                .serveur("Maelle achète des carottes chez Robin", lieu -> {
                    MarketStallBlockEntity e = be(lieu, ETAL_ROBIN, MarketStallBlockEntity.class);
                    constat("achat de Maelle chez Robin : " + StallTrade.buy(figurant("Maelle").joueur(), e, 0, 20L, 8));
                })
                .regarder(ETAL_ROBIN, new Vec3(0.0, 0.7, 2.3))
                .ouvrirBloc(ETAL_ROBIN, Direction.SOUTH, StallOwnerScreen.class)
                .pause(700)
                .repereInterface("onglets", mc -> gui(mc, 172, 25))
                .repereInterface("stock", mc -> gui(mc, 88, 75))
                .prise("etal-stock")
                .client("onglet Offres", mc -> mc.screen.mouseClicked(ongletEtal(mc, 1)[0], ongletEtal(mc, 1)[1], 0))
                .pause(500)
                .client("première offre", mc -> {
                    double[] c = gui(mc, 7 + 9, 50 + 9);
                    mc.screen.mouseClicked(c[0], c[1], 0);
                })
                .pause(700)
                .repereInterface("cases", mc -> gui(mc, 88, 59))
                .repereInterface("champs", mc -> gui(mc, 88, 76))
                .repereInterface("definir", mc -> gui(mc, 172, 90))
                .prise("etal-offres")
                .client("onglet Registre", mc -> mc.screen.mouseClicked(ongletEtal(mc, 3)[0], ongletEtal(mc, 3)[1], 0))
                .pause(1500)
                .prise("etal-registre")
                .client("onglet Caisse", mc -> mc.screen.mouseClicked(ongletEtal(mc, 2)[0], ongletEtal(mc, 2)[1], 0))
                .attendre("caisse", () -> Minecraft.getInstance().screen instanceof StallCashScreen)
                .pause(700)
                .prise("etal-caisse")
                .fermer()
                .serveur("Maelle part", lieu -> quitterFigurants());
    }

    // --- la facture ---------------------------------------------------------------------------------------------------

    private void facture(Atelier atelier) {
        atelier.scene("facture")
                .serveur("Maelle face au joueur, le carnet", lieu -> {
                    ile(lieu);
                    quitterFigurants();
                    JoueurDeTest maelle = figurant(lieu, "Maelle", new Vec3(0.5, 0, -0.5), 0F, true);
                    maelle.joueur().getInventory().setItem(0, new ItemStack(Coin.GOLD.item(), 2));
                    ServerPlayer j = lieu.joueur();
                    j.getInventory().setItem(0, new ItemStack(EconomyRegistry.INVOICE_BOOK.get()));
                    j.getInventory().setItem(1, bourse(0, 1, 0, 0));
                    InvoiceService.resetCooldown(j.getUUID());
                    InvoiceService.resetCooldown(maelle.uuid());
                })
                .camera(new Vec3(0.5, 1.62, 3.0), new Vec3(0.5, 1.2, -0.5))
                .client("le carnet en main", mc -> mc.player.getInventory().selected = 0)
                .attendre("carnet en main", () -> Minecraft.getInstance().player.getMainHandItem()
                        .is(EconomyRegistry.INVOICE_BOOK.get()))
                .attendre("Maelle visible", () -> joueurVu(Minecraft.getInstance(), "Maelle") != null)
                .client("clic droit sur Maelle", mc -> {
                    Player m = joueurVu(mc, "Maelle");
                    mc.gameMode.interactAt(mc.player, m, new EntityHitResult(m), InteractionHand.MAIN_HAND);
                    mc.gameMode.interact(mc.player, m, InteractionHand.MAIN_HAND);
                    mc.player.swing(InteractionHand.MAIN_HAND);
                })
                .attendre("formulaire", () -> Minecraft.getInstance().screen instanceof InvoiceFormScreen)
                .client("montant et motif tapés", mc -> {
                    taper(mc, "80");
                    mc.screen.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0);
                    taper(mc, "Cinq pains au four");
                })
                .pause(600)
                .repereInterface("montant", mc -> formulaire(mc, 160, 35 + 8))
                .repereInterface("motif", mc -> formulaire(mc, 160, 35 + 16 + 4 + 14 + 11 + 8))
                .repereInterface("emettre", mc -> formulaire(mc, 10 + 47, 35 + 16 + 4 + 14 + 11 + 16 + 12 - 6))
                .prise("facture-formulaire")
                .client("chat visible", ScenesEconomie::chatVisible)
                .client("« Émettre »", mc -> {
                    double[] b = formulaire(mc, 10 + 47, 35 + 16 + 4 + 14 + 11 + 16 + 12 + 8);
                    mc.screen.mouseClicked(b[0], b[1], 0);
                    if (mc.screen != null) {
                        mc.screen.mouseReleased(b[0], b[1], 0);
                    }
                })
                .attendre("formulaire fermé", () -> Minecraft.getInstance().screen == null)
                .pause(2500)
                // Maelle, à son tour, adresse une facture à Robin (sa règle d'émission, en son nom).
                .serveur("Maelle facture Robin, son carnet en main", lieu -> {
                    ServerPlayer m = figurant("Maelle").joueur();
                    m.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(EconomyRegistry.INVOICE_BOOK.get()));
                    constat("facture de Maelle à Robin : " + InvoiceService.issue(m, lieu.joueur().getUUID(), 45L,
                            "Trois pommes et un pain"));
                })
                .attendre("facture reçue", () -> premiere(Minecraft.getInstance(), st -> st.is(EconomyRegistry.INVOICE.get())) != null)
                .echelle(3)
                .pause(800)
                .prise("facture-chat")
                .echelle(2)
                .client("chat masqué", ScenesEconomie::chatMasque)
                .client("la facture en main", mc -> mc.player.getInventory().selected = caseInventaire(mc,
                        st -> st.is(EconomyRegistry.INVOICE.get())))
                .attendre("facture en main", () -> Minecraft.getInstance().player.getMainHandItem().is(EconomyRegistry.INVOICE.get()))
                .client("clic droit avec la facture", mc -> mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND))
                .attendre("écran de la facture", () -> Minecraft.getInstance().screen instanceof InvoiceScreen)
                .pause(600)
                .repereInterface("payer", mc -> boutonFacture(mc, 4, 8))
                .prise("facture-ecran")
                .client("« Payer »", mc -> {
                    double[] b = boutonFacture(mc, 10 + 47, 8);
                    mc.screen.mouseClicked(b[0], b[1], 0);
                })
                .attendre("reçu de la facture", () -> premiere(Minecraft.getInstance(), st -> st.is(EconomyRegistry.RECEIPT.get())) != null)
                .client("le reçu", mc -> {
                    PILE[0] = premiere(mc, st -> st.is(EconomyRegistry.RECEIPT.get())).copy();
                    constat("facture payée : porté " + valeurPortee(mc) + " cuivres (avant 100)");
                })
                .echelle(3)
                .ouvrir("infobulle du reçu", () -> new ScenesElectricite.Infobulle(PILE[0]))
                .pause(500)
                .prise("facture-recu")
                .fermer()
                .echelle(2)
                .serveur("Maelle part", lieu -> quitterFigurants());
    }

    // --- le trésor de la ville ----------------------------------------------------------------------------------------

    private static final BlockPos TRESOR = new BlockPos(-2, 0, -1);
    private static final BlockPos TABLEAU = new BlockPos(1, 0, -1);

    private void tresor(Atelier atelier) {
        atelier.scene("tresor")
                .serveur("la mairie, Robin maire", lieu -> {
                    ile(lieu);
                    for (int x = -4; x <= 4; x++) {
                        for (int y = 0; y <= 3; y++) {
                            lieu.poser(x, y, -2, Blocks.STONE_BRICKS.defaultBlockState());
                        }
                    }
                    lieu.poser(-4, 0, 0, Blocks.LANTERN.defaultBlockState());
                    lieu.poser(4, 0, 0, Blocks.LANTERN.defaultBlockState());
                    String moi = lieu.joueur().getGameProfile().getName();
                    console(lieu.serveur(), "role mayor set " + moi);
                    retours(lieu.serveur(), true);
                    ServerPlayer j = lieu.joueur();
                    j.getInventory().setItem(0, new ItemStack(EconomyRegistry.TREASURY_ITEM.get()));
                    j.getInventory().setItem(1, new ItemStack(Coin.GOLD.item(), 5));
                    j.getInventory().setItem(2, new ItemStack(Coin.SILVER.item(), 3));
                    ((TownBoardBlock) EconomyRegistry.TOWN_BOARD.get()).placeWhole(lieu.monde(),
                            lieu.p(TABLEAU.getX(), 0, TABLEAU.getZ()), Direction.SOUTH);
                })
                .camera(new Vec3(-1.5, 1.7, 1.6), new Vec3(-1.5, 0.0, -0.5))
                .client("le trésor en main", mc -> mc.player.getInventory().selected = 0)
                .attendre("trésor en main", () -> Minecraft.getInstance().player.getMainHandItem()
                        .is(EconomyRegistry.TREASURY_ITEM.get()))
                .client("clic droit sur le sol", mc -> FilmTournage.clicDroit(mc, origine(TRESOR.getX(), -1, TRESOR.getZ()),
                        Direction.UP))
                .attendre("trésor posé", () -> blocClient(TRESOR).is(EconomyRegistry.TREASURY.get()))
                .client("main vide", mc -> mc.player.getInventory().selected = 8)
                .camera(new Vec3(0.5, 1.9, 4.8), new Vec3(0.5, 0.8, -1.0))
                .repereBloc("tresor", TRESOR, 0.5, 1.05, 0.5)
                .repereBloc("tableau", TABLEAU.east().east(), 0.3, 1.05, 0.9)
                .prise("tresor-hero")
                .regarder(TRESOR, new Vec3(0.0, 0.8, 2.3))
                .ouvrirBloc(TRESOR, Direction.SOUTH, TreasuryScreen.class)
                .pause(500)
                .client("dépôt d'une pièce d'or", mc -> cliquerElement(mc, "row.gold.1"))
                .attendre("1 or au trésor", () -> totalEcran() == 100)
                .client("dépôt d'une seconde", mc -> cliquerElement(mc, "row.gold.1"))
                .attendre("2 or au trésor", () -> totalEcran() == 200)
                .pause(600)
                .repereInterface("lignes", mc -> element(mc, "row.gold.1", -24, 0))
                .repereInterface("change", mc -> element(mc, "group.ruby", 0, -19))
                .prise("tresor-ecran")
                .client("clic droit : retirer une pièce d'or", mc -> {
                    int[] c = ((CoinAccountScreen<?>) mc.screen).elementCenter("row.gold.1").orElseThrow();
                    mc.screen.mouseClicked(c[0], c[1], 1);
                })
                .attendre("fenêtre du motif", () -> Minecraft.getInstance().screen instanceof TreasuryReasonScreen)
                .client("motif tapé", mc -> taper(mc, "Lanternes de la place"))
                .pause(600)
                .repereInterface("motif", mc -> fenetreMotif(mc, 160, 24 + 14 + 11 + 8))
                .repereInterface("confirmer", mc -> fenetreMotif(mc, 10 + 47, 24 + 14 + 11 + 16 + 12 - 6))
                .prise("tresor-motif")
                .client("« Confirmer »", mc -> {
                    double[] b = fenetreMotif(mc, 10 + 47, 24 + 14 + 11 + 16 + 12 + 8);
                    mc.screen.mouseClicked(b[0], b[1], 0);
                })
                .attendre("retrait fait", () -> Minecraft.getInstance().screen instanceof TreasuryScreen && totalEcran() == 100)
                .fermer()
                .pause(1500)
                .camera(new Vec3(2.5, 1.55, 2.4), new Vec3(2.5, 1.05, -0.9))
                .prise("tresor-tableau")
                .camera(new Vec3(0.5, 1.7, 3.0), new Vec3(0.5, 3.5, 12.0))
                .client("chat visible", ScenesEconomie::chatVisible)
                .client("/treasury", mc -> mc.player.connection.sendCommand("treasury"))
                .attendre("réponse de /treasury", () -> cle("command.oneforallsurvival.treasury.balance"))
                .pause(1000)
                .echelle(3)
                .pause(500)
                .prise("tresor-commande")
                .client("chat vidé", mc -> mc.gui.getChat().clearMessages(false))
                .client("/treasury log", mc -> mc.player.connection.sendCommand("treasury log"))
                .attendre("réponse de /treasury log", () -> cle("command.oneforallsurvival.treasury.log"))
                .pause(1000)
                .prise("tresor-registre")
                .echelle(2)
                .client("chat masqué", ScenesEconomie::chatMasque)
                .serveur("plus de maire", lieu -> {
                    console(lieu.serveur(), "role mayor remove");
                    retours(lieu.serveur(), false);
                });
    }

    // --- le fournisseur d'électricité ---------------------------------------------------------------------------------

    private static final BlockPos POSTE = new BlockPos(1, 0, 0);
    private static final BlockPos DISJ = new BlockPos(0, 0, 0);
    private static final BlockPos CPT = new BlockPos(-1, 0, 0);
    private static final BlockPos MAISON = new BlockPos(-2, 0, 0);
    private static final String FOURNISSEUR = "Lumière du Port";
    private static final String ABONNEE = "InesLebrun";

    private void fournisseur(Atelier atelier) {
        atelier.scene("fournisseur")
                .serveur("ligne : alternateur, disjoncteur, compteur, maison ; Ines", lieu -> {
                    ile(lieu);
                    quitterFigurants();
                    figurant(lieu, ABONNEE, new Vec3(6.5, 0, 9.5), 160F, true);
                    alternateur(lieu, new BlockPos(2, 0, 0), Direction.EAST, 64);
                    lieu.poser(0, 0, 0, GridRegistry.BREAKER.get().defaultBlockState()
                            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
                    lieu.poser(-1, 0, 0, GridRegistry.METER.get().defaultBlockState()
                            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
                    lieu.poser(-2, 0, 0, face(FoodRegistry.DRYER.get()));
                    be(lieu, MAISON, DryerBlockEntity.class).inventory().setStackInSlot(DryerBlockEntity.SLOT_INPUT,
                            new ItemStack(Items.BEEF, 64));
                    for (int x = -4; x <= -3; x++) {
                        for (int y = 0; y <= 2; y++) {
                            lieu.poser(x, y, 0, Blocks.SPRUCE_PLANKS.defaultBlockState());
                            lieu.poser(x, y, -1, Blocks.SPRUCE_PLANKS.defaultBlockState());
                        }
                    }
                    ServerPlayer j = lieu.joueur();
                    oublierFournisseur(lieu.serveur(), j.getUUID());
                    RoleSavedData roles = RoleSavedData.get(lieu.serveur());
                    roles.book().assign(j.getUUID(), "ingenieur");
                    roles.setDirty();
                    RoleEvents.refreshAll(lieu.serveur());
                    j.getInventory().setItem(0, new ItemStack(GridRegistry.POWER_STATION_ITEM.get()));
                    j.getInventory().setItem(1, new ItemStack(RepairRegistry.TESTER.get()));
                })
                // La pose du poste, regard à l'ouest : l'avant (la sortie) vers la maison.
                .camera(new Vec3(3.3, 1.7, 0.5), new Vec3(1.5, -0.2, 0.5))
                .client("le poste en main", mc -> mc.player.getInventory().selected = 0)
                .attendre("poste en main", () -> Minecraft.getInstance().player.getMainHandItem()
                        .is(GridRegistry.POWER_STATION_ITEM.get()))
                .client("clic droit sur le sol", mc -> FilmTournage.clicDroit(mc, origine(1, -1, 0), Direction.UP))
                .attendre("poste posé", () -> blocClient(POSTE).is(GridRegistry.POWER_STATION.get()))
                .client("main vide", mc -> mc.player.getInventory().selected = 8)
                .regarder(POSTE, new Vec3(0.0, 0.8, 2.4))
                .ouvrirBloc(POSTE, Direction.SOUTH, PowerPanelScreen.class)
                .attendre("panneau prêt", () -> panneau() != null)
                .client("nom tapé", mc -> taper(mc, FOURNISSEUR))
                .pause(600)
                .repereInterface("nom", mc -> rect(panneau().layout().nameField(), 0.5, 0.5))
                .repereInterface("fonder", mc -> rect(panneau().layout().foundButton(), 0.5, 2.0))
                .prise("fournisseur-nom")
                .client("« Fonder »", mc -> cliquerRect(mc, panneau().layout().foundButton()))
                .attendre("fournisseur fondé", () -> panneau() != null && FOURNISSEUR.equals(panneau().state().name())
                        && panneau().layout().price() != null)
                .client("champ du prix", mc -> cliquerRect(mc, panneau().layout().price().box()))
                .client("prix tapé", mc -> taper(mc, "20"))
                .client("OK du prix", mc -> cliquerRect(mc, panneau().layout().price().button()))
                .attendre("prix de demain", () -> panneau() != null && panneau().state().nextPrice() == 20)
                .client("champ de l'abonnement", mc -> cliquerRect(mc, panneau().layout().subscription().box()))
                .client("abonnement tapé", mc -> taper(mc, "15"))
                .client("OK de l'abonnement", mc -> cliquerRect(mc, panneau().layout().subscription().button()))
                .attendre("abonnement de demain", () -> panneau() != null && panneau().state().nextSubscription() == 15)
                .pause(700)
                .repereInterface("prix", mc -> rect(panneau().layout().price().box(), -0.15, 0.5))
                .repereInterface("abonnement", mc -> rect(panneau().layout().subscription().box(), -0.15, 0.5))
                .repereInterface("caisse", mc -> rect(panneau().layout().tillButton(), -0.12, 0.5))
                .prise("fournisseur-tarifs")
                .fermer()
                // L'abonnée inscrite sur le compteur (clic droit, main vide) et sur le disjoncteur (accroupi, main vide).
                .regarder(CPT, new Vec3(0.3, 0.7, 2.2))
                .clicDroit(CPT, Direction.SOUTH)
                .attendre("écran du nom (compteur)", () -> Minecraft.getInstance().screen instanceof SubscriberNameScreen)
                .client("nom de l'abonnée", mc -> taper(mc, ABONNEE))
                .client("Inscrire", mc -> {
                    double[] b = nomEcran(mc, false);
                    mc.screen.mouseClicked(b[0], b[1] - 14, 0);
                })
                .attendre("compteur inscrit", () -> Minecraft.getInstance().screen == null)
                .client("accroupi", mc -> accroupir(mc, true))
                .attendre("accroupi", () -> Minecraft.getInstance().player.isShiftKeyDown())
                .pause(300)
                .clicDroit(DISJ, Direction.SOUTH)
                .attendre("écran du nom (disjoncteur)", () -> Minecraft.getInstance().screen instanceof SubscriberNameScreen)
                .client("debout", mc -> accroupir(mc, false))
                .client("nom de l'abonnée", mc -> taper(mc, ABONNEE))
                .client("Inscrire", mc -> {
                    double[] b = nomEcran(mc, false);
                    mc.screen.mouseClicked(b[0], b[1] - 14, 0);
                })
                .attendre("disjoncteur inscrit", () -> Minecraft.getInstance().screen == null)
                .serveur("constat des inscriptions", lieu -> constat("compteur « " + be(lieu, CPT, MeterBlockEntity.class).subscriber()
                        + " », disjoncteur « " + be(lieu, DISJ, BreakerBlockEntity.class).subscriber() + " », maison "
                        + etatServeur(lieu, MAISON)))
                // Le jumelage : testeur sur le poste, puis sur le compteur ; de nouveau sur le poste, puis sur le disjoncteur.
                .client("chat visible", ScenesEconomie::chatVisible)
                .client("le testeur en main", mc -> mc.player.getInventory().selected = 1)
                .attendre("testeur en main", () -> Minecraft.getInstance().player.getMainHandItem().is(RepairRegistry.TESTER.get()))
                .regarder(POSTE, new Vec3(-0.5, 0.8, 2.6))
                .client("testeur sur le poste", mc -> FilmTournage.clicDroit(mc, origine(1, 0, 0), Direction.SOUTH))
                .attendre("fournisseur retenu", () -> cle("message.oneforallsurvival.power.hold"))
                .client("testeur sur le compteur", mc -> FilmTournage.clicDroit(mc, origine(-1, 0, 0), Direction.SOUTH))
                .attendre("compteur jumelé", () -> cle("message.oneforallsurvival.power.paired"))
                .client("testeur sur le poste", mc -> FilmTournage.clicDroit(mc, origine(1, 0, 0), Direction.SOUTH))
                .pause(400)
                .client("testeur sur le disjoncteur", mc -> FilmTournage.clicDroit(mc, origine(0, 0, 0), Direction.SOUTH))
                .attendre("disjoncteur jumelé", () -> cle("message.oneforallsurvival.power.breaker_paired"))
                // L'accord d'Ines : le lien [Accepter] de sa demande, joué en son nom.
                .serveur("Ines donne son accord", lieu -> constat("accord d'Ines : " + accord(figurant(ABONNEE))))
                .attendre("le fournisseur est prévenu", () -> cle("message.oneforallsurvival.power.founder_consent"))
                .echelle(3)
                .pause(800)
                .prise("fournisseur-chat")
                .echelle(2)
                .client("chat masqué", ScenesEconomie::chatMasque)
                .client("main vide", mc -> mc.player.getInventory().selected = 8)
                .serveur("constat avant acceptation", lieu -> constat("avant « Accepter » : maison " + etatServeur(lieu, MAISON)))
                .regarder(POSTE, new Vec3(0.0, 0.8, 2.4))
                .ouvrirBloc(POSTE, Direction.SOUTH, PowerPanelScreen.class)
                .attendre("demande à accepter", () -> rangee(ABONNEE) != null
                        && rangee(ABONNEE).state() == PowerClient.State.AWAITING_ACCEPT)
                .pause(600)
                .repereInterface("accepter", mc -> rect(bouton(ABONNEE, 0), 0.5, 2.2))
                .prise("fournisseur-demande")
                .client("« Accepter »", mc -> cliquerRect(mc, bouton(ABONNEE, 0)))
                .attendre("abonnée active", () -> rangee(ABONNEE) != null && rangee(ABONNEE).state() == PowerClient.State.ACTIVE)
                .fermer()
                .attendre("maison en marche", () -> etat(MAISON) == MachineState.RUNNING, 20_000)
                .pause(6000)
                .camera(new Vec3(0.2, 2.0, 3.6), new Vec3(-0.4, 0.45, 0.0))
                .repereBloc("poste", POSTE, 0.5, 1.0, 0.6)
                .repereBloc("disjoncteur", DISJ, 0.5, 1.0, 0.75)
                .repereBloc("compteur", CPT, 0.5, 1.0, 0.75)
                .repereBloc("maison", MAISON, 0.3, 0.9, 1.0)
                .prise("fournisseur-ligne")
                .regarder(POSTE, new Vec3(0.0, 0.8, 2.4))
                .ouvrirBloc(POSTE, Direction.SOUTH, PowerPanelScreen.class)
                .attendre("consommation comptée", () -> rangee(ABONNEE) != null && rangee(ABONNEE).feToday() > 0)
                .pause(600)
                .repereInterface("couper", mc -> rect(bouton(ABONNEE, 0), 0.5, 2.2))
                .repereInterface("conso", mc -> rect(rangeeMiseEnPage(ABONNEE).usage().box(), 0.5, 2.2))
                .prise("fournisseur-clients")
                .client("« Couper »", mc -> cliquerRect(mc, bouton(ABONNEE, 0)))
                .attendre("abonnée coupée", () -> rangee(ABONNEE) != null && rangee(ABONNEE).cut())
                .pause(700)
                .repereInterface("retablir", mc -> rect(bouton(ABONNEE, 0), 0.5, 2.2))
                .prise("fournisseur-coupe")
                .fermer()
                .attendre("disjoncteur ouvert", () -> blocClient(DISJ).hasProperty(BreakerBlock.CLOSED)
                        && !blocClient(DISJ).getValue(BreakerBlock.CLOSED), 20_000)
                .serveur("constat de la coupure", lieu -> constat("après « Couper » : disjoncteur "
                        + lieu.monde().getBlockState(lieu.p(0, 0, 0)) + ", maison " + etatServeur(lieu, MAISON)))
                .regarder(DISJ, new Vec3(0.35, 0.45, 1.7))
                .prise("fournisseur-disjoncteur-ouvert")
                .serveur("Ines part", lieu -> {
                    quitterFigurants();
                    RoleSavedData roles = RoleSavedData.get(lieu.serveur());
                    roles.book().assign(lieu.joueur().getUUID(), "citoyen");
                    roles.setDirty();
                    RoleEvents.refreshAll(lieu.serveur());
                });
    }

    // --- le butin des mobs --------------------------------------------------------------------------------------------

    private static final List<UUID> ZOMBIES = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final Vec3[] PIECE = new Vec3[2];
    private static int vagues;

    private void butin(Atelier atelier) {
        atelier.scene("butin")
                .serveur("survie, épée, zombies sauvages", lieu -> {
                    ile(lieu);
                    console(lieu.serveur(), "difficulty easy");
                    ServerPlayer j = lieu.joueur();
                    j.setGameMode(GameType.SURVIVAL);
                    j.getInventory().setItem(0, new ItemStack(Items.IRON_SWORD));
                    vagues = 0;
                    vague(lieu);
                })
                .camera(new Vec3(0.5, 1.62, 1.6), new Vec3(0.5, 1.2, -1.5))
                .client("l'épée en main", mc -> mc.player.getInventory().selected = 0)
                .attendre("épée en main", () -> Minecraft.getInstance().player.getMainHandItem().is(Items.IRON_SWORD))
                .attendre("zombies visibles", () -> ZOMBIES.stream().allMatch(id -> entite(Minecraft.getInstance(), id) != null))
                .attendre("des pièces tombent", () -> chasse(Minecraft.getInstance()), 90_000)
                .pause(1500)
                .client("constat du butin", mc -> constat("butin : " + piecesAuSol(mc).stream()
                        .map(c -> c.count() + " " + c.coin().key()).toList() + " après " + vagues + " vague(s)"))
                .serveur("les zombies restants retirés du cadre", lieu -> {
                    for (Zombie z : lieu.monde().getEntitiesOfClass(Zombie.class, new AABB(lieu.p(0, 0, 0)).inflate(8))) {
                        z.discard();
                    }
                })
                .client("caméra sur les pièces", mc -> {
                    CoinDropEntity c = piecesAuSol(mc).get(0);
                    Vec3 cible = c.position().add(0, 0.2, 0);
                    Vec3 oeil = cible.add(0.9, 1.25, 1.7);
                    PIECE[0] = cible;
                    PIECE[1] = oeil;
                    float[] r = FilmTournage.regard(oeil, cible);
                    WikiStudio.placerCamera(oeil, r[0], r[1]);
                })
                .attendre("caméra posée", () -> PIECE[1] != null
                        && Minecraft.getInstance().player.getEyePosition().distanceTo(PIECE[1]) < 0.05)
                .pause(2000)
                .repereInterface("pieces", mc -> {
                    CoinDropEntity c = piecesAuSol(mc).isEmpty() ? null : piecesAuSol(mc).get(0);
                    return c == null ? null : projeterInterface(mc, c.position().add(0.45, 0.25, 0));
                })
                .prise("butin-pieces")
                .serveur("paisible", lieu -> {
                    console(lieu.serveur(), "difficulty peaceful");
                    lieu.joueur().setGameMode(GameType.CREATIVE);
                });
    }

    private static void vague(Lieu lieu) {
        ZOMBIES.clear();
        for (int i = 0; i < 5; i++) {
            Entity e = EntityType.ZOMBIE.spawn(lieu.monde(), lieu.p(i - 2, 0, -1 - (i % 2)), MobSpawnType.NATURAL);
            if (e instanceof Zombie z) {
                z.setBaby(false);
                if (z.getVehicle() != null) {
                    z.getVehicle().discard();
                }
                for (EquipmentSlot slot : EquipmentSlot.values()) {
                    z.setItemSlot(slot, ItemStack.EMPTY);
                }
                // Un casque de cuir : le soleil de midi ne les brûle pas (leur mort serait sans tueur).
                z.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
                z.setDropChance(EquipmentSlot.HEAD, 0F);
                z.setNoAi(true);
                z.setPersistenceRequired();
                z.setHealth(1F);
                z.setYRot(0F);
                z.setYHeadRot(0F);
                ZOMBIES.add(z.getUUID());
            }
        }
        vagues++;
        constat("vague " + vagues + " : " + ZOMBIES.size() + " zombies nés « naturellement »");
    }

    /** Un coup d'épée par image sur le premier zombie vivant ; vrai dès que des pièces sont au sol. */
    private static boolean chasse(Minecraft mc) {
        if (!piecesAuSol(mc).isEmpty()) {
            return true;
        }
        for (UUID id : ZOMBIES) {
            Entity e = entite(mc, id);
            if (e != null && e.isAlive()) {
                mc.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, e.getEyePosition());
                if (mc.player.getAttackStrengthScale(0.5F) >= 1.0F) {
                    mc.gameMode.attack(mc.player, e);
                    mc.player.swing(InteractionHand.MAIN_HAND);
                }
                return false;
            }
        }
        if (!drapeau && vagues < 6) {
            drapeau = true;
            mc.getSingleplayerServer().execute(() -> {
                Lieu l = lieuEnCours(mc);
                if (l != null) {
                    vague(l);
                }
                drapeau = false;
            });
        }
        return false;
    }

    /** Un coup d'épée par image sur le premier zombie vivant de la vague ; vrai quand tous sont morts. */
    private static boolean tousTues(Minecraft mc) {
        for (UUID id : ZOMBIES) {
            Entity e = entite(mc, id);
            if (e != null && e.isAlive()) {
                mc.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, e.getEyePosition());
                if (mc.player.getAttackStrengthScale(0.5F) >= 1.0F) {
                    mc.gameMode.attack(mc.player, e);
                    mc.player.swing(InteractionHand.MAIN_HAND);
                }
                return false;
            }
        }
        return true;
    }

    private static List<CoinDropEntity> piecesAuSol(Minecraft mc) {
        BlockPos o = WikiStudio.origineEnCours();
        if (o == null || mc.level == null) {
            return List.of();
        }
        return mc.level.getEntitiesOfClass(CoinDropEntity.class, new AABB(o).inflate(6));
    }

    // --- les quêtes ---------------------------------------------------------------------------------------------------

    private void quetes(Atelier atelier) {
        atelier.scene("quetes")
                .serveur("survie, épée, une journalière de combat", lieu -> {
                    ile(lieu);
                    console(lieu.serveur(), "difficulty easy");
                    ServerPlayer j = lieu.joueur();
                    j.setGameMode(GameType.SURVIVAL);
                    j.getInventory().setItem(0, new ItemStack(Items.IRON_SWORD));
                    // Toutes les recettes connues d'avance : aucune notification « Nouvelle(s) recette(s) » au ramassage.
                    console(lieu.serveur(), "recipe give " + j.getGameProfile().getName() + " *");
                    // Et tous les progrès : aucune notification « Chasseur de monstres » au premier zombie tué.
                    console(lieu.serveur(), "advancement grant " + j.getGameProfile().getName() + " everything");
                    console(lieu.serveur(), "e2equetes imposer " + j.getGameProfile().getName() + " daily 0 kill_zombies 5");
                    vagues = 0;
                    vague(lieu);
                })
                .attendre("quête imposée reçue", () -> journaliere(0) != null
                        && "kill_zombies".equals(journaliere(0).ref().templateId()))
                .camera(new Vec3(0.5, 1.62, 1.6), new Vec3(0.5, 1.2, -1.5))
                .client("l'épée en main", mc -> mc.player.getInventory().selected = 0)
                .attendre("épée en main", () -> Minecraft.getInstance().player.getMainHandItem().is(Items.IRON_SWORD))
                .attendre("zombies visibles", () -> ZOMBIES.stream().allMatch(id -> entite(Minecraft.getInstance(), id) != null))
                .pause(1500)
                .client("toasts gardés, coin de l'écran seul", mc -> {
                    WikiStudio.toasts(true);
                    mc.getToasts().clear();
                    WikiStudio.ath(mc, true);
                    WikiStudio.elements();
                    WikiStudio.reticule(false);
                    mc.gameRenderer.setRenderHand(false);
                })
                .attendre("cinq zombies éliminés", () -> tousTues(Minecraft.getInstance()), 60_000)
                .attendre("quête finie", () -> journaliere(0) != null && journaliere(0).progress() >= 5)
                .pause(1200)
                .prise("quetes-toast")
                .client("interface masquée", mc -> {
                    WikiStudio.ath(mc, false);
                    WikiStudio.toasts(false);
                })
                .client("touche J (appui)", mc -> toucheJ(mc, GLFW.GLFW_PRESS))
                .pause(200)
                .client("touche J (relâche)", mc -> toucheJ(mc, GLFW.GLFW_RELEASE))
                .attendre("écran des quêtes", () -> Minecraft.getInstance().screen instanceof QuestScreen q && q.layout() != null)
                .pause(800)
                .repereInterface("reclamer", mc -> {
                    double[] b = boutonQuete(mc, 0);
                    return b == null ? null : new double[] {b[0] + 41, b[1]};
                })
                .repereInterface("plafond", mc -> {
                    var t = ((QuestScreen) mc.screen).layout().cap();
                    return new double[] {t.x() + t.width() + 12, t.y() + 4};
                })
                .prise("quetes-ecran")
                .client("« Réclamer »", mc -> {
                    releve = valeurPortee(mc);
                    double[] b = boutonQuete(mc, 0);
                    mc.screen.mouseClicked(b[0], b[1], 0);
                })
                .attendre("récompense reçue", () -> valeurPortee(Minecraft.getInstance()) > releve
                        && journaliere(0) != null && journaliere(0).state() == QuestSyncPacket.State.CLAIMED)
                .pause(800)
                .client("constat de la quête", mc -> constat("quête réclamée : +" + (valeurPortee(mc) - releve) + " cuivres"))
                .prise("quetes-reclamee")
                .fermer()
                .serveur("créatif, paisible", lieu -> {
                    console(lieu.serveur(), "difficulty peaceful");
                    lieu.joueur().setGameMode(GameType.CREATIVE);
                });
    }

    // --- minuit : salaire, raccordement d'un client, prélèvement ------------------------------------------------------

    private static final String LUCAS = "Lucas";
    private static final String FOURNISSEUR_2 = "Courant de la Baie";

    private void minuit(Atelier atelier) {
        long[] serie = new long[1];
        atelier.scene("minuit")
                .serveur("Robin policier ; la maison de Robin ; Lucas, fournisseur", lieu -> {
                    ile(lieu);
                    quitterFigurants();
                    ServerPlayer j = lieu.joueur();
                    String moi = j.getGameProfile().getName();
                    console(lieu.serveur(), "role set " + moi + " policier");
                    retours(lieu.serveur(), true);
                    if (TreasuryAccount.get(lieu.serveur()).total() < 1_000L) {
                        console(lieu.serveur(), "eco treasury give 1000 Fonds de la ville");
                    }
                    viderCoffreFort(lieu.serveur(), j.getUUID());
                    // La maison de Robin : alternateur, disjoncteur, compteur, séchoir.
                    alternateur(lieu, new BlockPos(2, 0, 0), Direction.EAST, 64);
                    JoueurDeTest lucas = figurant(lieu, LUCAS, new Vec3(4.5, 0, 2.5), 120F, true);
                    oublierFournisseur(lieu.serveur(), lucas.uuid());
                    lucas.joueur().setYRot(90F);
                    poserPar(lucas.joueur(), new ItemStack(GridRegistry.POWER_STATION_ITEM.get()), lieu.p(1, -1, 0), Direction.UP);
                    var fonde = PowerStationBlock.found(lucas.joueur(), lieu.p(1, 0, 0), FOURNISSEUR_2);
                    constat("poste de Lucas : " + lieu.monde().getBlockState(lieu.p(1, 0, 0)) + " ; fondation " + fonde);
                    PowerSuppliers r = PowerSuppliers.get(lieu.serveur());
                    PowerSupplier f = r.byFounder(lucas.uuid()).orElseThrow();
                    r.setNextPrice(f.id(), 20L);
                    r.setNextSubscription(f.id(), 15L);
                    lieu.poser(0, 0, 0, GridRegistry.BREAKER.get().defaultBlockState()
                            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
                    lieu.poser(-1, 0, 0, GridRegistry.METER.get().defaultBlockState()
                            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
                    lieu.poser(-2, 0, 0, face(FoodRegistry.DRYER.get()));
                    be(lieu, MAISON, DryerBlockEntity.class).inventory().setStackInSlot(DryerBlockEntity.SLOT_INPUT,
                            new ItemStack(Items.BEEF, 64));
                    be(lieu, CPT, MeterBlockEntity.class).setSubscriber(moi);
                    be(lieu, DISJ, BreakerBlockEntity.class).setSubscriber(moi);
                })
                .camera(new Vec3(0.5, 1.9, 3.4), new Vec3(-0.5, 0.5, 0.0))
                .client("chat visible", ScenesEconomie::chatVisible)
                .serveur("Lucas jumelle le compteur et le disjoncteur de Robin", lieu -> {
                    ServerPlayer l = figurant(LUCAS).joueur();
                    PowerSupplier f = PowerSuppliers.get(lieu.serveur()).byFounder(l.getUUID()).orElseThrow();
                    constat("jumelage : compteur " + PowerPairing.pairMeter(l, f, be(lieu, CPT, MeterBlockEntity.class))
                            + ", disjoncteur " + PowerPairing.pairBreaker(l, f, be(lieu, DISJ, BreakerBlockEntity.class)));
                })
                .attendre("demande reçue", () -> lien("/power consent") != null)
                .echelle(3)
                .pause(800)
                .prise("minuit-demande")
                .echelle(2)
                .client("clic sur [Accepter]", mc -> cliquerLien(mc, lien("/power consent")))
                .attendre("accord donné", () -> cle("command.oneforallsurvival.power.consented"))
                .serveur("Lucas accepte au panneau", lieu -> constat("acceptation de Lucas : " + PowerPairing.accept(
                        figurant(LUCAS).joueur(), be(lieu, CPT, MeterBlockEntity.class).serial())))
                .attendre("courant accepté", () -> cle("message.oneforallsurvival.power.accepted"))
                .attendre("maison en marche", () -> etat(MAISON) == MachineState.RUNNING, 20_000)
                .echelle(3)
                .pause(800)
                .prise("minuit-accepte")
                .echelle(2)
                // Le temps de service se compte : deux minutes de policier connecté, en pauses de 20 s.
                .pause(20_000).pause(20_000).pause(20_000).pause(20_000).pause(20_000).pause(20_000)
                .client("chat vidé", mc -> {
                    mc.gui.getChat().clearMessages(false);
                    CHAT.clear();
                })
                .client("/treasury", mc -> mc.player.connection.sendCommand("treasury"))
                .attendre("réponse de /treasury", () -> cle("command.oneforallsurvival.treasury.time"))
                .pause(1000)
                .echelle(3)
                .pause(500)
                .prise("minuit-treasury")
                .echelle(2)
                .client("chat vidé", mc -> {
                    mc.gui.getChat().clearMessages(false);
                    CHAT.clear();
                })
                // Premier minuit : la paie du jour, les tarifs de Lucas entrent en vigueur.
                .serveur("premier minuit", lieu -> avantMinuit(3))
                .attendre("salaire versé", () -> cle("message.oneforallsurvival.salary.paid"), 30_000)
                .pause(3000)
                .echelle(3)
                .pause(500)
                .prise("minuit-salaire")
                .echelle(2)
                // Second minuit : la facture d'électricité du jour (abonnement 15 + consommation), Robin porte une pièce d'or.
                .serveur("une pièce d'or sur Robin", lieu -> {
                    lieu.joueur().getInventory().setItem(0, new ItemStack(Coin.GOLD.item(), 1));
                    serie[0] = 0;
                })
                .pause(8000)
                .client("chat vidé", mc -> {
                    mc.gui.getChat().clearMessages(false);
                    CHAT.clear();
                })
                .serveur("second minuit", lieu -> avantMinuit(3))
                .attendre("facture prélevée", () -> cle("message.oneforallsurvival.debit.paid"), 30_000)
                .pause(3000)
                .echelle(3)
                .pause(500)
                .prise("minuit-preleve")
                .echelle(2)
                // Troisième minuit : plus rien sur lui ni au coffre-fort, le prélèvement est refusé.
                .serveur("plus une pièce", lieu -> {
                    ServerPlayer j = lieu.joueur();
                    j.getInventory().clearContent();
                    viderCoffreFort(lieu.serveur(), j.getUUID());
                })
                .pause(8000)
                .client("chat vidé", mc -> {
                    mc.gui.getChat().clearMessages(false);
                    CHAT.clear();
                })
                .serveur("troisième minuit", lieu -> avantMinuit(3))
                .attendre("prélèvement refusé", () -> cle("message.oneforallsurvival.debit.refused"), 30_000)
                .pause(3000)
                .echelle(3)
                .pause(500)
                .prise("minuit-refuse")
                .echelle(2)
                .client("clic sur [Recevoir la facture]", mc -> cliquerLien(mc, lien("/invoice copy")))
                .attendre("papier de la facture", () -> premiere(Minecraft.getInstance(), st -> st.is(EconomyRegistry.INVOICE.get())) != null)
                .client("chat masqué", ScenesEconomie::chatMasque)
                .client("la facture en main", mc -> mc.player.getInventory().selected = caseInventaire(mc,
                        st -> st.is(EconomyRegistry.INVOICE.get())))
                .attendre("facture en main", () -> Minecraft.getInstance().player.getMainHandItem().is(EconomyRegistry.INVOICE.get()))
                .client("clic droit avec la facture", mc -> mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND))
                .attendre("écran de la facture", () -> Minecraft.getInstance().screen instanceof InvoiceScreen)
                .pause(600)
                .repereInterface("payer", mc -> boutonFacture(mc, 4, 8))
                .prise("minuit-papier")
                .fermer()
                .serveur("horloge rendue, Lucas part", lieu -> {
                    EconomyClock.reset();
                    quitterFigurants();
                    retours(lieu.serveur(), false);
                    console(lieu.serveur(), "role set " + lieu.joueur().getGameProfile().getName() + " citoyen");
                });
    }

    /** L'horloge de l'économie à {@code secondes} de la prochaine clôture du jour de paie (minuit). */
    private static void avantMinuit(int secondes) {
        long now = EconomyClock.now();
        ZoneId zone = EconomyCalendar.configuredZone();
        LocalDate jour = EconomyCalendar.payDay(now, zone, EconomyConfig.payHour());
        long frontiere = EconomyCalendar.boundaryAfter(jour, zone, EconomyConfig.payHour());
        long decalage = frontiere - secondes * 1_000L - System.currentTimeMillis();
        EconomyClock.setClock(() -> System.currentTimeMillis() + decalage);
        constat("horloge de l'économie avancée : clôture du " + jour + " dans " + secondes + " s");
    }

    // --- l'équipe : injection et tableau de bord ----------------------------------------------------------------------

    private void equipe(Atelier atelier) {
        atelier.scene("equipe")
                .serveur("terrain ; retours de commande visibles", lieu -> {
                    ile(lieu);
                    retours(lieu.serveur(), true);
                })
                .camera(new Vec3(0.5, 1.9, 4.0), new Vec3(0.5, 0.6, -3.0))
                .client("chat visible, haut", mc -> {
                    chatVisible(mc);
                    // Les sorties de l'équipe sont longues : chat pleine largeur, 20 lignes.
                    mc.options.chatWidth().set(1.0);
                    mc.options.chatHeightUnfocused().set(1.0);
                })
                .client("/eco give", mc -> mc.player.connection.sendCommand("eco give "
                        + mc.player.getGameProfile().getName() + " 500 Prime du concours de pêche"))
                .attendre("pièces données", () -> valeurPortee(Minecraft.getInstance()) >= 500)
                .pause(1000)
                .echelle(3)
                .pause(500)
                .prise("equipe-give")
                .echelle(2)
                .client("chat vidé", mc -> mc.gui.getChat().clearMessages(false))
                .client("/eco stats", mc -> mc.player.connection.sendCommand("eco stats"))
                .pause(1500)
                .prise("equipe-stats")
                .client("chat remonté", mc -> mc.gui.getChat().scrollChat(100))
                .pause(500)
                .prise("equipe-stats-haut")
                .client("chat redescendu", mc -> mc.gui.getChat().resetChatScroll())
                .client("chat vidé", mc -> mc.gui.getChat().clearMessages(false))
                .client("/eco ledger", mc -> mc.player.connection.sendCommand("eco ledger"))
                .pause(1500)
                .prise("equipe-registre")
                .client("chat vidé", mc -> mc.gui.getChat().clearMessages(false))
                .client("/eco loot et /eco quests", mc -> {
                    mc.player.connection.sendCommand("eco loot");
                    mc.player.connection.sendCommand("eco quests");
                })
                .pause(1500)
                .prise("equipe-butin-quetes")
                .client("chat vidé", mc -> mc.gui.getChat().clearMessages(false))
                .client("/eco power et /eco pending", mc -> {
                    mc.player.connection.sendCommand("eco power");
                    mc.player.connection.sendCommand("eco pending");
                })
                .pause(1500)
                .prise("equipe-power")
                .client("chat rendu", mc -> {
                    mc.options.chatHeightUnfocused().set(0.4440559392460575);
                    chatMasque(mc);
                })
                .serveur("retours de commande coupés", lieu -> retours(lieu.serveur(), false));
    }

    // --- outils -------------------------------------------------------------------------------------------------------

    private static JoueurDeTest figurant(Lieu lieu, String nom, Vec3 rel, float yaw, boolean vider) {
        Vec3 ou = lieu.v(rel.x, rel.y, rel.z);
        JoueurDeTest j = JoueurDeTest.rejoindre(lieu.monde(), nom, ou, vider, p -> {});
        j.joueur().teleportTo(lieu.monde(), ou.x, ou.y, ou.z, yaw, 0F);
        j.joueur().setYHeadRot(yaw);
        FIGURANTS.add(j);
        return j;
    }

    private static JoueurDeTest figurant(String nom) {
        for (JoueurDeTest j : FIGURANTS) {
            if (j.joueur().getGameProfile().getName().equals(nom)) {
                return j;
            }
        }
        throw new IllegalStateException("pas de joueur simulé " + nom);
    }

    private static void quitterFigurants() {
        for (JoueurDeTest j : FIGURANTS) {
            if (j.connecte()) {
                j.quitter();
            }
        }
        FIGURANTS.clear();
    }

    /** Une commande tapée par ce joueur simulé (sa source, ses droits). */
    private static void commandeDe(JoueurDeTest j, String ligne) {
        ServerPlayer p = j.joueur();
        int r = p.server.getCommands().performPrefixedCommand(p.createCommandSourceStack(), ligne);
        constat(p.getGameProfile().getName() + " tape /" + ligne + " -> " + r);
    }

    /** Les retours des commandes tapées par le joueur (règle sendCommandFeedback, coupée par l'atelier). */
    private static void retours(MinecraftServer srv, boolean oui) {
        srv.getGameRules().getRule(GameRules.RULE_SENDCOMMANDFEEDBACK).set(oui, srv);
    }

    /** Une commande de la console (niveau 4), sans retour. */
    private static void console(MinecraftServer srv, String ligne) {
        srv.getCommands().performPrefixedCommand(srv.createCommandSourceStack().withSuppressedOutput(), ligne);
    }

    /** Le lien [Accepter] de la demande de raccordement reçue par ce joueur simulé, joué en son nom. */
    private static boolean accord(JoueurDeTest j) {
        List<Component> recus = new ArrayList<>(j.composantsALaConnexion());
        recus.addAll(j.composantsRecus());
        for (Component c : recus) {
            for (String cmd : JoueurDeTest.commandes(c)) {
                if (cmd.startsWith("/power consent ")) {
                    commandeDe(j, cmd.substring(1));
                    return true;
                }
            }
        }
        return false;
    }

    /** Un clic droit de ce joueur (objet en main) sur la face d'un bloc, par le chemin du serveur. */
    private static void poserPar(ServerPlayer p, ItemStack pile, BlockPos pos, Direction face) {
        ItemStack avant = p.getItemInHand(InteractionHand.MAIN_HAND);
        p.setItemInHand(InteractionHand.MAIN_HAND, pile);
        try {
            Vec3 point = Vec3.atCenterOf(pos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
            p.gameMode.useItemOn(p, p.serverLevel(), pile, InteractionHand.MAIN_HAND, new BlockHitResult(point, face, pos, false));
        } finally {
            p.setItemInHand(InteractionHand.MAIN_HAND, avant);
        }
    }

    /** Le joueur oublie son ancien fournisseur (une scène rejouée dans le même monde en refonde un). */
    private static void oublierFournisseur(MinecraftServer srv, UUID qui) {
        PowerSuppliers r = PowerSuppliers.get(srv);
        r.byFounder(qui).ifPresent(f -> {
            for (PowerClient c : r.clients(f.id())) {
                r.removeClient(c.meterSerial());
            }
            r.removeIfEmpty(f.id());
        });
    }

    private static void viderCoffreFort(MinecraftServer srv, UUID qui) {
        SafeAccounts comptes = SafeAccounts.get(srv);
        for (Coin c : Coin.values()) {
            long n = comptes.count(qui, c);
            if (n > 0) {
                comptes.withdraw(qui, c, n);
            }
        }
    }

    /** Une bourse garnie (cuivre, argent, or, émeraude dans cet ordre ; jamais de rubis). */
    private static ItemStack bourse(long emeraude, long or, long argent, long cuivre) {
        ItemStack b = new ItemStack(EconomyRegistry.PURSE.get());
        Purse.add(b, Coin.EMERALD, emeraude);
        Purse.add(b, Coin.GOLD, or);
        Purse.add(b, Coin.SILVER, argent);
        Purse.add(b, Coin.COPPER, cuivre);
        return b;
    }

    /** Un étal tenu par {@code proprio} : pains, pommes, morue cuite ; les trois offres se voient sur le comptoir. */
    private static void etalGarni(Lieu lieu, BlockPos rel, ServerPlayer proprio) {
        lieu.poser(rel.getX(), rel.getY(), rel.getZ(), face(EconomyRegistry.MARKET_STALL.get()));
        MarketStallBlockEntity e = be(lieu, rel, MarketStallBlockEntity.class);
        e.setOwner(proprio.getUUID(), proprio.getGameProfile().getName());
        e.stock().setStackInSlot(0, new ItemStack(Items.BREAD, 32));
        e.stock().setStackInSlot(1, new ItemStack(Items.APPLE, 30));
        e.stock().setStackInSlot(2, new ItemStack(Items.COOKED_COD, 12));
        e.setOffer(0, new StallOffer(new ItemStack(Items.BREAD), 4, 30L));
        e.setOffer(1, new StallOffer(new ItemStack(Items.APPLE), 6, 40L));
        e.setOffer(2, new StallOffer(new ItemStack(Items.COOKED_COD), 3, 45L));
    }

    /** Des pièces posées au sol (jamais ramassées pendant la prise), en {@code (x, z)} relatifs. */
    private static void sol(Lieu lieu, double x, double z, Coin coin, int n) {
        Vec3 p = lieu.v(x, 0.05, z);
        CoinDropEntity c = CoinDropEntity.at(lieu.monde(), p.x, p.y, p.z, coin, n);
        c.setDeltaMovement(Vec3.ZERO);
        c.setPickUpDelay(CoinDropEntity.NEVER_PICKED_UP);
        lieu.monde().addFreshEntity(c);
    }

    private static BlockPos origine(int dx, int dy, int dz) {
        return WikiStudio.origineEnCours().offset(dx, dy, dz);
    }

    private static net.minecraft.world.level.block.state.BlockState blocClient(BlockPos rel) {
        Minecraft mc = Minecraft.getInstance();
        BlockPos o = WikiStudio.origineEnCours();
        return o == null || mc.level == null ? Blocks.AIR.defaultBlockState() : mc.level.getBlockState(o.offset(rel));
    }

    @Nullable
    private static Lieu lieuEnCours(Minecraft mc) {
        BlockPos o = WikiStudio.origineEnCours();
        return o == null ? null : new Lieu(mc.getSingleplayerServer(), o);
    }

    private static void chatVisible(Minecraft mc) {
        mc.gui.getChat().clearMessages(false);
        CHAT.clear();
        mc.options.chatVisibility().set(ChatVisiblity.FULL);
        mc.options.broadcastOptions();
        // Un chat plus étroit (en mémoire) : des lignes courtes, lisibles une fois l'image réduite à 390 px.
        mc.options.chatWidth().set(0.6);
        WikiStudio.ath(mc, true);
        WikiStudio.elements(VanillaGuiOverlay.CHAT_PANEL);
        WikiStudio.reticule(false);
        mc.gameRenderer.setRenderHand(false);
    }

    private static void chatMasque(Minecraft mc) {
        mc.gui.getChat().clearMessages(false);
        mc.options.chatVisibility().set(ChatVisiblity.HIDDEN);
        mc.options.broadcastOptions();
        mc.options.chatWidth().set(1.0);
        WikiStudio.ath(mc, false);
    }

    /** Vrai si un message reçu porte cette clé de traduction (le message lui-même ou une de ses parties). */
    private static boolean cle(String cle) {
        synchronized (CHAT) {
            for (Component c : CHAT) {
                if (porteCle(c, cle)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean porteCle(Component c, String cle) {
        if (c.getContents() instanceof TranslatableContents t) {
            if (t.getKey().startsWith(cle)) {
                return true;
            }
            for (Object arg : t.getArgs()) {
                if (arg instanceof Component inner && porteCle(inner, cle)) {
                    return true;
                }
            }
        }
        for (Component enfant : c.getSiblings()) {
            if (porteCle(enfant, cle)) {
                return true;
            }
        }
        return false;
    }

    /** Le style du dernier lien reçu dont la commande commence par {@code debut}, ou {@code null}. */
    @Nullable
    private static Style lien(String debut) {
        Style trouve = null;
        synchronized (CHAT) {
            for (Component c : CHAT) {
                Style s = lienDans(c, debut);
                if (s != null) {
                    trouve = s;
                }
            }
        }
        return trouve;
    }

    @Nullable
    private static Style lienDans(Component c, String debut) {
        ClickEvent clic = c.getStyle().getClickEvent();
        if (clic != null && clic.getValue().startsWith(debut)) {
            return c.getStyle();
        }
        if (c.getContents() instanceof TranslatableContents t) {
            for (Object arg : t.getArgs()) {
                if (arg instanceof Component inner) {
                    Style s = lienDans(inner, debut);
                    if (s != null) {
                        return s;
                    }
                }
            }
        }
        for (Component enfant : c.getSiblings()) {
            Style s = lienDans(enfant, debut);
            if (s != null) {
                return s;
            }
        }
        return null;
    }

    /** Un clic sur un lien du chat, comme le joueur le fait chat ouvert. */
    private static void cliquerLien(Minecraft mc, Style lien) {
        if (lien == null) {
            throw new IllegalStateException("lien absent du chat");
        }
        ChatScreen chat = new ChatScreen("");
        mc.setScreen(chat);
        chat.handleComponentClicked(lien);
        if (mc.screen == chat) {
            mc.setScreen(null);
        }
    }

    private static void taper(Minecraft mc, String texte) {
        for (char c : texte.toCharArray()) {
            mc.screen.charTyped(c, 0);
        }
    }

    private static void clicMenu(Minecraft mc, int indice, int bouton, ClickType type) {
        mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId, indice, bouton, type, mc.player);
    }

    /** La case du menu ouvert (côté inventaire du joueur) qui porte cette pièce. */
    private static int caseDe(Minecraft mc, Coin coin) {
        for (var s : mc.player.containerMenu.slots) {
            if (s.container == mc.player.getInventory() && s.getItem().is(coin.item())) {
                return s.index;
            }
        }
        throw new IllegalStateException("aucune pièce " + coin.key() + " dans l'inventaire");
    }

    private static int caseVide(Minecraft mc) {
        for (var s : mc.player.containerMenu.slots) {
            if (s.container == mc.player.getInventory() && s.getContainerSlot() < 36 && s.getItem().isEmpty()) {
                return s.index;
            }
        }
        throw new IllegalStateException("inventaire plein");
    }

    /** La case de la barre d'action (0 à 8) qui porte l'objet ; l'objet est d'abord ramené dans la barre au besoin. */
    private static int caseInventaire(Minecraft mc, Predicate<ItemStack> voulu) {
        for (int i = 0; i < 9; i++) {
            if (voulu.test(mc.player.getInventory().getItem(i))) {
                return i;
            }
        }
        for (int i = 9; i < 36; i++) {
            if (voulu.test(mc.player.getInventory().getItem(i))) {
                for (int h = 8; h >= 0; h--) {
                    if (mc.player.getInventory().getItem(h).isEmpty()) {
                        mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, i, h, ClickType.SWAP,
                                mc.player);
                        return h;
                    }
                }
            }
        }
        throw new IllegalStateException("objet absent de l'inventaire");
    }

    @Nullable
    private static ItemStack premiere(Minecraft mc, Predicate<ItemStack> voulu) {
        for (ItemStack s : mc.player.getInventory().items) {
            if (voulu.test(s)) {
                return s;
            }
        }
        return null;
    }

    private static int compter(Minecraft mc, Coin coin) {
        return compter(mc, coin.item());
    }

    private static int compter(Minecraft mc, net.minecraft.world.item.Item item) {
        int n = 0;
        for (ItemStack s : mc.player.getInventory().items) {
            if (s.is(item)) {
                n += s.getCount();
            }
        }
        return n;
    }

    private static long libre(Minecraft mc) {
        long n = 0;
        for (ItemStack s : mc.player.getInventory().items) {
            n += CoinMath.valueOf(s) + Purse.total(s);
        }
        return n;
    }

    private static long valeurPortee(Minecraft mc) {
        return libre(mc);
    }

    private static Player joueurVu(Minecraft mc, String nom) {
        if (mc.level == null) {
            return null;
        }
        for (Player j : mc.level.players()) {
            if (nom.equals(j.getGameProfile().getName())) {
                return j;
            }
        }
        return null;
    }

    @Nullable
    private static Entity entite(Minecraft mc, UUID id) {
        if (mc.level == null) {
            return null;
        }
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e.getUUID().equals(id)) {
                return e;
            }
        }
        return null;
    }

    /** S'accroupir (touche Maj tenue) ou se relever ; le vol est coupé pour que Maj ne fasse pas descendre. */
    private static void accroupir(Minecraft mc, boolean oui) {
        if (oui) {
            mc.player.getAbilities().flying = false;
        }
        mc.options.keyShift.setDown(oui);
    }

    private static void toucheJ(Minecraft mc, int action) {
        int code = QuestKeys.OPEN.getKey().getValue();
        mc.keyboardHandler.keyPress(mc.getWindow().getWindow(), code, GLFW.glfwGetKeyScancode(code), action, 0);
    }

    @Nullable
    private static QuestSyncPacket.Entry journaliere(int place) {
        QuestSyncPacket etat = QuestClientCache.get();
        if (etat == null) {
            return null;
        }
        for (QuestSyncPacket.Entry e : etat.quests()) {
            if (e.ref().kind() == QuestRef.Kind.DAILY && e.ref().index() == place) {
                return e;
            }
        }
        return null;
    }

    /** Le centre du bouton de la journalière {@code place} dans l'écran des quêtes. */
    @Nullable
    private static double[] boutonQuete(Minecraft mc, int place) {
        if (!(mc.screen instanceof QuestScreen q) || q.layout() == null) {
            return null;
        }
        QuestSyncPacket etat = QuestClientCache.get();
        int rang = 0;
        for (QuestSyncPacket.Entry e : etat.quests()) {
            if (e.ref().kind() == QuestRef.Kind.DAILY) {
                if (e.ref().index() == place) {
                    var b = q.layout().sections().get(0).rows().get(rang).button();
                    return b == null ? null : new double[] {b.x() + b.w() / 2.0, b.y() + b.h() / 2.0};
                }
                rang++;
            }
        }
        return null;
    }

    /** Le centre d'un élément d'un écran de pièces ou d'échange, décalé de {@code (dx, dy)}. */
    @Nullable
    private static double[] element(Minecraft mc, String id, double dx, double dy) {
        Optional<int[]> c = Optional.empty();
        if (mc.screen instanceof CoinAccountScreen<?> s) {
            c = s.elementCenter(id);
        } else if (mc.screen instanceof TradeScreen s) {
            c = s.elementCenter(id);
        }
        return c.map(p -> new double[] {p[0] + dx, p[1] + dy}).orElse(null);
    }

    private static void cliquerElement(Minecraft mc, String id) {
        double[] c = element(mc, id, 0, 0);
        if (c == null) {
            throw new IllegalStateException("aucun élément " + id + " sur " + mc.screen);
        }
        mc.screen.mouseClicked(c[0], c[1], 0);
    }

    @Nullable
    private static double[] centreCase(Minecraft mc, int indice, double dy) {
        return slotCentre(mc, indice, 0, dy);
    }

    @Nullable
    private static double[] slotCentre(Minecraft mc, int indice, double dx, double dy) {
        if (!(mc.screen instanceof AbstractContainerScreen<?> s)) {
            return null;
        }
        var c = s.getMenu().getSlot(indice);
        return new double[] {s.getGuiLeft() + c.x + 8 + dx, s.getGuiTop() + c.y + 8 + dy};
    }

    private static int dansEchange(Minecraft mc, boolean sienne, net.minecraft.world.item.Item objet) {
        if (!(mc.screen instanceof TradeScreen t)) {
            return 0;
        }
        int debut = sienne ? TradeMenu.OWN_START : TradeMenu.OTHER_START;
        int n = 0;
        for (int s = debut; s < debut + TradeMenu.OFFER_SIZE; s++) {
            ItemStack st = t.getMenu().getSlot(s).getItem();
            if (st.is(objet)) {
                n += st.getCount();
            }
        }
        return n;
    }

    /** Le bouton « Acheter » de la rangée {@code rangee} de la vitrine ouverte. */
    @Nullable
    private static int[] boutonVitrine(Minecraft mc, int rangee) {
        if (!(mc.screen instanceof AbstractContainerScreen<?> s)) {
            return null;
        }
        return new int[] {s.getGuiLeft() + 8 + 154 - 42 - 2 + 21,
            s.getGuiTop() + StallShopMenu.LIST_Y + rangee * StallShopMenu.ROW_H + (StallShopMenu.ROW_H - 14) / 2 + 7};
    }

    private static double[] ongletEtal(Minecraft mc, int i) {
        return gui(mc, 8 + i * 40 + 19, 18 + 7);
    }

    /** Un point du formulaire de facture ({@code left}, {@code top} lus sur l'écran). */
    @Nullable
    private static double[] formulaire(Minecraft mc, double gx, double gy) {
        return mc.screen instanceof InvoiceFormScreen ? new double[] {champ(mc.screen, "left") + gx,
            champ(mc.screen, "top") + gy} : null;
    }

    @Nullable
    private static double[] fenetreMotif(Minecraft mc, double gx, double gy) {
        return mc.screen instanceof TreasuryReasonScreen ? new double[] {champ(mc.screen, "left") + gx,
            champ(mc.screen, "top") + gy} : null;
    }

    /** Un point sur la ligne des boutons de l'écran de facture, décalé de {@code dy}. */
    @Nullable
    private static double[] boutonFacture(Minecraft mc, double gx, double dy) {
        return mc.screen instanceof InvoiceScreen ? new double[] {champ(mc.screen, "left") + gx,
            champ(mc.screen, "top") + champ(mc.screen, "buttonsY") + dy} : null;
    }

    private static int champ(Object o, String nom) {
        try {
            Field f = o.getClass().getDeclaredField(nom);
            f.setAccessible(true);
            return f.getInt(o);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("champ " + nom + " illisible : " + e);
        }
    }

    private static long totalEcran() {
        return Minecraft.getInstance().screen instanceof TreasuryScreen t ? t.getMenu().total() : -1;
    }

    @Nullable
    private static PowerPanelScreen panneau() {
        return Minecraft.getInstance().screen instanceof PowerPanelScreen s && s.layout() != null ? s : null;
    }

    @Nullable
    private static PowerPanelPacket.Client rangee(String abonne) {
        PowerPanelScreen s = panneau();
        if (s == null) {
            return null;
        }
        for (PowerPanelPacket.Client c : s.state().clients()) {
            if (c.subscriber().equalsIgnoreCase(abonne)) {
                return c;
            }
        }
        return null;
    }

    private static PowerPanelLayout.RowLayout rangeeMiseEnPage(String abonne) {
        PowerPanelScreen s = panneau();
        List<PowerPanelPacket.Client> clients = s.state().clients();
        for (int i = 0; i < clients.size(); i++) {
            if (clients.get(i).subscriber().equalsIgnoreCase(abonne)) {
                return s.layout().rows().get(i);
            }
        }
        throw new IllegalStateException("aucune ligne pour " + abonne);
    }

    private static PowerPanelLayout.Rect bouton(String abonne, int b) {
        return rangeeMiseEnPage(abonne).buttons().get(b);
    }

    @Nullable
    private static double[] rect(@Nullable PowerPanelLayout.Rect r, double fx, double fy) {
        return r == null ? null : new double[] {r.x() + r.w() * fx, r.y() + r.h() * fy};
    }

    private static void cliquerRect(Minecraft mc, PowerPanelLayout.Rect r) {
        if (r == null) {
            throw new IllegalStateException("bouton absent du panneau");
        }
        double x = r.x() + r.w() / 2.0;
        double y = r.y() + r.h() / 2.0;
        mc.screen.mouseClicked(x, y, 0);
        if (mc.screen != null) {
            mc.screen.mouseReleased(x, y, 0);
        }
    }

    /** Un point du monde projeté en coordonnées d'interface (pour un repère sur une entité). */
    @Nullable
    private static double[] projeterInterface(Minecraft mc, Vec3 point) {
        Optional<double[]> pc = Reperes.projeter(point);
        if (pc.isEmpty()) {
            return null;
        }
        double w = mc.getWindow().getGuiScaledWidth();
        double h = mc.getWindow().getGuiScaledHeight();
        return new double[] {pc.get()[0] / 100.0 * w, pc.get()[1] / 100.0 * h};
    }
}
