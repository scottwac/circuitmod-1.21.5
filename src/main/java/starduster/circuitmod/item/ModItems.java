package starduster.circuitmod.item;

import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.item.ToolMaterial;
import starduster.circuitmod.item.FuelRodItem;
import net.minecraft.registry.Registry;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import starduster.circuitmod.Circuitmod;

import java.util.function.Function;

public final class ModItems {
    private ModItems() {
    }
    
    // Define the lead ingot item
    public static final Item RAW_BAUXITE = register("raw_bauxite", Item::new, new Item.Settings());
    public static final Item CRUSHED_BAUXITE = register("crushed_bauxite", Item::new, new Item.Settings());
    public static final Item ALUMINUM_INGOT = register("aluminum_ingot", Item::new, new Item.Settings());

    public static final Item GRAPHITE = register("graphite", Item::new, new Item.Settings());
    public static final Item GRAPHITE_POWDER = register("graphite_powder", Item::new, new Item.Settings());

    public static final Item RAW_LEAD = register("raw_lead", Item::new, new Item.Settings());
    public static final Item LEAD_POWDER = register("lead_powder", Item::new, new Item.Settings());
    public static final Item LEAD_INGOT = register("lead_ingot", Item::new, new Item.Settings());

    public static final Item IRON_POLE = register("iron_pole", Item::new, new Item.Settings());
    public static final Item BLOOM = register("bloom", Item::new, new Item.Settings());
    public static final Item STEEL_INGOT = register("steel_ingot", Item::new, new Item.Settings());

    public static final Item NATURAL_RUBBER = register("natural_rubber", Item::new, new Item.Settings());
    public static final Item SYNTHETIC_RUBBER = register("synthetic_rubber", Item::new, new Item.Settings());
    public static final Item PLASTIC_PELLET = register("plastic_pellet", Item::new, new Item.Settings());
    public static final Item PLASTIC_BAR = register("plastic_bar", Item::new, new Item.Settings());

    public static final Item STONE_DUST = register("stone_dust", Item::new, new Item.Settings());

    public static final Item RAW_URANIUM = register("raw_uranium", Item::new, new Item.Settings());
    public static final Item CRUSHED_URANIUM = register("crushed_uranium", Item::new, new Item.Settings());
    public static final Item URANIUM_PELLET = register("uranium_pellet", Item::new, new Item.Settings());
    public static final Item FUEL_ROD = register("fuel_rod", FuelRodItem::new, new Item.Settings().maxCount(64));

    public static final Item ZIRCON = register("zircon", Item::new, new Item.Settings());
    public static final Item ZIRCONIUM_INGOT = register("zirconium_ingot", Item::new, new Item.Settings());
    public static final Item ZIRCONIUM_POWDER = register("zirconium_powder", Item::new, new Item.Settings());
    public static final Item ZIRCONIUM_TUBE = register("zirconium_tube", Item::new, new Item.Settings());

    public static final Item STEEL_SWORD = register("steel_sword", Item::new, (new Item.Settings()).sword(ModToolMaterials.STEEL_TOOL_MATERIAL, 3.0F, -2.4F));
    public static final Item STEEL_SHOVEL = register("steel_shovel", Item::new, (new Item.Settings()).shovel(ModToolMaterials.STEEL_TOOL_MATERIAL, 1.5F, -3.0F));
    public static final Item STEEL_PICKAXE = register("steel_pickaxe", Item::new, (new Item.Settings()).pickaxe(ModToolMaterials.STEEL_TOOL_MATERIAL, 1.0F, -2.8F));
    public static final Item STEEL_AXE = register("steel_axe", Item::new, (new Item.Settings()).axe(ModToolMaterials.STEEL_TOOL_MATERIAL, 6.0F, -3.1F));
    public static final Item STEEL_HOE = register("steel_hoe", Item::new, (new Item.Settings()).hoe(ModToolMaterials.STEEL_TOOL_MATERIAL, 2.0F, -1.0F));

    // Blueprint items
    public static final Item BLUEPRINT = register("blueprint", BlueprintItem::new, new Item.Settings().maxCount(1));
    public static final Item BLANK_BLUEPRINT = register("blank_blueprint", BlankBlueprintItem::new, new Item.Settings().maxCount(16));




    // Registration helper
    public static Item register(String path, Function<Item.Settings, Item> factory, Item.Settings settings) {
        final RegistryKey<Item> registryKey = RegistryKey.of(RegistryKeys.ITEM, Identifier.of("circuitmod", path));
        return Items.register(registryKey, factory, settings);
    }
    
    // Initialize method to be called from the main mod class
    public static void initialize() {
        Circuitmod.LOGGER.info("Registering mod items");
    }
} 