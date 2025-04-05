package starduster.circuitmod.item;

import net.minecraft.item.Item;
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
    public static final Item LEAD_INGOT = register("lead_ingot", Item::new);
    
    // Registration helper
    private static Item register(String id, Function<Item.Settings, Item> factory) {
        // Create the item key
        RegistryKey<Item> itemKey = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(Circuitmod.MOD_ID, id));
        // Create settings with registry key
        Item.Settings settings = new Item.Settings().registryKey(itemKey);
        // Create and register the item
        return Registry.register(Registries.ITEM, itemKey, factory.apply(settings));
    }
    
    // Initialize method to be called from the main mod class
    public static void initialize() {
        Circuitmod.LOGGER.info("Registering mod items");
    }
} 