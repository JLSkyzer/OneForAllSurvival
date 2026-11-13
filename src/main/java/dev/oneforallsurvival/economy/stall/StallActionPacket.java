package dev.oneforallsurvival.economy.stall;

import com.mojang.logging.LogUtils;
import dev.oneforallsurvival.bench.BenchNetwork;
import dev.oneforallsurvival.economy.EconomyRegistry;
import dev.oneforallsurvival.economy.MoneyStorageRules;
import java.util.Locale;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;

/**
 * Client vers serveur : un geste à l'étal en {@code pos}. Les clients achètent et revendent depuis la vitrine
 * ({@link #BUY}, {@link #SELL_BACK}) ; ceux qui gèrent l'étal écrivent les offres depuis la gestion
 * ({@link #SET_OFFER}, {@link #CLEAR_OFFER}, {@link #SET_BUYBACK}, {@link #CLEAR_BUYBACK}) et demandent une page du
 * registre ({@link #REQUEST_LEDGER}, {@code index} = la page).
 *
 * <p>Un client n'est jamais cru sur parole : joueur toujours connecté et vivant, action connue, chunk chargé, 8 blocs,
 * étal toujours là, écran voulu ouvert sur cet étal, droit de gérer pour les écritures. Le modèle d'une offre est la
 * pile du <b>curseur côté serveur</b>, jamais une pile envoyée par le client ; le serveur en garde une copie.
 *
 * <p>{@code price} et {@code lot} sont ceux que la vitrine affiche, {@code version} celle des offres qu'elle a vues :
 * si l'un d'eux n'est plus celui de l'étal, l'achat est refusé ({@link StallResult#CHANGED}). La version s'ajoute au
 * plan : sans elle, un modèle changé à prix et lot égaux aurait vendu un autre objet que celui affiché.
 */
public record StallActionPacket(BlockPos pos, int action, int index, long price, int lot, int lots, int version) {

    public static final int BUY = 0;
    public static final int SELL_BACK = 1;
    public static final int SET_OFFER = 2;
    public static final int CLEAR_OFFER = 3;
    public static final int SET_BUYBACK = 4;
    public static final int CLEAR_BUYBACK = 5;
    public static final int REQUEST_LEDGER = 6;

    private static final Logger LOGGER = LogUtils.getLogger();

    public static StallActionPacket buy(BlockPos pos, int index, long price, int lot, int version) {
        return new StallActionPacket(pos, BUY, index, price, lot, 0, version);
    }

    public static StallActionPacket sellBack(BlockPos pos, int index, long price, int lot, int version) {
        return new StallActionPacket(pos, SELL_BACK, index, price, lot, 0, version);
    }

    public static StallActionPacket setOffer(BlockPos pos, int index, int lot, long price) {
        return new StallActionPacket(pos, SET_OFFER, index, price, lot, 0, 0);
    }

    public static StallActionPacket clearOffer(BlockPos pos, int index) {
        return new StallActionPacket(pos, CLEAR_OFFER, index, 0L, 0, 0, 0);
    }

    public static StallActionPacket setBuyback(BlockPos pos, int index, int lot, long price, int lots) {
        return new StallActionPacket(pos, SET_BUYBACK, index, price, lot, lots, 0);
    }

    public static StallActionPacket clearBuyback(BlockPos pos, int index) {
        return new StallActionPacket(pos, CLEAR_BUYBACK, index, 0L, 0, 0, 0);
    }

    public static StallActionPacket requestLedger(BlockPos pos, int page) {
        return new StallActionPacket(pos, REQUEST_LEDGER, page, 0L, 0, 0, 0);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeVarInt(action);
        buf.writeVarInt(index);
        buf.writeVarLong(price);
        buf.writeVarInt(lot);
        buf.writeVarInt(lots);
        buf.writeVarInt(version);
    }

    public static StallActionPacket decode(FriendlyByteBuf buf) {
        return new StallActionPacket(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readVarLong(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt());
    }

    /**
     * La clé du message qui dit le résultat ; nulle s'il n'y a rien à dire (une page de registre envoyée).
     * {@code message.oneforallsurvival.stall.result.<résultat>}, et pour une réussite {@code bought}, {@code sold} ou
     * {@code saved}.
     */
    @Nullable
    public static String messageKey(int action, StallResult result) {
        String base = MarketStallBlock.MESSAGE + "result.";
        if (result != StallResult.DONE) {
            return base + result.name().toLowerCase(Locale.ROOT);
        }
        return switch (action) {
            case BUY -> base + "bought";
            case SELL_BACK -> base + "sold";
            case REQUEST_LEDGER -> null;
            default -> base + "saved";
        };
    }

    /** Sur le thread du serveur. */
    public static void handle(StallActionPacket packet, Supplier<NetworkEvent.Context> context) {
        ServerPlayer sender = context.get().getSender();
        if (sender == null) {
            return;
        }
        StallResult result = apply(sender, packet);
        String key = messageKey(packet.action, result);
        if (key != null && sender.connection != null) {
            sender.displayClientMessage(Component.translatable(key), true);
        }
        // L'écran et l'inventaire du client suivent tout de suite, sans attendre le tick.
        if (sender.containerMenu instanceof StallShopMenu || sender.containerMenu instanceof StallOwnerMenu) {
            sender.containerMenu.broadcastChanges();
        }
    }

    /** Le contrôle et le geste, sans le réseau. */
    public static StallResult apply(ServerPlayer player, StallActionPacket packet) {
        if (player.connection == null || !player.connection.isAcceptingMessages()
                || player.server.getPlayerList().getPlayer(player.getUUID()) != player || !player.isAlive()) {
            return StallResult.GONE;
        }
        if (packet.action < BUY || packet.action > REQUEST_LEDGER) {
            return StallResult.BAD_REQUEST;
        }
        ServerLevel level = player.serverLevel();
        BlockPos pos = packet.pos;
        if (!level.isLoaded(pos) || player.distanceToSqr(Vec3.atCenterOf(pos)) > MarketStallBlock.MAX_DISTANCE_SQR) {
            return StallResult.TOO_FAR;
        }
        if (!level.getBlockState(pos).is(EconomyRegistry.MARKET_STALL.get())
                || !(level.getBlockEntity(pos) instanceof MarketStallBlockEntity stall)) {
            return StallResult.GONE;
        }
        if (packet.action == BUY || packet.action == SELL_BACK) {
            if (!(player.containerMenu instanceof StallShopMenu menu) || !menu.pos().equals(pos) || menu.stall() != stall
                    || !menu.stillValid(player)) {
                return StallResult.NOT_OPEN;
            }
            if (packet.version != stall.version()) {
                return StallResult.CHANGED;
            }
            return packet.action == BUY
                    ? StallTrade.buy(player, stall, packet.index, packet.price, packet.lot)
                    : StallTrade.sellBack(player, stall, packet.index, packet.price, packet.lot);
        }
        if (!MarketStallBlock.mayManage(player, stall.owner())) {
            return StallResult.NOT_ALLOWED;
        }
        if (!(player.containerMenu instanceof StallOwnerMenu menu) || !menu.pos().equals(pos) || menu.stall() != stall
                || !menu.stillValid(player)) {
            return StallResult.NOT_OPEN;
        }
        return switch (packet.action) {
            case SET_OFFER -> writeOffer(player, stall, menu.getCarried(), packet, false);
            case SET_BUYBACK -> writeOffer(player, stall, menu.getCarried(), packet, true);
            case CLEAR_OFFER -> inSlots(packet.index) && stall.setOffer(packet.index, null) ? StallResult.DONE : StallResult.BAD_REQUEST;
            case CLEAR_BUYBACK -> inSlots(packet.index) && stall.setBuyback(packet.index, null) ? StallResult.DONE
                    : StallResult.BAD_REQUEST;
            default -> sendLedger(player, stall, packet.index);
        };
    }

    private static boolean inSlots(int index) {
        return index >= 0 && index < MarketStallBlockEntity.offerSlots();
    }

    /** Écrit une offre dont le modèle est la pile du curseur (côté serveur), copiée ; le curseur n'est pas touché. */
    private static StallResult writeOffer(ServerPlayer player, MarketStallBlockEntity stall, ItemStack carried,
            StallActionPacket packet, boolean buyback) {
        if (carried.isEmpty() || MoneyStorageRules.isMoney(carried)) {
            return StallResult.BAD_TEMPLATE;
        }
        if (!inSlots(packet.index)) {
            return StallResult.BAD_REQUEST;
        }
        // Un modèle trop lourd partirait à tous les clients qui voient l'étal : refusé avant tout.
        if (!stall.templateFits(buyback, packet.index, carried)) {
            return StallResult.BAD_TEMPLATE;
        }
        boolean written;
        if (buyback) {
            written = StallBuyback.valid(carried, packet.lot, packet.price, packet.lots)
                    && stall.setBuyback(packet.index, new StallBuyback(carried, packet.lot, packet.price, packet.lots));
        } else {
            written = StallOffer.valid(carried, packet.lot, packet.price)
                    && stall.setOffer(packet.index, new StallOffer(carried, packet.lot, packet.price));
        }
        if (!written) {
            return StallResult.BAD_REQUEST;
        }
        LOGGER.debug("Economie : {} ecrit l'offre {} {} de l'etal en {}", player.getGameProfile().getName(),
                buyback ? "de rachat" : "de vente", packet.index, stall.getBlockPos());
        return StallResult.DONE;
    }

    /** Envoie une page du registre à ce joueur seul (il gère l'étal et a la gestion ouverte : vérifié plus haut). */
    private static StallResult sendLedger(ServerPlayer player, MarketStallBlockEntity stall, int page) {
        int pages = stall.ledger().pages();
        int shown = Math.max(0, Math.min(pages - 1, page));
        BenchNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new StallLedgerPacket(stall.getBlockPos(), shown, pages, stall.ledger().page(shown)));
        return StallResult.DONE;
    }
}
