package dev.oneforallsurvival.food;

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
 * Une recette du séchoir : un aliment séché devient une ration qui se garde, et une ration salée, qui ne périme
 * jamais, quand du sel l'accompagne. La durée est en ticks de travail (le rendement de la machine les allonge) ;
 * le courant est un réglage de la machine ({@link FoodConfig#dryerEnergy()}), pas de la recette.
 *
 * <p>JSON : {@code ingredient}, {@code result}, {@code salted_result} et {@code ticks} ({@value #DEFAULT_TICKS} par
 * défaut). Un résultat est un objet neuf, sans aucune donnée : il ne garde pas la date de l'aliment séché.
 */
public final class DryingRecipe implements Recipe<Container> {

    public static final int DEFAULT_TICKS = 1200;

    private final ResourceLocation id;
    private final Ingredient ingredient;
    private final ItemStack result;
    private final ItemStack saltedResult;
    private final int ticks;

    public DryingRecipe(ResourceLocation id, Ingredient ingredient, ItemStack result, ItemStack saltedResult, int ticks) {
        this.id = id;
        this.ingredient = ingredient;
        this.result = result;
        this.saltedResult = saltedResult;
        this.ticks = ticks;
    }

    /**
     * Forge donne une barrière pour un ingrédient dont l'étiquette est vide : ce n'est pas un vrai objet accepté.
     */
    public boolean acceptsInput(ItemStack stack) {
        return !stack.isEmpty() && !stack.is(Items.BARRIER) && ingredient.test(stack);
    }

    public Ingredient ingredient() {
        return ingredient;
    }

    /** La ration obtenue, salée ou non. Une copie neuve à chaque appel. */
    public ItemStack resultFor(boolean salted) {
        return (salted ? saltedResult : result).copy();
    }

    public int ticks() {
        return ticks;
    }

    @Override
    public boolean matches(Container container, Level level) {
        return acceptsInput(container.getItem(0));
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
        return FoodRegistry.DRYING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return FoodRegistry.DRYING_TYPE.get();
    }

    /** Hors du livre de recettes : le séchoir a sa propre catégorie JEI. */
    @Override
    public boolean isSpecial() {
        return true;
    }

    public static final class Serializer implements RecipeSerializer<DryingRecipe> {

        @Override
        public DryingRecipe fromJson(ResourceLocation id, JsonObject json) {
            Ingredient ingredient = Ingredient.fromJson(GsonHelper.getNonNull(json, "ingredient"));
            ItemStack result = ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json, "result"));
            ItemStack salted = ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json, "salted_result"));
            int ticks = GsonHelper.getAsInt(json, "ticks", DEFAULT_TICKS);
            if (ticks < 1) {
                throw new JsonSyntaxException("ticks doit être au moins 1 : " + ticks);
            }
            return new DryingRecipe(id, ingredient, result, salted, ticks);
        }

        @Override
        public DryingRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            Ingredient ingredient = Ingredient.fromNetwork(buf);
            ItemStack result = buf.readItem();
            ItemStack salted = buf.readItem();
            return new DryingRecipe(id, ingredient, result, salted, buf.readVarInt());
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, DryingRecipe recipe) {
            recipe.ingredient.toNetwork(buf);
            buf.writeItem(recipe.result);
            buf.writeItem(recipe.saltedResult);
            buf.writeVarInt(recipe.ticks);
        }
    }
}
