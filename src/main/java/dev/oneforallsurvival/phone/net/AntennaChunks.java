package dev.oneforallsurvival.phone.net;

import com.mojang.logging.LogUtils;
import dev.oneforallsurvival.OneForAllSurvival;
import dev.oneforallsurvival.phone.PhoneConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ForcedChunksSavedData;
import net.minecraftforge.common.world.ForgeChunkManager;
import org.slf4j.Logger;

/**
 * Le chunk d'une antenne reste chargé, et tique, même loin de tout joueur (décision de Killian, 2026-11-20) : un ticket
 * de Forge « qui tique » ({@link ForgeChunkManager#forceChunk(ServerLevel, String, BlockPos, int, int, boolean,
 * boolean)}), dont le propriétaire est la position de l'antenne, sur son seul chunk, tant que le réglage
 * {@code antennaKeepsChunkLoaded} est vrai.
 *
 * <p>Forge garde ces tickets dans les données {@code chunks} du monde (écrites par le jeu avec le reste) et les remet au
 * démarrage ; avant cela, {@link #validate(ServerLevel, ForgeChunkManager.TicketHelper)} (le rappel inscrit pour le
 * mod) rend ceux dont l'antenne n'existe plus au registre, dont l'opérateur a disparu, ou tous si le réglage est coupé.
 * En jeu, l'antenne rend le sien quand elle quitte le monde ({@link AntennaBlock}) et revoit le sien toutes les
 * {@value AntennaBlockEntity#SYNC_TICKS} tics ; {@link #validate(ServerLevel)} fait le même tri à la demande.
 */
public final class AntennaChunks {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Nom des données du monde où Forge et le jeu gardent les chunks forcés. */
    private static final String FORCED_DATA = "chunks";

    /** Un ticket du mod : son propriétaire (une position d'antenne), le chunk qu'il tient, et s'il le fait tiquer. */
    public record Ticket(BlockPos owner, long chunk, boolean ticking) {}

    private AntennaChunks() {}

    /**
     * Garde chargé (et tiquant) le chunk de l'antenne en {@code pos}, si le réglage le veut. Sans effet si le ticket y
     * est déjà.
     *
     * @return vrai si un ticket est (ou était déjà) posé
     */
    public static boolean hold(ServerLevel level, BlockPos pos) {
        if (!PhoneConfig.antennaKeepsChunkLoaded()) {
            return false;
        }
        ForgeChunkManager.forceChunk(level, OneForAllSurvival.MODID, pos.immutable(), pos.getX() >> 4,
                pos.getZ() >> 4, true, true);
        return true;
    }

    /** Rend le ticket de l'antenne en {@code pos} (sans effet s'il n'y en a pas). */
    public static void release(ServerLevel level, BlockPos pos) {
        ForgeChunkManager.forceChunk(level, OneForAllSurvival.MODID, pos.immutable(), pos.getX() >> 4,
                pos.getZ() >> 4, false, true);
    }

    /**
     * Le ticket d'une antenne en {@code pos} de ce monde a-t-il lieu d'être ? Seulement si le réglage le veut et qu'une
     * antenne inscrite au registre est à cette position, d'un opérateur qui existe.
     */
    public static boolean keeps(ServerLevel level, BlockPos pos) {
        if (!PhoneConfig.antennaKeepsChunkLoaded()) {
            return false;
        }
        PhoneOperators registry = PhoneOperators.get(level.getServer());
        for (AntennaRecord antenna : registry.antennas()) {
            if (antenna.dimension().equals(level.dimension()) && antenna.pos().equals(pos)) {
                return registry.byId(antenna.operatorId()).isPresent();
            }
        }
        return false;
    }

    /**
     * Rappel de validation de Forge ({@link ForgeChunkManager.LoadingValidationCallback}), appelé au chargement de
     * chaque monde, avant que les chunks forcés ne soient remis : rend chaque ticket du mod qui n'a plus lieu d'être
     * ({@link #keeps}).
     */
    public static void validate(ServerLevel level, ForgeChunkManager.TicketHelper helper) {
        for (BlockPos owner : List.copyOf(helper.getBlockTickets().keySet())) {
            if (!keeps(level, owner)) {
                helper.removeAllTickets(owner);
                LOGGER.info("Telephone : ticket de chunk de l'antenne disparue en {} ({}) rendu au demarrage",
                        owner.toShortString(), level.dimension().location());
            }
        }
    }

    /**
     * Le même tri, en jeu : rend chaque ticket du mod, dans ce monde, qui n'a plus lieu d'être ({@link #keeps}).
     *
     * @return le nombre de tickets rendus
     */
    public static int validate(ServerLevel level) {
        int released = 0;
        for (Ticket ticket : held(level)) {
            if (!keeps(level, ticket.owner())) {
                ChunkPos chunk = new ChunkPos(ticket.chunk());
                if (ForgeChunkManager.forceChunk(level, OneForAllSurvival.MODID, ticket.owner(), chunk.x, chunk.z, false,
                        ticket.ticking())) {
                    released++;
                }
            }
        }
        return released;
    }

    /**
     * Les tickets que le mod tient dans ce monde, lus dans les données des chunks forcés telles que Forge les écrit
     * ({@link ForgeChunkManager#writeForgeForcedChunks}) : le détail des propriétaires n'a pas d'autre accès public.
     * Rien n'est écrit sur le disque.
     */
    public static List<Ticket> held(ServerLevel level) {
        List<Ticket> out = new ArrayList<>();
        ForcedChunksSavedData data = level.getDataStorage().get(ForcedChunksSavedData::load, FORCED_DATA);
        if (data == null) {
            return out;
        }
        CompoundTag saved = data.save(new CompoundTag());
        for (Tag modTag : saved.getList("ForgeForced", Tag.TAG_COMPOUND)) {
            CompoundTag mod = (CompoundTag) modTag;
            if (!OneForAllSurvival.MODID.equals(mod.getString("Mod"))) {
                continue;
            }
            for (Tag entryTag : mod.getList("ModForced", Tag.TAG_COMPOUND)) {
                CompoundTag entry = (CompoundTag) entryTag;
                long chunk = entry.getLong("Chunk");
                for (Tag owner : entry.getList("Blocks", Tag.TAG_COMPOUND)) {
                    out.add(new Ticket(NbtUtils.readBlockPos((CompoundTag) owner), chunk, false));
                }
                for (Tag owner : entry.getList("TickingBlocks", Tag.TAG_COMPOUND)) {
                    out.add(new Ticket(NbtUtils.readBlockPos((CompoundTag) owner), chunk, true));
                }
            }
        }
        return out;
    }

    /** Le mod tient-il un ticket dont le propriétaire est {@code pos}, dans ce monde ? */
    public static boolean isHeld(ServerLevel level, BlockPos pos) {
        for (Ticket ticket : held(level)) {
            if (ticket.owner().equals(pos)) {
                return true;
            }
        }
        return false;
    }
}
