package dev.oneforallsurvival.grid;

import dev.oneforallsurvival.grid.supplier.PowerPairing;
import dev.oneforallsurvival.grid.supplier.PowerSuppliers;
import dev.oneforallsurvival.machine.InlineEnergyBlockEntity;
import dev.oneforallsurvival.role.RoleGate;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Le disjoncteur d'abonné : un bloc en ligne sans réserve ni usure, qui laisse passer le courant tant qu'il est
 * enclenché ({@link BreakerBlock#CLOSED}). Il porte le nom de l'abonné, qui suit l'objet quand on le démonte.
 *
 * <p>Jumelé à un client d'un fournisseur (lot E6), il porte un numéro et ne laisse rien passer tant que le client est
 * coupé à distance, même enclenché ; chargé alors qu'il est coupé, il s'ouvre (état du bloc) au tic suivant.
 */
public final class BreakerBlockEntity extends InlineEnergyBlockEntity implements SubscriberHolder {

    private static final String TAG_SUBSCRIBER = "subscriber";
    private static final String TAG_SERIAL = "serial";

    private String subscriber = "";
    /** Tiré au premier jumelage seulement : un disjoncteur jamais jumelé n'en a pas. */
    private UUID serial;

    public BreakerBlockEntity(BlockPos pos, BlockState state) {
        super(GridRegistry.BREAKER_BE.get(), pos, state, GridConfig.breakerMaxPerTick());
    }

    /**
     * Le courant passe quand le disjoncteur est enclenché (l'état vit dans le bloc, donc dans la sauvegarde du monde) et
     * que son client, s'il est jumelé, n'est pas coupé à distance.
     */
    @Override
    public boolean isOpen() {
        if (!getBlockState().getValue(BreakerBlock.CLOSED)) {
            return false;
        }
        return serial == null || !(level instanceof ServerLevel server) || !PowerPairing.isCut(server.getServer(), serial);
    }

    public boolean hasSerial() {
        return serial != null;
    }

    /** Le numéro, tiré à la première demande (le jumelage) puis gardé. */
    public UUID serial() {
        if (serial == null) {
            serial = UUID.randomUUID();
            setChanged();
        }
        return serial;
    }

    @Override
    public String subscriber() {
        return subscriber;
    }

    @Override
    public void setSubscriber(String name) {
        subscriber = name.trim();
        setChanged();
    }

    /** Jumelé : les ayants droit du fournisseur ; sinon l'ingénieur (lot 4). */
    @Override
    public boolean mayInscribe(ServerPlayer player) {
        return PowerPairing.ownerOf(player.serverLevel(), worldPosition).isPresent()
                ? PowerPairing.mayTouch(player, player.serverLevel(), worldPosition)
                : RoleGate.canDoEngineerWork(player);
    }

    /** Chargé : se fait connaître du registre ; coupé à distance mais enclenché, il s'ouvrira au tic suivant. */
    @Override
    public void onLoad() {
        super.onLoad();
        if (serial != null && level instanceof ServerLevel server) {
            PowerSuppliers.get(server.getServer()).noteBreakerLoaded(serial, GlobalPos.of(server.dimension(), worldPosition));
            if (getBlockState().getValue(BreakerBlock.CLOSED) && PowerPairing.isCut(server.getServer(), serial)) {
                server.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
            }
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        forgetLoaded();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        forgetLoaded();
    }

    private void forgetLoaded() {
        if (serial != null && level instanceof ServerLevel server) {
            PowerSuppliers.get(server.getServer()).forgetBreakerLoaded(serial,
                    GlobalPos.of(server.dimension(), worldPosition));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!subscriber.isEmpty()) {
            tag.putString(TAG_SUBSCRIBER, subscriber);
        }
        if (serial != null) {
            tag.putUUID(TAG_SERIAL, serial);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        subscriber = tag.getString(TAG_SUBSCRIBER);
        serial = tag.hasUUID(TAG_SERIAL) ? tag.getUUID(TAG_SERIAL) : null;
    }
}
