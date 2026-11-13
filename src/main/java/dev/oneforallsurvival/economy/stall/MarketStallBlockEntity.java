package dev.oneforallsurvival.economy.stall;

import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.CoinStore;
import dev.oneforallsurvival.economy.EconomyConfig;
import dev.oneforallsurvival.economy.EconomyRegistry;
import dev.oneforallsurvival.economy.MoneyStorageRules;
import dev.oneforallsurvival.economy.Purse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
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
 * Ce que garde un étal : son propriétaire, un stock de 27 cases, une caisse (des pièces comptées par coupure), le
 * registre de ses opérations, ses offres de vente et de rachat. {@link #version()} compte les écritures d'offre, pour
 * qu'un achat refuse un affichage périmé.
 *
 * <p>L'entité n'implémente pas {@code Container} : une trémie vanilla ne la voit que par la capacité d'objets de
 * Forge, et celle-ci n'expose que {@link StallStockHandler#insertOnly()}. La caisse n'est exposée nulle part.
 * Sont envoyés aux clients : le propriétaire, son nom, les offres (modèle, lot, prix, lots de rachat restants) et la
 * version, informations publiques de la vitrine. Le stock, la caisse et le registre ne leur sont jamais envoyés hors
 * des écrans de gestion (la vitrine ne reçoit que des disponibilités, par ses données de menu).
 */
public final class MarketStallBlockEntity extends BlockEntity {

    private static final String TAG_OWNER = "Owner";
    private static final String TAG_OWNER_NAME = "OwnerName";
    private static final String TAG_STOCK = "Stock";
    private static final String TAG_CASH = "Cash";
    private static final String TAG_LEDGER = "Ledger";
    private static final String TAG_VERSION = "Version";
    private static final String TAG_OFFERS = "Offers";
    private static final String TAG_BUYBACKS = "Buybacks";

    /** Cases d'offre gardées au plus (le plafond du réglage {@code stallMaxOffers}). */
    public static final int MAX_OFFERS = 18;

    /**
     * Taille totale des modèles d'un étal (ventes et rachats), NBT sérialisé : 32 Kio. Trente-six modèles courants
     * (quelques centaines d'octets chacun) y tiennent largement ; l'étal entier part aux clients avec le chunk.
     */
    public static final int MAX_TEMPLATES_BYTES = 32 * 1024;

    @Nullable
    private UUID owner;
    private String ownerName = "";
    private final StallStockHandler stock = new StallStockHandler();
    private final long[] cash = new long[Coin.values().length];
    private final StallLedger ledger = new StallLedger();
    private int version;
    private final StallOffer[] offers = new StallOffer[MAX_OFFERS];
    private final StallBuyback[] buybacks = new StallBuyback[MAX_OFFERS];
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

    /** Le compteur des écritures d'offre ; 0 à la pose. Une vente ou un rachat ne le change pas. */
    public int version() {
        return version;
    }

    /** À appeler à chaque changement d'offre : un affichage vu avant n'est plus valable. */
    public void bumpVersion() {
        version++;
        setChanged();
    }

    // ---------------------------------------------------------------------------------------------
    // Offres
    // ---------------------------------------------------------------------------------------------

    /** Le nombre de cases d'offre (de vente, et de rachat) de chaque étal : le réglage, borné à {@link #MAX_OFFERS}. */
    public static int offerSlots() {
        return Math.max(1, Math.min(MAX_OFFERS, EconomyConfig.stallMaxOffers()));
    }

    /** Les offres de vente, une par case ({@link #offerSlots()}), nulle pour une case vide. Lecture seule. */
    public List<StallOffer> offers() {
        return Collections.unmodifiableList(Arrays.asList(Arrays.copyOf(offers, offerSlots())));
    }

    /** Les offres de rachat, une par case, nulle pour une case vide. Lecture seule. */
    public List<StallBuyback> buybacks() {
        return Collections.unmodifiableList(Arrays.asList(Arrays.copyOf(buybacks, offerSlots())));
    }

    /**
     * Écrit (ou efface, {@code null}) l'offre de vente de la case {@code index}. Le droit est vérifié par l'appelant.
     * Toute écriture change la version et part aux clients.
     *
     * @return faux, sans rien écrire, si la case est hors des bornes ou l'offre invalide
     */
    public boolean setOffer(int index, @Nullable StallOffer offer) {
        if (index < 0 || index >= offerSlots() || (offer != null && (!offer.isValid() || !templateFits(false, index, offer.template())))) {
            return false;
        }
        offers[index] = offer;
        bumpVersion();
        syncToClients();
        return true;
    }

    /** Comme {@link #setOffer}, pour une offre de rachat (de 1 à 9 999 lots). */
    public boolean setBuyback(int index, @Nullable StallBuyback buyback) {
        if (index < 0 || index >= offerSlots() || (buyback != null
                && (!StallBuyback.valid(buyback.template(), buyback.lot(), buyback.price(), buyback.lotsLeft())
                        || !templateFits(true, index, buyback.template())))) {
            return false;
        }
        buybacks[index] = buyback;
        bumpVersion();
        syncToClients();
        return true;
    }

    /**
     * Ce modèle peut-il occuper cette case ? Au plus {@link StallOffer#MAX_TEMPLATE_BYTES}, et le total des modèles de
     * l'étal (celui de la case remplacé par celui-ci) au plus {@link #MAX_TEMPLATES_BYTES}.
     */
    public boolean templateFits(boolean buyback, int index, ItemStack template) {
        int size = StallOffer.nbtSize(template);
        if (size > StallOffer.MAX_TEMPLATE_BYTES) {
            return false;
        }
        long total = size;
        for (int i = 0; i < MAX_OFFERS; i++) {
            if (offers[i] != null && !(!buyback && i == index)) {
                total += StallOffer.nbtSize(offers[i].template());
            }
            if (buybacks[i] != null && !(buyback && i == index)) {
                total += StallOffer.nbtSize(buybacks[i].template());
            }
        }
        return total <= MAX_TEMPLATES_BYTES;
    }

    /** Un rachat a eu lieu : un lot de moins. Ce n'est pas une écriture d'offre, la version ne change pas. */
    void consumeBuybackLot(int index) {
        if (buybacks[index] != null) {
            buybacks[index] = buybacks[index].oneLotLess();
            syncToClients();
        }
    }

    /** Les lots entiers de l'offre de vente {@code index} en stock ; 0 sans offre. */
    public int available(int offerIndex) {
        if (offerIndex < 0 || offerIndex >= offerSlots() || offers[offerIndex] == null) {
            return 0;
        }
        StallOffer offer = offers[offerIndex];
        return countInStock(offer::matches) / offer.lot();
    }

    /** Le nombre d'objets du stock qui satisfont {@code matches}. */
    public int countInStock(Predicate<ItemStack> matches) {
        int total = 0;
        for (int i = 0; i < stock.getSlots(); i++) {
            ItemStack stack = stock.getStackInSlot(i);
            if (!stack.isEmpty() && matches.test(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /**
     * Retire du stock exactement {@code count} objets qui satisfont {@code matches}, ou rien.
     *
     * @return les piles retirées, vide si le stock n'en a pas assez
     */
    List<ItemStack> takeFromStock(Predicate<ItemStack> matches, int count) {
        List<ItemStack> out = new ArrayList<>();
        if (countInStock(matches) < count) {
            return out;
        }
        int left = count;
        for (int i = 0; i < stock.getSlots() && left > 0; i++) {
            ItemStack stack = stock.getStackInSlot(i);
            if (!stack.isEmpty() && matches.test(stack)) {
                ItemStack taken = stock.extractItem(i, Math.min(left, stack.getCount()), false);
                left -= taken.getCount();
                out.add(taken);
            }
        }
        return out;
    }

    /** Part aux clients qui voient l'étal : propriétaire, offres, version. */
    private void syncToClients() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
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

    /**
     * Remplace la caisse par {@code box} (un paiement par la caisse, déjà calculé à valeur conservée par
     * {@link CommerceMath#payFromBox}). Une coupure négative ou au-dessus du plafond : une exception, rien n'est écrit.
     */
    void replaceCash(Map<Coin, Long> box) {
        long[] next = new long[cash.length];
        for (Map.Entry<Coin, Long> entry : box.entrySet()) {
            long n = entry.getValue();
            if (n < 0L || n > CoinStore.MAX_PER_COIN) {
                throw new IllegalArgumentException("caisse : " + n + " " + entry.getKey());
            }
            next[entry.getKey().ordinal()] = n;
        }
        System.arraycopy(next, 0, cash, 0, cash.length);
        setChanged();
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
        saveOffers(tag);
    }

    private void saveOffers(CompoundTag tag) {
        ListTag sales = new ListTag();
        ListTag buys = new ListTag();
        for (int i = 0; i < MAX_OFFERS; i++) {
            if (offers[i] != null) {
                CompoundTag one = offers[i].save();
                one.putInt("slot", i);
                sales.add(one);
            }
            if (buybacks[i] != null) {
                CompoundTag one = buybacks[i].save();
                one.putInt("slot", i);
                buys.add(one);
            }
        }
        tag.put(TAG_OFFERS, sales);
        tag.put(TAG_BUYBACKS, buys);
    }

    /** Relit les offres ; une offre abîmée, hors des cases ou dont le modèle est de l'argent est ignorée. */
    private void loadOffers(CompoundTag tag) {
        Arrays.fill(offers, null);
        Arrays.fill(buybacks, null);
        ListTag sales = tag.getList(TAG_OFFERS, Tag.TAG_COMPOUND);
        for (int i = 0; i < sales.size(); i++) {
            CompoundTag one = sales.getCompound(i);
            int slot = one.getInt("slot");
            StallOffer offer = StallOffer.load(one);
            if (slot >= 0 && slot < MAX_OFFERS && offer != null && !MoneyStorageRules.isMoney(offer.template())
                    && StallOffer.nbtSize(offer.template()) <= StallOffer.MAX_TEMPLATE_BYTES) {
                offers[slot] = offer;
            }
        }
        ListTag buys = tag.getList(TAG_BUYBACKS, Tag.TAG_COMPOUND);
        for (int i = 0; i < buys.size(); i++) {
            CompoundTag one = buys.getCompound(i);
            int slot = one.getInt("slot");
            StallBuyback buyback = StallBuyback.load(one);
            if (slot >= 0 && slot < MAX_OFFERS && buyback != null && !MoneyStorageRules.isMoney(buyback.template())
                    && StallOffer.nbtSize(buyback.template()) <= StallOffer.MAX_TEMPLATE_BYTES) {
                buybacks[slot] = buyback;
            }
        }
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
        loadOffers(tag);
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

    /** Aux clients : le propriétaire, son nom, les offres et la version ; rien du stock, de la caisse ni du registre. */
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        if (owner != null) {
            tag.putUUID(TAG_OWNER, owner);
        }
        tag.putString(TAG_OWNER_NAME, ownerName);
        tag.putInt(TAG_VERSION, version);
        saveOffers(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
