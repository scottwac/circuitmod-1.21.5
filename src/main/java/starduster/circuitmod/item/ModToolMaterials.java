package starduster.circuitmod.item;

import net.minecraft.item.ToolMaterial;
import net.minecraft.registry.tag.BlockTags;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.util.ModItemTags;

public class ModToolMaterials {
    private ModToolMaterials() {}

    public static final ToolMaterial STEEL_TOOL_MATERIAL = new ToolMaterial(
                BlockTags.INCORRECT_FOR_IRON_TOOL,
                855,
                9.0F,
                2.5F,
                14,
                ModItemTags.STEEL_TOOL_MATERIALS
        );


    public static void initialize() {
        Circuitmod.LOGGER.info("Registering tool materials");
    }
}
