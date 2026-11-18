package dev.oneforallsurvival.grid.supplier;

import com.mojang.logging.LogUtils;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.EconomyConfig;
import dev.oneforallsurvival.grid.GridRegistry;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;

/**
 * Client vers serveur : un geste du panneau du poste en {@link #pos}. Un client n'est jamais cru sur parole : le serveur
 * revérifie le joueur (en ligne, pas un faux joueur), la distance, le bloc du poste et le droit
 * ({@link PowerRules#mayManage}), puis rejoue le geste par les règles déjà écrites : {@link PowerStationBlock#found}
 * (nom d'un poste neuf), {@link PowerSuppliers#setNextPrice} et {@link PowerSuppliers#setNextSubscription} (tarif du
 * lendemain, prix borné au plafond du lendemain), {@link PowerPairing#accept}, {@link PowerPairing#refuseRequest},
 * {@link PowerPairing#setCut} (contrôle {@link PowerPairing#mayActOn} : le client n'est jamais maître de sa coupure) et
 * {@link PowerPairing#detachFromPanel}.
 *
 * <p>Chaque geste d'un ayant droit répond par un message en barre d'action et par un nouvel état
 * ({@link PowerPanelPacket}). Un compteur inconnu, d'un autre fournisseur ou périmé, ou un geste inconnu, ne fait rien
 * d'autre que renvoyer l'état. Un joueur sans droit n'apprend que « Poste de … » et ne reçoit aucun état.
 *
 * @param meter le compteur visé ({@link #ACCEPT} à {@link #DETACH}), sinon nul
 * @param value le prix ou l'abonnement ({@link #SET_PRICE}, {@link #SET_SUBSCRIPTION}), en cuivres
 * @param text le nom ({@link #NAME}), sinon vide
 */
public record PowerActionPacket(int action, BlockPos pos, @Nullable UUID meter, long value, String text) {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final int NAME = 0;
    public static final int SET_PRICE = 1;
    public static final int SET_SUBSCRIPTION = 2;
    public static final int ACCEPT = 3;
    public static final int REFUSE = 4;
    public static final int CUT = 5;
    public static final int RESTORE = 6;
    public static final int DETACH = 7;
    public static final int OPEN_TILL = 8;

    public static final String MESSAGE = "message.oneforallsurvival.power_panel.";
    /** Borne du nom sur le réseau : au-delà, le paquet ne se lit pas (le registre, lui, plafonne à 24). */
    public static final int MAX_WIRE_TEXT = 64;
    /**
     * Frein : un même joueur ne lance pas plus d'un geste tous les {@value #MIN_TICKS_BETWEEN} tics ; au-delà, son geste
     * est ignoré et il ne reçoit que l'état (chaque état relit les impayés de tous les clients).
     */
    public static final int MIN_TICKS_BETWEEN = 5;
    /** Le tic du dernier geste accepté de chaque joueur (non sauvegardé ; oublié à la déconnexion). */
    private static final java.util.Map<UUID, Integer> LAST_GESTURE = new java.util.concurrent.ConcurrentHashMap<>();

    /** Comme la caisse : à 8 blocs au plus du centre du poste. */
    public static final double MAX_DISTANCE_SQR = PowerTillMenu.MAX_DISTANCE_SQR;

    /** L'issue d'un geste, pour les essais. */
    public enum Result {
        /** Le geste est fait. */
        DONE,
        /** Refusé par une règle (prix au-dessus du plafond, état qui ne s'y prête pas, nom pris…) : l'état est renvoyé. */
        REFUSED,
        /** Pas le droit : seul « Poste de … » est dit. */
        NOT_ALLOWED,
        /** Compteur inconnu, d'un autre fournisseur ou périmé, geste inconnu : seulement l'état. */
        STALE,
        /** Trop loin, chunk non chargé, poste disparu : rien. */
        TOO_FAR,
        /** Joueur parti ou faux joueur : rien. */
        GONE,
        /** Trop tôt après le geste précédent du même joueur (frein) : seulement l'état. */
        THROTTLED
    }

    public PowerActionPacket {
        text = text == null ? "" : text;
    }

    public static PowerActionPacket name(BlockPos pos, String name) {
        return new PowerActionPacket(NAME, pos, null, 0L, name);
    }

    public static PowerActionPacket price(BlockPos pos, long price) {
        return new PowerActionPacket(SET_PRICE, pos, null, price, "");
    }

    public static PowerActionPacket subscription(BlockPos pos, long subscription) {
        return new PowerActionPacket(SET_SUBSCRIPTION, pos, null, subscription, "");
    }

    /** Un geste sur un client : {@link #ACCEPT}, {@link #REFUSE}, {@link #CUT}, {@link #RESTORE}, {@link #DETACH}. */
    public static PowerActionPacket onMeter(int action, BlockPos pos, UUID meter) {
        return new PowerActionPacket(action, pos, meter, 0L, "");
    }

    public static PowerActionPacket openTill(BlockPos pos) {
        return new PowerActionPacket(OPEN_TILL, pos, null, 0L, "");
    }

    // ---------------------------------------------------------------------------------------------
    // Réseau
    // ---------------------------------------------------------------------------------------------

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(action);
        buf.writeBlockPos(pos);
        buf.writeBoolean(meter != null);
        if (meter != null) {
            buf.writeUUID(meter);
        }
        buf.writeLong(value);
        buf.writeUtf(text, MAX_WIRE_TEXT);
    }

    public static PowerActionPacket decode(FriendlyByteBuf buf) {
        int action = buf.readVarInt();
        BlockPos pos = buf.readBlockPos();
        UUID meter = buf.readBoolean() ? buf.readUUID() : null;
        long value = buf.readLong();
        String text = buf.readUtf(MAX_WIRE_TEXT);
        return new PowerActionPacket(action, pos, meter, value, text);
    }

    /** Sur le thread du serveur (consommateur du fil principal). */
    public static void handle(PowerActionPacket packet, Supplier<NetworkEvent.Context> context) {
        ServerPlayer sender = context.get().getSender();
        if (sender != null) {
            apply(sender, packet);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Le geste
    // ---------------------------------------------------------------------------------------------

    /** Le contrôle, le geste, le message et l'état renvoyé, sans le réseau. */
    public static Result apply(ServerPlayer player, PowerActionPacket packet) {
        // Un client parti entre l'envoi et le traitement, ou un automate : rien.
        if (player instanceof FakePlayer || player.connection == null || !player.connection.isAcceptingMessages()
                || player.server.getPlayerList().getPlayer(player.getUUID()) != player) {
            return Result.GONE;
        }
        ServerLevel level = player.serverLevel();
        BlockPos pos = packet.pos;
        if (pos == null || !level.isLoaded(pos) || player.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_DISTANCE_SQR
                || !level.getBlockState(pos).is(GridRegistry.POWER_STATION.get())
                || !(level.getBlockEntity(pos) instanceof PowerStationBlockEntity be)) {
            return Result.TOO_FAR;
        }
        Optional<PowerSupplier> supplier = be.supplier();
        if (supplier.isEmpty()) {
            return unnamed(player, pos, be, packet);
        }
        if (!PowerRules.mayManage(player, supplier.get())) {
            tell(player, Component.translatable(PowerStationBlock.MESSAGE + "of", supplier.get().name()));
            return Result.NOT_ALLOWED;
        }
        if (throttled(player)) {
            sendState(player, pos, false);
            return Result.THROTTLED;
        }
        if (packet.action == OPEN_TILL) {
            // L'écran de la caisse remplace le panneau : pas d'état à renvoyer.
            return PowerStationBlock.openTill(player, pos) ? Result.DONE : Result.STALE;
        }
        PowerSuppliers registry = PowerSuppliers.get(player.server);
        String id = supplier.get().id();
        Result result = switch (packet.action) {
            case NAME -> {
                tell(player, Component.translatable(MESSAGE + "already_named", supplier.get().name()));
                yield Result.REFUSED;
            }
            case SET_PRICE -> setPrice(player, registry, supplier.get(), packet.value);
            case SET_SUBSCRIPTION -> setSubscription(player, registry, supplier.get(), packet.value);
            case ACCEPT, REFUSE, CUT, RESTORE, DETACH -> onMeter(player, registry, id, packet);
            default -> Result.STALE;
        };
        sendState(player, pos, false);
        return result;
    }

    /** Un poste neuf : seul le nom, par un ayant droit du poseur. */
    private static Result unnamed(ServerPlayer player, BlockPos pos, PowerStationBlockEntity be, PowerActionPacket packet) {
        if (!PowerRules.mayManage(player, be.founder())) {
            tell(player, Component.translatable(PowerStationBlock.MESSAGE + "no_supplier"));
            return Result.NOT_ALLOWED;
        }
        if (packet.action != NAME) {
            sendState(player, pos, false);
            return Result.STALE;
        }
        if (throttled(player)) {
            sendState(player, pos, false);
            return Result.THROTTLED;
        }
        Optional<PowerSuppliers.Result> founded = PowerStationBlock.found(player, pos, packet.text);
        if (founded.isEmpty()) {
            sendState(player, pos, false);
            return Result.STALE;
        }
        PowerSuppliers.Result r = founded.get();
        switch (r.status()) {
            case FOUNDED -> tell(player, Component.translatable(MESSAGE + "founded", r.supplier().name()));
            case BAD_NAME -> tell(player, Component.translatable(MESSAGE + "bad_name", PowerSuppliers.MAX_NAME));
            case NAME_TAKEN -> tell(player, Component.translatable(MESSAGE + "name_taken", packet.text.strip()));
            case ALREADY_HAS_ONE -> tell(player, Component.translatable(MESSAGE + "already_has_one"));
        }
        sendState(player, pos, false);
        return r.founded() ? Result.DONE : Result.REFUSED;
    }

    /** Vrai si ce joueur a déjà fait un geste il y a moins de {@link #MIN_TICKS_BETWEEN} tics ; sinon note ce tic. */
    private static boolean throttled(ServerPlayer player) {
        int now = player.server.getTickCount();
        Integer last = LAST_GESTURE.get(player.getUUID());
        if (last != null && now - last >= 0 && now - last < MIN_TICKS_BETWEEN) {
            return true;
        }
        LAST_GESTURE.put(player.getUUID(), now);
        return false;
    }

    /** Oublie le frein de ce joueur (déconnexion ; essais qui enchaînent des gestes dans le même tic). */
    public static void clearThrottle(UUID player) {
        LAST_GESTURE.remove(player);
    }

    /** Branché sur le bus de Forge : le frein d'un joueur parti ne sert plus. */
    public static void onLoggedOut(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        clearThrottle(event.getEntity().getUUID());
    }

    /**
     * Qui règle le tarif : le fondateur et l'équipe toujours ; un client du fournisseur jamais (il fixerait son propre
     * prix, même ingénieur ou maire) ; sinon les ayants droit (ingénieur, maire).
     */
    public static boolean mayTariff(ServerPlayer player, PowerSupplier supplier, PowerSuppliers registry) {
        if (player.hasPermissions(2) || supplier.founder().equals(player.getUUID())) {
            return true;
        }
        return !PowerRules.isClientOf(player, supplier, registry) && PowerRules.mayManage(player, supplier);
    }

    /** Le plafond qui bornera le prix du lendemain : celui du lendemain s'il a changé aujourd'hui. */
    static long nextCap(PowerSuppliers registry) {
        return registry.nextPriceCap().orElse(registry.priceCap());
    }

    private static Result setPrice(ServerPlayer player, PowerSuppliers registry, PowerSupplier supplier, long price) {
        if (!mayTariff(player, supplier, registry)) {
            tell(player, Component.translatable(MESSAGE + "tariff_client"));
            return Result.REFUSED;
        }
        long cap = nextCap(registry);
        if (price < 0L || price > cap) {
            tell(player, Component.translatable(MESSAGE + "price_above_cap", CoinMath.format(cap),
                    EconomyConfig.powerPriceUnitFe()));
            return Result.REFUSED;
        }
        long before = supplier.nextPrice() != null ? supplier.nextPrice() : supplier.price();
        registry.setNextPrice(supplier.id(), price);
        tell(player, Component.translatable(MESSAGE + "price_set", CoinMath.format(price),
                EconomyConfig.powerPriceUnitFe()));
        LOGGER.info("Electricite : {} fixe le prix de {} a {} pour demain", player.getGameProfile().getName(),
                supplier.name(), price);
        if (price != before) {
            announceTariff(player.server, registry, supplier.id());
        }
        return Result.DONE;
    }

    private static Result setSubscription(ServerPlayer player, PowerSuppliers registry, PowerSupplier supplier,
            long subscription) {
        if (!mayTariff(player, supplier, registry)) {
            tell(player, Component.translatable(MESSAGE + "tariff_client"));
            return Result.REFUSED;
        }
        long max = EconomyConfig.powerMaxSubscription();
        if (subscription < 0L || subscription > max) {
            tell(player, Component.translatable(MESSAGE + "subscription_above_max", CoinMath.format(max)));
            return Result.REFUSED;
        }
        long before = supplier.nextSubscription() != null ? supplier.nextSubscription() : supplier.subscription();
        registry.setNextSubscription(supplier.id(), subscription);
        tell(player, Component.translatable(MESSAGE + "subscription_set", CoinMath.format(subscription)));
        LOGGER.info("Electricite : {} fixe l'abonnement de {} a {} pour demain", player.getGameProfile().getName(),
                supplier.name(), subscription);
        if (subscription != before) {
            announceTariff(player.server, registry, supplier.id());
        }
        return Result.DONE;
    }

    /**
     * Les clients connectés sont prévenus tout de suite du tarif du lendemain (une fois par joueur). Aussi pour un tarif
     * réglé par le maire ou l'équipe ({@link PowerCommands}).
     */
    static void announceTariff(net.minecraft.server.MinecraftServer server, PowerSuppliers registry, String supplierId) {
        Optional<PowerSupplier> supplier = registry.byId(supplierId);
        if (supplier.isEmpty()) {
            return;
        }
        PowerSupplier s = supplier.get();
        long price = s.nextPrice() != null ? s.nextPrice() : s.price();
        long subscription = s.nextSubscription() != null ? s.nextSubscription() : s.subscription();
        Set<UUID> told = new HashSet<>();
        for (PowerClient client : registry.clients(supplierId)) {
            if (!client.state().passes() || !told.add(client.player())) {
                continue;
            }
            ServerPlayer debtor = server.getPlayerList().getPlayer(client.player());
            if (debtor != null) {
                debtor.sendSystemMessage(Component.translatable(MESSAGE + "tariff_next", s.name(),
                        CoinMath.format(price), EconomyConfig.powerPriceUnitFe(), CoinMath.format(subscription)));
            }
        }
    }

    private static Result onMeter(ServerPlayer player, PowerSuppliers registry, String supplierId,
            PowerActionPacket packet) {
        if (packet.meter == null) {
            return Result.STALE;
        }
        Optional<PowerClient> client = registry.clientOf(packet.meter);
        if (client.isEmpty() || !client.get().supplierId().equals(supplierId)) {
            return Result.STALE; // inconnu, détaché entre-temps, ou chez un autre fournisseur
        }
        PowerPairing.Outcome outcome = switch (packet.action) {
            case ACCEPT -> PowerPairing.accept(player, packet.meter);
            case REFUSE -> PowerPairing.refuseRequest(player, packet.meter);
            case CUT -> PowerPairing.setCut(player, packet.meter, true);
            case RESTORE -> PowerPairing.setCut(player, packet.meter, false);
            default -> PowerPairing.detachFromPanel(player, packet.meter);
        };
        String subscriber = client.get().subscriber();
        return switch (outcome) {
            case DONE -> {
                String done = switch (packet.action) {
                    case ACCEPT -> "accepted";
                    case REFUSE -> "refused";
                    case CUT -> "cut";
                    case RESTORE -> "restored";
                    default -> "detached";
                };
                tell(player, Component.translatable(MESSAGE + done, subscriber));
                yield Result.DONE;
            }
            case NOT_ALLOWED -> {
                tell(player, Component.translatable(MESSAGE + "own_client"));
                yield Result.REFUSED;
            }
            case NO_BREAKER -> {
                tell(player, Component.translatable(MESSAGE + "no_breaker", subscriber));
                yield Result.REFUSED;
            }
            case WRONG_STATE -> {
                tell(player, Component.translatable(MESSAGE + "wrong_state", subscriber));
                yield Result.REFUSED;
            }
            default -> Result.STALE;
        };
    }

    /**
     * Renvoie l'état du panneau à un ayant droit (le droit est déjà vérifié par l'appelant) ; {@code open} : celui d'un
     * clic droit, qui ouvre le panneau (la réponse à un geste ne fait que mettre à jour un panneau ouvert).
     */
    public static void sendState(ServerPlayer player, BlockPos pos, boolean open) {
        if (!(player.serverLevel().getBlockEntity(pos) instanceof PowerStationBlockEntity be)) {
            return;
        }
        PowerPanelPacket state = be.supplier().map(s -> PowerPanelPacket.of(player, pos, open, s))
                .orElseGet(() -> PowerPanelPacket.unnamed(pos, open));
        dev.oneforallsurvival.bench.BenchNetwork.CHANNEL.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player), state);
    }

    private static void tell(ServerPlayer player, Component message) {
        player.displayClientMessage(message, true);
    }
}
