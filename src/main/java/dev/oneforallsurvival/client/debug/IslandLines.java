package dev.oneforallsurvival.client.debug;

import dev.oneforallsurvival.client.debug.DebugFormat.Labels;
import dev.oneforallsurvival.client.debug.DebugFormat.Row;
import dev.oneforallsurvival.client.debug.DebugFormat.Segment;
import dev.oneforallsurvival.client.debug.DebugFormat.Tone;
import dev.oneforallsurvival.debug.IslandDebugInfo;
import dev.oneforallsurvival.machine.MachineState;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * Lignes du panneau Lost Island. L'état d'une machine vient du bloc (tout le monde le voit) ; usure, panne et
 * index ne s'affichent que si le serveur les a envoyés. Logique pure.
 */
public final class IslandLines {

    /**
     * @param info            dernière réponse du serveur, {@code null} si rien de récent
     * @param responseMatches la réponse porte sur le bloc regardé (sinon elle date de la cible précédente)
     * @param lookedState     état de la machine regardée, lu sur le bloc ; {@code null} si ce n'est pas une machine
     * @param lookedName      nom du bloc regardé, pour la machine
     * @param fePerTick       débit lissé du compteur regardé
     */
    public record Input(@Nullable IslandDebugInfo info, boolean responseMatches, @Nullable MachineState lookedState,
            @Nullable String lookedName, double fePerTick) {}

    private IslandLines() {}

    /** Les lignes du panneau ; aucune en mode réduit, qui masque le panneau entier. */
    public static List<Row> rows(boolean reduced, Input in, Labels l) {
        if (reduced) {
            return List.of();
        }
        List<Row> out = new ArrayList<>(6);
        out.add(line("island.zone", l, "label.zone", zone(in.info(), l)));

        if (in.lookedState() != null) {
            String name = in.lookedName() == null ? "" : in.lookedName() + DebugFormat.SEPARATOR;
            String state = l.get(DebugFormat.key("state." + in.lookedState().name().toLowerCase(Locale.ROOT)));
            out.add(line("island.machine", l, "label.machine", name + state));
        }
        IslandDebugInfo target = in.responseMatches() ? in.info() : null;
        if (target == null) {
            return out;
        }
        if (target.machine() != null) {
            IslandDebugInfo.Machine machine = target.machine();
            if (machine.wearPercent() != null) {
                out.add(line("island.wear", l, "label.wear", Math.round(machine.wearPercent()) + " %"));
            }
            if (machine.failure() != null) {
                out.add(line("island.failure", l, "label.failure", l.get(machine.failure().translationKey())));
            }
        }
        if (target.meter() != null) {
            IslandDebugInfo.Meter meter = target.meter();
            String subscriber = meter.subscriber().isEmpty() ? l.get(DebugFormat.key("value.nobody")) : meter.subscriber();
            out.add(line("island.subscriber", l, "label.subscriber", subscriber));
            out.add(line("island.flow", l, "label.flow", Math.round(in.fePerTick()) + " FE/t"));
            if (meter.index() != null) {
                out.add(line("island.index", l, "label.index", meter.index() + " FE"));
            }
        }
        if (target.breaker() != null) {
            IslandDebugInfo.Breaker breaker = target.breaker();
            String state = l.get(DebugFormat.key(breaker.closed() ? "value.closed" : "value.open"));
            if (breaker.sealed()) {
                state += DebugFormat.SEPARATOR + l.get(DebugFormat.key("value.sealed"));
            }
            out.add(line("island.breaker", l, "label.breaker", state));
        }
        return out;
    }

    private static String zone(@Nullable IslandDebugInfo info, Labels l) {
        if (info == null) {
            return "…";
        }
        return l.get(DebugFormat.key(info.coastZone() ? "value.salt_water" : "value.no_salt_water"));
    }

    private static Row line(String key, Labels l, String label, String value) {
        return new Row(key, List.of(new Segment(l.get(DebugFormat.key(label)), Tone.LABEL), new Segment(" " + value, Tone.VALUE)));
    }
}
