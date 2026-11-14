package dev.oneforallsurvival.economy.payroll;

import com.mojang.logging.LogUtils;
import dev.oneforallsurvival.economy.EconomyCalendar;
import dev.oneforallsurvival.economy.EconomyClock;
import dev.oneforallsurvival.economy.EconomyConfig;
import dev.oneforallsurvival.role.RoleBook;
import dev.oneforallsurvival.role.RoleSavedData;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;

/**
 * Compte le temps passé connecté dans un rôle payé. Le serveur échantillonne chaque seconde (20 ticks) les joueurs
 * connectés : l'écart depuis le dernier échantillon du joueur, plafonné à {@link #MAX_GAP_MILLIS} (un serveur figé ou
 * une machine en veille ne paient pas des heures), découpé à la frontière du jour de paie, est crédité à la clé de son
 * rôle si elle a un taux, et à {@link SalaryRates#MAYOR_KEY} s'il est maire et que ce taux est non nul. À la
 * connexion le compte part de zéro ; à la déconnexion il est crédité jusqu'à l'instant, puis oublié : rien ne compte
 * hors ligne.
 *
 * <p>Le dernier échantillon de chaque joueur est transitoire (mémoire) ; le temps compté, lui, vit dans {@link Payroll}.
 * Une instance par serveur ({@link #server()}) ; les essais créent la leur. Fil du serveur.
 */
public final class RoleTimeTracker {

    /** Écart compté au plus entre deux échantillons d'un joueur. */
    public static final long MAX_GAP_MILLIS = 60_000L;
    /** Un échantillon par seconde. */
    public static final int PERIOD_TICKS = 20;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final RoleTimeTracker SERVER = new RoleTimeTracker();

    /** Par joueur : l'instant jusqu'où son temps est compté (reste de moins d'une seconde reporté). */
    private final Map<UUID, Long> lastSample = new HashMap<>();

    public RoleTimeTracker() {}

    /** Le suivi du serveur, branché sur ses événements. */
    public static RoleTimeTracker server() {
        return SERVER;
    }

    /** Crédite chaque joueur connecté jusqu'à {@code now}. */
    public void sample(MinecraftServer server, Payroll payroll, long now) {
        RoleBook book = RoleSavedData.get(server).book();
        Settings settings = Settings.read();
        long behind = 0L;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            behind = Math.max(behind, advance(player, payroll, book, settings, now));
        }
        if (behind > 1_000L) {
            LOGGER.warn("Paie : l'horloge a recule de {} ms ; rien n'est compte pour cet ecart", behind);
        }
    }

    /** Le compte du joueur part de zéro à cet instant. */
    public void onLoggedIn(ServerPlayer player, long now) {
        lastSample.put(player.getUUID(), now);
    }

    /** Crédite le joueur jusqu'à l'instant, puis l'oublie. */
    public void onLoggedOut(ServerPlayer player, Payroll payroll, long now) {
        RoleBook book = RoleSavedData.get(player.server).book();
        advance(player, payroll, book, Settings.read(), now);
        lastSample.remove(player.getUUID());
    }

    /** Oublie tous les joueurs (arrêt du serveur). */
    public void clear() {
        lastSample.clear();
    }

    /** Les réglages lus une fois par échantillon. */
    private record Settings(ZoneId zone, int hour, long maxSeconds) {
        static Settings read() {
            return new Settings(EconomyCalendar.configuredZone(), EconomyConfig.payHour(),
                    EconomyConfig.salaryMaxHoursPerDay() * SalaryMath.SECONDS_PER_HOUR);
        }
    }

    /**
     * Crédite un joueur de son dernier échantillon jusqu'à {@code now}. @return de combien l'horloge a reculé pour lui
     * (0 sinon) ; un recul ne compte rien et repart de {@code now}
     */
    private long advance(ServerPlayer player, Payroll payroll, RoleBook book, Settings settings, long now) {
        UUID id = player.getUUID();
        Long last = lastSample.get(id);
        if (last == null) {
            lastSample.put(id, now);
            return 0L;
        }
        if (now < last) {
            lastSample.put(id, now);
            return last - now;
        }
        long from = Math.max(last, now - MAX_GAP_MILLIS);
        LocalDate day = EconomyCalendar.payDay(from, settings.zone(), settings.hour());
        long boundary = EconomyCalendar.boundaryAfter(day, settings.zone(), settings.hour());
        long[] parts = SalaryMath.split(from, now, boundary);
        lastSample.put(id, now - parts[2]);
        if (parts[0] > 0L) {
            creditKeys(player, payroll, book, settings, day, parts[0]);
        }
        if (parts[1] > 0L) {
            LocalDate next = EconomyCalendar.payDay(boundary, settings.zone(), settings.hour());
            creditKeys(player, payroll, book, settings, next, parts[1]);
        }
        return 0L;
    }

    private static void creditKeys(ServerPlayer player, Payroll payroll, RoleBook book, Settings settings,
            LocalDate day, long seconds) {
        UUID id = player.getUUID();
        String name = player.getGameProfile().getName();
        String role = book.roleOf(id).id();
        long roleRate = SalaryRates.rateFor(role);
        if (roleRate > 0L) {
            payroll.credit(id, name, day, role, seconds, roleRate, settings.maxSeconds());
        }
        if (book.isMayor(id)) {
            long mayorRate = SalaryRates.rateFor(SalaryRates.MAYOR_KEY);
            if (mayorRate > 0L) {
                payroll.credit(id, name, day, SalaryRates.MAYOR_KEY, seconds, mayorRate, settings.maxSeconds());
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Événements du serveur
    // ---------------------------------------------------------------------------------------------

    /** Branché sur le tick du serveur : un échantillon par seconde, en fin de tick, à {@link EconomyClock#now()}. */
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % PERIOD_TICKS != 0) {
            return;
        }
        MinecraftServer server = event.getServer();
        SERVER.sample(server, Payroll.get(server), EconomyClock.now());
    }

    public static void onLoggedInEvent(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SERVER.onLoggedIn(player, EconomyClock.now());
        }
    }

    public static void onLoggedOutEvent(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SERVER.onLoggedOut(player, Payroll.get(player.server), EconomyClock.now());
        }
    }

    /** La mémoire des échantillons ne survit pas au serveur (monde solo relancé dans le même jeu). */
    public static void onServerStopped(ServerStoppedEvent event) {
        SERVER.clear();
    }
}
