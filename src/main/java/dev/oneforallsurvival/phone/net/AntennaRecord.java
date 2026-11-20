package dev.oneforallsurvival.phone.net;

import java.util.UUID;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Ce que le registre ({@link PhoneOperators}) sait d'une antenne : à quel opérateur elle est, où elle est, si elle
 * tourne, et ce qu'elle a consommé. Valeur figée : le registre la remplace à chaque changement.
 *
 * <p>La position est gardée en deux morceaux, dimension et bloc, comme {@link RelayPlanner.AntennaPoint} : un
 * {@link GlobalPos} ne s'obtient pas sous JUnit (son codec amorce les registres du jeu). {@link #globalPos()} le rend
 * pour qui en a besoin.
 *
 * @param id identifiant de l'antenne (celui de son bloc)
 * @param operatorId l'opérateur dont elle est
 * @param dimension la dimension où elle est posée
 * @param pos le bloc où elle est posée
 * @param placedAt instant de la pose ({@code EconomyClock.now()}), qui décide de l'antenne principale
 * @param running tourne-t-elle (alimentée, chargée) ?
 * @param seenGameTime le dernier temps de jeu où elle a donné signe de vie
 * @param feToday FE consommés aujourd'hui
 * @param feYesterday FE consommés hier
 * @param last le détail de la dernière consommation par tic
 */
public record AntennaRecord(UUID id, String operatorId, ResourceKey<Level> dimension, BlockPos pos, long placedAt,
        boolean running, long seenGameTime, long feToday, long feYesterday, AntennaLoad.Breakdown last) {

    public AntennaRecord {
        pos = pos.immutable();
        if (last == null) {
            last = new AntennaLoad.Breakdown(0, 0, 0);
        }
    }

    /** La position en un seul morceau (amorce le codec de {@link GlobalPos} : pas sous JUnit). */
    public GlobalPos globalPos() {
        return GlobalPos.of(dimension, pos);
    }

    /** Un tic de plus : en marche ou non, vu à ce temps de jeu, {@code newFeToday} consommés aujourd'hui, ce détail. */
    AntennaRecord withTick(boolean nowRunning, long gameTime, long newFeToday, AntennaLoad.Breakdown breakdown) {
        return new AntennaRecord(id, operatorId, dimension, pos, placedAt, nowRunning, gameTime, newFeToday,
                feYesterday, breakdown);
    }

    /** Le jour clos : la consommation du jour passe à « hier » et repart de zéro. */
    AntennaRecord withDayClosed() {
        return new AntennaRecord(id, operatorId, dimension, pos, placedAt, running, seenGameTime, 0L, feToday, last);
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("operator", operatorId);
        tag.putString("dimension", dimension.location().toString());
        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());
        tag.putLong("placedAt", placedAt);
        tag.putBoolean("running", running);
        tag.putLong("seen", seenGameTime);
        tag.putLong("feToday", feToday);
        tag.putLong("feYesterday", feYesterday);
        tag.putInt("standby", last.standby());
        tag.putInt("clients", last.clients());
        tag.putInt("traffic", last.traffic());
        return tag;
    }

    /**
     * Relit une antenne ; nulle si la ligne est inutilisable : sans identifiant, sans opérateur, sans position ou avec
     * une dimension absente, vide ou illisible.
     *
     * @param dimensions fabrique des clés de dimension (celle du jeu : {@code ResourceKey.create(Registries.DIMENSION, ...)})
     */
    @Nullable
    static AntennaRecord load(CompoundTag tag, Function<ResourceLocation, ResourceKey<Level>> dimensions) {
        String operator = tag.getString("operator");
        String dimensionText = tag.getString("dimension");
        ResourceLocation dimension = dimensionText.isEmpty() ? null : ResourceLocation.tryParse(dimensionText);
        if (!tag.hasUUID("id") || operator.isEmpty() || dimension == null || !tag.contains("x", 99)
                || !tag.contains("y", 99) || !tag.contains("z", 99)) {
            return null;
        }
        return new AntennaRecord(tag.getUUID("id"), operator, dimensions.apply(dimension),
                new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")), Math.max(0L, tag.getLong("placedAt")),
                tag.getBoolean("running"), Math.max(0L, tag.getLong("seen")), Math.max(0L, tag.getLong("feToday")),
                Math.max(0L, tag.getLong("feYesterday")),
                new AntennaLoad.Breakdown(Math.max(0, tag.getInt("standby")), Math.max(0, tag.getInt("clients")),
                        Math.max(0, tag.getInt("traffic"))));
    }
}
