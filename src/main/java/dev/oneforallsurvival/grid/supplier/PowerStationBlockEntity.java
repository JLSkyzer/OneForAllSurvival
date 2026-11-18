package dev.oneforallsurvival.grid.supplier;

import dev.oneforallsurvival.grid.GridConfig;
import dev.oneforallsurvival.grid.GridRegistry;
import dev.oneforallsurvival.machine.InlineEnergyBlockEntity;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Le poste de fournisseur : un bloc en ligne (entrée à l'arrière, sortie devant, règle du lot 4) en tête du réseau
 * d'un fournisseur. Il ne garde que l'identifiant de son fournisseur et, tant qu'il n'en a pas, celui qui l'a posé ;
 * la caisse, les clients et la sortie du jour vivent dans {@link PowerSuppliers}.
 *
 * <p>Le courant ne passe que si le fournisseur existe et que ce poste est bien son poste noté (une copie du bloc ailleurs
 * ne laisse rien passer). Chaque FE réellement accepté en sortie est ajouté à la sortie du jour du fournisseur.
 */
public final class PowerStationBlockEntity extends InlineEnergyBlockEntity {

    private static final String TAG_SUPPLIER = "supplier";
    private static final String TAG_FOUNDER = "founder";
    private static final String TAG_FOUNDER_NAME = "founderName";

    @Nullable
    private String supplierId;
    /** Qui a posé un poste encore sans fournisseur : lui seul (et les ayants droit) le fonde. */
    @Nullable
    private UUID founder;
    private String founderName = "";

    public PowerStationBlockEntity(BlockPos pos, BlockState state) {
        super(GridRegistry.POWER_STATION_BE.get(), pos, state, GridConfig.stationMaxPerTick());
    }

    @Nullable
    public String supplierId() {
        return supplierId;
    }

    void setSupplierId(@Nullable String id) {
        supplierId = id;
        setChanged();
    }

    @Nullable
    public UUID founder() {
        return founder;
    }

    public String founderName() {
        return founderName;
    }

    void setFounder(UUID uuid, String name) {
        founder = uuid;
        founderName = name;
        setChanged();
    }

    /**
     * À qui appartient ce poste tant qu'il n'a pas de fournisseur en service : le fondateur actuel de son fournisseur
     * s'il a été supprimé (même après un {@code /power owner}), sinon qui l'a posé. Côté serveur ; côté client, le poseur.
     */
    @Nullable
    public PowerSuppliers.FormerFounder idleOwner() {
        if (supplierId != null && level instanceof ServerLevel server) {
            Optional<PowerSuppliers.FormerFounder> former =
                    PowerSuppliers.get(server.getServer()).formerFounder(supplierId);
            if (former.isPresent()) {
                return former.get();
            }
        }
        return founder == null ? null : new PowerSuppliers.FormerFounder(founder, founderName);
    }

    /** Le fournisseur de ce poste, s'il existe et que ce poste est son poste noté (côté serveur seulement). */
    public Optional<PowerSupplier> supplier() {
        if (supplierId == null || !(level instanceof ServerLevel server)) {
            return Optional.empty();
        }
        GlobalPos here = GlobalPos.of(server.dimension(), worldPosition);
        return PowerSuppliers.get(server.getServer()).byId(supplierId).filter(s -> here.equals(s.station()));
    }

    /** Vrai seulement si son fournisseur existe (et que ce poste est le sien). */
    @Override
    public boolean isOpen() {
        return supplier().isPresent();
    }

    @Override
    protected void onPassed(int accepted) {
        if (supplierId != null && level instanceof ServerLevel server) {
            PowerSuppliers.get(server.getServer()).recordOut(supplierId, accepted);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (supplierId != null) {
            tag.putString(TAG_SUPPLIER, supplierId);
        }
        if (founder != null) {
            tag.putUUID(TAG_FOUNDER, founder);
            tag.putString(TAG_FOUNDER_NAME, founderName);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        String id = tag.getString(TAG_SUPPLIER);
        supplierId = id.isEmpty() ? null : id;
        founder = tag.hasUUID(TAG_FOUNDER) ? tag.getUUID(TAG_FOUNDER) : null;
        founderName = tag.getString(TAG_FOUNDER_NAME);
    }
}
