package starduster.circuitmod.item;

import net.minecraft.item.Item;
import net.minecraft.item.Items;
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
    public static final Item STEEL_INGOT = register("steel_ingot", Item::new, new Item.Settings());

    public static final Item NATURAL_RUBBER = register("natural_rubber", Item::new, new Item.Settings());
    public static final Item SYNTHETIC_RUBBER = register("synthetic_rubber", Item::new, new Item.Settings());
    public static final Item PLASTIC_PELLET = register("plastic_pellet", Item::new, new Item.Settings());
    public static final Item PLASTIC_BAR = register("plastic_bar", Item::new, new Item.Settings());

    public static final Item STONE_DUST = register("stone_dust", Item::new, new Item.Settings());

    public static final Item ZIRCONIUM_INGOT = register("zirconium_ingot", Item::new, new Item.Settings());
    public static final Item ZIRCONIUM_POWDER = register("zirconium_powder", Item::new, new Item.Settings());
    public static final Item ZIRCONIUM_TUBE = register("zirconium_tube", Item::new, new Item.Settings());

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