package dev.oneforallsurvival.economy;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/** Les cinq coupures, de la plus petite à la plus grande. La valeur, en cuivres, vient de la configuration. */
public enum Coin {
    COPPER("copper"),
    SILVER("silver"),
    GOLD("gold"),
    EMERALD("emerald"),
    RUBY("ruby");

    private final String name;

    Coin(String name) {
        this.name = name;
    }

    /** Début des identifiants : {@code copper}, d'où l'objet {@code copper_coin}. */
    public String key() {
        return name;
    }

    public ResourceLocation itemId() {
        return new ResourceLocation(dev.oneforallsurvival.OneForAllSurvival.MODID, name + "_coin");
    }

    /** Valeur de la pièce, en cuivres. */
    public long value() {
        return EconomyConfig.coinValue(this);
    }

    public Item item() {
        return EconomyRegistry.coinItem(this);
    }

    /** Clé de langue du nom de la coupure, sans le nombre : « or », « argent »... */
    public String unitKey() {
        return "economy.oneforallsurvival.unit." + name;
    }

    /**
     * Clé de langue du nom de la coupure pour {@code n} pièces : le pluriel au-delà de 1 (en français 0 et 1 sont au
     * singulier), le singulier sinon.
     */
    public String unitKey(long n) {
        return n > 1L ? unitKey() + ".plural" : unitKey();
    }

    public static Optional<Coin> fromItem(Item item) {
        for (Coin coin : values()) {
            if (coin.item() == item) {
                return Optional.of(coin);
            }
        }
        return Optional.empty();
    }
}
