package dev.oneforallsurvival.grid.supplier;

import dev.oneforallsurvival.economy.EconomyConfig;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Serveur vers client : l'état du panneau du poste en {@link #pos}, envoyé à un ayant droit seulement (au clic droit et
 * après chaque geste du panneau). Il ouvre le panneau, ou met à jour celui qui est ouvert sur ce poste.
 *
 * <p>Un poste neuf a un nom vide (le panneau montre alors le champ du nom) et rien d'autre. Sinon : tarifs du jour et du
 * lendemain (le lendemain vaut le jour s'il n'a pas changé), plafond et taxe du jour et du lendemain, sortie du poste et
 * somme des compteurs d'aujourd'hui et d'hier (l'écart se calcule chez le client), valeur de la caisse, et au plus
 * {@value #MAX_CLIENTS} clients (demandes à accepter d'abord), avec le nombre total.
 *
 * <p>Taille bornée : noms de {@value #MAX_TEXT} caractères au plus, {@value #MAX_CLIENTS} clients au plus.
 */
public record PowerPanelPacket(BlockPos pos, String name, long price, long subscription, long nextPrice,
        long nextSubscription, long cap, long nextCap, int tax, int nextTax, long unitFe, long maxSubscription,
        int warnPercent, long outToday, long metersToday, long outYesterday, long metersYesterday, long till,
        boolean mayTakeTill, int clientCount, List<Client> clients) {

    public static final int MAX_CLIENTS = 64;
    public static final int MAX_TEXT = 64;

    /**
     * Un client au panneau.
     *
     * @param unpaid la somme de ses factures d'électricité dues, en cuivres
     * @param breaker un disjoncteur au moins lui est jumelé (sans lui, pas de coupure à distance)
     */
    public record Client(UUID meter, String subscriber, String player, PowerClient.State state, long feToday,
            long feYesterday, long unpaid, boolean breaker, boolean cut) {}

    public PowerPanelPacket {
        clients = List.copyOf(clients);
    }

    /** Un poste neuf, sans fournisseur : le panneau ne montre que le champ du nom. */
    public static PowerPanelPacket unnamed(BlockPos pos) {
        return new PowerPanelPacket(pos, "", 0L, 0L, 0L, 0L, 0L, 0L, 0, 0, EconomyConfig.powerPriceUnitFe(),
                EconomyConfig.powerMaxSubscription(), EconomyConfig.powerGapWarnPercent(), 0L, 0L, 0L, 0L, 0L, false, 0,
                List.of());
    }

    /** L'état du fournisseur {@code supplier} vu par {@code viewer}, au poste en {@code pos}. */
    public static PowerPanelPacket of(ServerPlayer viewer, BlockPos pos, PowerSupplier supplier) {
        MinecraftServer server = viewer.server;
        PowerSuppliers registry = PowerSuppliers.get(server);
        List<PowerClient> all = new ArrayList<>(registry.clients(supplier.id()));
        all.sort(Comparator.comparingInt(PowerPanelPacket::rank)
                .thenComparing(c -> c.subscriber().toLowerCase(Locale.ROOT))
                .thenComparing(c -> c.meterSerial().toString()));
        long metersToday = 0L;
        List<Client> shown = new ArrayList<>();
        for (PowerClient c : all) {
            metersToday = saturatedAdd(metersToday, c.feToday());
            if (shown.size() < MAX_CLIENTS) {
                shown.add(new Client(c.meterSerial(), c.subscriber(), c.playerName(), c.state(), c.feToday(),
                        c.feYesterday(), PowerBilling.SERVER.unpaid(server, c.meterSerial()), !c.breakers().isEmpty(),
                        c.cut()));
            }
        }
        // Les compteurs détachés aujourd'hui ont mesuré ce que le poste a fait sortir : ils comptent dans l'écart.
        for (PowerClient d : registry.detached()) {
            if (d.supplierId().equals(supplier.id())) {
                metersToday = saturatedAdd(metersToday, d.feToday());
            }
        }
        long cap = registry.priceCap();
        int tax = registry.taxPercent();
        return new PowerPanelPacket(pos, supplier.name(), supplier.price(), supplier.subscription(),
                supplier.nextPrice() != null ? supplier.nextPrice() : supplier.price(),
                supplier.nextSubscription() != null ? supplier.nextSubscription() : supplier.subscription(), cap,
                registry.nextPriceCap().orElse(cap), tax, registry.nextTaxPercent().orElse(tax),
                EconomyConfig.powerPriceUnitFe(), EconomyConfig.powerMaxSubscription(),
                EconomyConfig.powerGapWarnPercent(), supplier.outToday(), metersToday, supplier.outYesterday(),
                supplier.metersYesterday(), supplier.tillValue(), PowerRules.mayTakeTill(viewer, supplier), all.size(),
                shown);
    }

    /** Les demandes à accepter d'abord, puis celles qui attendent le client, les coupés, les autres. */
    private static int rank(PowerClient c) {
        if (c.state() == PowerClient.State.AWAITING_ACCEPT) {
            return 0;
        }
        if (c.state() == PowerClient.State.AWAITING_CONSENT) {
            return 1;
        }
        return c.cut() ? 2 : 3;
    }

    private static long saturatedAdd(long a, long b) {
        long sum = a + b;
        return sum < a ? Long.MAX_VALUE : sum;
    }

    // ---------------------------------------------------------------------------------------------
    // Réseau
    // ---------------------------------------------------------------------------------------------

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeUtf(name, MAX_TEXT);
        buf.writeVarLong(price);
        buf.writeVarLong(subscription);
        buf.writeVarLong(nextPrice);
        buf.writeVarLong(nextSubscription);
        buf.writeVarLong(cap);
        buf.writeVarLong(nextCap);
        buf.writeVarInt(tax);
        buf.writeVarInt(nextTax);
        buf.writeVarLong(unitFe);
        buf.writeVarLong(maxSubscription);
        buf.writeVarInt(warnPercent);
        buf.writeVarLong(outToday);
        buf.writeVarLong(metersToday);
        buf.writeVarLong(outYesterday);
        buf.writeVarLong(metersYesterday);
        buf.writeVarLong(till);
        buf.writeBoolean(mayTakeTill);
        buf.writeVarInt(clientCount);
        int n = Math.min(MAX_CLIENTS, clients.size());
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            Client c = clients.get(i);
            buf.writeUUID(c.meter());
            buf.writeUtf(c.subscriber(), MAX_TEXT);
            buf.writeUtf(c.player(), MAX_TEXT);
            buf.writeByte(c.state().ordinal());
            buf.writeVarLong(c.feToday());
            buf.writeVarLong(c.feYesterday());
            buf.writeVarLong(c.unpaid());
            buf.writeBoolean(c.breaker());
            buf.writeBoolean(c.cut());
        }
    }

    public static PowerPanelPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String name = buf.readUtf(MAX_TEXT);
        long price = buf.readVarLong();
        long subscription = buf.readVarLong();
        long nextPrice = buf.readVarLong();
        long nextSubscription = buf.readVarLong();
        long cap = buf.readVarLong();
        long nextCap = buf.readVarLong();
        int tax = buf.readVarInt();
        int nextTax = buf.readVarInt();
        long unitFe = buf.readVarLong();
        long maxSubscription = buf.readVarLong();
        int warnPercent = buf.readVarInt();
        long outToday = buf.readVarLong();
        long metersToday = buf.readVarLong();
        long outYesterday = buf.readVarLong();
        long metersYesterday = buf.readVarLong();
        long till = buf.readVarLong();
        boolean mayTakeTill = buf.readBoolean();
        int clientCount = buf.readVarInt();
        int n = buf.readVarInt();
        if (n < 0 || n > MAX_CLIENTS) {
            throw new DecoderException("clients : " + n + " au lieu de " + MAX_CLIENTS + " au plus");
        }
        PowerClient.State[] states = PowerClient.State.values();
        List<Client> clients = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            UUID meter = buf.readUUID();
            String subscriber = buf.readUtf(MAX_TEXT);
            String player = buf.readUtf(MAX_TEXT);
            int state = buf.readByte();
            long feToday = buf.readVarLong();
            long feYesterday = buf.readVarLong();
            long unpaid = buf.readVarLong();
            boolean breaker = buf.readBoolean();
            boolean cut = buf.readBoolean();
            if (state < 0 || state >= states.length) {
                throw new DecoderException("client mal formé");
            }
            clients.add(new Client(meter, subscriber, player, states[state], feToday, feYesterday, unpaid, breaker,
                    cut));
        }
        return new PowerPanelPacket(pos, name, price, subscription, nextPrice, nextSubscription, cap, nextCap, tax,
                nextTax, unitFe, maxSubscription, warnPercent, outToday, metersToday, outYesterday, metersYesterday,
                till, mayTakeTill, clientCount, clients);
    }

    public static void handle(PowerPanelPacket packet, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> dev.oneforallsurvival.grid.supplier.client.PowerPanelScreen.accept(packet));
    }
}
