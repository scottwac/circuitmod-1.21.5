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
    public static final Item LEAD_INGOT = register("lead_ingot", Item::new, new Item.Settings());
    
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