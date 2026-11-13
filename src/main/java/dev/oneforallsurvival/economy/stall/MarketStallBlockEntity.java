package dev.oneforallsurvival.economy.stall;

import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.CoinStore;
import dev.oneforallsurvival.economy.EconomyRegistry;
import dev.oneforallsurvival.economy.MoneyStorageRules;
import dev.oneforallsurvival.economy.Purse;
import java.util.EnumMap;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;

/**
 * Ce que garde un étal : son propriétaire, un stock de 27 cases, une caisse (des pièces comptées par coupure) et le
 * registre de ses opérations. La tâche suivante y ajoute les offres ; {@link #version()} compte leurs changements,
 * pour qu'un achat refuse un affichage périmé.
 *
 * <p>L'entité n'implémente pas {@code Container} : une trémie vanilla ne la voit que par la capacité d'objets de
 * Forge, et celle-ci n'expose que {@link StallStockHandler#insertOnly()}. La caisse n'est exposée nulle part.
 * Seuls le propriétaire (le poseur) et son nom sont envoyés aux clients ; le contenu ne leur est jamais envoyé hors
 * des écrans de gestion.
 */
public final class MarketStallBlockEntity extends BlockEntity {

    private static final String TAG_OWNER = "Owner";
    private static final String TAG_OWNER_NAME = "OwnerName";
    private static final String TAG_STOCK = "Stock";
    private static final String TAG_CASH = "Cash";
    private static final String TAG_LEDGER = "Ledger";
    private static final String TAG_VERSION = "Version";

    @Nullable
    private UUID owner;
    private String ownerName = "";
    private final StallStockHandler stock = new StallStockHandler();
    private final long[] cash = new long[Coin.values().length];
    private final StallLedger ledger = new StallLedger();
    private int version;
    private LazyOptional<IItemHandler> items = LazyOptional.of(stock::insertOnly);

    public MarketStallBlockEntity(BlockPos pos, BlockState state) {
        super(EconomyRegistry.MARKET_STALL_BE.get(), pos, state);
        stock.setOnChange(this::setChanged);
    }

    /** Le propriétaire, ou {@code null} pour un étal posé sans joueur (commande de l'équipe). */
    @Nullable
    public UUID owner() {
        return owner;
    }

    /** Le nom du propriétaire à la pose ; vide s'il n'y en a pas. */
    public String ownerName() {
        return ownerName;
    }

    public void setOwner(@Nullable UUID owner, String name) {
        this.owner = owner;
        this.ownerName = name == null ? "" : name;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** Le stock : 27 cases, jamais d'argent. */
    public StallStockHandler stock() {
        return stock;
    }

    public StallLedger ledger() {
        return ledger;
    }

    /** Le compteur des changements d'offre (tâche suivante) ; 0 à la pose. */
    public int version() {
        return version;
    }

    /** À appeler à chaque changement d'offre : un affichage vu avant n'est plus valable. */
    public void bumpVersion() {
        version++;
        setChanged();
    }

    // ---------------------------------------------------------------------------------------------
    // Caisse
    // ---------------------------------------------------------------------------------------------

    /** Une copie de la caisse, coupures nulles omises. */
    public EnumMap<Coin, Long> cash() {
        EnumMap<Coin, Long> out = new EnumMap<>(Coin.class);
        for (Coin coin : Coin.values()) {
            if (cash[coin.ordinal()] > 0L) {
                out.put(coin, cash[coin.ordinal()]);
            }
        }
        return out;
    }

    public long cashCount(Coin coin) {
        return cash[coin.ordinal()];
    }

    /** La valeur de la caisse en cuivres. */
    public long cashValue() {
        return CoinMath.valueOf(cash());
    }

    /** Combien de pièces de cette coupure la caisse peut encore recevoir. */
    public long cashRoom(Coin coin) {
        return Math.max(0L, CoinStore.MAX_PER_COIN - cash[coin.ordinal()]);
    }

    /** Ajoute des pièces ; l'appelant a vérifié {@link #cashRoom} (sinon une exception, rien n'est écrit). */
    public void addCash(Coin coin, long count) {
        if (count < 0L || count > cashRoom(coin)) {
            throw new IllegalArgumentException("caisse : " + count + " " + coin + " ne tiennent pas");
        }
        if (count > 0L) {
            cash[coin.ordinal()] += count;
            setChanged();
        }
    }

    /** Retire exactement {@code count} pièces, ou rien. @return faux si la caisse en a moins (ou si {@code count} n'est pas positif) */
    public boolean removeCash(Coin coin, long count) {
        if (count <= 0L || cash[coin.ordinal()] < count) {
            return false;
        }
        cash[coin.ordinal()] -= count;
        setChanged();
        return true;
    }

    // ---------------------------------------------------------------------------------------------
    // Capacités
    // ---------------------------------------------------------------------------------------------

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return items.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        items.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        items = LazyOptional.of(stock::insertOnly);
    }

    // ---------------------------------------------------------------------------------------------
    // Sauvegarde
    // ---------------------------------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (owner != null) {
            tag.putUUID(TAG_OWNER, owner);
        }
        tag.putString(TAG_OWNER_NAME, ownerName);
        tag.put(TAG_STOCK, stock.serializeNBT());
        CompoundTag coins = new CompoundTag();
        for (Coin coin : Coin.values()) {
            if (cash[coin.ordinal()] > 0L) {
                coins.putLong(coin.key(), cash[coin.ordinal()]);
            }
        }
        tag.put(TAG_CASH, coins);
        tag.put(TAG_LEDGER, ledger.save());
        tag.putInt(TAG_VERSION, version);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        owner = tag.hasUUID(TAG_OWNER) ? tag.getUUID(TAG_OWNER) : null;
        ownerName = tag.getString(TAG_OWNER_NAME);
        stock.deserializeNBT(tag.getCompound(TAG_STOCK));
        // Un fichier abîmé ne fait pas gagner de pièces : jamais négatif, jamais au-dessus du plafond.
        CompoundTag coins = tag.getCompound(TAG_CASH);
        for (Coin coin : Coin.values()) {
            cash[coin.ordinal()] = Math.max(0L, Math.min(CoinStore.MAX_PER_COIN, coins.getLong(coin.key())));
        }
        ledger.load(tag.getList(TAG_LEDGER, Tag.TAG_COMPOUND));
        moveMoneyOutOfStock();
        version = Math.max(0, tag.getInt(TAG_VERSION));
    }

    /**
     * Un stock relu ne garde pas d'argent (un objet à {@code BlockEntityTag} posé par commande ou en créatif contourne
     * {@code isItemValid}) : une pièce passe dans la caisse, et le contenu d'une bourse aussi (la bourse reste, vide).
     * La valeur est conservée, rien n'est détruit ni dupliqué ; ce qui ne tiendrait pas sous le plafond de la caisse
     * reste où c'est (cas qui n'arrive que sur un fichier forgé).
     */
    private void moveMoneyOutOfStock() {
        for (int i = 0; i < stock.getSlots(); i++) {
            ItemStack stack = stock.getStackInSlot(i);
            if (stack.isEmpty() || !MoneyStorageRules.isMoney(stack)) {
                continue;
            }
            Optional<Coin> coin = Coin.fromItem(stack.getItem());
            if (coin.isPresent()) {
                long moved = Math.min(stack.getCount(), cashRoom(coin.get()));
                cash[coin.get().ordinal()] += moved;
                stack.shrink((int) moved);
                if (stack.isEmpty()) {
                    stock.setStackInSlot(i, ItemStack.EMPTY);
                }
            } else if (Purse.isPurse(stack)) {
                for (Coin c : Coin.values()) {
                    long moved = Math.min(Purse.count(stack, c), cashRoom(c));
                    if (moved > 0L && Purse.remove(stack, c, moved)) {
                        cash[c.ordinal()] += moved;
                    }
                }
            }
        }
    }

    /** Aux clients : le propriétaire et son nom, rien du contenu. */
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        if (owner != null) {
            tag.putUUID(TAG_OWNER, owner);
        }
        tag.putString(TAG_OWNER_NAME, ownerName);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
