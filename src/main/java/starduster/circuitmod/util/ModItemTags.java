package starduster.circuitmod.util;

import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import starduster.circuitmod.Circuitmod;

public class ModItemTags {
    private ModItemTags() {

    }

    public static final TagKey<Item> STEEL_TOOL_MATERIALS = TagKey.of(RegistryKeys.ITEM, Identifier.of("circuitmod","steel_tool_materials"));
    public static final TagKey<Item> PULSE_STICK_REPAIR_MATERIALS = TagKey.of(RegistryKeys.ITEM, Identifier.of("circuitmod","pulse_stick_repair_materials"));
    public static final TagKey<Item> FUEL = TagKey.of(RegistryKeys.ITEM, Identifier.of("circuitmod","fuel"));
    public static final TagKey<Item> SHARINGA_LOGS = TagKey.of(RegistryKeys.ITEM, Identifier.of("circuitmod","sharinga_logs"));

    public static void initialize() {
        Circuitmod.LOGGER.info("Registering mod item tags");
    }
}
