package starduster.circuitmod.item;

import net.minecraft.item.equipment.ArmorMaterial;
import net.minecraft.item.equipment.EquipmentType;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import starduster.circuitmod.Circuitmod;

import java.util.EnumMap;

public class ModArmorMaterials {
    private ModArmorMaterials() {}

    // EMU Suit armor material - simply a data holder, not registered to a registry
    // In 1.21.5, armor materials are data-driven via JSON files
    public static final ArmorMaterial EMU_SUIT_MATERIAL = new ArmorMaterial(
            37, // Base durability multiplier
            Util.make(new EnumMap<>(EquipmentType.class), map -> {
                map.put(EquipmentType.BOOTS, 3);
                map.put(EquipmentType.LEGGINGS, 6);
                map.put(EquipmentType.CHESTPLATE, 8);
                map.put(EquipmentType.HELMET, 3);
            }),
            15, // Enchantability
            SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE,
            0.0F, // Toughness
            0.0F, // Knockback resistance
            TagKey.of(RegistryKeys.ITEM, Identifier.of("circuitmod", "emu_suit_repair_materials")),
            null // Asset ID - will be handled by GeckoLib rendering
    );

    public static void initialize() {
        Circuitmod.LOGGER.info("Registering armor materials");
    }
}
