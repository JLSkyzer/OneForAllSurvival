package dev.oneforallsurvival.economy.quest;

import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.EconomyClock;
import dev.oneforallsurvival.economy.EconomyConfig;
import dev.oneforallsurvival.economy.EconomyLedger;
import dev.oneforallsurvival.economy.PlayerCoins;
import dev.oneforallsurvival.economy.quest.QuestService.ClaimResult;
import java.time.LocalDate;
import java.util.Locale;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client vers serveur : un geste de l'écran des quêtes. {@code OPEN} demande l'état ; {@code CLAIM} et {@code REROLL}
 * désignent une quête par sa {@link QuestRef} (liste, place, modèle, clé de série, origine, début d'événement) ;
 * {@code CLAIM_PENDING_ALL} réclame les récompenses en attente une à une, jusqu'au premier refus.
 *
 * <p>Un client n'est jamais cru sur parole : le serveur revérifie que le joueur est toujours en ligne, puis rejoue
 * {@link QuestService} avec {@link EconomyClock#now()}, qui relit le carnet et refuse une référence périmée. Une
 * référence inconnue ou périmée ne fait rien d'autre que renvoyer l'état. Chaque geste répond par un message en barre
 * d'action et par un nouvel état ({@link QuestSyncPacket}).
 */
public record QuestActionPacket(int action, @Nullable QuestRef ref) {

    public static final int OPEN = 0;
    public static final int CLAIM = 1;
    public static final int REROLL = 2;
    public static final int CLAIM_PENDING_ALL = 3;
    /** Longueur d'un identifiant de modèle ou d'événement lue sur le réseau (32 au plus dans le jeu). */
    public static final int MAX_ID = 64;
    static final String MESSAGE = QuestService.MESSAGE;

    public static QuestActionPacket open() {
        return new QuestActionPacket(OPEN, null);
    }

    public static QuestActionPacket claim(QuestRef ref) {
        return new QuestActionPacket(CLAIM, ref);
    }

    public static QuestActionPacket reroll(QuestRef ref) {
        return new QuestActionPacket(REROLL, ref);
    }

    public static QuestActionPacket claimPendingAll() {
        return new QuestActionPacket(CLAIM_PENDING_ALL, null);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(action);
        buf.writeBoolean(ref != null);
        if (ref != null) {
            writeRef(buf, ref);
        }
    }

    public static QuestActionPacket decode(FriendlyByteBuf buf) {
        int action = buf.readVarInt();
        QuestRef ref = buf.readBoolean() ? readRef(buf) : null;
        return new QuestActionPacket(action, ref);
    }

    /** Une référence sur le réseau ; {@link #readRef} la relit, bornée (identifiant de {@value #MAX_ID} caractères). */
    public static void writeRef(FriendlyByteBuf buf, QuestRef ref) {
        buf.writeByte(ref.kind().ordinal());
        buf.writeVarInt(ref.index());
        buf.writeUtf(ref.templateId(), MAX_ID);
        buf.writeLong(ref.seriesKey() == null ? Long.MIN_VALUE : ref.seriesKey().toEpochDay());
        buf.writeByte(ref.origin() == null ? -1 : ref.origin().ordinal());
        buf.writeLong(ref.eventKey());
    }

    /** Relit une référence ; un genre ou une origine hors des bornes donne une référence qui ne désigne rien. */
    @Nullable
    public static QuestRef readRef(FriendlyByteBuf buf) {
        int kind = buf.readByte();
        int index = buf.readVarInt();
        String templateId = buf.readUtf(MAX_ID);
        long day = buf.readLong();
        int origin = buf.readByte();
        long eventKey = buf.readLong();
        QuestRef.Kind[] kinds = QuestRef.Kind.values();
        if (kind < 0 || kind >= kinds.length || origin < -1 || origin >= kinds.length
                || day != Long.MIN_VALUE && (day < -365_000_000L || day > 365_000_000L)) {
            return null;
        }
        return new QuestRef(kinds[kind], index, templateId, day == Long.MIN_VALUE ? null : LocalDate.ofEpochDay(day),
                origin < 0 ? null : kinds[origin], eventKey);
    }

    /** Sur le thread du serveur (consommateur du fil principal). */
    public static void handle(QuestActionPacket packet, Supplier<NetworkEvent.Context> context) {
        ServerPlayer sender = context.get().getSender();
        if (sender != null) {
            apply(sender, packet);
        }
    }

    /** Le contrôle et le geste, sans le réseau ; l'état part ensuite au joueur. */
    public static void apply(ServerPlayer player, QuestActionPacket packet) {
        // Un client parti entre l'envoi et le traitement : rien (son inventaire ne serait plus sauvegardé).
        if (player instanceof FakePlayer || player.connection == null || !player.connection.isAcceptingMessages()
                || player.server.getPlayerList().getPlayer(player.getUUID()) != player) {
            return;
        }
        long now = EconomyClock.now();
        switch (packet.action) {
            case OPEN -> QuestService.refresh(player, now);
            case CLAIM -> {
                if (packet.ref != null) {
                    claim(player, packet.ref, now);
                }
            }
            case REROLL -> {
                if (packet.ref != null) {
                    reroll(player, packet.ref, now);
                }
            }
            case CLAIM_PENDING_ALL -> claimPendingAll(player, now);
            default -> {
                // Geste inconnu : seulement l'état.
            }
        }
        QuestService.sendState(player, now);
    }

    private static long held(ServerPlayer player) {
        return CoinMath.valueOf(PlayerCoins.held(player));
    }

    private static void claim(ServerPlayer player, QuestRef ref, long now) {
        long before = held(player);
        ClaimResult result = QuestService.claim(player, ref, now, EconomyLedger.get(player.server));
        tell(player, result, held(player) - before);
    }

    /** Le message d'une réclamation ; rien pour une référence périmée (l'état suffit). */
    static void tell(ServerPlayer player, ClaimResult result, long received) {
        if (result == ClaimResult.STALE) {
            return;
        }
        String key = MESSAGE + "claim." + result.name().toLowerCase(Locale.ROOT);
        player.displayClientMessage(result == ClaimResult.PAID || result == ClaimResult.CAPPED_PARTIAL
                ? Component.translatable(key, CoinMath.format(received)) : Component.translatable(key), true);
    }

    private static void reroll(ServerPlayer player, QuestRef ref, long now) {
        if (QuestService.reroll(player, ref, now)) {
            player.displayClientMessage(Component.translatable(MESSAGE + "reroll.done"), true);
            return;
        }
        QuestBook book = QuestBook.of(player);
        if (book.find(ref).isEmpty()) {
            return;
        }
        boolean left = book.canReroll(QuestService.today(now), EconomyConfig.questRerollsPerDay());
        player.displayClientMessage(Component.translatable(MESSAGE + (left ? "reroll.refused" : "reroll.none_left")),
                true);
    }

    /** Les récompenses en attente, la plus ancienne d'abord, jusqu'au premier refus (plafond, place). */
    private static void claimPendingAll(ServerPlayer player, long now) {
        EconomyLedger ledger = EconomyLedger.get(player.server);
        long before = held(player);
        int claimed = 0;
        ClaimResult refusal = null;
        int guard = Math.max(1, EconomyConfig.questPendingMax()) + 1;
        while (guard-- > 0) {
            QuestBook book = QuestBook.of(player);
            if (book.pending().isEmpty()) {
                break;
            }
            ClaimResult result = QuestService.claim(player, book.ref(QuestRef.Kind.PENDING, 0), now, ledger);
            if (result == ClaimResult.PAID || result == ClaimResult.CAPPED_PARTIAL) {
                claimed++;
                if (result == ClaimResult.CAPPED_PARTIAL) {
                    break;
                }
            } else {
                refusal = result;
                break;
            }
        }
        long received = held(player) - before;
        if (claimed > 0) {
            player.displayClientMessage(Component.translatable(MESSAGE + "pending.done", claimed,
                    CoinMath.format(received)), true);
        } else if (refusal != null) {
            tell(player, refusal, 0L);
        }
    }
}
