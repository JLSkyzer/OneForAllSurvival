package dev.oneforallsurvival.phone.net;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;

/**
 * Une carte SIM, telle que le registre ({@link PhoneOperators}) la garde. Valeur figée : le registre la remplace à
 * chaque changement.
 *
 * <p>Le jeton ({@code token}) prouve qu'un objet « carte » est bien la carte du registre : il change à chaque insertion
 * dans un téléphone ({@link PhoneOperators#rotateToken}). C'est un secret : il ne sort ni dans un journal ni dans un
 * message d'erreur, et {@link #toString()} le masque.
 *
 * @param simId identifiant de la carte
 * @param number son numéro, « 06 » et huit chiffres, jamais réattribué ({@link SimNumbers})
 * @param operatorId l'opérateur qui l'a émise
 * @param holder le titulaire, ou nul pour une carte sans nom
 * @param holderName son nom, tel qu'il était à l'émission (vide pour une carte sans nom)
 * @param state l'état de la carte
 * @param token le jeton courant
 * @param phoneId le téléphone où la carte est logée, s'il y en a un
 * @param issuedAt instant de l'émission ({@code EconomyClock.now()})
 */
public record SimCard(UUID simId, String number, String operatorId, @Nullable UUID holder, String holderName,
        State state, UUID token, @Nullable UUID phoneId, long issuedAt) {

    /** L'état d'une carte : active, suspendue (le numéro reste à elle) ou désactivée (son numéro reste réservé). */
    public enum State { ACTIVE, SUSPENDED, DEACTIVATED }

    /** Tout sauf le jeton. */
    @Override
    public String toString() {
        return "SimCard[simId=" + simId + ", number=" + number + ", operatorId=" + operatorId + ", holder=" + holder
                + ", holderName=" + holderName + ", state=" + state + ", token=***, phoneId=" + phoneId
                + ", issuedAt=" + issuedAt + "]";
    }

    SimCard withState(State newState) {
        return new SimCard(simId, number, operatorId, holder, holderName, newState, token, phoneId, issuedAt);
    }

    SimCard withToken(UUID newToken, @Nullable UUID newPhoneId) {
        return new SimCard(simId, number, operatorId, holder, holderName, state, newToken, newPhoneId, issuedAt);
    }

    SimCard withPhone(@Nullable UUID newPhoneId) {
        return new SimCard(simId, number, operatorId, holder, holderName, state, token, newPhoneId, issuedAt);
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("sim", simId);
        tag.putString("number", number);
        tag.putString("operator", operatorId);
        if (holder != null) {
            tag.putUUID("holder", holder);
        }
        tag.putString("holderName", holderName);
        tag.putString("state", state.name());
        tag.putUUID("token", token);
        if (phoneId != null) {
            tag.putUUID("phone", phoneId);
        }
        tag.putLong("issuedAt", issuedAt);
        return tag;
    }

    /**
     * Relit une carte ; nulle si la ligne est inutilisable : sans identifiant, sans jeton, sans opérateur, numéro qui
     * n'est pas de la forme d'un numéro de carte, ou état inconnu.
     */
    @Nullable
    static SimCard load(CompoundTag tag) {
        String number = tag.getString("number");
        String operator = tag.getString("operator");
        if (!tag.hasUUID("sim") || !tag.hasUUID("token") || operator.isEmpty()
                || !SimNumbers.parse(number).filter(number::equals).isPresent()) {
            return null;
        }
        State state;
        try {
            state = State.valueOf(tag.getString("state"));
        } catch (IllegalArgumentException e) {
            return null;
        }
        return new SimCard(tag.getUUID("sim"), number, operator, tag.hasUUID("holder") ? tag.getUUID("holder") : null,
                tag.getString("holderName"), state, tag.getUUID("token"),
                tag.hasUUID("phone") ? tag.getUUID("phone") : null, Math.max(0L, tag.getLong("issuedAt")));
    }
}
