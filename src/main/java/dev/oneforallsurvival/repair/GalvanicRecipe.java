package dev.oneforallsurvival.repair;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;

/**
 * Une recette du bain galvanique : une pièce brute plongée dans le bain, payée en zinc, ressort traitée.
 * La durée est en ticks de travail (le rendement de la machine les allonge), la consommation en FE par tick.
 *
 * <p>JSON : {@code ingredient}, {@code zinc}, {@code zinc_count} (1 par défaut), {@code result},
 * {@code ticks} et {@code energy_per_tick}. Le zinc est un ingrédient ordinaire, en général l'étiquette
 * {@code forge:ingots/zinc} : sans Create elle est vide et le bain n'a alors rien à traiter.
 */
public final class GalvanicRecipe implements Recipe<Container> {

    public static final int DEFAULT_TICKS = 200;
    public static final int DEFAULT_ENERGY_PER_TICK = 20;

    private final ResourceLocation id;
    private final Ingredient ingredient;
    private final Ingredient zinc;
    private final int zincCount;
    private final ItemStack result;
    private final int ticks;
    private final int energyPerTick;

    public GalvanicRecipe(ResourceLocation id, Ingredient ingredient, Ingredient zinc, int zincCount,
            ItemStack result, int ticks, int energyPerTick) {
        this.id = id;
        this.ingredient = ingredient;
        this.zinc = zinc;
        this.zincCount = zincCount;
        this.result = result;
        this.ticks = ticks;
        this.energyPerTick = energyPerTick;
    }

    /**
     * Forge donne une barrière pour un ingrédient dont l'étiquette est vide : ce n'est pas un vrai objet
     * accepté (sans Create, l'étiquette du zinc est vide).
     */
    private static boolean accepts(Ingredient ingredient, ItemStack stack) {
        return !stack.isEmpty() && !stack.is(Items.BARRIER) && ingredient.test(stack);
    }

    public boolean acceptsPiece(ItemStack stack) {
        return accepts(ingredient, stack);
    }

    public boolean acceptsZinc(ItemStack stack) {
        return accepts(zinc, stack);
    }

    /** Les objets d'un ingrédient, sans la barrière que Forge met pour une étiquette vide. */
    public static java.util.List<ItemStack> realStacks(Ingredient ingredient) {
        java.util.List<ItemStack> out = new java.util.ArrayList<>();
        for (ItemStack stack : ingredient.getItems()) {
            if (!stack.is(Items.BARRIER)) {
                out.add(stack);
            }
        }
        return out;
    }

    public Ingredient ingredient() {
        return ingredient;
    }

    public Ingredient zinc() {
        return zinc;
    }

    public int zincCount() {
        return zincCount;
    }

    public ItemStack result() {
        return result;
    }

    public int ticks() {
        return ticks;
    }

    public int energyPerTick() {
        return energyPerTick;
    }

    @Override
    public boolean matches(Container container, Level level) {
        return acceptsPiece(container.getItem(0));
    }

    @Override
    public ItemStack assemble(Container container, RegistryAccess access) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess access) {
        return result;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return RepairRegistry.GALVANIC_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return RepairRegistry.GALVANIC_TYPE.get();
    }

    /** Hors du livre de recettes : le bain a sa propre catégorie JEI. */
    @Override
    public boolean isSpecial() {
        return true;
    }

    public static final class Serializer implements RecipeSerializer<GalvanicRecipe> {

        @Override
        public GalvanicRecipe fromJson(ResourceLocation id, JsonObject json) {
            Ingredient ingredient = Ingredient.fromJson(GsonHelper.getNonNull(json, "ingredient"));
            Ingredient zinc = Ingredient.fromJson(GsonHelper.getNonNull(json, "zinc"));
            int zincCount = GsonHelper.getAsInt(json, "zinc_count", 1);
            int ticks = GsonHelper.getAsInt(json, "ticks", DEFAULT_TICKS);
            int energy = GsonHelper.getAsInt(json, "energy_per_tick", DEFAULT_ENERGY_PER_TICK);
            ItemStack result = ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json, "result"));
            if (zincCount < 1 || zincCount > 64) {
                throw new JsonSyntaxException("zinc_count doit être entre 1 et 64 : " + zincCount);
            }
            if (ticks < 1) {
                throw new JsonSyntaxException("ticks doit être au moins 1 : " + ticks);
            }
            if (energy < 0) {
                throw new JsonSyntaxException("energy_per_tick ne peut pas être négatif : " + energy);
            }
            return new GalvanicRecipe(id, ingredient, zinc, zincCount, result, ticks, energy);
        }

        @Override
        public GalvanicRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            Ingredient ingredient = Ingredient.fromNetwork(buf);
            Ingredient zinc = Ingredient.fromNetwork(buf);
            int zincCount = buf.readVarInt();
            ItemStack result = buf.readItem();
            int ticks = buf.readVarInt();
            int energy = buf.readVarInt();
            return new GalvanicRecipe(id, ingredient, zinc, zincCount, result, ticks, energy);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, GalvanicRecipe recipe) {
            recipe.ingredient.toNetwork(buf);
            recipe.zinc.toNetwork(buf);
            buf.writeVarInt(recipe.zincCount);
            buf.writeItem(recipe.result);
            buf.writeVarInt(recipe.ticks);
            buf.writeVarInt(recipe.energyPerTick);
        }
    }
}
