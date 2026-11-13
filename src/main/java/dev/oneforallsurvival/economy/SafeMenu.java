package dev.oneforallsurvival.economy;

import java.util.EnumMap;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Le menu du coffre-fort : le compte n'a aucune case, rien ne passe par le curseur. Les pièces entrent et sortent par
 * {@link SafeActionPacket} ; le compte voyage vers le client par les champs de données de {@link CoinAccountMenu}.
 */
public final class SafeMenu extends CoinAccountMenu {

    private final BlockPos pos;
    /** Le propriétaire du compte et les comptes : côté serveur seulement. */
    @Nullable
    private final UUID owner;
    @Nullable
    private final SafeAccounts accounts;

    /** Côté serveur (avec le propriétaire et les comptes) ; côté client, les deux sont nuls. */
    public SafeMenu(int containerId, Inventory inventory, BlockPos pos, @Nullable UUID owner,
            @Nullable SafeAccounts accounts) {
        super(EconomyRegistry.SAFE_MENU.get(), containerId, inventory, -1);
        this.pos = pos.immutable();
        this.owner = owner;
        this.accounts = accounts;
    }

    /** Côté client : ce que {@code SafeBlock#open} a écrit. */
    public static SafeMenu fromNetwork(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        return new SafeMenu(containerId, inventory, buf.readBlockPos(), null, null);
    }

    @Override
    protected boolean isServerSide() {
        return accounts != null && owner != null;
    }

    @Override
    protected EnumMap<Coin, Long> liveContents() {
        return new SafeStore(accounts, owner).contents();
    }

    @Override
    public boolean usesPurses() {
        return true;
    }

    public BlockPos pos() {
        return pos;
    }

    /** Le propriétaire du compte affiché ; nul côté client. */
    @Nullable
    public UUID owner() {
        return owner;
    }

    /**
     * Le bloc est toujours là, le joueur à portée et toujours ayant droit : l'équipe qui perd son niveau, ou un
     * propriétaire dont le coffre-fort a été remplacé, voit l'écran se fermer. Côté client, qui ne connaît ni le
     * compte ni les droits, seul le bloc compte.
     */
    @Override
    public boolean stillValid(Player player) {
        if (!player.level().getBlockState(pos).is(EconomyRegistry.SAFE.get())) {
            return false;
        }
        if (player.distanceToSqr(Vec3.atCenterOf(pos)) > SafeBlock.MAX_DISTANCE_SQR) {
            return false;
        }
        return owner == null || (SafeBlock.mayAccess(player, owner)
                && player.level().getBlockEntity(pos) instanceof SafeBlockEntity be && owner.equals(be.owner()));
    }
}
