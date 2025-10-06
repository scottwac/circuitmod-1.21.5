package starduster.circuitmod.item;

import net.minecraft.item.equipment.ArmorMaterial;
import net.minecraft.item.equipment.ArmorMaterials;
import starduster.circuitmod.Circuitmod;

public final class ModArmorMaterials {
    private ModArmorMaterials() {}

    // Using diamond armor material as base for emu suit
    public static final ArmorMaterial EMU_SUIT_MATERIAL = ArmorMaterials.DIAMOND;

    public static void initialize() {
        Circuitmod.LOGGER.info("Registering armor materials");
    }
}
