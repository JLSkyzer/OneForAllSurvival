package dev.oneforallsurvival.dev;

import com.mojang.logging.LogUtils;
import dev.oneforallsurvival.economy.CoinDropEntity;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Diagnostic des GameTests (lot T2, tâche 4, revue I2) : une pièce au sol qui disparaît par expiration (6 000 ticks,
 * {@code MoneySink.DESPAWN}) pendant une passe a été oubliée par un essai antérieur ; elle fausse les essais qui lisent
 * les destructions du registre selon l'ordre des lots. Sa position, son lanceur, son âge et le nombre de ticks qu'elle a
 * vraiment vécu sont écrits en WARN, pour désigner l'essai qui l'a laissée. Seulement sur un {@link GameTestServer} ;
 * aucun effet sur le jeu.
 */
@Mod.EventBusSubscriber(modid = "oneforallsurvival", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PiecesOubliees {

    private static final Logger LOGGER = LogUtils.getLogger();

    private PiecesOubliees() {}

    @SubscribeEvent
    public static void auRetrait(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof CoinDropEntity piece) || !(event.getLevel() instanceof ServerLevel level)
                || !(level.getServer() instanceof GameTestServer) || piece.getAge() < CoinDropEntity.LIFETIME) {
            return;
        }
        LOGGER.warn("[pieces-oubliees] piece expiree en ({}, {}, {}) : {} x {} ({} cuivres), lanceur {}, age {}, "
                        + "{} ticks vecus, retrait {}", String.format("%.1f", piece.getX()), String.format("%.1f", piece.getY()),
                String.format("%.1f", piece.getZ()), piece.count(), piece.isPurse() ? "bourse" : piece.coin(), piece.value(),
                piece.thrower(), piece.getAge(), piece.tickCount, piece.getRemovalReason());
    }
}
