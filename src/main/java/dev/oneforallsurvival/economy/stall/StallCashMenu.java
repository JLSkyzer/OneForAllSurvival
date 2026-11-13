package dev.oneforallsurvival.economy.stall;

import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinAccountMenu;
import dev.oneforallsurvival.economy.EconomyRegistry;
import java.util.EnumMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * La caisse d'un étal, onglet Caisse de la gestion : le menu commun du coffre-fort et de la bourse
 * ({@link CoinAccountMenu}), sur la caisse de l'étal. Comme celui du coffre-fort, il est déclaré rangement permis
 * pour l'argent, n'a aucune case de contenu (rien ne passe par le curseur : les pièces vont et viennent par
 * {@link StallCashPacket}) et porte les 36 cases du joueur pour que l'inventaire du client suive.
 */
public final class StallCashMenu extends CoinAccountMenu {

    /** Bouton de menu : retour à l'onglet Stock. */
    public static final int BUTTON_STOCK = 0;

    private final BlockPos pos;
    /** Côté serveur : l'étal ; nul côté client. */
    @Nullable
    private final MarketStallBlockEntity stall;

    /** Côté serveur avec l'étal ; côté client {@code stall} est nul ({@link #fromNetwork}). */
    public StallCashMenu(int containerId, Inventory inventory, BlockPos pos, @Nullable MarketStallBlockEntity stall) {
        super(EconomyRegistry.STALL_CASH_MENU.get(), containerId, inventory, -1);
        this.pos = pos.immutable();
        this.stall = stall;
    }

    /** Côté client : ce que {@code MarketStallBlock#openCash} a écrit. */
    public static StallCashMenu fromNetwork(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        return new StallCashMenu(containerId, inventory, buf.readBlockPos(), null);
    }

    public BlockPos pos() {
        return pos;
    }

    /** L'étal dont la caisse est affichée ; nul côté client. */
    @Nullable
    public MarketStallBlockEntity stall() {
        return stall;
    }

    @Override
    protected boolean isServerSide() {
        return stall != null;
    }

    @Override
    protected EnumMap<Coin, Long> liveContents() {
        return stall.cash();
    }

    @Override
    public boolean usesPurses() {
        return true;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (stall == null || !(player instanceof ServerPlayer serverPlayer) || !stillValid(player)) {
            return false;
        }
        return id == BUTTON_STOCK && MarketStallBlock.openManage(serverPlayer, pos);
    }

    /** Comme la gestion : l'étal, la portée, le droit. */
    @Override
    public boolean stillValid(Player player) {
        if (!player.level().getBlockState(pos).is(EconomyRegistry.MARKET_STALL.get())) {
            return false;
        }
        if (player.distanceToSqr(Vec3.atCenterOf(pos)) > MarketStallBlock.MAX_DISTANCE_SQR) {
            return false;
        }
        return stall == null || (player.level().getBlockEntity(pos) == stall && MarketStallBlock.mayManage(player, stall.owner()));
    }
}
