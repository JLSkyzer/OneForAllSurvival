package dev.oneforallsurvival.phone.call;

import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Serveur vers client (« AirDrop », retours d'essai du 27/11) : la liste « Partager mon numéro » de l'écran du téléphone
 * ouvert, et le mot du dernier envoi. Chaque ligne : le numéro d'entité du joueur (ce que le client voit déjà de lui) et
 * le nom de son téléphone (son propriétaire). Ni numéro de carte, ni UUID. Borné dès la construction : au plus
 * {@link ShareRules#MAX_ROWS} lignes, noms de {@value #MAX_NAME} au plus.
 *
 * @param message le retour d'un envoi (composant traduit chez le client) ; {@link Component#empty()} sinon
 */
public record PhoneSharePacket(List<Row> rows, Component message) {

    public static final int MAX_NAME = ContactRules.MAX_NAME;

    /** Une personne à proximité : son numéro d'entité et le nom de son téléphone. */
    public record Row(int id, String name) {

        public Row {
            name = PhoneCallActionPacket.bound(name, MAX_NAME);
        }
    }

    public PhoneSharePacket {
        List<Row> r = new ArrayList<>();
        if (rows != null) {
            for (Row row : rows) {
                if (r.size() == ShareRules.MAX_ROWS) {
                    break;
                }
                r.add(row);
            }
        }
        rows = List.copyOf(r);
        message = message == null ? Component.empty() : message;
    }

    @Override
    public String toString() {
        return "PhoneSharePacket[rows=" + rows.size() + "]";
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(rows.size());
        for (Row row : rows) {
            buf.writeVarInt(row.id());
            buf.writeUtf(row.name(), MAX_NAME);
        }
        boolean hasMessage = !message.equals(Component.empty());
        buf.writeBoolean(hasMessage);
        if (hasMessage) {
            buf.writeComponent(message);
        }
    }

    public static PhoneSharePacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        if (n < 0 || n > ShareRules.MAX_ROWS) {
            throw new DecoderException("lignes : " + n + " (au plus " + ShareRules.MAX_ROWS + ")");
        }
        List<Row> rows = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            rows.add(new Row(buf.readVarInt(), buf.readUtf(MAX_NAME)));
        }
        Component message = buf.readBoolean() ? buf.readComponent() : Component.empty();
        return new PhoneSharePacket(rows, message);
    }

    /** Côté client, sur son fil principal : l'écran du téléphone ouvert, s'il y en a un. */
    public static void handle(PhoneSharePacket packet, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> dev.oneforallsurvival.phone.client.PhoneClient.acceptShare(packet));
    }
}
