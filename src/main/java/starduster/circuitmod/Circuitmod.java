package starduster.circuitmod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.registry.FuelRegistryEvents;
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
import starduster.circuitmod.block.entity.LaserMiningDrillBlockEntity;
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

		FuelRegistryEvents.BUILD.register((builder, context) -> {
			//builder.add(Items.COAL, context.baseSmeltTime() / 8);
		});
		
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
				entries.add(ModBlocks.XP_GENERATOR);
				entries.add(ModBlocks.GENERATOR);
				entries.add(ModBlocks.SOLAR_PANEL);
				entries.add(ModBlocks.TESLA_COIL);
				            entries.add(ModBlocks.DRILL_BLOCK);
            entries.add(ModBlocks.LASER_MINING_DRILL_BLOCK);
				entries.add(ModBlocks.QUARRY_BLOCK);
				entries.add(ModBlocks.CONSTRUCTOR_BLOCK);
				entries.add(ModBlocks.BLUEPRINT_DESK);
				entries.add(ModBlocks.FLUID_TANK);

				entries.add(ModBlocks.ELECTRIC_CARPET);
				entries.add(ModBlocks.ITEM_PIPE);
				entries.add(ModBlocks.OUTPUT_PIPE);
				entries.add(ModBlocks.SORTING_PIPE);
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
				entries.add(ModItems.FUEL_ROD);
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

				// Add blank blueprint item
				entries.add(ModItems.BLANK_BLUEPRINT);

				// Add consumable items
				entries.add(ModItems.STIMULANTS);
				entries.add(ModItems.MINING_EXPLOSIVE);
				
				// Add movement items  
				entries.add(ModItems.PULSE_STICK);

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
		starduster.circuitmod.entity.ModEntityTypes.initialize();
		ModScreenHandlers.initialize();
		ModNetworking.initialize();
		ModItemTags.initialize();
		ModToolMaterials.initialize();
		ModRecipes.initialize();
		ModSounds.initialize();
		starduster.circuitmod.effect.ModStatusEffects.initialize();
		starduster.circuitmod.item.PulseStickHandler.initialize();
		// starduster.circuitmod.worldgen.ModBiomes.initialize();
		
		// Initialize world generation
		starduster.circuitmod.worldgen.ModOreGeneration.generateOres();
		
		// Initialize tree components
		starduster.circuitmod.worldgen.tree.ModTrunkPlacerTypes.initialize();
		starduster.circuitmod.worldgen.tree.ModFoliagePlacerTypes.initialize();
		
		// starduster.circuitmod.worldgen.ModTreeGeneration.generateTrees();
		
		
		// Initialize commands
		starduster.circuitmod.command.ModCommands.initialize();

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
				} else if (context.player().getWorld().getBlockEntity(machinePos) instanceof LaserMiningDrillBlockEntity laserDrill) {
					laserDrill.toggleMining();
					LOGGER.info("[SERVER] Toggled mining for laser mining drill at " + machinePos + " by player " + context.player().getName().getString());
				}
			});
		});
		
		// Register XP collection handler
		ServerPlayNetworking.registerGlobalReceiver(ModNetworking.CollectXpPayload.ID, (payload, context) -> {
			var xpGeneratorPos = payload.xpGeneratorPos();
			context.server().execute(() -> {
				// Handle on the server thread
				if (context.player().getWorld().getBlockEntity(xpGeneratorPos) instanceof starduster.circuitmod.block.entity.XpGeneratorBlockEntity xpGenerator) {
					xpGenerator.collectXp(context.player());
					LOGGER.info("[SERVER] Collected XP from generator at " + xpGeneratorPos + " for player " + context.player().getName().getString());
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
		
		// Register drill dimensions handler
		ServerPlayNetworking.registerGlobalReceiver(ModNetworking.DrillDimensionsPayload.ID, (payload, context) -> {
			var drillPos = payload.drillPos();
			var height = payload.height();
			var width = payload.width();
			context.server().execute(() -> {
				// Handle on the server thread
				if (context.player().getWorld().getBlockEntity(drillPos) instanceof DrillBlockEntity drill) {
					drill.setMiningDimensions(height, width);
					LOGGER.info("[SERVER] Set drill dimensions at " + drillPos + " to " + height + "x" + width + " by player " + context.player().getName().getString());
					
					// Send sync packet back to the client
					ModNetworking.sendDrillDimensionsUpdate(context.player(), height, width);
				} else if (context.player().getWorld().getBlockEntity(drillPos) instanceof LaserMiningDrillBlockEntity laserDrill) {
					// For laser mining drill, use the height parameter as depth
					laserDrill.setMiningDepth(height);
					LOGGER.info("[SERVER] Set laser mining drill depth at " + drillPos + " to " + height + " by player " + context.player().getName().getString());
				}
			});
		});
		
		// Register laser mining drill depth handler
		ServerPlayNetworking.registerGlobalReceiver(ModNetworking.LaserDrillDepthPayload.ID, (payload, context) -> {
			var drillPos = payload.drillPos();
			var depth = payload.depth();
			context.server().execute(() -> {
				// Handle on the server thread
				if (context.player().getWorld().getBlockEntity(drillPos) instanceof LaserMiningDrillBlockEntity laserDrill) {
					laserDrill.setMiningDepth(depth);
					LOGGER.info("[SERVER] Set laser mining drill depth at " + drillPos + " to " + depth + " by player " + context.player().getName().getString());
					
					// Send sync packet back to the client
					ModNetworking.sendLaserDrillDepthUpdate(context.player(), drillPos, depth);
				}
			});
		});
		
		// Register constructor building handler
		ServerPlayNetworking.registerGlobalReceiver(ModNetworking.ConstructorBuildingPayload.ID, (payload, context) -> {
			var constructorPos = payload.constructorPos();
			context.server().execute(() -> {
				// Handle on the server thread
				if (context.player().getWorld().getBlockEntity(constructorPos) instanceof starduster.circuitmod.block.entity.ConstructorBlockEntity constructor) {
					constructor.toggleBuilding();
					LOGGER.info("[SERVER] Toggled building for constructor at " + constructorPos + " by player " + context.player().getName().getString());
				}
			});
		});

		// Register constructor transform (offset/rotation) handler
		ServerPlayNetworking.registerGlobalReceiver(ModNetworking.ConstructorTransformPayload.ID, (payload, context) -> {
			var constructorPos = payload.constructorPos();
			int forward = payload.forwardOffset();
			int right = payload.rightOffset();
			int up = payload.upOffset();
			int rotation = payload.rotation();
			context.server().execute(() -> {
				if (context.player().getWorld().getBlockEntity(constructorPos) instanceof starduster.circuitmod.block.entity.ConstructorBlockEntity constructor) {
					constructor.setForwardOffset(forward);
					constructor.setRightOffset(right);
					constructor.setUpOffset(up);
					constructor.setBlueprintRotation(rotation);
					constructor.markDirty();

					// Recompute build positions and send to nearby players
					java.util.List<net.minecraft.util.math.BlockPos> buildPositions = constructor.getBlueprintBuildPositions();
					for (net.minecraft.server.network.ServerPlayerEntity player : context.player().getServer().getPlayerManager().getPlayerList()) {
						if (player.getWorld() == constructor.getWorld() && player.getBlockPos().getSquaredDistance(constructorPos) <= 64 * 64) {
							starduster.circuitmod.network.ModNetworking.sendConstructorBuildPositionsSync(player, constructorPos, buildPositions);
						}
					}

					LOGGER.info("[SERVER] Updated constructor transform at {} to off({},{},{}) rot:{} by player {}", constructorPos, forward, right, up, rotation, context.player().getName().getString());
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