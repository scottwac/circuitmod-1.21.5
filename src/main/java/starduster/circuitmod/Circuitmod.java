package starduster.circuitmod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.book.CookingRecipeCategory;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import starduster.circuitmod.item.ModItems;
import starduster.circuitmod.block.ModBlocks;
import starduster.circuitmod.block.entity.ModBlockEntities;
import starduster.circuitmod.block.entity.QuarryBlockEntity;
import starduster.circuitmod.block.entity.DrillBlockEntity;
import starduster.circuitmod.item.ModToolMaterials;
import starduster.circuitmod.network.ModNetworking;
import starduster.circuitmod.recipe.ModRecipes;
import starduster.circuitmod.screen.ModScreenHandlers;
import starduster.circuitmod.sound.ModSounds;
import starduster.circuitmod.util.ModItemTags;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Circuitmod implements ModInitializer {
	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger("circuitmod");
	public static final String MOD_ID = "circuitmod";

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		LOGGER.info("Hello Fabric world!");
		
		// Register mod creative mode tab
		Registry.register(Registries.ITEM_GROUP, Identifier.of(MOD_ID, "main"),
			FabricItemGroup.builder()
			.icon(() -> new ItemStack(ModItems.BLOOM))
			.displayName(Text.translatable("itemgroup.circuitmod.main"))
			.entries((context, entries) -> {
				// Add blocks first
				entries.add(ModBlocks.BATTERY);
				entries.add(ModBlocks.POWER_CABLE);
				entries.add(ModBlocks.BLOOMERY);
				entries.add(ModBlocks.CRUSHER);
				entries.add(ModBlocks.ELECTRIC_FURNACE);
				entries.add(ModBlocks.GENERATOR);
				entries.add(ModBlocks.SOLAR_PANEL);
				entries.add(ModBlocks.TESLA_COIL);
				entries.add(ModBlocks.DRILL_BLOCK);
				entries.add(ModBlocks.QUARRY_BLOCK);
				entries.add(ModBlocks.CONSTRUCTOR_BLOCK);

				entries.add(ModBlocks.ELECTRIC_CARPET);
				entries.add(ModBlocks.ITEM_PIPE);
				entries.add(ModBlocks.REACTOR_BLOCK);
				entries.add(ModBlocks.NUKE);

				// Add ingots and materials
				entries.add(ModItems.BLOOM);

				// Add ores
				entries.add(ModBlocks.BAUXITE_ORE);
				entries.add(ModBlocks.DEEPSLATE_BAUXITE_ORE);
				entries.add(ModBlocks.LEAD_ORE);
				entries.add(ModBlocks.DEEPSLATE_LEAD_ORE);
				entries.add(ModBlocks.URANIUM_ORE);
				entries.add(ModBlocks.DEEPSLATE_URANIUM_ORE);
				entries.add(ModBlocks.ZIRCON_ORE);
				entries.add(ModBlocks.DEEPSLATE_ZIRCON_ORE);

				entries.add(ModItems.RAW_BAUXITE);
				entries.add(ModItems.RAW_LEAD);
				entries.add(ModItems.RAW_URANIUM);
				entries.add(ModItems.ZIRCON);

				entries.add(ModItems.CRUSHED_BAUXITE);
				entries.add(ModItems.CRUSHED_URANIUM);

				entries.add(ModItems.LEAD_POWDER);
				entries.add(ModItems.ZIRCONIUM_POWDER);
				entries.add(ModItems.STONE_DUST);

				entries.add(ModItems.ALUMINUM_INGOT);
				entries.add(ModItems.LEAD_INGOT);
				entries.add(ModItems.STEEL_INGOT);
				entries.add(ModItems.URANIUM_PELLET);
				entries.add(ModItems.ZIRCONIUM_INGOT);

				entries.add(ModItems.GRAPHITE);
				entries.add(ModItems.GRAPHITE_POWDER);

				entries.add(ModItems.IRON_POLE);
				entries.add(ModItems.ZIRCONIUM_TUBE);

				entries.add(ModItems.NATURAL_RUBBER);
				entries.add(ModItems.SYNTHETIC_RUBBER);
				entries.add(ModItems.PLASTIC_PELLET);
				entries.add(ModItems.PLASTIC_BAR);

				entries.add(ModBlocks.CREATIVE_GENERATOR); // Add creative generator to creative tab
				entries.add(ModBlocks.MEGA_CREATIVE_GENERATOR); // Add mega creative generator to creative tab
				entries.add(ModBlocks.CREATIVE_CONSUMER); // Add creative consumer to creative tab

				entries.add(ModBlocks.STEEL_BLOCK);

				entries.add(ModItems.STEEL_SHOVEL);
				entries.add(ModItems.STEEL_PICKAXE);
				entries.add(ModItems.STEEL_AXE);
				entries.add(ModItems.STEEL_HOE);
				entries.add(ModItems.STEEL_SWORD);

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
		ModItemTags.initialize();
		ModToolMaterials.initialize();
		ModRecipes.initialize();
		ModSounds.initialize();

		// Register server-side networking handlers
		ServerPlayNetworking.registerGlobalReceiver(ModNetworking.ToggleMiningPayload.ID, (payload, context) -> {
			var machinePos = payload.machinePos();
			context.server().execute(() -> {
				// Handle on the server thread
				if (context.player().getWorld().getBlockEntity(machinePos) instanceof QuarryBlockEntity quarry) {
					quarry.toggleMining();
					LOGGER.info("[SERVER] Toggled mining for quarry at " + machinePos + " by player " + context.player().getName().getString());
				} else if (context.player().getWorld().getBlockEntity(machinePos) instanceof DrillBlockEntity drill) {
					drill.toggleMining();
					LOGGER.info("[SERVER] Toggled mining for drill at " + machinePos + " by player " + context.player().getName().getString());
				}
			});
		});
		
		// Register quarry dimensions handler
		ServerPlayNetworking.registerGlobalReceiver(ModNetworking.QuarryDimensionsPayload.ID, (payload, context) -> {
			var quarryPos = payload.quarryPos();
			var width = payload.width();
			var length = payload.length();
			context.server().execute(() -> {
				// Handle on the server thread
				if (context.player().getWorld().getBlockEntity(quarryPos) instanceof QuarryBlockEntity quarry) {
					quarry.setMiningDimensions(width, length);
					LOGGER.info("[SERVER] Set quarry dimensions at " + quarryPos + " to " + width + "x" + length + " by player " + context.player().getName().getString());
					
					// Send sync packet back to the client
					ModNetworking.sendQuarryDimensionsSync(context.player(), width, length, quarryPos);
				}
			});
		});
		
		// Register player connection/disconnection handlers for debugging
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			LOGGER.info("[SERVER] Player joined: " + handler.player.getName().getString());

		});
		
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			LOGGER.info("[SERVER] Player left: " + handler.player.getName().getString());
		});
	}
}