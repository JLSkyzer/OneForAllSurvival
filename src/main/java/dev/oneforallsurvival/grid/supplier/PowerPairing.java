package dev.oneforallsurvival.grid.supplier;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import dev.oneforallsurvival.grid.BreakerBlock;
import dev.oneforallsurvival.grid.BreakerBlockEntity;
import dev.oneforallsurvival.grid.GridRegistry;
import dev.oneforallsurvival.grid.MeterBlockEntity;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.slf4j.Logger;

/**
 * Le raccordement d'un compteur (et de ses disjoncteurs) à un fournisseur d'électricité, et l'accord du client.
 *
 * <ol>
 *   <li><b>Jumelage</b> : un ayant droit ({@link PowerRules#mayManage}) touche le poste avec le testeur, qui retient le
 *       fournisseur {@value #HOLD_TICKS} ticks (dans l'objet), puis un compteur : {@link #pairMeter}. Le débiteur est le
 *       joueur dont le nom est inscrit sur le compteur ; il doit être connu du serveur. Le client est
 *       {@link PowerClient.State#AWAITING_CONSENT} : rien ne passe, personne n'est facturé.</li>
 *   <li><b>Accord</b> : le joueur reçoit un message cliquable portant un code à usage unique (à sa connexion s'il est
 *       absent) : {@code /power consent <code>} ({@link #consent}) ou {@code /power refuse <code>} ({@link #refuse}).</li>
 *   <li><b>Acceptation</b> par le fournisseur ({@link #accept}, le panneau du poste à la tâche 5) : le courant passe et
 *       la consommation se compte.</li>
 * </ol>
 *
 * <p>Un disjoncteur se jumelle par le même geste au client du même fournisseur qui porte le même nom d'abonné
 * ({@link #pairBreaker}) ; la coupure à distance ({@link #setCut}) l'ouvre. Un compteur ou un disjoncteur jumelé
 * appartient au fournisseur : seuls les ayants droit l'ouvrent, l'inscrivent, le basculent ou le cassent
 * ({@link #mayTouch}, {@link #onBreak}). Démonté, un compteur est détaché : reposé ailleurs, il ne facture personne.
 */
public final class PowerPairing {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final String MESSAGE = "message.oneforallsurvival.power.";

    /** Combien de temps le testeur retient le fournisseur : 60 s. */
    public static final int HOLD_TICKS = 1200;

    /** Clés de l'objet testeur : le fournisseur retenu et le tic (temps de jeu) où il est oublié. */
    public static final String TAG_HOLD_SUPPLIER = "oneforallsurvival_hold_supplier";
    public static final String TAG_HOLD_UNTIL = "oneforallsurvival_hold_until";

    /** Dureté avec laquelle un joueur casse un compteur ou un disjoncteur (le bloc lui-même est à -1). */
    public static final float PLAYER_HARDNESS = 3.0F;

    /** La réflexion sur le cache des profils a déjà échoué une fois (avertissement unique). */
    private static volatile boolean cacheWarned;

    /** Issue d'un jumelage. */
    public enum PairResult { PAIRED, NOT_ALLOWED, NO_SUPPLIER, NO_SUBSCRIBER, UNKNOWN_PLAYER, ALREADY_PAIRED, NO_CLIENT,
        AMBIGUOUS }

    /** Issue d'un geste sur un raccordement (accord, refus, acceptation, coupure). */
    public enum Outcome { DONE, UNKNOWN_CODE, NOT_YOURS, WRONG_STATE, NOT_ALLOWED, NO_BREAKER }

    private PowerPairing() {}

    // ---------------------------------------------------------------------------------------------
    // Lectures
    // ---------------------------------------------------------------------------------------------

    /** Le client de ce compteur et son fournisseur, s'il est jumelé (côté serveur). */
    public static Optional<PowerClient> clientOfMeter(MinecraftServer server, UUID meterSerial) {
        return PowerSuppliers.get(server).clientOf(meterSerial);
    }

    /**
     * Le client dont ce bloc (compteur ou disjoncteur) est jumelé, s'il l'est **à cet endroit** (une copie de ses données
     * posée ailleurs ne l'est pas). Côté serveur seulement.
     */
    public static Optional<PowerClient> clientAt(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) {
            return Optional.empty();
        }
        PowerSuppliers registry = PowerSuppliers.get(server.getServer());
        BlockEntity be = level.getBlockEntity(pos);
        GlobalPos here = GlobalPos.of(server.dimension(), pos);
        if (be instanceof MeterBlockEntity meter && meter.hasSerial() && registry.isPairedAt(meter.serial(), here)) {
            return registry.clientOf(meter.serial());
        }
        if (be instanceof BreakerBlockEntity breaker && breaker.hasSerial() && registry.isPairedAt(breaker.serial(), here)) {
            return registry.clientOfBreaker(breaker.serial());
        }
        return Optional.empty();
    }

    /** Le fournisseur à qui appartient ce bloc (compteur ou disjoncteur jumelé ici), ou vide (côté serveur seulement). */
    public static Optional<PowerSupplier> ownerOf(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) {
            return Optional.empty();
        }
        PowerSuppliers registry = PowerSuppliers.get(server.getServer());
        return clientAt(level, pos).flatMap(c -> registry.byId(c.supplierId()));
    }

    /**
     * {@code player} peut-il agir sur le raccordement de {@code client} (basculer, casser, couper, rétablir, accepter) ?
     * Le fondateur du fournisseur et l'équipe, toujours ; le client lui-même, jamais (même ingénieur ou maire) ; sinon
     * les ayants droit ({@link PowerRules#mayManage} : ingénieur, maire). Jamais un faux joueur.
     */
    public static boolean mayActOn(Player player, PowerSupplier supplier, PowerClient client) {
        if (player instanceof FakePlayer) {
            return false;
        }
        if (!(player instanceof ServerPlayer server)) {
            return true;
        }
        if (server.hasPermissions(2) || supplier.founder().equals(server.getUUID())) {
            return true;
        }
        if (client.player().equals(server.getUUID())) {
            return false;
        }
        return PowerRules.mayManage(player, supplier);
    }

    /**
     * {@code player} peut-il toucher à ce bloc (ouvrir, inscrire, basculer, casser) ? Non jumelé : oui (les règles du lot
     * 4 s'appliquent ensuite). Jumelé : {@link #mayActOn}. Côté client, oui : le serveur tranche.
     */
    public static boolean mayTouch(Player player, Level level, BlockPos pos) {
        Optional<PowerClient> client = clientAt(level, pos);
        if (client.isEmpty() || !(level instanceof ServerLevel server)) {
            return true;
        }
        Optional<PowerSupplier> supplier = PowerSuppliers.get(server.getServer()).byId(client.get().supplierId());
        return supplier.isEmpty() || mayActOn(player, supplier.get(), client.get());
    }

    /** La progression de casse d'un joueur, comme pour un bloc de dureté {@link #PLAYER_HARDNESS}. */
    public static float playerProgress(BlockState state, Player player, BlockPos pos) {
        int divisor = net.minecraftforge.common.ForgeHooks.isCorrectToolForDrops(state, player) ? 30 : 100;
        return player.getDigSpeed(state, pos) / PLAYER_HARDNESS / divisor;
    }

    /** Le message de refus à un joueur sans droit sur un bloc jumelé : au client, son raccordement ; aux autres, à qui. */
    public static Component refusal(Player player, Level level, BlockPos pos) {
        Optional<PowerSupplier> owner = ownerOf(level, pos);
        String name = owner.map(PowerSupplier::name).orElse("?");
        boolean client = clientAt(level, pos).map(c -> c.player().equals(player.getUUID())).orElse(false);
        return Component.translatable(MESSAGE + (client ? "touch_client" : "touch_denied"), name);
    }

    // ---------------------------------------------------------------------------------------------
    // Testeur
    // ---------------------------------------------------------------------------------------------

    /** Le fournisseur retenu par ce testeur, s'il l'est encore au tic {@code gameTime}. */
    public static Optional<String> heldSupplier(ItemStack tester, long gameTime) {
        CompoundTag tag = tester.getTag();
        if (tag == null || !tag.contains(TAG_HOLD_SUPPLIER) || tag.getLong(TAG_HOLD_UNTIL) < gameTime) {
            return Optional.empty();
        }
        String id = tag.getString(TAG_HOLD_SUPPLIER);
        return id.isEmpty() ? Optional.empty() : Optional.of(id);
    }

    private static void forgetHold(ItemStack tester) {
        CompoundTag tag = tester.getTag();
        if (tag != null) {
            tag.remove(TAG_HOLD_SUPPLIER);
            tag.remove(TAG_HOLD_UNTIL);
            if (tag.isEmpty()) {
                tester.setTag(null);
            }
        }
    }

    /**
     * Le testeur sur un poste, un compteur ou un disjoncteur : retenir le fournisseur, jumeler. Vide si le geste ne
     * concerne pas le raccordement (le testeur fait alors son office du lot 4 : relevé, rapport de machine).
     */
    public static Optional<InteractionResult> useTester(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockEntity be = level.getBlockEntity(pos);
        boolean station = be instanceof PowerStationBlockEntity;
        boolean held = heldSupplier(stack, level.getGameTime()).isPresent();
        if (!station && !(held && (be instanceof MeterBlockEntity || be instanceof BreakerBlockEntity))) {
            if (!held && stack.hasTag() && stack.getTag().contains(TAG_HOLD_SUPPLIER) && !level.isClientSide) {
                forgetHold(stack); // échu
            }
            return Optional.empty();
        }
        if (level.isClientSide) {
            return Optional.of(InteractionResult.SUCCESS);
        }
        if (!(context.getPlayer() instanceof ServerPlayer player) || player instanceof FakePlayer) {
            return Optional.of(InteractionResult.CONSUME);
        }
        if (be instanceof PowerStationBlockEntity post) {
            hold(player, stack, post);
        } else {
            PowerSuppliers registry = PowerSuppliers.get(player.server);
            Optional<PowerSupplier> supplier = heldSupplier(stack, level.getGameTime()).flatMap(registry::byId);
            if (supplier.isEmpty()) {
                forgetHold(stack);
                player.displayClientMessage(Component.translatable(MESSAGE + "hold_lost"), true);
            } else if (be instanceof MeterBlockEntity meter) {
                pairMeter(player, supplier.get(), meter);
            } else if (be instanceof BreakerBlockEntity breaker) {
                pairBreaker(player, supplier.get(), breaker);
            }
        }
        return Optional.of(InteractionResult.CONSUME);
    }

    /** Le testeur retient le fournisseur de ce poste, pour un ayant droit. */
    private static void hold(ServerPlayer player, ItemStack stack, PowerStationBlockEntity station) {
        Optional<PowerSupplier> supplier = station.supplier();
        if (supplier.isEmpty()) {
            player.displayClientMessage(Component.translatable(PowerStationBlock.MESSAGE + "no_supplier"), true);
            return;
        }
        if (!PowerRules.mayManage(player, supplier.get())) {
            player.displayClientMessage(Component.translatable(PowerStationBlock.MESSAGE + "of", supplier.get().name()),
                    true);
            return;
        }
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(TAG_HOLD_SUPPLIER, supplier.get().id());
        tag.putLong(TAG_HOLD_UNTIL, player.level().getGameTime() + HOLD_TICKS);
        player.sendSystemMessage(Component.translatable(MESSAGE + "hold", supplier.get().name(), HOLD_TICKS / 20));
    }

    // ---------------------------------------------------------------------------------------------
    // Jumelage
    // ---------------------------------------------------------------------------------------------

    /**
     * Jumelle ce compteur à ce fournisseur, par un ayant droit. Le compteur doit porter le nom d'un joueur connu du
     * serveur ; il n'est jumelé à personne d'autre. Le client attend l'accord du joueur, qui reçoit un message cliquable
     * (ou le recevra à sa connexion).
     */
    public static PairResult pairMeter(ServerPlayer actor, PowerSupplier supplier, MeterBlockEntity meter) {
        MinecraftServer server = actor.server;
        PowerSuppliers registry = PowerSuppliers.get(server);
        PairResult result;
        if (registry.byId(supplier.id()).isEmpty()) {
            result = PairResult.NO_SUPPLIER;
        } else if (!PowerRules.mayManage(actor, supplier)) {
            result = PairResult.NOT_ALLOWED;
        } else if (meter.subscriber().isEmpty()) {
            result = PairResult.NO_SUBSCRIBER;
        } else if (registry.clientOf(meter.serial()).isPresent()) {
            result = PairResult.ALREADY_PAIRED;
        } else {
            Optional<GameProfile> debtor = knownPlayer(server, meter.subscriber());
            if (debtor.isEmpty()) {
                result = PairResult.UNKNOWN_PLAYER;
            } else {
                PowerClient client = new PowerClient(meter.serial(), supplier.id(), debtor.get().getId(),
                        debtor.get().getName(), meter.subscriber(), PowerClient.State.AWAITING_CONSENT, 0L, 0L, Set.of(),
                        false);
                registry.putClient(client);
                if (meter.getLevel() instanceof ServerLevel level) {
                    GlobalPos here = GlobalPos.of(level.dimension(), meter.getBlockPos());
                    registry.setPairedAt(meter.serial(), here);
                    registry.noteMeterLoaded(meter.serial(), here);
                }
                String code = registry.newConsentCode(meter.serial(), actor.getRandom());
                ServerPlayer online = server.getPlayerList().getPlayer(client.player());
                if (online != null) {
                    sendRequest(online, supplier, client, code);
                }
                LOGGER.info("Electricite : {} jumelle le compteur {} au fournisseur {} pour {} (accord attendu)",
                        actor.getGameProfile().getName(), meter.serial(), supplier.name(), client.playerName());
                result = PairResult.PAIRED;
            }
        }
        String subscriber = meter.subscriber();
        actor.sendSystemMessage(switch (result) {
            case PAIRED -> Component.translatable(MESSAGE + "paired", supplier.name(), subscriber);
            case NOT_ALLOWED -> Component.translatable(PowerStationBlock.MESSAGE + "not_yours", supplier.name());
            case NO_SUPPLIER -> Component.translatable(MESSAGE + "hold_lost");
            case NO_SUBSCRIBER -> Component.translatable(MESSAGE + "no_subscriber");
            case UNKNOWN_PLAYER -> Component.translatable(MESSAGE + "unknown_player", subscriber);
            default -> Component.translatable(MESSAGE + "already_paired");
        });
        return result;
    }

    /**
     * Jumelle ce disjoncteur au client du même fournisseur dont le nom d'abonné est le sien (sans casse). Refusé s'il
     * n'y en a aucun ou plusieurs, ou si le disjoncteur sert déjà un client.
     */
    public static PairResult pairBreaker(ServerPlayer actor, PowerSupplier supplier, BreakerBlockEntity breaker) {
        PowerSuppliers registry = PowerSuppliers.get(actor.server);
        PairResult result;
        PowerClient target = null;
        if (registry.byId(supplier.id()).isEmpty()) {
            result = PairResult.NO_SUPPLIER;
        } else if (!PowerRules.mayManage(actor, supplier)) {
            result = PairResult.NOT_ALLOWED;
        } else if (breaker.subscriber().isEmpty()) {
            result = PairResult.NO_SUBSCRIBER;
        } else if (breaker.hasSerial() && registry.clientOfBreaker(breaker.serial()).isPresent()) {
            result = PairResult.ALREADY_PAIRED;
        } else {
            List<PowerClient> same = registry.clients(supplier.id()).stream()
                    .filter(c -> c.subscriber().equalsIgnoreCase(breaker.subscriber())).toList();
            if (same.isEmpty()) {
                result = PairResult.NO_CLIENT;
            } else if (same.size() > 1) {
                result = PairResult.AMBIGUOUS;
            } else {
                target = same.get(0);
                Set<UUID> breakers = new HashSet<>(target.breakers());
                breakers.add(breaker.serial());
                registry.putClient(target.withBreakers(breakers));
                if (breaker.getLevel() instanceof ServerLevel level) {
                    GlobalPos here = GlobalPos.of(level.dimension(), breaker.getBlockPos());
                    registry.setPairedAt(breaker.serial(), here);
                    registry.noteBreakerLoaded(breaker.serial(), here);
                    if (target.cut()) {
                        openBreaker(level, breaker.getBlockPos());
                    }
                }
                LOGGER.info("Electricite : {} jumelle un disjoncteur au client {} du fournisseur {}",
                        actor.getGameProfile().getName(), target.playerName(), supplier.name());
                result = PairResult.PAIRED;
            }
        }
        String subscriber = breaker.subscriber();
        actor.sendSystemMessage(switch (result) {
            case PAIRED -> Component.translatable(MESSAGE + "breaker_paired", supplier.name(), subscriber);
            case NOT_ALLOWED -> Component.translatable(PowerStationBlock.MESSAGE + "not_yours", supplier.name());
            case NO_SUPPLIER -> Component.translatable(MESSAGE + "hold_lost");
            case NO_SUBSCRIBER -> Component.translatable(MESSAGE + "no_subscriber");
            case NO_CLIENT -> Component.translatable(MESSAGE + "breaker_no_client", subscriber, supplier.name());
            case AMBIGUOUS -> Component.translatable(MESSAGE + "breaker_ambiguous", subscriber);
            default -> Component.translatable(MESSAGE + "already_paired");
        });
        return result;
    }

    /** La demande d'accord, avec ses deux boutons. */
    private static void sendRequest(ServerPlayer player, PowerSupplier supplier, PowerClient client, String code) {
        MutableComponent accept = Component.translatable(MESSAGE + "button_consent").withStyle(s -> s
                .withColor(ChatFormatting.GREEN)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/power consent " + code)));
        MutableComponent refuse = Component.translatable(MESSAGE + "button_refuse").withStyle(s -> s
                .withColor(ChatFormatting.RED)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/power refuse " + code)));
        player.sendSystemMessage(Component.translatable(MESSAGE + "request", supplier.name(), client.subscriber(),
                accept, refuse));
    }

    // ---------------------------------------------------------------------------------------------
    // Accord, refus, acceptation, résiliation
    // ---------------------------------------------------------------------------------------------

    /** Le joueur donne son accord : il demande le raccordement ; le fondateur est prévenu. Le code est consommé. */
    public static Outcome consent(ServerPlayer player, String code) {
        if (player instanceof FakePlayer) {
            return Outcome.NOT_YOURS; // un automate ne donne jamais d'accord, même au nom de son propriétaire
        }
        PowerSuppliers registry = PowerSuppliers.get(player.server);
        Optional<PowerClient> client = registry.meterOfCode(code).flatMap(registry::clientOf);
        if (client.isEmpty()) {
            return Outcome.UNKNOWN_CODE;
        }
        if (!client.get().player().equals(player.getUUID())) {
            return Outcome.NOT_YOURS;
        }
        if (client.get().state() != PowerClient.State.AWAITING_CONSENT) {
            return Outcome.WRONG_STATE;
        }
        registry.dropCode(code);
        registry.putClient(client.get().withState(PowerClient.State.AWAITING_ACCEPT));
        registry.byId(client.get().supplierId()).ifPresent(s -> tellFounder(player.server, s,
                Component.translatable(MESSAGE + "founder_consent", player.getGameProfile().getName(),
                        client.get().subscriber())));
        LOGGER.info("Electricite : {} demande le raccordement du compteur {}", player.getGameProfile().getName(),
                client.get().meterSerial());
        return Outcome.DONE;
    }

    /** Le joueur refuse : le compteur est détaché, personne n'est facturé. Le code est consommé. */
    public static Outcome refuse(ServerPlayer player, String code) {
        if (player instanceof FakePlayer) {
            return Outcome.NOT_YOURS;
        }
        PowerSuppliers registry = PowerSuppliers.get(player.server);
        Optional<PowerClient> client = registry.meterOfCode(code).flatMap(registry::clientOf);
        if (client.isEmpty()) {
            return Outcome.UNKNOWN_CODE;
        }
        if (!client.get().player().equals(player.getUUID())) {
            return Outcome.NOT_YOURS;
        }
        if (client.get().state() != PowerClient.State.AWAITING_CONSENT) {
            return Outcome.WRONG_STATE;
        }
        registry.removeClient(client.get().meterSerial());
        registry.byId(client.get().supplierId()).ifPresent(s -> tellFounder(player.server, s,
                Component.translatable(MESSAGE + "founder_refuse", player.getGameProfile().getName(),
                        client.get().subscriber())));
        LOGGER.info("Electricite : {} refuse le raccordement du compteur {}", player.getGameProfile().getName(),
                client.get().meterSerial());
        return Outcome.DONE;
    }

    /**
     * Le fournisseur (ou un ayant droit) accepte une demande de raccordement : le courant passe et la consommation se
     * compte dès maintenant. Le client est prévenu. (Appelé par le panneau du poste, {@link PowerActionPacket}.)
     */
    public static Outcome accept(ServerPlayer actor, UUID meterSerial) {
        PowerSuppliers registry = PowerSuppliers.get(actor.server);
        Optional<PowerClient> client = registry.clientOf(meterSerial);
        Optional<PowerSupplier> supplier = client.flatMap(c -> registry.byId(c.supplierId()));
        if (client.isEmpty() || supplier.isEmpty()) {
            return Outcome.UNKNOWN_CODE;
        }
        if (!mayActOn(actor, supplier.get(), client.get())) {
            return Outcome.NOT_ALLOWED;
        }
        if (client.get().state() != PowerClient.State.AWAITING_ACCEPT) {
            return Outcome.WRONG_STATE;
        }
        registry.putClient(client.get().withState(PowerClient.State.ACTIVE));
        refreshMeter(actor.server, meterSerial);
        ServerPlayer debtor = actor.server.getPlayerList().getPlayer(client.get().player());
        if (debtor != null) {
            debtor.sendSystemMessage(Component.translatable(MESSAGE + "accepted", supplier.get().name(),
                    client.get().subscriber()));
        }
        LOGGER.info("Electricite : {} accepte le raccordement du compteur {} ({})", actor.getGameProfile().getName(),
                meterSerial, client.get().playerName());
        return Outcome.DONE;
    }

    /**
     * Le fournisseur (ou un ayant droit) refuse au panneau une demande de raccordement pas encore acceptée (qu'elle
     * attende l'accord du joueur ou le fournisseur) : le compteur est retiré sans rien garder, personne n'est facturé.
     * Le joueur est prévenu. Un raccordement accepté ne se refuse pas : il se détache ({@link #detachFromPanel}).
     */
    public static Outcome refuseRequest(ServerPlayer actor, UUID meterSerial) {
        PowerSuppliers registry = PowerSuppliers.get(actor.server);
        Optional<PowerClient> client = registry.clientOf(meterSerial);
        Optional<PowerSupplier> supplier = client.flatMap(c -> registry.byId(c.supplierId()));
        if (client.isEmpty() || supplier.isEmpty()) {
            return Outcome.UNKNOWN_CODE;
        }
        if (!mayActOn(actor, supplier.get(), client.get())) {
            return Outcome.NOT_ALLOWED;
        }
        if (client.get().state().passes()) {
            return Outcome.WRONG_STATE;
        }
        registry.removeClient(meterSerial);
        refreshMeter(actor.server, meterSerial);
        ServerPlayer debtor = actor.server.getPlayerList().getPlayer(client.get().player());
        if (debtor != null) {
            debtor.sendSystemMessage(Component.translatable(MESSAGE + "request_refused", supplier.get().name(),
                    client.get().subscriber()));
        }
        LOGGER.info("Electricite : {} refuse la demande du compteur {} ({})", actor.getGameProfile().getName(),
                meterSerial, client.get().playerName());
        return Outcome.DONE;
    }

    /**
     * Le fournisseur (ou un ayant droit) détache au panneau un client accepté, sans démonter son compteur : le courant
     * ne passe plus, la consommation et l'abonnement du jour restent dus à la clôture ({@link PowerSuppliers#detach},
     * comme un compteur démonté). Le client est prévenu. Une demande pas encore acceptée se refuse
     * ({@link #refuseRequest}).
     */
    public static Outcome detachFromPanel(ServerPlayer actor, UUID meterSerial) {
        PowerSuppliers registry = PowerSuppliers.get(actor.server);
        Optional<PowerClient> client = registry.clientOf(meterSerial);
        Optional<PowerSupplier> supplier = client.flatMap(c -> registry.byId(c.supplierId()));
        if (client.isEmpty() || supplier.isEmpty()) {
            return Outcome.UNKNOWN_CODE;
        }
        if (!mayActOn(actor, supplier.get(), client.get())) {
            return Outcome.NOT_ALLOWED;
        }
        if (!client.get().state().passes()) {
            return Outcome.WRONG_STATE;
        }
        registry.detach(meterSerial);
        refreshMeter(actor.server, meterSerial);
        ServerPlayer debtor = actor.server.getPlayerList().getPlayer(client.get().player());
        if (debtor != null) {
            debtor.sendSystemMessage(Component.translatable(MESSAGE + "detached_by_supplier", supplier.get().name(),
                    client.get().subscriber()));
        }
        LOGGER.info("Electricite : {} detache le compteur {} ({}, {} FE du jour gardes)",
                actor.getGameProfile().getName(), meterSerial, client.get().playerName(), client.get().feToday());
        return Outcome.DONE;
    }

    /**
     * Le joueur résilie ses raccordements (ceux de {@code supplierName} seulement s'il est donné) : un raccordement
     * actif passe en résiliation (le courant passe encore jusqu'à la dernière facture, tâche 4) ; une demande pas
     * encore acceptée est simplement retirée.
     *
     * @return combien de raccordements ont changé
     */
    public static int resign(ServerPlayer player, @Nullable String supplierName) {
        PowerSuppliers registry = PowerSuppliers.get(player.server);
        int changed = 0;
        for (PowerSupplier supplier : registry.all()) {
            if (supplierName != null && !supplier.name().equalsIgnoreCase(supplierName.strip())) {
                continue;
            }
            for (PowerClient client : registry.clients(supplier.id())) {
                if (!client.player().equals(player.getUUID())) {
                    continue;
                }
                if (client.state() == PowerClient.State.ACTIVE) {
                    registry.putClient(client.withState(PowerClient.State.RESIGNING));
                } else if (client.state() != PowerClient.State.RESIGNING) {
                    registry.removeClient(client.meterSerial());
                    refreshMeter(player.server, client.meterSerial());
                } else {
                    continue;
                }
                changed++;
                tellFounder(player.server, supplier, Component.translatable(MESSAGE + "founder_resign",
                        player.getGameProfile().getName(), client.subscriber()));
                LOGGER.info("Electricite : {} resilie le compteur {} chez {}", player.getGameProfile().getName(),
                        client.meterSerial(), supplier.name());
            }
        }
        return changed;
    }

    // ---------------------------------------------------------------------------------------------
    // Coupure à distance
    // ---------------------------------------------------------------------------------------------

    /**
     * Coupe ({@code cut}) ou rétablit le client de ce compteur, par un ayant droit de son fournisseur. Il faut au moins un
     * disjoncteur jumelé : la coupure l'ouvre (tout de suite s'il est chargé, à son chargement sinon), le rétablissement
     * le referme s'il n'est pas scellé.
     */
    public static Outcome setCut(ServerPlayer actor, UUID meterSerial, boolean cut) {
        PowerSuppliers registry = PowerSuppliers.get(actor.server);
        Optional<PowerClient> client = registry.clientOf(meterSerial);
        Optional<PowerSupplier> supplier = client.flatMap(c -> registry.byId(c.supplierId()));
        if (supplier.isEmpty()) {
            return Outcome.UNKNOWN_CODE;
        }
        if (!mayActOn(actor, supplier.get(), client.get())) {
            return Outcome.NOT_ALLOWED;
        }
        return setCut(actor.server, meterSerial, cut);
    }

    /** Comme {@link #setCut(ServerPlayer, UUID, boolean)}, sans contrôle de droit (appelant déjà vérifié). */
    public static Outcome setCut(MinecraftServer server, UUID meterSerial, boolean cut) {
        PowerSuppliers registry = PowerSuppliers.get(server);
        Optional<PowerClient> client = registry.clientOf(meterSerial);
        if (client.isEmpty()) {
            return Outcome.UNKNOWN_CODE;
        }
        if (client.get().breakers().isEmpty()) {
            return Outcome.NO_BREAKER;
        }
        registry.putClient(client.get().withCut(cut));
        for (UUID breaker : client.get().breakers()) {
            registry.loadedBreaker(breaker).ifPresent(at -> {
                ServerLevel level = server.getLevel(at.dimension());
                if (level != null && level.isLoaded(at.pos())
                        && level.getBlockEntity(at.pos()) instanceof BreakerBlockEntity be && be.hasSerial()
                        && be.serial().equals(breaker)) {
                    if (cut) {
                        openBreaker(level, at.pos());
                    } else {
                        BlockState state = level.getBlockState(at.pos());
                        if (!state.getValue(BreakerBlock.SEALED) && !state.getValue(BreakerBlock.CLOSED)) {
                            level.setBlock(at.pos(), state.setValue(BreakerBlock.CLOSED, true), Block.UPDATE_ALL);
                        }
                    }
                }
            });
        }
        refreshMeter(server, meterSerial); // le compteur lui-même ne passe plus (ou de nouveau) : ses voisins le revoient
        ServerPlayer debtor = server.getPlayerList().getPlayer(client.get().player());
        if (debtor != null) {
            String name = registry.byId(client.get().supplierId()).map(PowerSupplier::name).orElse("?");
            debtor.sendSystemMessage(Component.translatable(MESSAGE + (cut ? "cut" : "restored"), name,
                    client.get().subscriber()));
        }
        LOGGER.info("Electricite : compteur {} {}", meterSerial, cut ? "coupe a distance" : "retabli");
        return Outcome.DONE;
    }

    /** Ouvre le disjoncteur en {@code pos} (état du bloc), s'il est fermé. */
    public static void openBreaker(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(GridRegistry.BREAKER.get()) && state.getValue(BreakerBlock.CLOSED)) {
            level.setBlock(pos, state.setValue(BreakerBlock.CLOSED, false), Block.UPDATE_ALL);
        }
    }

    /** Ce disjoncteur jumelé est-il coupé à distance (côté serveur) ? */
    public static boolean isCut(MinecraftServer server, UUID breakerSerial) {
        return PowerSuppliers.get(server).clientOfBreaker(breakerSerial).map(PowerClient::cut).orElse(false);
    }

    // ---------------------------------------------------------------------------------------------
    // Casse et détachement
    // ---------------------------------------------------------------------------------------------

    /**
     * Un compteur jumelé à cet endroit, démonté ou déplacé : détaché (sa journée reste due), client et fondateur prévenus.
     */
    public static void detachMeter(MinecraftServer server, UUID meterSerial, GlobalPos at) {
        PowerSuppliers registry = PowerSuppliers.get(server);
        if (!registry.isPairedAt(meterSerial, at)) {
            return; // une copie de ses données posée ailleurs : le vrai compteur reste jumelé
        }
        Optional<PowerClient> removed = registry.detach(meterSerial);
        if (removed.isEmpty()) {
            return;
        }
        PowerClient client = removed.get();
        String name = registry.byId(client.supplierId()).map(PowerSupplier::name).orElse("?");
        ServerPlayer debtor = server.getPlayerList().getPlayer(client.player());
        if (debtor != null) {
            debtor.sendSystemMessage(Component.translatable(MESSAGE + "detached", name, client.subscriber()));
        }
        registry.byId(client.supplierId()).ifPresent(s -> tellFounder(server, s,
                Component.translatable(MESSAGE + "founder_detached", client.playerName(), client.subscriber())));
        LOGGER.info("Electricite : compteur {} demonte, detache de {} ({} FE du jour gardes)", meterSerial, name,
                client.feToday());
    }

    /** Un disjoncteur jumelé démonté quitte son client. */
    public static void detachBreaker(MinecraftServer server, UUID breakerSerial, GlobalPos at) {
        PowerSuppliers registry = PowerSuppliers.get(server);
        if (!registry.isPairedAt(breakerSerial, at)) {
            return; // une copie : le vrai disjoncteur reste jumelé et tient la coupure
        }
        registry.clearPairedAt(breakerSerial);
        registry.clientOfBreaker(breakerSerial).ifPresent(client -> {
            Set<UUID> rest = new HashSet<>(client.breakers());
            rest.remove(breakerSerial);
            registry.putClient(client.withBreakers(rest));
        });
    }

    /**
     * Filet de sécurité de la casse : un compteur ou un disjoncteur jumelé ne se casse que par un ayant droit, quelle que
     * soit la manière (créatif, faux joueur d'un automate). Branché sur le bus de Forge.
     */
    public static void onBreak(BlockEvent.BreakEvent event) {
        BlockState state = event.getState();
        if (!(state.is(GridRegistry.METER.get()) || state.is(GridRegistry.BREAKER.get()))
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!mayTouch(event.getPlayer(), level, event.getPos())) {
            event.setCanceled(true);
            if (event.getPlayer() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
                player.displayClientMessage(refusal(player, level, event.getPos()), true);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Voisins et connexion
    // ---------------------------------------------------------------------------------------------

    /**
     * Le compteur, s'il est chargé, prévient ses voisins que le courant passe ou ne passe plus : les réseaux qui
     * recensent leurs consommateurs au changement de voisin (Create: New Age) le revoient.
     */
    public static void refreshMeter(MinecraftServer server, UUID meterSerial) {
        PowerSuppliers.get(server).loadedMeter(meterSerial).ifPresent(at -> {
            ServerLevel level = server.getLevel(at.dimension());
            if (level != null && level.isLoaded(at.pos()) && level.getBlockState(at.pos()).is(GridRegistry.METER.get())) {
                level.updateNeighborsAt(at.pos(), GridRegistry.METER.get());
            }
        });
    }

    /** À la connexion : les demandes d'accord qui attendent ce joueur, et les demandes à accepter de son fournisseur. */
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PowerSuppliers registry = PowerSuppliers.get(player.server);
        for (PowerSupplier supplier : registry.all()) {
            int waiting = 0;
            for (PowerClient client : registry.clients(supplier.id())) {
                if (client.player().equals(player.getUUID()) && client.state() == PowerClient.State.AWAITING_CONSENT) {
                    Optional<String> code = registry.codeOf(client.meterSerial());
                    if (code.isPresent()) {
                        sendRequest(player, supplier, client, code.get());
                    }
                }
                if (client.state() == PowerClient.State.AWAITING_ACCEPT) {
                    waiting++;
                }
            }
            if (waiting > 0 && supplier.founder().equals(player.getUUID())) {
                player.sendSystemMessage(Component.translatable(MESSAGE + "founder_waiting", supplier.name(), waiting));
            }
        }
    }

    private static void tellFounder(MinecraftServer server, PowerSupplier supplier, Component message) {
        ServerPlayer founder = server.getPlayerList().getPlayer(supplier.founder());
        if (founder != null) {
            founder.sendSystemMessage(message);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Joueur connu
    // ---------------------------------------------------------------------------------------------

    /**
     * Le joueur de ce nom, s'il est connu du serveur : connecté, ou dans le cache des profils (joueurs déjà venus). Jamais
     * de requête réseau : {@code GameProfileCache.get(String)} interrogerait les serveurs de Mojang pour un nom absent
     * (et, hors ligne, inventerait un profil pour n'importe quel nom) ; on lit donc la table du cache telle quelle.
     */
    public static Optional<GameProfile> knownPlayer(MinecraftServer server, String name) {
        String wanted = name.strip();
        ServerPlayer online = server.getPlayerList().getPlayerByName(wanted);
        if (online != null) {
            return Optional.of(online.getGameProfile());
        }
        GameProfileCache cache = server.getProfileCache();
        if (cache == null) {
            return Optional.empty();
        }
        try {
            Map<String, ?> byName = ObfuscationReflectionHelper.getPrivateValue(GameProfileCache.class, cache, "f_10966_");
            Object info = byName == null ? null : byName.get(wanted.toLowerCase(Locale.ROOT));
            if (info == null) {
                return Optional.empty();
            }
            Method profile = ObfuscationReflectionHelper.findMethod(info.getClass(), "m_11028_");
            return Optional.ofNullable((GameProfile) profile.invoke(info));
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (!cacheWarned) {
                cacheWarned = true;
                LOGGER.warn("Electricite : cache des profils illisible, seuls les joueurs connectes sont reconnus", e);
            }
            return Optional.empty();
        }
    }
}
