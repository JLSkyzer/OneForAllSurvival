package dev.oneforallsurvival.bench;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Serveur vers client : une partie du catalogue. Un serveur dédié a un catalogue vide côté client, et un
 * paquet personnalisé ne peut pas dépasser environ 1 Mio : le catalogue est donc découpé en fragments
 * d'au plus {@link #MAX_FRAGMENT_BYTES} octets, numérotés. Le client ne remplace son catalogue qu'à
 * la réception du dernier.
 *
 * @param generation génération du catalogue (voir {@link BenchCatalog#generation()})
 * @param part       numéro du fragment, de 0 à {@code parts - 1}
 * @param parts      nombre total de fragments
 */
public record RecipeListPacket(int generation, int part, int parts, List<BenchRecipe> recipes) {

    /** Taille visée d'un fragment : le quart de la limite d'un paquet personnalisé. */
    public static final int MAX_FRAGMENT_BYTES = 256 * 1024;
    private static final int MAX_INGREDIENTS = 1024;

    public static List<RecipeListPacket> fragments(int generation, Collection<BenchRecipe> recipes) {
        return fragments(generation, recipes, MAX_FRAGMENT_BYTES);
    }

    /** Découpe le catalogue en fragments d'au plus {@code maxBytes} octets (une recette plus grosse reste seule). */
    public static List<RecipeListPacket> fragments(int generation, Collection<BenchRecipe> recipes, int maxBytes) {
        List<List<BenchRecipe>> groups = new ArrayList<>();
        List<BenchRecipe> group = new ArrayList<>();
        int bytes = 0;
        FriendlyByteBuf scratch = new FriendlyByteBuf(Unpooled.buffer());
        try {
            for (BenchRecipe recipe : recipes) {
                scratch.clear();
                write(scratch, recipe);
                int size = scratch.readableBytes();
                if (!group.isEmpty() && bytes + size > maxBytes) {
                    groups.add(group);
                    group = new ArrayList<>();
                    bytes = 0;
                }
                group.add(recipe);
                bytes += size;
            }
        } finally {
            scratch.release();
        }
        if (!group.isEmpty() || groups.isEmpty()) {
            groups.add(group); // un catalogue vide s'envoie aussi : le client doit le savoir
        }
        List<RecipeListPacket> out = new ArrayList<>(groups.size());
        for (int i = 0; i < groups.size(); i++) {
            out.add(new RecipeListPacket(generation, i, groups.size(), groups.get(i)));
        }
        return out;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(generation);
        buf.writeVarInt(part);
        buf.writeVarInt(parts);
        buf.writeVarInt(recipes.size());
        for (BenchRecipe recipe : recipes) {
            write(buf, recipe);
        }
    }

    public static RecipeListPacket decode(FriendlyByteBuf buf) {
        int generation = buf.readVarInt();
        int part = buf.readVarInt();
        int parts = buf.readVarInt();
        int n = buf.readVarInt();
        if (n < 0 || n > buf.readableBytes()) {
            throw new DecoderException("catalogue de l'etabli : nombre de recettes invalide " + n);
        }
        List<BenchRecipe> recipes = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            recipes.add(read(buf));
        }
        return new RecipeListPacket(generation, part, parts, recipes);
    }

    private static void write(FriendlyByteBuf buf, BenchRecipe recipe) {
        buf.writeResourceLocation(recipe.id());
        buf.writeItem(recipe.result());
        buf.writeVarInt(recipe.ingredients().size());
        for (Ingredient ingredient : recipe.ingredients()) {
            ingredient.toNetwork(buf);
        }
        buf.writeUtf(recipe.category());
        buf.writeVarInt(recipe.seconds());
    }

    private static BenchRecipe read(FriendlyByteBuf buf) {
        var id = buf.readResourceLocation();
        var result = buf.readItem();
        int n = buf.readVarInt();
        if (n < 0 || n > MAX_INGREDIENTS) {
            throw new DecoderException("catalogue de l'etabli : nombre d'ingredients invalide " + n);
        }
        List<Ingredient> ingredients = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            ingredients.add(Ingredient.fromNetwork(buf));
        }
        return new BenchRecipe(id, result, ingredients, buf.readUtf(), buf.readVarInt());
    }
}
