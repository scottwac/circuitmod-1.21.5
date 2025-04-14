package starduster.circuitmod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
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
import starduster.circuitmod.network.ModNetworking;
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
		// TODO Move the item groups to their own ModItemGroups class
		Registry.register(Registries.ITEM_GROUP, ITEM_GROUP, FabricItemGroup.builder()
			.displayName(Text.translatable("itemgroup." + MOD_ID + ".main"))
			.icon(() -> new ItemStack(ModItems.LEAD_INGOT))
			.entries((displayContext, entries) -> {
				// Add all mod items to the creative tab
				entries.add(ModItems.RAW_BAUXITE);
				entries.add(ModItems.CRUSHED_BAUXITE);
				entries.add(ModItems.ALUMINUM_INGOT);

				entries.add(ModItems.GRAPHITE);
				entries.add(ModItems.GRAPHITE_POWDER);

				entries.add(ModItems.RAW_LEAD);
				entries.add(ModItems.LEAD_POWDER);
				entries.add(ModItems.LEAD_INGOT);

				entries.add(ModItems.IRON_POLE);
				entries.add(ModItems.STEEL_INGOT);

				entries.add(ModItems.NATURAL_RUBBER);
				entries.add(ModItems.SYNTHETIC_RUBBER);
				entries.add(ModItems.PLASTIC_PELLET);
				entries.add(ModItems.PLASTIC_BAR);

				entries.add(ModItems.STONE_DUST);

				entries.add(ModItems.ZIRCONIUM_POWDER);
				entries.add(ModItems.ZIRCONIUM_INGOT);
				entries.add(ModItems.ZIRCONIUM_TUBE);
				// Add quarry block to the creative tab
				entries.add(ModBlocks.QUARRY_BLOCK);
				entries.add(ModBlocks.ELECTRIC_FURNACE);
				entries.add(ModBlocks.BLOOMERY); // Add bloomery to creative tab
				entries.add(ModBlocks.SOLAR_PANEL); // Add solar panel to creative tab
				entries.add(ModBlocks.REACTOR_BLOCK); // Add reactor to creative tab
				entries.add(ModBlocks.POWER_CABLE); // Add power cable to creative tab
				entries.add(ModBlocks.CREATIVE_GENERATOR); // Add creative generator to creative tab
				entries.add(ModBlocks.CREATIVE_CONSUMER); // Add creative consumer to creative tab
				entries.add(ModBlocks.BATTERY); // Add battery to creative tab
				entries.add(ModBlocks.ITEM_PIPE); // Add item pipe to creative tab
				entries.add(ModBlocks.NUKE); // Add nuke to creative tab

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
		ModNetworking.initialize();
		
		// Register player connection/disconnection handlers for debugging
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			LOGGER.info("[SERVER] Player " + handler.player.getName().getString() + " connected! " +
				"UUID: " + handler.player.getUuid());
		});
		
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			LOGGER.info("[SERVER] Player " + handler.player.getName().getString() + " disconnected. " +
				"UUID: " + handler.player.getUuid());
		});
	}
}