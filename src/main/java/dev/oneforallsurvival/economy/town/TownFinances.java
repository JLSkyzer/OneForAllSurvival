package dev.oneforallsurvival.economy.town;

import dev.oneforallsurvival.bench.BenchNetwork;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.TreasuryAccount;
import dev.oneforallsurvival.economy.payroll.Payroll;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.network.PacketDistributor;

/**
 * Les comptes publics de la ville tels que le tableau de la mairie les montre : le solde du trésor et les salaires
 * dus. Un instantané ({@link Snapshot}) est lu côté serveur, poussé à tous les clients à la connexion puis chaque fois
 * qu'il a changé (vérifié une fois par seconde), et décrit en texte pour le chat ({@link #describe}) ; le rendu du
 * tableau utilise les mêmes lignes. Rien ici n'est une donnée du monde : tout se relit du trésor et de la paie.
 */
public final class TownFinances {

    /** Préfixe des clés de langue du tableau. */
    public static final String KEY = "gui.oneforallsurvival.town_board.";

    /** Le tableau est mis à jour chaque seconde. */
    public static final int PERIOD_TICKS = 20;

    /** Le solde du trésor (cuivres), le total des salaires dus (cuivres) et leur nombre. */
    public record Snapshot(long balance, long owedTotal, int owedCount) {}

    /** Le dernier instantané envoyé à tous ; nul tant que rien n'est parti depuis le démarrage du serveur. */
    @Nullable
    private static volatile Snapshot lastSent;

    private TownFinances() {}

    /** Les comptes du serveur. */
    public static Snapshot of(MinecraftServer server) {
        return of(TreasuryAccount.get(server), Payroll.get(server));
    }

    /** Les comptes de ces instances (les essais passent des instances détachées). */
    public static Snapshot of(TreasuryAccount treasury, Payroll payroll) {
        return new Snapshot(treasury.total(), payroll.owedTotal(), payroll.debts().size());
    }

    /**
     * Envoie à tous les joueurs les comptes du serveur s'ils ont changé depuis le dernier envoi (ou si rien n'est parti
     * depuis le démarrage).
     *
     * @return vrai si un paquet est parti
     */
    public static boolean broadcastIfChanged(MinecraftServer server) {
        Snapshot now = of(server);
        if (now.equals(lastSent)) {
            return false;
        }
        lastSent = now;
        BenchNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), TownFinancesPacket.of(now));
        return true;
    }

    /** Envoie à ce joueur les comptes du serveur (à sa connexion). */
    public static void sendTo(ServerPlayer player) {
        BenchNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), TownFinancesPacket.of(of(player.server)));
    }

    // ---------------------------------------------------------------------------------------------
    // Le texte : le chat et le tableau partagent les mêmes lignes
    // ---------------------------------------------------------------------------------------------

    /** « Trésor de la ville ». */
    public static Component titleLine() {
        return Component.translatable(KEY + "title");
    }

    /** « Solde : <montant> » ; « … » tant que rien n'est reçu ({@code null}). */
    public static Component balanceLine(@Nullable Snapshot snapshot) {
        return Component.translatable(KEY + "balance",
                snapshot == null ? Component.literal("…") : CoinMath.format(snapshot.balance()));
    }

    /** « Salaires dus : <n> (<montant>) », ou « Aucun salaire dû » ; « … » tant que rien n'est reçu. */
    public static Component owedLine(@Nullable Snapshot snapshot) {
        if (snapshot == null) {
            return Component.translatable(KEY + "owed_unknown");
        }
        if (snapshot.owedCount() <= 0) {
            return Component.translatable(KEY + "none");
        }
        return Component.translatable(KEY + "owed", snapshot.owedCount(), CoinMath.format(snapshot.owedTotal()));
    }

    /**
     * Un compte du tableau en deux parties : de quoi l'on parle (« Solde : », « Salaires dus : 3 ») et les coupures du
     * montant, une par élément (vide quand il n'y a pas de montant à lire). Le tableau, trop étroit pour la phrase
     * entière, range les mots du début et les coupures sur autant de lignes qu'il lui en faut.
     */
    public record Parts(Component head, List<Component> amount) {}

    /** « Solde : » et les coupures du solde (« … » tant que rien n'est reçu). */
    public static Parts balanceParts(@Nullable Snapshot snapshot) {
        return new Parts(Component.translatable(KEY + "balance_label"),
                snapshot == null ? List.of(Component.literal("…")) : CoinMath.parts(snapshot.balance()));
    }

    /**
     * « Salaires dus : N » et les coupures du total dû ; « Aucun salaire dû » seul s'il n'y en a pas, « Salaires dus : …
     * » tant que rien n'est reçu.
     */
    public static Parts owedParts(@Nullable Snapshot snapshot) {
        if (snapshot == null) {
            return new Parts(Component.translatable(KEY + "owed_unknown"), List.of());
        }
        if (snapshot.owedCount() <= 0) {
            return new Parts(Component.translatable(KEY + "none"), List.of());
        }
        return new Parts(Component.translatable(KEY + "owed_head", snapshot.owedCount()),
                CoinMath.parts(snapshot.owedTotal()));
    }

    /** Le texte du chat : les trois lignes du tableau. */
    public static Component describe(Snapshot snapshot) {
        MutableComponent out = Component.translatable(KEY + "title");
        out.append("\n").append(balanceLine(snapshot));
        out.append("\n").append(owedLine(snapshot));
        return out;
    }

    // ---------------------------------------------------------------------------------------------
    // Événements
    // ---------------------------------------------------------------------------------------------

    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendTo(player);
        }
    }

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.getServer().getTickCount() % PERIOD_TICKS == 0) {
            broadcastIfChanged(event.getServer());
        }
    }

    /** Un autre serveur dans le même processus (monde solo rouvert) repart de rien. */
    public static void onServerStopped(ServerStoppedEvent event) {
        lastSent = null;
    }
}
