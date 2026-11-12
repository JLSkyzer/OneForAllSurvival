package dev.oneforallsurvival.economy;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Le menu du coffre-fort : aucune case, car rien ne passe par le curseur. Les pièces entrent et sortent par
 * {@link SafeActionPacket}. Le compte voyage vers le client par des champs de données : le jeu n'en envoie que 16
 * bits à la fois, chaque coupure en prend donc quatre, remontés par {@link #count}. Le client n'a que la copie de
 * ce que le serveur lui a dit ; il n'a jamais autorité.
 */
public final class SafeMenu extends AbstractContainerMenu {

    private static final int PARTS = 4;
    private static final int PART_BITS = 16;
    private static final long PART_MASK = (1L << PART_BITS) - 1L;

    private final BlockPos pos;
    /** Le propriétaire du compte et les comptes : côté serveur seulement. */
    @Nullable
    private final UUID owner;
    @Nullable
    private final SafeAccounts accounts;
    /** Côté client : les morceaux reçus, quatre par coupure. */
    private final int[] mirror = new int[Coin.values().length * PARTS];

    /** Côté serveur. */
    public SafeMenu(int containerId, Inventory inventory, BlockPos pos, UUID owner, SafeAccounts accounts) {
        this(containerId, pos, owner, accounts);
    }

    /** Côté client : ce que {@code SafeBlock#open} a écrit. */
    public static SafeMenu fromNetwork(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        return new SafeMenu(containerId, buf.readBlockPos(), null, null);
    }

    private SafeMenu(int containerId, BlockPos pos, @Nullable UUID owner, @Nullable SafeAccounts accounts) {
        super(EconomyRegistry.SAFE_MENU.get(), containerId);
        this.pos = pos.immutable();
        this.owner = owner;
        this.accounts = accounts;
        for (Coin coin : Coin.values()) {
            for (int part = 0; part < PARTS; part++) {
                addDataSlot(new Part(coin, part));
            }
        }
    }

    /** Un morceau de 16 bits du nombre de pièces d'une coupure. */
    private final class Part extends DataSlot {
        private final Coin coin;
        private final int part;

        Part(Coin coin, int part) {
            this.coin = coin;
            this.part = part;
        }

        @Override
        public int get() {
            if (accounts == null || owner == null) {
                return mirror[coin.ordinal() * PARTS + part];
            }
            return (int) ((accounts.count(owner, coin) >>> (part * PART_BITS)) & PART_MASK);
        }

        @Override
        public void set(int value) {
            mirror[coin.ordinal() * PARTS + part] = value & (int) PART_MASK;
        }
    }

    public BlockPos pos() {
        return pos;
    }

    /** Le propriétaire du compte affiché ; nul côté client. */
    @Nullable
    public UUID owner() {
        return owner;
    }

    /** Le nombre de pièces de cette coupure dans le compte (côté client : ce que le serveur a envoyé). */
    public long count(Coin coin) {
        if (accounts != null && owner != null) {
            return accounts.count(owner, coin);
        }
        long total = 0L;
        for (int part = 0; part < PARTS; part++) {
            total |= (mirror[coin.ordinal() * PARTS + part] & PART_MASK) << (part * PART_BITS);
        }
        return total;
    }

    /** La valeur du compte, en cuivres. */
    public long total() {
        java.util.EnumMap<Coin, Long> counts = new java.util.EnumMap<>(Coin.class);
        for (Coin coin : Coin.values()) {
            counts.put(coin, count(coin));
        }
        return SafeAccounts.saturatedValue(counts);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
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
