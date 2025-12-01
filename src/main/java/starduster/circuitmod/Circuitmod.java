package starduster.circuitmod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.registry.FuelRegistryEvents;
import net.fabricmc.fabric.api.registry.StrippableBlockRegistry;
import net.minecraft.entity.Entity;
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
import starduster.circuitmod.advancement.TorchPlacementCriterion;
import starduster.circuitmod.item.ModItems;
import starduster.circuitmod.block.ModBlocks;
import starduster.circuitmod.block.entity.ModBlockEntities;
import starduster.circuitmod.block.entity.QuarryBlockEntity;
import starduster.circuitmod.block.entity.DrillBlockEntity;
import starduster.circuitmod.block.entity.LaserMiningDrillBlockEntity;
import starduster.circuitmod.block.entity.HologramTableBlockEntity;
import starduster.circuitmod.item.ModToolMaterials;
import starduster.circuitmod.network.ModNetworking;
import starduster.circuitmod.recipe.ModRecipes;
import starduster.circuitmod.screen.ModScreenHandlers;
import starduster.circuitmod.sound.ModSounds;
import starduster.circuitmod.util.CircuitmodRegistries;
import starduster.circuitmod.util.ModBlockTags;
import starduster.circuitmod.util.ModCreativeTabs;
import starduster.circuitmod.util.ModItemTags;
import net.minecraft.server.network.ServerPlayerEntity;
import starduster.circuitmod.satellite.SatelliteSystem;
import starduster.circuitmod.satellite.MiningOperationManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Circuitmod implements ModInitializer {
	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger("circuitmod");
	public static final String MOD_ID = "circuitmod";
	

	// Custom criterion for torch placement advancement
	public static final TorchPlacementCriterion TORCH_PLACED_IN_LUNA = net.minecraft.advancement.criterion.Criteria.register("circuitmod/torch_placed_luna", new TorchPlacementCriterion());

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

		
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		// Initialize our mod components
		ModItems.initialize();
		ModBlocks.initialize();
		ModBlockEntities.initialize();
		starduster.circuitmod.entity.ModEntityTypes.initialize();
		starduster.circuitmod.entity.ModEntities.initialize();
		ModScreenHandlers.initialize();
		ModNetworking.initialize();
		ModItemTags.initialize();
		ModBlockTags.initialize();
		ModToolMaterials.initialize();
		starduster.circuitmod.item.ModArmorMaterials.initialize();
		ModRecipes.initialize();
		ModSounds.initialize();
		starduster.circuitmod.effect.ModStatusEffects.initialize();
		starduster.circuitmod.item.PulseStickHandler.initialize();
		starduster.circuitmod.power.EnergyNetworkTickHandler.initialize();
		// starduster.circuitmod.worldgen.ModBiomes.initialize();
		ModCreativeTabs.initialize();
        CircuitmodRegistries.initialize();
		SatelliteSystem.initialize();
		starduster.circuitmod.expedition.ExpeditionSystem.initialize();
		
	
		
		
		// Initialize world generation
		starduster.circuitmod.worldgen.ModOreGeneration.generateOres();
		
		// Initialize tree components
		starduster.circuitmod.worldgen.tree.ModTrunkPlacerTypes.initialize();
		starduster.circuitmod.worldgen.tree.ModFoliagePlacerTypes.initialize();
		
		starduster.circuitmod.worldgen.ModTreeGeneration.generateTrees();
		
		// Initialize commands
		starduster.circuitmod.command.ModCommands.initialize();
		
		// Disable startup mode after initialization to reduce logging spam
		starduster.circuitmod.power.EnergyNetwork.setStartupMode(false);
		starduster.circuitmod.power.EnergyNetworkManager.setStartupMode(false);
		
		// Register world tick event for mining operations
		ServerTickEvents.END_WORLD_TICK.register(world -> {
			MiningOperationManager.tick(world);
		});
		
		
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
		
		// Register quarry reset height handler
		ServerPlayNetworking.registerGlobalReceiver(ModNetworking.QuarryResetHeightPayload.ID, (payload, context) -> {
			var quarryPos = payload.quarryPos();
			context.server().execute(() -> {
				// Handle on the server thread
				if (context.player().getWorld().getBlockEntity(quarryPos) instanceof QuarryBlockEntity quarry) {
					quarry.resetHeight();
					LOGGER.info("[SERVER] Reset quarry height at " + quarryPos + " by player " + context.player().getName().getString());
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
		
		
		
		
		// Register rocket spacebar input handler
		ServerPlayNetworking.registerGlobalReceiver(ModNetworking.RocketSpacebarInputPayload.ID, (payload, context) -> {
			int entityId = payload.entityId();
			boolean spacePressed = payload.spacePressed();
			context.server().execute(() -> {
				// Find the rocket entity by ID
				Entity entity = context.player().getWorld().getEntityById(entityId);
				if (entity instanceof starduster.circuitmod.entity.RocketEntity rocket) {
					rocket.setSpacePressed(spacePressed);
					LOGGER.info("[SERVER] Rocket spacebar input: {} for entity {}", spacePressed, entityId);
				}
			});
		});
		
		// Register hovercraft input handler
		ServerPlayNetworking.registerGlobalReceiver(ModNetworking.HovercraftInputPayload.ID, (payload, context) -> {
			int entityId = payload.entityId();
			boolean forward = payload.forward();
			boolean backward = payload.backward();
			boolean left = payload.left();
			boolean right = payload.right();
			boolean up = payload.up();
			boolean down = payload.down();
			boolean boost = payload.boost();
			context.server().execute(() -> {
				// Find the hovercraft entity by ID
				Entity entity = context.player().getWorld().getEntityById(entityId);
				if (entity instanceof starduster.circuitmod.entity.HovercraftEntity hovercraft) {
					// Verify the player is actually riding this hovercraft
					if (hovercraft.hasPassenger(context.player())) {
						hovercraft.setInputs(forward, backward, left, right, up, down, boost);
						// Debug logging removed to prevent spam
					} else {
						LOGGER.warn("[SERVER] Player {} tried to send input for hovercraft {} but is not riding it", 
							context.player().getName().getString(), entityId);
					}
				} else {
					LOGGER.warn("[SERVER] Hovercraft entity {} not found for input from player {}", 
						entityId, context.player().getName().getString());
				}
			});
		});
		
		// Register hologram table area handler
		ServerPlayNetworking.registerGlobalReceiver(ModNetworking.HologramAreaPayload.ID, (payload, context) -> {
			var tablePos = payload.tablePos();
			context.server().execute(() -> {
				if (context.player().getWorld().getBlockEntity(tablePos) instanceof HologramTableBlockEntity table) {
					if (payload.reset()) {
						table.resetToChunkArea();
					} else {
						table.updateRenderArea(payload.minX(), payload.maxX(), payload.minZ(), payload.maxZ(), payload.minY());
					}
				}
			});
		});
		
		// Register missile control coordinate update handler
		ServerPlayNetworking.registerGlobalReceiver(ModNetworking.MissileControlUpdatePayload.ID, (payload, context) -> {
			var controlBlockPos = payload.controlBlockPos();
			context.server().execute(() -> {
				if (context.player().getWorld().getBlockEntity(controlBlockPos) instanceof starduster.circuitmod.block.entity.MissileControlBlockEntity controlBlock) {
					controlBlock.updateTargetCoordinates(payload.targetX(), payload.targetY(), payload.targetZ());
					LOGGER.info("[SERVER] Updated missile control block at {} with target ({}, {}, {})", 
						controlBlockPos, payload.targetX(), payload.targetY(), payload.targetZ());
				}
			});
		});
		
		// Register missile fire command handler
		ServerPlayNetworking.registerGlobalReceiver(ModNetworking.MissileFirePayload.ID, (payload, context) -> {
			var controlBlockPos = payload.controlBlockPos();
			context.server().execute(() -> {
				if (context.player().getWorld().getBlockEntity(controlBlockPos) instanceof starduster.circuitmod.block.entity.MissileControlBlockEntity controlBlock) {
					boolean success = controlBlock.fireMissile();
					if (success) {
						LOGGER.info("[SERVER] Fired missile from control block at {}", controlBlockPos);
					} else {
						LOGGER.warn("[SERVER] Failed to fire missile from control block at {} (no missile attached)", controlBlockPos);
					}
				}
			});
		});
		
		// Register satellite control command handler
		ServerPlayNetworking.registerGlobalReceiver(ModNetworking.SatelliteCommandPayload.ID, (payload, context) -> {
			var controlBlockPos = payload.controlBlockPos();
			String command = payload.command();
			ServerPlayerEntity player = context.player();
			context.server().execute(() -> {
				if (player.getWorld().getBlockEntity(controlBlockPos) instanceof starduster.circuitmod.block.entity.SatelliteControlBlockEntity satelliteControl) {
					satelliteControl.executeCommand(command);
					LOGGER.info("[SERVER] Executed satellite command '{}' for control block at {}", command, controlBlockPos);
					
					// Sync output back to the player
					java.util.List<String> output = satelliteControl.getOutputLines();
					ModNetworking.sendSatelliteOutputSync(player, controlBlockPos, output);
				}
			});
		});
		
		// Register expedition control command handler
		ServerPlayNetworking.registerGlobalReceiver(ModNetworking.ExpeditionCommandPayload.ID, (payload, context) -> {
			var controlBlockPos = payload.controlBlockPos();
			String command = payload.command();
			ServerPlayerEntity player = context.player();
			context.server().execute(() -> {
				if (player.getWorld().getBlockEntity(controlBlockPos) instanceof starduster.circuitmod.block.entity.ExpeditionControlBlockEntity expeditionControl) {
					expeditionControl.executeCommand(command, player.getUuid());
					LOGGER.info("[SERVER] Executed expedition command '{}' for control block at {}", command, controlBlockPos);
					
					// Sync output back to the player
					java.util.List<String> output = expeditionControl.getOutputLines();
					int fuel = expeditionControl.getStoredFuel();
					boolean monitor = expeditionControl.isMonitorMode();
					ModNetworking.sendExpeditionOutputSync(player, controlBlockPos, output, fuel, monitor);
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