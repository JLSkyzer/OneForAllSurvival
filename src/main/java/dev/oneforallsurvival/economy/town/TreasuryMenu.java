package dev.oneforallsurvival.economy.town;

import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinAccountMenu;
import dev.oneforallsurvival.economy.EconomyRegistry;
import dev.oneforallsurvival.economy.TreasuryAccount;
import java.util.EnumMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Le menu du trésor : le même que celui du coffre-fort ({@link CoinAccountMenu}), sur le compte du trésor. Aucune
 * case pour le compte : les pièces entrent et sortent par {@link TreasuryActionPacket}.
 */
public final class TreasuryMenu extends CoinAccountMenu {

    private final BlockPos pos;
    /** Le compte : côté serveur seulement. */
    @Nullable
    private final TreasuryAccount account;

    /** Côté serveur (avec le compte) ; côté client, le compte est nul. */
    public TreasuryMenu(int containerId, Inventory inventory, BlockPos pos, @Nullable TreasuryAccount account) {
        super(EconomyRegistry.TREASURY_MENU.get(), containerId, inventory, -1);
        this.pos = pos.immutable();
        this.account = account;
    }

    /** Côté client : ce que {@code TreasuryBlock#open} a écrit. */
    public static TreasuryMenu fromNetwork(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        return new TreasuryMenu(containerId, inventory, buf.readBlockPos(), null);
    }

    @Override
    protected boolean isServerSide() {
        return account != null;
    }

    @Override
    protected EnumMap<Coin, Long> liveContents() {
        return new TreasuryStore(account).contents();
    }

    @Override
    public boolean usesPurses() {
        return true;
    }

    public BlockPos pos() {
        return pos;
    }

    /**
     * Le bloc est toujours là, à l'emplacement noté, le joueur à portée et toujours ayant droit : un maire destitué,
     * l'équipe qui perd son niveau, ou un trésor déplacé ferment l'écran. Côté client seul le bloc compte.
     */
    @Override
    public boolean stillValid(Player player) {
        if (!player.level().getBlockState(pos).is(EconomyRegistry.TREASURY.get())) {
            return false;
        }
        if (player.distanceToSqr(Vec3.atCenterOf(pos)) > TreasuryBlock.MAX_DISTANCE_SQR) {
            return false;
        }
        if (account == null) {
            return true;
        }
        return player.level() instanceof ServerLevel level && TreasuryBlock.isNoted(level, pos)
                && TownBlockRules.mayOpenTreasury(player);
    }
}
