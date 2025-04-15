package starduster.circuitmod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.render.RenderLayer;
import starduster.circuitmod.block.ModBlocks;
import starduster.circuitmod.block.entity.ModBlockEntities;
import starduster.circuitmod.client.render.QuarryBlockEntityRenderer;
import starduster.circuitmod.screen.DrillScreen;
import starduster.circuitmod.screen.ModScreenHandlers;
import starduster.circuitmod.screen.QuarryScreen;
import starduster.circuitmod.screen.BloomeryScreen;

public class CircuitmodClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		Circuitmod.LOGGER.info("[CLIENT] Initializing CircuitmodClient");

		// Set block render layers
		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.TESLA_COIL, RenderLayer.getCutout());
		
		// Register screens
		HandledScreens.register(ModScreenHandlers.QUARRY_SCREEN_HANDLER, QuarryScreen::new);
		HandledScreens.register(ModScreenHandlers.DRILL_SCREEN_HANDLER, DrillScreen::new);
		HandledScreens.register(ModScreenHandlers.BLOOMERY_SCREEN_HANDLER, BloomeryScreen::new);
		
		// Register block entity renderers
		BlockEntityRendererRegistry.register(ModBlockEntities.QUARRY_BLOCK_ENTITY, QuarryBlockEntityRenderer::new);
		
		// Initialize client networking
		starduster.circuitmod.network.ClientNetworking.initialize();
		
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