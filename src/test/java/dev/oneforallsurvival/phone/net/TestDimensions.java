package dev.oneforallsurvival.phone.net;

import java.lang.reflect.Method;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Les clés de dimension pour les tests JUnit. {@code Level.OVERWORLD}, {@code Registries.DIMENSION} et
 * {@code ResourceKey.createRegistryKey} exigent l'amorçage du jeu (registres, donc Forge et son réseau), que JUnit n'a
 * pas. On passe par la fabrique privée {@code ResourceKey.create(ResourceLocation, ResourceLocation)} (réflexion),
 * qui internise : deux appels de même chemin rendent la même instance.
 *
 * <p>Non testé : que ces clés soient la même instance que {@code Level.OVERWORLD} et {@code Level.NETHER} (lire
 * {@code Level} sans amorçage lève). D'après le code de la fabrique, c'est le cas (registre {@code
 * minecraft:dimension}, chemin donné), mais seul un GameTest le prouverait : ne pas mélanger ces clés avec de vraies
 * dans un test JUnit. Un changement de version de Minecraft qui rend cette fabrique inaccessible fait lever
 * {@link AssertionError} ici, à un seul endroit.
 */
public final class TestDimensions {

    public static final ResourceKey<Level> OVERWORLD = key("overworld");
    public static final ResourceKey<Level> NETHER = key("the_nether");

    private TestDimensions() {}

    // "removal" : ResourceLocation(String, String) est marqué à retirer en 1.20.1 (le compilateur le signale sinon).
    @SuppressWarnings({"unchecked", "removal"})
    public static ResourceKey<Level> key(String path) {
        try {
            Method create = ResourceKey.class.getDeclaredMethod("create", ResourceLocation.class,
                    ResourceLocation.class);
            create.setAccessible(true);
            return (ResourceKey<Level>) create.invoke(null, new ResourceLocation("minecraft", "dimension"),
                    new ResourceLocation("minecraft", path));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("fabrique de ResourceKey inaccessible : adapter TestDimensions", e);
        }
    }
}
