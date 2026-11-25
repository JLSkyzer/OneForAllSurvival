package dev.oneforallsurvival.compat.mixin.createaddition;

import dev.oneforallsurvival.phone.WallFeed;
import dev.oneforallsurvival.phone.WallSocketBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Un connecteur de Crafts &amp; Additions posé sur un mur qui porte une prise murale alimente cette prise
 * ({@link WallFeed}). Bytecode lu sur 1.20.1-1.3.3 : {@code updateExternalEnergyStorage} prend la réserve du seul bloc
 * contre lequel le connecteur est posé ({@code worldPosition.relative(FACING)}), ou rien sans bloc-entité.
 */
@Pseudo
@Mixin(targets = "com.mrh0.createaddition.blocks.connector.base.AbstractConnectorBlockEntity", remap = false)
public abstract class ConnectorWallSocketMixin {

    @Shadow(remap = false)
    protected LazyOptional<IEnergyStorage> external;

    @Shadow(remap = false)
    boolean externalStorageInvalid;

    @Inject(method = "updateExternalEnergyStorage", at = @At("HEAD"), cancellable = true, remap = false)
    private void oneforallsurvival$wallSocket(CallbackInfo callback) {
        BlockEntity self = (BlockEntity) (Object) this;
        Level level = self.getLevel();
        if (level == null || level.isClientSide || !level.isLoaded(self.getBlockPos())) {
            return;
        }
        Direction facing = oneforallsurvival$facing(self.getBlockState());
        if (facing == null) {
            return;
        }
        BlockPos wall = self.getBlockPos().relative(facing);
        WallSocketBlockEntity socket = WallFeed.socketOn(level, wall).orElse(null);
        if (socket == null) {
            // Pas de prise sur ce mur : le connecteur fait comme d'habitude (et oublie une prise partie).
            return;
        }
        externalStorageInvalid = false;
        LazyOptional<IEnergyStorage> energy = socket.getCapability(ForgeCapabilities.ENERGY, null);
        if (!energy.equals(external)) {
            external = energy;
            energy.addListener(gone -> externalStorageInvalid = true);
        }
        callback.cancel();
    }

    private static Direction oneforallsurvival$facing(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if (property instanceof DirectionProperty direction && "facing".equals(property.getName())) {
                return state.getValue(direction);
            }
        }
        return null;
    }
}
