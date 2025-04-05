package starduster.circuitmod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import starduster.circuitmod.item.ModItems;
import starduster.circuitmod.block.ModBlocks;
import starduster.circuitmod.block.entity.ModBlockEntities;
import starduster.circuitmod.screen.ModScreenHandlers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Circuitmod implements ModInitializer {
	public static final String MOD_ID = "circuitmod";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	
	// Create a RegistryKey for our item group
	public static final RegistryKey<ItemGroup> ITEM_GROUP = RegistryKey.of(
		RegistryKeys.ITEM_GROUP, 
		Identifier.of(MOD_ID, "item_group")
	);

	@Override
	public void onInitialize() {
		// Register our item group
		Registry.register(Registries.ITEM_GROUP, ITEM_GROUP, FabricItemGroup.builder()
			.displayName(Text.translatable("itemgroup." + MOD_ID + ".item_group"))
			.icon(() -> new ItemStack(ModItems.LEAD_INGOT))
			.entries((displayContext, entries) -> {
				// Add all mod items to the creative tab
				entries.add(ModItems.LEAD_INGOT);
				// Add quarry block to the creative tab
				entries.add(ModBlocks.QUARRY_BLOCK);
				
				// Add more mod items here as they are created
			})
			.build()
		);
		
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		// Initialize our mod components
		ModItems.initialize();
		ModBlocks.initialize();
		ModBlockEntities.initialize();
		ModScreenHandlers.initialize();
		
		LOGGER.info("Hello Fabric world!");
	}
}