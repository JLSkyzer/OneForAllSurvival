package dev.oneforallsurvival.grid;

import dev.oneforallsurvival.grid.MeterLedger.Reading;
import dev.oneforallsurvival.machine.InlineEnergyBlockEntity;
import dev.oneforallsurvival.role.RoleGate;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Le compteur : un bloc en ligne, sans réserve ni usure, dont l'index est le total des FE passés. Il porte un numéro
 * tiré à la première pose et le nom de l'abonné ; les trois suivent l'objet quand on le démonte.
 */
public final class MeterBlockEntity extends InlineEnergyBlockEntity implements SubscriberHolder {

    public static final String MESSAGE = "message.oneforallsurvival.meter.";

    private static final String TAG_SERIAL = "serial";
    private static final String TAG_SUBSCRIBER = "subscriber";

    private UUID serial;
    private String subscriber = "";

    public MeterBlockEntity(BlockPos pos, BlockState state) {
        super(GridRegistry.METER_BE.get(), pos, state, GridConfig.meterMaxPerTick());
    }

    /** Le numéro du compteur, tiré une fois pour toutes à la première demande (la pose) puis gardé. */
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

    /**
     * Inscrit ou change l'abonné, et le note au registre : le nouvel abonné ne paie qu'à partir de l'index actuel,
     * l'ancien garde ce qu'il n'a pas encore payé (voir {@link MeterLedger#inscribe}).
     */
    @Override
    public void inscribe(String name, MinecraftServer server) {
        SubscriberSavedData.get(server).inscribe(subscriber, name, serial(), totalTransferred(),
                SubscriberSavedData.today(server), MeterLedger.formatRealDate(Instant.now(), ZoneId.systemDefault()));
        setSubscriber(name);
    }

    /** Un compteur qui n'a rien compté et n'a pas d'abonné : son objet reste nu et s'empile. */
    boolean isFactoryFresh() {
        return totalTransferred() == 0 && subscriber.isEmpty();
    }

    /**
     * Le relevé par {@code player}, avec le testeur. Réservé à l'ingénieur ; refusé sans abonné. Le relevé est donné
     * (ou lâché si l'inventaire est plein) et le registre de l'abonné mis à jour.
     */
    public void readBy(ServerPlayer player) {
        if (!RoleGate.requireEngineer(player)) {
            return;
        }
        if (subscriber.isEmpty()) {
            player.sendSystemMessage(Component.translatable(MESSAGE + "no_subscriber_reading"));
            return;
        }
        MinecraftServer server = player.server;
        String realDate = MeterLedger.formatRealDate(Instant.now(), ZoneId.systemDefault());
        Reading reading = SubscriberSavedData.get(server).take(subscriber, serial(), totalTransferred(),
                SubscriberSavedData.today(server), realDate);
        ItemStack paper = MeterReadingItem.create(reading);
        if (!player.getInventory().add(paper)) {
            player.drop(paper, false);
        }
        player.sendSystemMessage(Component.translatable(MESSAGE + "reading_done", reading.subscriber(),
                reading.consumption()));
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (serial != null) {
            tag.putUUID(TAG_SERIAL, serial);
        }
        if (!subscriber.isEmpty()) {
            tag.putString(TAG_SUBSCRIBER, subscriber);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.hasUUID(TAG_SERIAL)) {
            serial = tag.getUUID(TAG_SERIAL);
        }
        subscriber = tag.getString(TAG_SUBSCRIBER);
    }
}
