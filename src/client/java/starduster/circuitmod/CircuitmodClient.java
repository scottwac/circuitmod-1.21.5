package starduster.circuitmod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import starduster.circuitmod.block.ModBlocks;
import starduster.circuitmod.block.entity.ModBlockEntities;
import starduster.circuitmod.client.render.QuarryBlockEntityRenderer;
import starduster.circuitmod.client.render.DrillBlockEntityRenderer;
import starduster.circuitmod.client.render.LaserMiningDrillBlockEntityRenderer;
import starduster.circuitmod.client.render.ConstructorBlockEntityRenderer;
import starduster.circuitmod.screen.DrillScreen;
import starduster.circuitmod.screen.LaserMiningDrillScreen;
import starduster.circuitmod.screen.ModScreenHandlers;
import starduster.circuitmod.screen.QuarryScreen;
import starduster.circuitmod.screen.BloomeryScreen;
import starduster.circuitmod.screen.GeneratorScreen;
import starduster.circuitmod.screen.BlueprintDeskScreen;
import starduster.circuitmod.screen.ConstructorScreen;
import starduster.circuitmod.screen.MassFabricatorScreen;
import starduster.circuitmod.screen.ReactorScreen;
import starduster.circuitmod.screen.CrusherScreen;
import starduster.circuitmod.screen.BatteryScreen;
import starduster.circuitmod.screen.ElectricFurnaceScreen;
import starduster.circuitmod.screen.XpGeneratorScreen;

public class CircuitmodClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		Circuitmod.LOGGER.info("[CLIENT] Initializing CircuitmodClient");

		// Set block render layers
		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.TESLA_COIL, RenderLayer.getCutout());
		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.ELECTRIC_CARPET, RenderLayer.getTranslucent());
		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.ITEM_PIPE, RenderLayer.getTranslucent());
		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.LASER_MINING_DRILL_BLOCK, RenderLayer.getTranslucent());

		// Register screens
		HandledScreens.register(ModScreenHandlers.QUARRY_SCREEN_HANDLER, QuarryScreen::new);
		        HandledScreens.register(ModScreenHandlers.DRILL_SCREEN_HANDLER, DrillScreen::new);
        HandledScreens.register(ModScreenHandlers.LASER_MINING_DRILL_SCREEN_HANDLER, LaserMiningDrillScreen::new);
		HandledScreens.register(ModScreenHandlers.BLOOMERY_SCREEN_HANDLER, BloomeryScreen::new);
		HandledScreens.register(ModScreenHandlers.GENERATOR_SCREEN_HANDLER, GeneratorScreen::new);
		HandledScreens.register(ModScreenHandlers.MASS_FABRICATOR_SCREEN_HANDLER, MassFabricatorScreen::new);
		HandledScreens.register(ModScreenHandlers.REACTOR_SCREEN_HANDLER, ReactorScreen::new);
		HandledScreens.register(ModScreenHandlers.BLUEPRINT_DESK_SCREEN_HANDLER, BlueprintDeskScreen::new);
		HandledScreens.register(ModScreenHandlers.CONSTRUCTOR_SCREEN_HANDLER, ConstructorScreen::new);
		HandledScreens.register(ModScreenHandlers.CRUSHER_SCREEN_HANDLER, CrusherScreen::new);
		HandledScreens.register(ModScreenHandlers.BATTERY_SCREEN_HANDLER, BatteryScreen::new);
		HandledScreens.register(ModScreenHandlers.ELECTRIC_FURNACE_SCREEN_HANDLER, ElectricFurnaceScreen::new);
		HandledScreens.register(ModScreenHandlers.XP_GENERATOR_SCREEN_HANDLER, XpGeneratorScreen::new);

		// Register block entity renderers
		BlockEntityRendererFactories.register(ModBlockEntities.QUARRY_BLOCK_ENTITY, QuarryBlockEntityRenderer::new);
		        BlockEntityRendererFactories.register(ModBlockEntities.DRILL_BLOCK_ENTITY, DrillBlockEntityRenderer::new);
        BlockEntityRendererFactories.register(ModBlockEntities.LASER_MINING_DRILL_BLOCK_ENTITY, LaserMiningDrillBlockEntityRenderer::new);
		BlockEntityRendererFactories.register(ModBlockEntities.CONSTRUCTOR_BLOCK_ENTITY, ConstructorBlockEntityRenderer::new);
		
		// Initialize client networking
		starduster.circuitmod.network.ClientNetworking.initialize();
		
		// Initialize client network animator
		starduster.circuitmod.network.ClientNetworkAnimator.initialize();
		
		// Register client connection/disconnection handlers for debugging
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			Circuitmod.LOGGER.info("[CLIENT] Connected to server! Local player: " + 
				(client.player != null ? client.player.getName().getString() : "unknown"));
		});
		
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			Circuitmod.LOGGER.info("[CLIENT] Disconnected from server");
		});
		
		Circuitmod.LOGGER.info("[CLIENT] CircuitmodClient initialization complete");
	}
}