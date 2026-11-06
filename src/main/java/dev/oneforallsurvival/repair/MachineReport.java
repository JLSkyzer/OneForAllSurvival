package dev.oneforallsurvival.repair;

import dev.oneforallsurvival.machine.ElectricMachineBlockEntity;
import dev.oneforallsurvival.machine.FaultType;
import dev.oneforallsurvival.machine.MachineWear;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;

/** Le rapport du testeur : ce qu'on lit d'une machine sans l'ouvrir. Lisible par tout le monde. */
public final class MachineReport {

    private MachineReport() {}

    public static List<Component> lines(ElectricMachineBlockEntity machine) {
        List<Component> lines = new ArrayList<>();
        double condition = machine.condition();
        lines.add(Component.translatable("message.oneforallsurvival.report.condition", Math.round(condition)));
        lines.add(Component.translatable("message.oneforallsurvival.report.visible", visible(machine)));
        lines.add(Component.translatable("message.oneforallsurvival.report.energy",
                machine.energy().getEnergyStored(), machine.energy().getMaxEnergyStored()));
        FaultType fault = machine.fault();
        if (fault != null) {
            lines.add(Component.translatable("message.oneforallsurvival.report.fault",
                    Component.translatable(fault.translationKey())));
        }
        if (condition < MachineWear.FULL) {
            lines.add(Component.translatable("message.oneforallsurvival.report.part",
                    RepairRegistry.partItem(machine.expectedPart(), false).getDescription()));
        }
        lines.add(Component.translatable(machine.isProtected()
                ? "message.oneforallsurvival.report.protected"
                : "message.oneforallsurvival.report.exposed"));
        return lines;
    }

    /** Ce qu'on voit de la machine : en panne, fumante, fatiguée ou en bon état. */
    private static Component visible(ElectricMachineBlockEntity machine) {
        String key;
        if (machine.fault() != null) {
            key = "fault";
        } else if (machine.condition() < MachineWear.SMOKE_BELOW) {
            key = "smoking";
        } else if (machine.condition() < MachineWear.REDUCED_BELOW) {
            key = "tired";
        } else {
            key = "good";
        }
        return Component.translatable("message.oneforallsurvival.report.visible." + key);
    }
}
