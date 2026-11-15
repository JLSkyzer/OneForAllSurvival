package dev.oneforallsurvival.economy.quest.client;

import dev.oneforallsurvival.OneForAllSurvival;
import dev.oneforallsurvival.economy.quest.QuestRef;
import dev.oneforallsurvival.economy.quest.QuestSyncPacket;
import dev.oneforallsurvival.economy.quest.QuestSyncPacket.Entry;
import dev.oneforallsurvival.economy.quest.QuestSyncPacket.State;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Côté client : le dernier état des quêtes reçu du serveur, et l'instant de sa réception (les délais de renouvellement
 * en partent). Quand une quête passe de « en cours » à « finie » entre deux états successifs, un {@link QuestToast}
 * le dit ; jamais à la première réception (connexion), ni pour une quête qu'on n'avait pas encore vue.
 */
@Mod.EventBusSubscriber(modid = OneForAllSurvival.MODID, value = Dist.CLIENT)
public final class QuestClientCache {

    @Nullable
    private static QuestSyncPacket last;
    private static long receivedAt;
    /** Incrémenté à chaque état reçu : l'écran ouvert se remet en page. */
    private static int version;

    private QuestClientCache() {}

    /** Un état reçu du serveur, sur le fil du client. */
    public static void accept(QuestSyncPacket state) {
        QuestSyncPacket previous = last;
        last = state;
        receivedAt = Util.getMillis();
        version++;
        for (Entry e : newlyCompleted(previous, state)) {
            Minecraft.getInstance().getToasts().addToast(new QuestToast(e.title(), e.icon()));
        }
    }

    /**
     * Les quêtes qui viennent de finir : en cours dans l'état d'avant, finies dans le nouveau, sous la même référence.
     * Rien à la première réception ({@code previous} nul), ni pour une quête neuve (relance, renouvellement).
     */
    static List<Entry> newlyCompleted(@Nullable QuestSyncPacket previous, QuestSyncPacket state) {
        if (previous == null) {
            return List.of();
        }
        Map<QuestRef, State> before = new HashMap<>();
        for (Entry e : previous.quests()) {
            before.put(e.ref(), e.state());
        }
        List<Entry> out = new ArrayList<>();
        for (Entry e : state.quests()) {
            if (before.get(e.ref()) == State.IN_PROGRESS && e.state() == State.COMPLETE) {
                out.add(e);
            }
        }
        return out;
    }

    @Nullable
    public static QuestSyncPacket get() {
        return last;
    }

    /** Millisecondes écoulées depuis la réception du dernier état. */
    public static long ageMillis() {
        return Math.max(0L, Util.getMillis() - receivedAt);
    }

    public static int version() {
        return version;
    }

    public static void clear() {
        last = null;
        version++;
    }

    /** Les quêtes d'un serveur ne passent pas à un autre ; la reconnexion est une première réception. */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }
}
