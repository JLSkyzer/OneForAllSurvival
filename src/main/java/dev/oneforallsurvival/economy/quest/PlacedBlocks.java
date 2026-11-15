package dev.oneforallsurvival.economy.quest;

import dev.oneforallsurvival.economy.EconomyConfig;
import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;

/**
 * La marque « posé » des blocs d'un chunk : une capacité de {@link LevelChunk}, écrite par Forge avec le chunk
 * ({@code ForgeCaps} de {@code ChunkSerializer}), donc dans le même fichier que le bloc. Un bloc marqué ne fait jamais
 * avancer une quête de casse : il a été posé (par un joueur, un faux joueur, un déployeur), formé par un fluide
 * (générateur de pierre) ou poussé par un piston.
 *
 * <p>Positions locales tassées dans un {@code int} ({@link #pack}), dans l'ordre d'arrivée ; au plus
 * {@link EconomyConfig#questPlacedMarksPerChunk()} par chunk, les plus anciennes oubliées. Une marque oubliée ou en
 * trop ne fait qu'accorder ou refuser une progression, jamais créer de l'argent.
 */
public final class PlacedBlocks implements ICapabilitySerializable<IntArrayTag> {

    public static final ResourceLocation KEY = new ResourceLocation("oneforallsurvival", "placed_blocks");
    public static final Capability<PlacedBlocks> CAPABILITY = CapabilityManager.get(new CapabilityToken<>() {});

    /** De la plus ancienne à la plus récente. */
    private final IntLinkedOpenHashSet marks = new IntLinkedOpenHashSet();
    private final LazyOptional<PlacedBlocks> self = LazyOptional.of(() -> this);

    public PlacedBlocks() {}

    // ---------------------------------------------------------------------------------------------
    // Règles sur un chunk (pures)
    // ---------------------------------------------------------------------------------------------

    /**
     * Marque cette position (une position déjà marquée devient la plus récente) ; au-delà de {@code max}, les plus
     * anciennes sont oubliées.
     */
    public void add(int packed, int max) {
        marks.addAndMoveToLast(packed);
        while (marks.size() > Math.max(1, max)) {
            marks.removeFirstInt();
        }
    }

    public boolean remove(int packed) {
        return marks.remove(packed);
    }

    public boolean contains(int packed) {
        return marks.contains(packed);
    }

    public int size() {
        return marks.size();
    }

    /** Position locale : 4 bits de x, 4 de z, le reste pour la hauteur au-dessus de {@code minBuildHeight}. */
    public static int pack(BlockPos pos, int minBuildHeight) {
        return ((pos.getY() - minBuildHeight) << 8) | ((pos.getX() & 15) << 4) | (pos.getZ() & 15);
    }

    public static BlockPos unpack(int packed, ChunkPos chunk, int minBuildHeight) {
        return new BlockPos(chunk.getMinBlockX() + ((packed >> 4) & 15), (packed >>> 8) + minBuildHeight,
                chunk.getMinBlockZ() + (packed & 15));
    }

    // ---------------------------------------------------------------------------------------------
    // Dans le monde
    // ---------------------------------------------------------------------------------------------

    /** Les marques du chunk de cette position, si c'est un chunk chargé d'un monde du serveur. */
    @Nullable
    private static PlacedBlocks of(LevelAccessor level, BlockPos pos) {
        if (!(level instanceof ServerLevel server) || !server.isLoaded(pos)) {
            return null;
        }
        return server.getChunkAt(pos).getCapability(CAPABILITY).resolve().orElse(null);
    }

    public static boolean isPlaced(LevelAccessor level, BlockPos pos) {
        PlacedBlocks marks = of(level, pos);
        return marks != null && marks.contains(pack(pos, level.getMinBuildHeight()));
    }

    /** Marque la position ; le chunk est à sauver. */
    public static void mark(LevelAccessor level, BlockPos pos) {
        PlacedBlocks marks = of(level, pos);
        if (marks != null) {
            marks.add(pack(pos, level.getMinBuildHeight()), EconomyConfig.questPlacedMarksPerChunk());
            ((ServerLevel) level).getChunkAt(pos).setUnsaved(true);
        }
    }

    /** Retire la marque de la position ; le chunk est à sauver s'il y en avait une. */
    public static void unmark(LevelAccessor level, BlockPos pos) {
        PlacedBlocks marks = of(level, pos);
        if (marks != null && marks.remove(pack(pos, level.getMinBuildHeight()))) {
            ((ServerLevel) level).getChunkAt(pos).setUnsaved(true);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Capacité
    // ---------------------------------------------------------------------------------------------

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        return CAPABILITY.orEmpty(cap, self);
    }

    @Override
    public IntArrayTag serializeNBT() {
        return new IntArrayTag(marks.toIntArray());
    }

    @Override
    public void deserializeNBT(IntArrayTag tag) {
        marks.clear();
        for (int packed : tag.getAsIntArray()) {
            marks.addAndMoveToLast(packed);
        }
    }

    /** Bus du mod. */
    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.register(PlacedBlocks.class);
    }

    /** Chaque chunk d'un monde du serveur reçoit ses marques (le client n'en a pas l'usage). */
    public static void onAttachChunk(AttachCapabilitiesEvent<LevelChunk> event) {
        if (!event.getObject().getLevel().isClientSide()) {
            event.addCapability(KEY, new PlacedBlocks());
        }
    }
}
