package dev.oneforallsurvival.fence;

/** Les règles chiffrées de la clôture électrique, sans Minecraft : consommation et dégâts. */
public final class FenceRules {

    private FenceRules() {}

    /**
     * Énergie par tick de la ligne : 0 sans clôture, sinon {@code ceil((base + clôtures × parClôture + végétation ×
     * parVégétation) × (orage ? multiplicateur : 1))}, bornée à {@code max}.
     */
    public static int energyPerTick(int fences, int vegetation, boolean storm, double base, double perFence,
            double perVegetation, double stormMultiplier, int max) {
        if (fences <= 0) {
            return 0;
        }
        double cost = (base + fences * perFence + vegetation * perVegetation) * (storm ? stormMultiplier : 1.0);
        return (int) Math.min(max, Math.ceil(cost));
    }

    /**
     * Dégâts à infliger : {@code amount} si le coup peut tuer, sinon ce qu'il faut pour laisser {@code floor} points de
     * vie au plus bas, jamais négatif.
     */
    public static float damageFor(float health, float amount, float floor, boolean lethal) {
        if (lethal) {
            return amount;
        }
        return Math.max(0f, Math.min(amount, health - floor));
    }
}
