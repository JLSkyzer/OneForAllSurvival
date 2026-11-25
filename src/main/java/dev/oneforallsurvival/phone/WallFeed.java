package dev.oneforallsurvival.phone;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.fml.ModList;

/**
 * La prise murale prend le courant à travers son mur : un connecteur de Crafts &amp; Additions posé sur le bloc qui la
 * porte, quelle que soit la face, l'alimente comme s'il était posé sur elle.
 *
 * <p>Un connecteur donne son courant au seul bloc contre lequel il est posé. {@code ConnectorBlockEntityMixin} lui rend
 * la réserve de la prise quand ce bloc n'a pas de bloc-entité (un mur : une machine ou un coffre gardent leur
 * connecteur), et {@link #wake} passe en sortie un connecteur resté « aucun » parce qu'un mur n'a rien à recevoir.
 *
 * <p>Aucune classe de Crafts &amp; Additions n'est nommée ici : sans le mod, rien ne se passe.
 */
public final class WallFeed {

    /** Tics entre deux passages de la prise sur les connecteurs de son mur. */
    static final int PERIOD = 20;

    private static final String MOD = "createaddition";

    private WallFeed() {}

    /**
     * La prise accrochée à ce mur, s'il n'a pas de bloc-entité à lui. Sans effet si l'une des quatre cases autour du
     * mur n'est pas chargée (rien n'est chargé de force). Plusieurs prises sur le même bloc : la première dans l'ordre
     * des directions horizontales.
     */
    public static Optional<WallSocketBlockEntity> socketOn(Level level, BlockPos wall) {
        if (!level.isLoaded(wall) || level.getBlockEntity(wall) != null) {
            return Optional.empty();
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos pos = wall.relative(side);
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            // La prise regarde à l'opposé de son mur : accrochée à ce bloc, elle regarde du côté où elle se trouve.
            if (state.is(PhoneRegistry.WALL_SOCKET.get()) && state.getValue(WallSocketBlock.FACING) == side
                    && level.getBlockEntity(pos) instanceof WallSocketBlockEntity socket) {
                return Optional.of(socket);
            }
        }
        return Optional.empty();
    }

    /**
     * Les connecteurs posés sur le mur de cette prise relisent ce qu'ils alimentent ; un connecteur resté « aucun »
     * passe en sortie. Un mode réglé à la clé (entrée, passif) n'est pas touché.
     */
    static void wake(Level level, BlockPos socket, BlockState state) {
        if (level.isClientSide || !ModList.get().isLoaded(MOD)) {
            return;
        }
        BlockPos wall = socket.relative(state.getValue(WallSocketBlock.FACING).getOpposite());
        if (!level.isLoaded(wall) || level.getBlockEntity(wall) != null) {
            return;
        }
        for (Direction side : Direction.values()) {
            BlockPos pos = wall.relative(side);
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState connector = level.getBlockState(pos);
            // « facing » d'un connecteur : le bloc contre lequel il est posé.
            if (!isConnector(connector) || !side.getOpposite().getSerializedName().equals(value(connector, "facing"))) {
                continue;
            }
            if ("none".equals(value(connector, "mode"))) {
                level.setBlock(pos, with(connector, "mode", "push"), 3);
            }
            level.neighborChanged(pos, level.getBlockState(wall).getBlock(), wall);
        }
    }

    private static boolean isConnector(BlockState state) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return MOD.equals(id.getNamespace()) && id.getPath().endsWith("connector");
    }

    private static String value(BlockState state, String name) {
        for (Property<?> p : state.getProperties()) {
            if (p.getName().equals(name)) {
                return serialized(state, p);
            }
        }
        return "";
    }

    private static <T extends Comparable<T>> String serialized(BlockState state, Property<T> p) {
        return p.getName(state.getValue(p));
    }

    private static BlockState with(BlockState state, String name, String value) {
        for (Property<?> p : state.getProperties()) {
            if (p.getName().equals(name)) {
                return set(state, p, value);
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState set(BlockState state, Property<T> p, String value) {
        return p.getValue(value).map(v -> state.setValue(p, v)).orElse(state);
    }
}
