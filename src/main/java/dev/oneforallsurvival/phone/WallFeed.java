package dev.oneforallsurvival.phone;

import dev.oneforallsurvival.compat.OptionalModMixinPlugin;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
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
 * <p>Un connecteur donne son courant au seul bloc contre lequel il est posé. {@code ConnectorWallSocketMixin} lui rend
 * la réserve de la prise quand ce bloc n'a pas de bloc-entité (un mur ; une machine, un coffre ou un panneau gardent
 * leur connecteur), et {@link #wake} passe en sortie, une seule fois, un connecteur « aucun » qu'il découvre sur ce
 * mur. Un relais d'autrui resté « aucun » sur ce bloc en fait partie : c'est une affaire de RP (décision de Killian,
 * 2026-11-26).
 *
 * <p>Aucune classe de Crafts &amp; Additions n'est nommée ici : sans le mod, ou avec une version dont le bytecode n'a
 * pas été lu, rien ne se passe.
 */
public final class WallFeed {

    /** Tics entre deux passages de la prise sur les connecteurs de son mur. */
    static final int PERIOD = 20;

    private static final String MOD = "createaddition";

    /** La version chargée est-elle celle que le mixin accepte ? Lu au premier besoin. */
    private static Boolean supported;

    private WallFeed() {}

    private static boolean supported() {
        if (supported == null) {
            supported = ModList.get().getModContainerById(MOD)
                    .map(mod -> OptionalModMixinPlugin.inRange(mod.getModInfo().getVersion().toString(),
                            new OptionalModMixinPlugin.CreateAddition().verifiedRange()))
                    .orElse(false);
        }
        return supported;
    }

    /**
     * La prise accrochée à ce mur, s'il n'a pas de bloc-entité à lui. Une case non chargée autour du mur est ignorée
     * (rien n'est chargé de force). Plusieurs prises sur le même bloc : une seule est servie, la première qui peut
     * encore recevoir du courant (nord, est, sud, ouest), sinon la première.
     */
    public static Optional<WallSocketBlockEntity> socketOn(Level level, BlockPos wall) {
        if (!level.isLoaded(wall) || level.getBlockEntity(wall) != null) {
            return Optional.empty();
        }
        WallSocketBlockEntity first = null;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos pos = wall.relative(side);
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            // La prise regarde à l'opposé de son mur : accrochée à ce bloc, elle regarde du côté où elle se trouve.
            if (state.is(PhoneRegistry.WALL_SOCKET.get()) && state.getValue(WallSocketBlock.FACING) == side
                    && level.getBlockEntity(pos) instanceof WallSocketBlockEntity socket) {
                if (socket.energy().receiveEnergy(1, true) > 0) {
                    return Optional.of(socket);
                }
                first = first == null ? socket : first;
            }
        }
        return Optional.ofNullable(first);
    }

    /**
     * Les connecteurs posés sur le mur de cette prise relisent ce qu'ils alimentent. Un connecteur « aucun » que la
     * prise découvre passe en sortie, une seule fois : remis sur « aucun » à la clé, il le reste, et un mode réglé à la
     * clé n'est jamais changé.
     *
     * @param handled positions des connecteurs déjà vus par cette prise (sauvegardées avec elle) ; mis à jour ici
     * @return vrai si {@code handled} a changé
     */
    static boolean wake(Level level, BlockPos socket, BlockState state, Set<Long> handled) {
        if (level.isClientSide || !supported()) {
            return false;
        }
        BlockPos wall = socket.relative(state.getValue(WallSocketBlock.FACING).getOpposite());
        if (!level.isLoaded(wall)) {
            return false;
        }
        // Un mur qui porte un bloc-entité garde ses réglages ; ses connecteurs le relisent quand même.
        boolean plain = level.getBlockEntity(wall) == null;
        boolean added = false;
        Set<Long> gone = new HashSet<>();
        for (Direction side : Direction.values()) {
            BlockPos pos = wall.relative(side);
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState connector = level.getBlockState(pos);
            // « facing » d'un connecteur : le bloc contre lequel il est posé.
            if (!isConnector(connector) || !side.getOpposite().getSerializedName().equals(value(connector, "facing"))) {
                gone.add(pos.asLong());
                continue;
            }
            if (plain && handled.add(pos.asLong())) {
                added = true;
                if ("none".equals(value(connector, "mode"))) {
                    level.setBlock(pos, with(connector, "mode", "push"), 3);
                }
            }
            level.neighborChanged(pos, level.getBlockState(wall).getBlock(), wall);
        }
        // Un connecteur retiré est oublié au passage suivant : reposé après, il sera de nouveau mis en sortie.
        boolean removed = handled.removeAll(gone);
        return added || removed;
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
