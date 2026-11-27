package dev.oneforallsurvival.phone.net;

import dev.oneforallsurvival.phone.PhoneRegistry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * La carte SIM (lot T2) : émise par le fondateur d'un opérateur ({@link PhoneSims#issueTo}), jamais fabriquée.
 *
 * <p>Ce qui décide vit au registre ({@link PhoneOperators}) : la pile ne porte que l'identifiant de la carte
 * ({@value #TAG_ID}) et son jeton ({@value #TAG_TOKEN}), comparé à celui du registre à chaque insertion et à chaque
 * lecture de la ligne. Les copies d'affichage (numéro, opérateur, titulaire) ne servent qu'à l'infobulle et ne sont
 * jamais lues pour décider.
 *
 * <p>Le jeton est une <b>preuve de présence</b>, pas un secret fort : il dit que cette pile est passée par le registre
 * depuis la dernière insertion. Il n'est écrit dans aucun journal, et il n'est retiré de ce que reçoit le client que par
 * {@link #getShareTag} (cartes en inventaire, au sol, en main, logées dans un téléphone), qui met à sa place son
 * empreinte publique ({@value #TAG_MARK}, {@link CreativeSimTokens#mark}). Le survol du nom d'une pile dans un message
 * (message de mort d'un tueur qui la tient renommée, par exemple) passe par {@code HoverEventItemStackInfoMixin} et
 * {@code PhonePrivacy} : rien de la carte, empreinte comprise (tâche 15). Le jeton peut encore voyager vers un client
 * par des chemins qui n'appellent pas {@code getShareTag} : une carte rangée dans un conteneur porté comme un objet
 * (boîte de Shulker, sac d'un mod), dont le NBT part entier, ou posée dans une machine de Create (déployeur, dépôt,
 * tapis : le bloc envoie sa pile entière). Exploiter un jeton connu demande une pile à NBT libre (créatif ou commande,
 * l'équipe seule) ; et la première insertion d'une des deux piles fait tourner le jeton : jamais deux lignes.
 *
 * <p>Mode créatif : le client créatif renvoie au serveur la pile qu'il a reçue, avec l'empreinte et sans le jeton. Le
 * serveur rend à la carte (seule ou logée dans un téléphone) le jeton de cette empreinte qu'il a vu chez ce joueur
 * ({@link CreativeSims}, retours d'essai du 27/11), jamais celui d'un autre. Une carte abîmée avant cette correction se
 * remplace : le fondateur la rend par « Remplacer la carte » ({@link PhoneSims#replaceCard}), même numéro, nouveau jeton.
 */
public class SimCardItem extends Item {

    public static final String TAG_ID = "sim_id";
    public static final String TAG_TOKEN = "sim_token";
    /** L'empreinte publique du jeton : ce que le client reçoit à sa place. */
    public static final String TAG_MARK = "sim_mark";
    public static final String TAG_NUMBER = "sim_number";
    public static final String TAG_OPERATOR = "sim_operator";
    public static final String TAG_HOLDER = "sim_holder";

    private static final String TOOLTIP = "tooltip.oneforallsurvival.sim_card.";

    public SimCardItem(Properties properties) {
        super(properties);
    }

    /** L'identifiant de la carte que porte cette pile ; vide si ce n'est pas une carte ou si elle n'en a pas. */
    public static Optional<UUID> simId(ItemStack stack) {
        if (stack.getItem() instanceof SimCardItem && stack.hasTag() && stack.getTag().hasUUID(TAG_ID)) {
            return Optional.of(stack.getTag().getUUID(TAG_ID));
        }
        return Optional.empty();
    }

    /** Le jeton que porte cette pile (serveur seulement : le client ne le reçoit jamais). */
    static Optional<UUID> token(ItemStack stack) {
        if (stack.getItem() instanceof SimCardItem && stack.hasTag() && stack.getTag().hasUUID(TAG_TOKEN)) {
            return Optional.of(stack.getTag().getUUID(TAG_TOKEN));
        }
        return Optional.empty();
    }

    /** La pile de cette carte, avec son jeton courant et ses copies d'affichage. Serveur seulement. */
    public static ItemStack create(SimCard card, String operatorName) {
        ItemStack stack = new ItemStack(PhoneRegistry.SIM_CARD.get());
        CompoundTag tag = stack.getOrCreateTag();
        tag.putUUID(TAG_ID, card.simId());
        write(stack, card, operatorName);
        return stack;
    }

    /** Réécrit le jeton et les copies d'affichage d'une pile de cette carte. */
    static void write(ItemStack stack, SimCard card, String operatorName) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.remove(TAG_MARK);
        tag.putUUID(TAG_TOKEN, card.token());
        tag.putString(TAG_NUMBER, card.number());
        tag.putString(TAG_OPERATOR, operatorName == null ? "" : operatorName);
        tag.putString(TAG_HOLDER, card.holderName());
    }

    /** Ce que le client reçoit : tout, l'empreinte du jeton à la place du jeton. */
    @Override
    @Nullable
    public CompoundTag getShareTag(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_TOKEN)) {
            return tag;
        }
        CompoundTag copy = tag.copy();
        hideToken(copy);
        return copy;
    }

    /**
     * Dans le NBT d'une carte destiné au client (une copie), remplace le jeton par son empreinte publique ; sans jeton,
     * ne change rien. Un jeton illisible est seulement ôté.
     */
    public static void hideToken(CompoundTag card) {
        if (card.hasUUID(TAG_TOKEN)) {
            card.putUUID(TAG_MARK, CreativeSimTokens.mark(card.getUUID(TAG_TOKEN)));
        }
        card.remove(TAG_TOKEN);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        appendCard(stack, tooltip);
    }

    /** Les lignes d'une carte : numéro formaté, opérateur, titulaire ou « SIM sans nom ». */
    public static void appendCard(ItemStack stack, List<Component> tooltip) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_NUMBER)) {
            tooltip.add(Component.translatable(TOOLTIP + "blank").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable(TOOLTIP + "number", SimNumbers.format(tag.getString(TAG_NUMBER)))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(TOOLTIP + "operator", tag.getString(TAG_OPERATOR))
                .withStyle(ChatFormatting.GRAY));
        String holder = tag.getString(TAG_HOLDER);
        tooltip.add((holder.isEmpty() ? Component.translatable(TOOLTIP + "anonymous")
                : Component.translatable(TOOLTIP + "holder", holder)).withStyle(ChatFormatting.GRAY));
    }
}
