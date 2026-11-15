package dev.oneforallsurvival.economy.quest;

import com.mojang.logging.LogUtils;
import dev.oneforallsurvival.bench.BenchNetwork;
import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.EconomyCalendar;
import dev.oneforallsurvival.economy.EconomyClock;
import dev.oneforallsurvival.economy.EconomyConfig;
import dev.oneforallsurvival.economy.EconomyLedger;
import dev.oneforallsurvival.economy.InventoryFit;
import dev.oneforallsurvival.economy.PlayerCoins;
import dev.oneforallsurvival.economy.quest.QuestGenerator.Series;
import dev.oneforallsurvival.role.RoleSavedData;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;

/**
 * Les quêtes d'un joueur en jeu : renouvellement des séries, relance, réclamation. Tout passe par le carnet
 * ({@link QuestBook}) du joueur, relu et réécrit dans ses données persistantes à chaque geste ; l'heure est passée par
 * l'appelant ({@link EconomyClock#now()}), le registre aussi pour la réclamation.
 *
 * <p>Une réclamation est la seule porte par où ce lot crée de l'argent : les pièces entrent chez le joueur, la quête
 * est marquée réclamée dans le même fichier que son inventaire, et le registre compte la somme à la source
 * {@code QUEST}, dans le même appel. Tout entre ({@link InventoryFit#fits} avant de rien bouger) ou rien ne bouge.
 */
public final class QuestService {

    private static final Logger LOGGER = LogUtils.getLogger();
    /** Le tic de contrôle des séries des joueurs en ligne. */
    public static final int CHECK_TICKS = 20;
    /** L'envoi de l'état aux joueurs marqués sales : au plus une fois toutes les {@value} ticks. */
    public static final int SYNC_TICKS = 10;
    /** Les joueurs dont l'état des quêtes a changé depuis le dernier envoi. */
    private static final Set<UUID> DIRTY = ConcurrentHashMap.newKeySet();
    static final String MESSAGE = "message.oneforallsurvival.quest.";

    public enum ClaimResult { PAID, CAPPED_PARTIAL, CAP_REACHED, NO_ROOM, NOT_COMPLETE, ALREADY_CLAIMED, STALE, CREATIVE }

    private QuestService() {}

    // ---------------------------------------------------------------------------------------------
    // Périodes
    // ---------------------------------------------------------------------------------------------

    private static ZoneId zone() {
        return EconomyCalendar.configuredZone();
    }

    public static LocalDate today(long now) {
        return QuestPeriods.dayKey(now, zone(), EconomyConfig.questResetHour());
    }

    public static LocalDate week(long now) {
        return QuestPeriods.weekKey(now, zone(), EconomyConfig.questResetHour());
    }

    // ---------------------------------------------------------------------------------------------
    // Tirage
    // ---------------------------------------------------------------------------------------------

    /** Graine d'un tirage : joueur, série, clé de période et numéro de relance ; le même tirage redonne les mêmes quêtes. */
    static long seed(UUID player, Series series, LocalDate key, int reroll) {
        long h = player.getMostSignificantBits() * 31L + player.getLeastSignificantBits();
        h = h * 31L + series.ordinal();
        h = h * 31L + key.toEpochDay();
        h = h * 31L + reroll;
        // Mélange (SplitMix64) : deux graines voisines ne donnent pas deux suites voisines.
        h += 0x9E3779B97F4A7C15L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }

    /**
     * Tire une série ; une réserve vide ou trop petite donne une série plus courte, avec un avertissement, jamais une
     * exception.
     */
    static List<Quest> drawSeries(List<QuestTemplate> pool, String roleId, Series series, int count, int weeklyFactor,
            long seed) {
        if (count <= 0) {
            return List.of();
        }
        List<Quest> drawn = QuestGenerator.draw(pool, roleId, series, count, Set.of(), weeklyFactor,
                RandomSource.create(seed));
        if (drawn.size() < count) {
            LOGGER.warn("Quetes : serie {} de {} quetes au lieu de {} pour le role {} (reserve de {} modeles)",
                    series, drawn.size(), count, roleId, pool.size());
        }
        return drawn;
    }

    private static String roleOf(ServerPlayer player) {
        return RoleSavedData.get(player.server).book().roleOf(player.getUUID()).id();
    }

    private static Function<Series, List<Quest>> drawer(ServerPlayer player, LocalDate today, LocalDate week) {
        List<QuestTemplate> pool = QuestTemplates.current();
        String role = roleOf(player);
        int factor = EconomyConfig.questWeeklyFactor();
        return series -> drawSeries(pool, role, series,
                series == Series.DAILY ? EconomyConfig.questDailyCount() : EconomyConfig.questWeeklyCount(), factor,
                seed(player.getUUID(), series, series == Series.DAILY ? today : week, 0));
    }

    // ---------------------------------------------------------------------------------------------
    // Renouvellement
    // ---------------------------------------------------------------------------------------------

    /**
     * Renouvelle les séries du joueur dont la période est passée.
     *
     * @return vrai si quelque chose a changé
     */
    public static boolean refresh(ServerPlayer player, long now) {
        return refresh(player, now, activeEvents(player, now));
    }

    /** Comme {@link #refresh(ServerPlayer, long)}, avec les événements en cours donnés (essais, registre détaché). */
    public static boolean refresh(ServerPlayer player, long now, List<QuestEvent> events) {
        QuestBook book = QuestBook.of(player);
        if (!refresh(player, book, now, events)) {
            return false;
        }
        book.saveTo(player);
        markDirty(player);
        return true;
    }

    /** Les événements de l'équipe en cours à cet instant (le tableau de l'équipe du monde du joueur). */
    private static List<QuestEvent> activeEvents(ServerPlayer player, long now) {
        return QuestBoard.get(player.server).activeEvents(now);
    }

    private static boolean refresh(ServerPlayer player, QuestBook book, long now, List<QuestEvent> events) {
        LocalDate today = today(now);
        LocalDate week = week(now);
        boolean changed = book.refresh(today, week, drawer(player, today, week), EconomyConfig.questPendingMax(),
                events);
        if (book.forgotten() > 0) {
            player.displayClientMessage(Component.translatable(MESSAGE + "pending_forgotten", book.forgotten()), false);
        }
        return changed;
    }

    /** Au démarrage du monde : charge le tableau de l'équipe, qui met en service ses modèles avant tout tirage. */
    public static void onServerStarted(net.minecraftforge.event.server.ServerStartedEvent event) {
        QuestBoard.get(event.getServer());
    }

    /** À la connexion : séries renouvelées, puis l'état part tout de suite au client. */
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
            long now = EconomyClock.now();
            refresh(player, now);
            sendState(player, now);
        }
    }

    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        DIRTY.remove(event.getEntity().getUUID());
    }

    /**
     * Toutes les {@value #CHECK_TICKS} ticks, pour les joueurs en ligne : renouvellement. Toutes les
     * {@value #SYNC_TICKS} ticks : l'état part aux joueurs marqués sales (au plus une fois par période).
     */
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        int tick = event.getServer().getTickCount();
        long now = EconomyClock.now();
        if (tick % CHECK_TICKS == 0) {
            for (ServerPlayer player : List.copyOf(event.getServer().getPlayerList().getPlayers())) {
                if (!(player instanceof FakePlayer)) {
                    refresh(player, now);
                }
            }
        }
        if (tick % SYNC_TICKS == 0 && !DIRTY.isEmpty()) {
            for (UUID id : List.copyOf(DIRTY)) {
                DIRTY.remove(id);
                ServerPlayer player = event.getServer().getPlayerList().getPlayer(id);
                if (player != null) {
                    sendState(player, now);
                }
            }
        }
    }

    /** L'état des quêtes du joueur a changé : il partira au client au prochain envoi groupé. */
    public static void markDirty(ServerPlayer player) {
        if (!(player instanceof FakePlayer)) {
            DIRTY.add(player.getUUID());
        }
    }

    /** Vrai si l'état du joueur attend l'envoi groupé (essais). */
    public static boolean isDirty(ServerPlayer player) {
        return DIRTY.contains(player.getUUID());
    }

    /** Envoie l'état des quêtes du joueur tout de suite (demande, geste, connexion) ; il n'est plus marqué sale. */
    public static void sendState(ServerPlayer player, long now) {
        DIRTY.remove(player.getUUID());
        if (player instanceof FakePlayer || player.connection == null) {
            return;
        }
        BenchNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), QuestSyncPacket.of(player, now));
    }

    // ---------------------------------------------------------------------------------------------
    // Réclamation
    // ---------------------------------------------------------------------------------------------

    /**
     * Réclame la quête que désigne {@code ref}. La somme payée est {@code min(récompense, reste du plafond)} ;
     * plafond atteint ou pièces qui ne tiennent pas : rien ne bouge, la quête attend. Sinon, dans le même appel :
     * pièces chez le joueur (bourses puis inventaire), quête marquée, compteur du plafond, registre ({@code QUEST}),
     * carnet réécrit.
     */
    public static ClaimResult claim(ServerPlayer player, QuestRef ref, long now, EconomyLedger ledger) {
        return claim(player, ref, now, ledger, activeEvents(player, now));
    }

    /** Comme {@link #claim(ServerPlayer, QuestRef, long, EconomyLedger)}, avec les événements en cours donnés. */
    public static ClaimResult claim(ServerPlayer player, QuestRef ref, long now, EconomyLedger ledger,
            List<QuestEvent> events) {
        if (player.isCreative()) {
            return ClaimResult.CREATIVE;
        }
        QuestBook book = QuestBook.of(player);
        // Une série (ou un événement) renouvelée entre l'affichage et le clic : la référence ne désigne plus rien.
        if (refresh(player, book, now, events)) {
            book.saveTo(player);
            markDirty(player);
        }
        Optional<Quest> found = book.find(ref);
        if (found.isEmpty() || !player.isAlive()) {
            return ClaimResult.STALE;
        }
        Quest quest = found.get();
        if (quest.claimed()) {
            return ClaimResult.ALREADY_CLAIMED;
        }
        if (!quest.complete()) {
            return ClaimResult.NOT_COMPLETE;
        }
        LocalDate week = week(now);
        long amount = Math.min(quest.reward(), book.capRemaining(week, EconomyConfig.questWeeklyCap()));
        if (amount <= 0L) {
            return ClaimResult.CAP_REACHED;
        }
        EnumMap<Coin, Long> coins = CoinMath.decompose(amount);
        if (!InventoryFit.fits(player, coinStacks(coins))) {
            return ClaimResult.NO_ROOM;
        }

        // --- Engager : plus rien ne peut échouer. ---
        long entered = 0L;
        for (Map.Entry<Coin, Long> entry : coins.entrySet()) {
            entered += CoinMath.valueOf(entry.getKey(), PlayerCoins.receive(player, entry.getKey(), entry.getValue()));
        }
        if (entered != amount) {
            // InventoryFit a dit oui : n'arrive pas. On compte ce qui est vraiment entré, jamais plus.
            LOGGER.error("Quetes : {} cuivres sur {} entres chez {} pour {}", entered, amount,
                    player.getGameProfile().getName(), quest.templateId());
        }
        book.markClaimed(ref);
        book.recordPaid(week, entered);
        boolean fromEvent = ref.kind() == QuestRef.Kind.EVENT
                || ref.kind() == QuestRef.Kind.PENDING && ref.origin() == QuestRef.Kind.EVENT;
        ledger.questCreated(player.getUUID(), fromEvent ? "event:" + quest.templateId() : quest.templateId(), entered,
                entered >= EconomyConfig.questJournalThreshold());
        book.saveTo(player);
        markDirty(player);
        return amount < quest.reward() ? ClaimResult.CAPPED_PARTIAL : ClaimResult.PAID;
    }

    private static List<ItemStack> coinStacks(Map<Coin, Long> coins) {
        List<ItemStack> out = new ArrayList<>();
        for (Map.Entry<Coin, Long> entry : coins.entrySet()) {
            long left = entry.getValue();
            int max = new ItemStack(entry.getKey().item()).getMaxStackSize();
            while (left > 0L) {
                int size = (int) Math.min(left, max);
                out.add(new ItemStack(entry.getKey().item(), size));
                left -= size;
            }
        }
        return out;
    }

    // ---------------------------------------------------------------------------------------------
    // Relance
    // ---------------------------------------------------------------------------------------------

    /**
     * Relance la quête journalière ou hebdomadaire que désigne {@code ref} : une par jour ({@code questRerollsPerDay}),
     * jamais sur une quête finie ou réclamée, jamais vers un modèle déjà dans la série.
     *
     * @return vrai si la quête a été remplacée
     */
    public static boolean reroll(ServerPlayer player, QuestRef ref, long now) {
        if (ref.kind() != QuestRef.Kind.DAILY && ref.kind() != QuestRef.Kind.WEEKLY) {
            return false;
        }
        QuestBook book = QuestBook.of(player);
        boolean refreshed = refresh(player, book, now, activeEvents(player, now));
        LocalDate today = today(now);
        Optional<Quest> found = book.find(ref);
        if (found.isEmpty() || found.get().complete() || found.get().claimed()
                || !book.canReroll(today, EconomyConfig.questRerollsPerDay())) {
            if (refreshed) {
                book.saveTo(player);
                markDirty(player);
            }
            return false;
        }
        Series series = ref.kind() == QuestRef.Kind.DAILY ? Series.DAILY : Series.WEEKLY;
        LocalDate key = book.seriesKey(series);
        int number = book.rerollsUsedOn(today) + 1;
        List<QuestTemplate> pool = QuestTemplates.current();
        String role = roleOf(player);
        int factor = EconomyConfig.questWeeklyFactor();
        RandomSource random = RandomSource.create(seed(player.getUUID(), series, key, number) ^ today.toEpochDay());
        Optional<Quest> fresh = book.reroll(series, ref.index(), today, excluded -> QuestGenerator
                .draw(pool, role, series, 1, excluded, factor, random).stream().findFirst());
        if (fresh.isEmpty()) {
            LOGGER.warn("Quetes : relance sans modele disponible pour {} (reserve de {} modeles)",
                    player.getGameProfile().getName(), pool.size());
        }
        if (fresh.isPresent() || refreshed) {
            book.saveTo(player);
            markDirty(player);
        }
        return fresh.isPresent();
    }
}
