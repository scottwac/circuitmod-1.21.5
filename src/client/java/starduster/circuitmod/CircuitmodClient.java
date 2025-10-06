package starduster.circuitmod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.minecraft.client.MinecraftClient;
import net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.DimensionEffects;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import starduster.circuitmod.block.ModBlocks;
import starduster.circuitmod.block.entity.ModBlockEntities;
import starduster.circuitmod.client.render.QuarryBlockEntityRenderer;
import starduster.circuitmod.client.render.DrillBlockEntityRenderer;
import starduster.circuitmod.client.render.LaserMiningDrillBlockEntityRenderer;
import starduster.circuitmod.client.render.ConstructorBlockEntityRenderer;
import starduster.circuitmod.client.render.HovercraftEntityRenderer;
import starduster.circuitmod.client.HovercraftInputHandler;
import starduster.circuitmod.client.render.sky.LunaSkyRenderer;
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
import starduster.circuitmod.screen.FluidTankScreen;
import starduster.circuitmod.screen.SortingPipeScreen;
import starduster.circuitmod.entity.ModEntityTypes;

public class CircuitmodClient implements ClientModInitializer {
	
    // Identifier used by dimension_type effects mapping
    @SuppressWarnings("unused")
    private static final Identifier NO_MOON_ID = Identifier.of(Circuitmod.MOD_ID, "no_moon");
    private static final Identifier LUNA_ID = Identifier.of(Circuitmod.MOD_ID, "luna");

    public static final RegistryKey<World> LUNA_KEY =
            RegistryKey.of(RegistryKeys.WORLD, LUNA_ID);

	@Override
	public void onInitializeClient() {
		Circuitmod.LOGGER.info("[CLIENT] Initializing CircuitmodClient");
		
		// Register GeckoLib armor renderers for split-source compatibility
		registerArmorRenderers();

		// Set block render layers
		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.TESLA_COIL, RenderLayer.getCutout());
		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.ELECTRIC_CARPET, RenderLayer.getTranslucent());
		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.ITEM_PIPE, RenderLayer.getTranslucent());
		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.OUTPUT_PIPE, RenderLayer.getTranslucent());
		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.SORTING_PIPE, RenderLayer.getTranslucent());
		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.LASER_MINING_DRILL_BLOCK, RenderLayer.getTranslucent());

		// Register screens
		HandledScreens.register(ModScreenHandlers.QUARRY_SCREEN_HANDLER, QuarryScreen::new);
		HandledScreens.register(ModScreenHandlers.DRILL_SCREEN_HANDLER, DrillScreen::new);
		HandledScreens.register(ModScreenHandlers.LASER_MINING_DRILL_SCREEN_HANDLER, LaserMiningDrillScreen::new);
		HandledScreens.register(ModScreenHandlers.SORTING_PIPE_SCREEN_HANDLER, SortingPipeScreen::new);
		HandledScreens.register(ModScreenHandlers.BLOOMERY_SCREEN_HANDLER, BloomeryScreen::new);
		HandledScreens.register(ModScreenHandlers.GENERATOR_SCREEN_HANDLER, GeneratorScreen::new);
		HandledScreens.register(ModScreenHandlers.MASS_FABRICATOR_SCREEN_HANDLER, MassFabricatorScreen::new);
		HandledScreens.register(ModScreenHandlers.REACTOR_SCREEN_HANDLER, ReactorScreen::new);
		HandledScreens.register(ModScreenHandlers.BLUEPRINT_DESK_SCREEN_HANDLER, BlueprintDeskScreen::new);
		HandledScreens.register(ModScreenHandlers.CONSTRUCTOR_SCREEN_HANDLER, ConstructorScreen::new);
		HandledScreens.register(ModScreenHandlers.CRUSHER_SCREEN_HANDLER, CrusherScreen::new);
		HandledScreens.register(ModScreenHandlers.BATTERY_SCREEN_HANDLER, BatteryScreen::new);
		HandledScreens.register(ModScreenHandlers.FLUID_TANK_SCREEN_HANDLER, FluidTankScreen::new);
		HandledScreens.register(ModScreenHandlers.ELECTRIC_FURNACE_SCREEN_HANDLER, ElectricFurnaceScreen::new);
		HandledScreens.register(ModScreenHandlers.XP_GENERATOR_SCREEN_HANDLER, XpGeneratorScreen::new);

		// Register block entity renderers
        BlockEntityRendererFactories.register(ModBlockEntities.QUARRY_BLOCK_ENTITY, QuarryBlockEntityRenderer::new);
        BlockEntityRendererFactories.register(ModBlockEntities.FLUID_TANK_BLOCK_ENTITY, starduster.circuitmod.client.render.FluidTankBlockEntityRenderer::new);
		BlockEntityRendererFactories.register(ModBlockEntities.DRILL_BLOCK_ENTITY, DrillBlockEntityRenderer::new);
        BlockEntityRendererFactories.register(ModBlockEntities.LASER_MINING_DRILL_BLOCK_ENTITY, LaserMiningDrillBlockEntityRenderer::new);
		BlockEntityRendererFactories.register(ModBlockEntities.CONSTRUCTOR_BLOCK_ENTITY, ConstructorBlockEntityRenderer::new);
		
		// Register entity renderers
		EntityRendererRegistry.register(ModEntityTypes.MINING_EXPLOSIVE, FlyingItemEntityRenderer::new);
		EntityRendererRegistry.register(ModEntityTypes.HOVERCRAFT, HovercraftEntityRenderer::new);

		// Register color providers for biome-based tinting
		registerColorProviders();
		
		// Initialize client networking
		starduster.circuitmod.network.ClientNetworking.initialize();
		
		// Initialize hovercraft input handler
		HovercraftInputHandler.initialize();

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
		
        // Force black sky background for our dimension - use NORMAL sky type to allow custom sky renderers
        DimensionRenderingRegistry.registerDimensionEffects(LUNA_ID,
            new DimensionEffects(
                192,                     // clouds height
                false,                    // alternate sky color (use getSkyColor below)
                DimensionEffects.SkyType.NORMAL,  // Use NORMAL type to allow custom sky renderers
                false,                   // brightenLighting
                true)                    // darkened (dim ambient lighting)
        {
            @Override
            public int getSkyColor(float skyAngle) {
                return 0x000000; // Always black sky
            }

            @Override
            public Vec3d adjustFogColor(Vec3d color, float sunHeight) {
                return color; // Keep fog unchanged
            }

            @Override
            public boolean useThickFog(int camX, int camY) {
                return false;
            }
        });
        // Disable clouds for our dimension (draw nothing)
        DimensionRenderingRegistry.registerCloudRenderer(
                net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, Identifier.of("circuitmod", "luna")),
            context -> {}
        );

        // Don't register the sky renderer through Fabric - we'll handle it with our mixin
        Circuitmod.LOGGER.info("[CLIENT] Sky rendering will be handled by WorldRendererSkyMixin");

        Circuitmod.LOGGER.info("[CLIENT] CircuitmodClient initialization complete");
	}
	
	/**
	 * Register GeckoLib armor renderers using the split-source pattern
	 */
	private static void registerArmorRenderers() {
		// Set the render providers for each emu suit armor piece
		((starduster.circuitmod.item.EmuSuitArmorItem) starduster.circuitmod.item.ModItems.EMU_SUIT_HELMET).renderProviderHolder.setValue(
			new software.bernie.geckolib.animatable.client.GeoRenderProvider() {
				private starduster.circuitmod.client.renderer.armor.EmuSuitArmorRenderer<?> renderer;

				@org.jetbrains.annotations.Nullable
				@Override
				public <S extends net.minecraft.client.render.entity.state.BipedEntityRenderState> software.bernie.geckolib.renderer.GeoArmorRenderer<?, ?> getGeoArmorRenderer(
						@org.jetbrains.annotations.Nullable S renderState,
						net.minecraft.item.ItemStack itemStack,
						net.minecraft.entity.EquipmentSlot equipmentSlot,
						net.minecraft.client.render.entity.equipment.EquipmentModel.LayerType type,
						@org.jetbrains.annotations.Nullable net.minecraft.client.render.entity.model.BipedEntityModel<S> original) {
					if (this.renderer == null)
						this.renderer = new starduster.circuitmod.client.renderer.armor.EmuSuitArmorRenderer<>();
					return this.renderer;
				}
			}
		);
		
		((starduster.circuitmod.item.EmuSuitArmorItem) starduster.circuitmod.item.ModItems.EMU_SUIT_CHESTPLATE).renderProviderHolder.setValue(
			new software.bernie.geckolib.animatable.client.GeoRenderProvider() {
				private starduster.circuitmod.client.renderer.armor.EmuSuitArmorRenderer<?> renderer;

				@org.jetbrains.annotations.Nullable
				@Override
				public <S extends net.minecraft.client.render.entity.state.BipedEntityRenderState> software.bernie.geckolib.renderer.GeoArmorRenderer<?, ?> getGeoArmorRenderer(
						@org.jetbrains.annotations.Nullable S renderState,
						net.minecraft.item.ItemStack itemStack,
						net.minecraft.entity.EquipmentSlot equipmentSlot,
						net.minecraft.client.render.entity.equipment.EquipmentModel.LayerType type,
						@org.jetbrains.annotations.Nullable net.minecraft.client.render.entity.model.BipedEntityModel<S> original) {
					if (this.renderer == null)
						this.renderer = new starduster.circuitmod.client.renderer.armor.EmuSuitArmorRenderer<>();
					return this.renderer;
				}
			}
		);
		
		((starduster.circuitmod.item.EmuSuitArmorItem) starduster.circuitmod.item.ModItems.EMU_SUIT_LEGGINGS).renderProviderHolder.setValue(
			new software.bernie.geckolib.animatable.client.GeoRenderProvider() {
				private starduster.circuitmod.client.renderer.armor.EmuSuitArmorRenderer<?> renderer;

				@org.jetbrains.annotations.Nullable
				@Override
				public <S extends net.minecraft.client.render.entity.state.BipedEntityRenderState> software.bernie.geckolib.renderer.GeoArmorRenderer<?, ?> getGeoArmorRenderer(
						@org.jetbrains.annotations.Nullable S renderState,
						net.minecraft.item.ItemStack itemStack,
						net.minecraft.entity.EquipmentSlot equipmentSlot,
						net.minecraft.client.render.entity.equipment.EquipmentModel.LayerType type,
						@org.jetbrains.annotations.Nullable net.minecraft.client.render.entity.model.BipedEntityModel<S> original) {
					if (this.renderer == null)
						this.renderer = new starduster.circuitmod.client.renderer.armor.EmuSuitArmorRenderer<>();
					return this.renderer;
				}
			}
		);
		
		((starduster.circuitmod.item.EmuSuitArmorItem) starduster.circuitmod.item.ModItems.EMU_SUIT_BOOTS).renderProviderHolder.setValue(
			new software.bernie.geckolib.animatable.client.GeoRenderProvider() {
				private starduster.circuitmod.client.renderer.armor.EmuSuitArmorRenderer<?> renderer;

				@org.jetbrains.annotations.Nullable
				@Override
				public <S extends net.minecraft.client.render.entity.state.BipedEntityRenderState> software.bernie.geckolib.renderer.GeoArmorRenderer<?, ?> getGeoArmorRenderer(
						@org.jetbrains.annotations.Nullable S renderState,
						net.minecraft.item.ItemStack itemStack,
						net.minecraft.entity.EquipmentSlot equipmentSlot,
						net.minecraft.client.render.entity.equipment.EquipmentModel.LayerType type,
						@org.jetbrains.annotations.Nullable net.minecraft.client.render.entity.model.BipedEntityModel<S> original) {
					if (this.renderer == null)
						this.renderer = new starduster.circuitmod.client.renderer.armor.EmuSuitArmorRenderer<>();
					return this.renderer;
				}
			}
		);
	}
	
	/**
	 * Register color providers for blocks that should have biome-based tinting
	 */
	private static void registerColorProviders() {
		// Register lunar regolith color provider based on biome
		ColorProviderRegistry.BLOCK.register((state, view, pos, tintIndex) -> {
			if (view != null && pos != null) {
				try {
					// Try to get the world and biome through the client
					MinecraftClient client = MinecraftClient.getInstance();
					if (client.world != null) {
						var biomeRegistry = client.world.getRegistryManager().getOrThrow(RegistryKeys.BIOME);
						var biome = client.world.getBiome(pos);
						var biomeId = biomeRegistry.getId(biome.value());
						
						if (biomeId != null) {
							String biomeName = biomeId.getPath();
							
							// Return different colors based on biome name
							switch (biomeName) {
								case "lunar_maria":
									return 0xD6D6D6; // Light gray - smooth plains (mare)
								case "lunar_terrae":
									return 0xF7F7F7; // Medium gray - highland terrain
								case "lunar_rocky_maria":
									return 0xD6D6D6; // Light gray with rocky tint
								case "lunar_rocky_terrae":
									return 0xF7F7F7; // Darker gray - rocky highlands
								case "lunar_glacies":
									return 0xFEFFFF; // Very light gray with blue tint - icy areas
								case "lunar_rocky_glacies":
									return 0xFEFFFF; // Light gray with blue tint - rocky icy areas
							}
						}
					}
				} catch (Exception e) {
					// If there's any issue getting the biome, fall back to default
					// Silently ignore to avoid spam in logs
				}
			}
			
			// Default color (base regolith)
			return 0xF7F7F7;
		}, ModBlocks.LUNAR_REGOLITH);
	}
}