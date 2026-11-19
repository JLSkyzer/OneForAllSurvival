package dev.oneforallsurvival.dev;

import static dev.oneforallsurvival.dev.FilmTournage.LOG;
import static dev.oneforallsurvival.dev.FilmTournage.o;
import static dev.oneforallsurvival.dev.FilmTournage.serveur;
import static dev.oneforallsurvival.dev.FilmTournage.sur;

import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.stall.MarketStallBlock;
import dev.oneforallsurvival.economy.stall.StallActionPacket;
import dev.oneforallsurvival.economy.stall.StallCashPacket;
import dev.oneforallsurvival.economy.stall.StallOwnerMenu;
import dev.oneforallsurvival.economy.trade.TradeMenu;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Les gestes de Maya, le second joueur des films montés (développement seulement, jamais empaquetés) : une connexion
 * simulée côté serveur ({@link JoueurDeTest}), dont les gestes passent par les paquets d'un client ({@code envoyer}) ou
 * par les mêmes appels que l'essai de bout en bout. Sur le thread du serveur, sauf {@link #mayaVa} et {@link #mayaVue}.
 */
final class FilmMaya {

    static final String MAYA = "Maya";
    static JoueurDeTest maya;

    private FilmMaya() {}

    static ServerPlayer maya() {
        return maya.joueur();
    }

    static Player mayaVue(Minecraft mc) {
        return FilmTournage.joueurVu(mc, MAYA);
    }

    /** Sur le thread du serveur : Maya entre (ou revient) à cette place, regard vers le joueur au sud. */
    static void mayaEntre(MinecraftServer serveur, double x, double z, float yaw) {
        ServerLevel monde = serveur.overworld();
        maya = JoueurDeTest.rejoindre(monde, MAYA, new Vec3(x, o.getY(), z), false, j -> j.setYRot(yaw));
        maya().teleportTo(monde, x, o.getY(), z, yaw, 0F);
        maya().setYHeadRot(yaw);
    }

    static void mayaVa(Minecraft mc, double x, double z, float yaw) {
        sur(mc, () -> {
            maya().teleportTo(serveur(mc).overworld(), x, o.getY(), z, yaw, 0F);
            maya().setYHeadRot(yaw);
        });
    }

    static boolean mayaEnMainVide(ServerPlayer j) {
        for (int i = 0; i < 9; i++) {
            if (j.getInventory().getItem(i).isEmpty()) {
                j.getInventory().selected = i;
                return true;
            }
        }
        return false;
    }

    static int caseMaya(ServerPlayer j, Item objet) {
        for (int i = 0; i < 36; i++) {
            if (j.getInventory().getItem(i).is(objet)) {
                return i;
            }
        }
        return -1;
    }

    /** Maya pose un objet de bloc sur le sol sous {@code pos} (clic droit sur la face du dessus). */
    static void mayaPoseBloc(MinecraftServer serveur, BlockPos pos, Item objet) {
        ServerLevel monde = serveur.overworld();
        ServerPlayer j = maya();
        j.teleportTo(monde, pos.getX() + 0.5, o.getY(), pos.getZ() + 2.5, 180F, 30F);
        mayaEnMainVide(j);
        j.getInventory().setItem(j.getInventory().selected, new ItemStack(objet));
        BlockPos sol = pos.below();
        // Accroupie : un clic sur un bloc qui s'ouvre (la trémie sous son étal) pose l'objet au lieu d'ouvrir le bloc.
        j.setShiftKeyDown(true);
        var r = j.gameMode.useItemOn(j, monde, j.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(sol).add(0, 0.5, 0), Direction.UP, sol, false));
        j.setShiftKeyDown(false);
        j.swing(InteractionHand.MAIN_HAND, true);
        LOG.info("[film] Maya pose {} en {} : {}", objet, pos, r);
    }

    /** Maya pose {@code n} objets dans sa moitié de la fenêtre d'échange (clics du menu, comme un client). */
    static void mayaPoseDansEchange(Item objet, int n) {
        ServerPlayer j = maya();
        if (!(j.containerMenu instanceof TradeMenu menu)) {
            throw new IllegalStateException("Maya n'a pas la fenêtre d'échange");
        }
        int depuis = caseMaya(j, objet);
        int cible = -1;
        for (int s = TradeMenu.OWN_START; s < TradeMenu.OWN_START + TradeMenu.OFFER_SIZE && cible < 0; s++) {
            ItemStack dans = menu.getSlot(s).getItem();
            if (dans.isEmpty() || dans.is(objet)) {
                cible = s;
            }
        }
        int menuDepuis = TradeMenu.slotOfInventory(depuis);
        menu.clicked(menuDepuis, 0, ClickType.PICKUP, j);
        for (int i = 0; i < n; i++) {
            menu.clicked(cible, 1, ClickType.PICKUP, j);
        }
        menu.clicked(menuDepuis, 0, ClickType.PICKUP, j);
        menu.broadcastChanges();
    }

    static int caseGestion(int inventaire) {
        return StallOwnerMenu.INVENTORY_START + (inventaire < 9 ? 27 + inventaire : inventaire - 9);
    }

    /**
     * Maya garnit son étal par les gestes de la gestion : trois offres de vente (le modèle tenu au curseur), un rachat de blé,
     * le stock rangé (majuscule-clic), puis 1 or en caisse.
     */
    static void mayaGarnit(BlockPos pos) {
        ServerPlayer j = maya();
        if (!MarketStallBlock.openManage(j, pos) || !(j.containerMenu instanceof StallOwnerMenu menu)) {
            throw new IllegalStateException("la gestion ne s'ouvre pas pour Maya");
        }
        Object[][] offres = {{Items.BREAD, 4, 30L}, {Items.APPLE, 3, 12L}, {Items.COOKED_COD, 2, 15L}};
        for (int i = 0; i < offres.length; i++) {
            int c = caseGestion(caseMaya(j, (Item) offres[i][0]));
            menu.clicked(c, 0, ClickType.PICKUP, j);
            LOG.info("[film] offre de Maya {} : {}", i,
                    StallActionPacket.apply(j, StallActionPacket.setOffer(pos, i, (Integer) offres[i][1], (Long) offres[i][2])));
            menu.clicked(c, 0, ClickType.PICKUP, j);
        }
        int ble = caseGestion(caseMaya(j, Items.WHEAT));
        menu.clicked(ble, 0, ClickType.PICKUP, j);
        LOG.info("[film] rachat de Maya : {}", StallActionPacket.apply(j, StallActionPacket.setBuyback(pos, 0, 8, 16L, 5)));
        menu.clicked(ble, 0, ClickType.PICKUP, j);
        for (Item it : List.of(Items.BREAD, Items.APPLE, Items.COOKED_COD)) {
            menu.clicked(caseGestion(caseMaya(j, it)), 0, ClickType.QUICK_MOVE, j);
        }
        menu.broadcastChanges();
        j.closeContainer();
        if (MarketStallBlock.openCash(j, pos)) {
            LOG.info("[film] caisse de Maya : {}", StallCashPacket.apply(j, StallCashPacket.deposit(pos, Coin.GOLD, 1)));
            j.closeContainer();
        }
    }
}
