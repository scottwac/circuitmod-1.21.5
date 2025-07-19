package starduster.circuitmod.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.math.BlockPos;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.block.entity.QuarryBlockEntity;
import starduster.circuitmod.block.entity.DrillBlockEntity;
import starduster.circuitmod.block.entity.LaserMiningDrillBlockEntity;
import starduster.circuitmod.screen.QuarryScreenHandler;
import starduster.circuitmod.screen.DrillScreenHandler;
import starduster.circuitmod.screen.ConstructorScreenHandler;
import starduster.circuitmod.screen.QuarryScreen;
import starduster.circuitmod.screen.DrillScreen;
import starduster.circuitmod.screen.LaserMiningDrillScreen;

import starduster.circuitmod.block.entity.ConstructorBlockEntity;
import net.minecraft.item.ItemStack;
import java.util.Map;
import java.util.List;

public class ClientNetworking {
    /**
     * Initialize client-side networking
     */
    public static void initialize() {

        // Register handler for quarry mining progress updates
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.QuarryMiningProgressPayload.ID, (payload, context) -> {
            // Extract data from the payload
            int miningProgress = payload.miningProgress();
            var miningPos = payload.miningPos();
            var quarryPos = payload.quarryPos();
            
            // Process on the game thread
            context.client().execute(() -> {
                // If the player's world is loaded
                if (context.client().world != null) {
                    // Try to get the quarry block entity at the position
                    if (context.client().world.getBlockEntity(quarryPos) instanceof QuarryBlockEntity quarry) {
                        
                        // Update the mining progress and position directly
                        quarry.setMiningProgressFromNetwork(miningProgress, miningPos);
                        
                        Circuitmod.LOGGER.info("[CLIENT] Received quarry mining progress update: " + miningProgress + "% at " + miningPos + " for quarry at " + quarryPos);
                    }
                    // Also try to get the drill block entity at the position
                    else if (context.client().world.getBlockEntity(quarryPos) instanceof starduster.circuitmod.block.entity.DrillBlockEntity drill) {
                        
                        // Update the mining progress and position directly
                        drill.setMiningProgressFromNetwork(miningProgress, miningPos);
                        
                        Circuitmod.LOGGER.info("[CLIENT] Received drill mining progress update: " + miningProgress + "% at " + miningPos + " for drill at " + quarryPos);
                    }
                }
            });
        });
        
        // Register handler for mining enabled status updates
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.MiningEnabledStatusPayload.ID, (payload, context) -> {
            // Extract data from the payload
            boolean enabled = payload.enabled();
            var machinePos = payload.machinePos();
            
            // Process on the game thread
            context.client().execute(() -> {
                // If the player's world is loaded
                if (context.client().world != null) {
                    // Try to get the quarry block entity at the position
                    if (context.client().world.getBlockEntity(machinePos) instanceof QuarryBlockEntity quarry) {
                        // Update the mining enabled state directly
                        quarry.setMiningEnabledFromNetwork(enabled);
                        Circuitmod.LOGGER.info("[CLIENT] Updated mining enabled status: " + enabled);
                    }
                    // Also try to get the drill block entity at the position
                    else if (context.client().world.getBlockEntity(machinePos) instanceof starduster.circuitmod.block.entity.DrillBlockEntity drill) {
                        // Update the mining enabled state directly
                        drill.setMiningEnabledFromNetwork(enabled);
                        Circuitmod.LOGGER.info("[CLIENT] Updated drill mining enabled status: " + enabled);
                    }
                }
                
                // Also update the screen handler if the screen is open
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null && client.player.currentScreenHandler instanceof QuarryScreenHandler handler) {
                    // Update the property delegate through the new method
                    handler.updateMiningEnabledFromNetwork(enabled);
                    Circuitmod.LOGGER.info("[CLIENT] Updated QuarryScreenHandler mining enabled property to: {}", enabled);
                } else if (client.player != null && client.player.currentScreenHandler instanceof DrillScreenHandler handler) {
                    // Update the property delegate through the new method
                    handler.updateMiningEnabledFromNetwork(enabled);
                    Circuitmod.LOGGER.info("[CLIENT] Updated DrillScreenHandler mining enabled property to: {}", enabled);
                }
            });
        });
        
        // Register handler for quarry dimensions sync updates
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.QuarryDimensionsSyncPayload.ID, (payload, context) -> {
            // Extract data from the payload
            int width = payload.width();
            int length = payload.length();
            var quarryPos = payload.quarryPos();
            
            // Process on the game thread
            context.client().execute(() -> {
                // If the player's world is loaded
                if (context.client().world != null) {
                    // Try to get the quarry block entity at the position
                    if (context.client().world.getBlockEntity(quarryPos) instanceof QuarryBlockEntity quarry) {
                        // Update the mining dimensions directly
                        quarry.setMiningDimensionsFromNetwork(width, length);
                        Circuitmod.LOGGER.info("[CLIENT] Received quarry dimensions sync: {}x{} for quarry at {}", width, length, quarryPos);
                    }
                }
                
                // Also update the screen if it's open
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.currentScreen instanceof QuarryScreen quarryScreen) {
                    quarryScreen.updateTextFields(width, length);
                    Circuitmod.LOGGER.info("[CLIENT] Updated quarry screen text fields with dimensions: {}x{}", width, length);
                }
            });
        });
        
        // Register handler for drill mining progress updates
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.DrillMiningProgressPayload.ID, (payload, context) -> {
            // Extract data from the payload
            int miningProgress = payload.miningProgress();
            var miningPos = payload.miningPos();
            
            // Process on the game thread
            context.client().execute(() -> {
                // If the player's world is loaded
                if (context.client().world != null) {
                    // Try to get the drill block entity at the position
                    if (context.client().world.getBlockEntity(miningPos) instanceof DrillBlockEntity drill) {
                        // Update the mining progress and position directly
                        drill.setMiningProgressFromNetwork(miningProgress, miningPos);
                        Circuitmod.LOGGER.info("[CLIENT] Received drill mining progress update: {}% at {}", miningProgress, miningPos);
                    }
                }
            });
        });
        
        // Register handler for drill mining enabled status updates
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.DrillMiningEnabledPayload.ID, (payload, context) -> {
            // Extract data from the payload
            boolean enabled = payload.enabled();
            
            // Process on the game thread
            context.client().execute(() -> {
                // Also update the screen handler if the screen is open
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null && client.player.currentScreenHandler instanceof DrillScreenHandler handler) {
                    // Update the property delegate through the new method
                    handler.updateMiningEnabledFromNetwork(enabled);
                    Circuitmod.LOGGER.info("[CLIENT] Updated DrillScreenHandler mining enabled property to: {}", enabled);
                }
            });
        });
        
        // Register handler for drill dimensions sync updates
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.DrillDimensionsSyncPayload.ID, (payload, context) -> {
            // Extract data from the payload
            int height = payload.height();
            int width = payload.width();
            
            // Process on the game thread
            context.client().execute(() -> {
                // If the player's world is loaded
                if (context.client().world != null) {
                    // Try to get the drill block entity at the position
                    // Note: We need to find the drill by searching since we don't have the position
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client.player != null) {
                        BlockPos playerPos = client.player.getBlockPos();
                        for (int x = -10; x <= 10; x++) {
                            for (int y = -5; y <= 5; y++) {
                                for (int z = -10; z <= 10; z++) {
                                    BlockPos pos = playerPos.add(x, y, z);
                                    if (context.client().world.getBlockEntity(pos) instanceof DrillBlockEntity drill) {
                                        // Update the mining dimensions directly
                                        drill.setMiningDimensionsFromNetwork(height, width);
                                        Circuitmod.LOGGER.info("[CLIENT] Received drill dimensions sync: {}x{} for drill at {}", height, width, pos);
                                        return;
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Also update the screen if it's open
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.currentScreen instanceof DrillScreen drillScreen) {
                    drillScreen.updateTextFields(height, width);
                    Circuitmod.LOGGER.info("[CLIENT] Updated drill screen text fields with dimensions: {}x{}", height, width);
                }
            });
        });
        
        // Register handler for laser mining drill depth sync updates
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.LaserDrillDepthSyncPayload.ID, (payload, context) -> {
            // Extract data from the payload
            BlockPos drillPos = payload.drillPos();
            int depth = payload.depth();
            
            // Process on the game thread
            context.client().execute(() -> {
                // If the player's world is loaded
                if (context.client().world != null) {
                    // Get the laser mining drill block entity at the exact position
                    if (context.client().world.getBlockEntity(drillPos) instanceof LaserMiningDrillBlockEntity laserDrill) {
                        // Update the mining depth directly
                        laserDrill.setMiningDepthFromNetwork(depth);
                        Circuitmod.LOGGER.info("[CLIENT] Received laser mining drill depth sync: {} for drill at {}", depth, drillPos);
                    } else {
                        Circuitmod.LOGGER.warn("[CLIENT] Could not find laser mining drill at position: {}", drillPos);
                    }
                }
                
                // Also update the screen if it's open
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.currentScreen instanceof LaserMiningDrillScreen laserDrillScreen) {
                    laserDrillScreen.updateDepthField(depth);
                    Circuitmod.LOGGER.info("[CLIENT] Updated laser mining drill screen depth field with depth: {}", depth);
                }
            });
        });
        
        // Register handler for item move animations
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.ItemMovePayload.ID, (payload, context) -> {
            // Extract data from the payload and create a copy to ensure isolation
            ItemStack stack = payload.stack().copy();
            BlockPos from = payload.from();
            BlockPos to = payload.to();
            long serverStartTick = payload.startTick();
            int durationTicks = payload.durationTicks();
            
            // Process on the game thread
            context.client().execute(() -> {
                // Use server timing for perfect synchronization with actual transfers
                ClientNetworkAnimator.addAnimation(stack, from, to, serverStartTick, durationTicks);
                Circuitmod.LOGGER.info("[CLIENT] Received item move animation: {} from {} to {} (server start: {})", 
                    stack.getItem().getName().getString(), from, to, serverStartTick);
            });
        });
        
        // Register handler for constructor building status updates
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.ConstructorBuildingStatusPayload.ID, (payload, context) -> {
            // Extract data from the payload
            boolean building = payload.building();
            boolean hasBlueprint = payload.hasBlueprint();
            var constructorPos = payload.constructorPos();
            
            // Process on the game thread
            context.client().execute(() -> {
                // If the player's world is loaded
                if (context.client().world != null) {
                    // Try to get the constructor block entity at the position
                    if (context.client().world.getBlockEntity(constructorPos) instanceof ConstructorBlockEntity constructor) {
                        // Update the building status directly
                        constructor.setBuildingStatusFromNetwork(building, hasBlueprint);
                        Circuitmod.LOGGER.info("[CLIENT] Updated constructor building status: building={}, hasBlueprint={}", building, hasBlueprint);
                    }
                }
                
                // Also update the screen handler if the screen is open
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null && client.player.currentScreenHandler instanceof ConstructorScreenHandler handler) {
                    // Update the property delegate through the new method
                    handler.updateBuildingStatusFromNetwork(building, hasBlueprint);
                    Circuitmod.LOGGER.info("[CLIENT] Updated ConstructorScreenHandler building status: building={}, hasBlueprint={}", building, hasBlueprint);
                }
            });
        });
        
        // Register handler for constructor power status updates
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.ConstructorPowerStatusPayload.ID, (payload, context) -> {
            // Extract data from the payload
            boolean hasPower = payload.hasPower();
            var constructorPos = payload.constructorPos();
            
            // Process on the game thread
            context.client().execute(() -> {
                // If the player's world is loaded
                if (context.client().world != null) {
                    // Try to get the constructor block entity at the position
                    if (context.client().world.getBlockEntity(constructorPos) instanceof ConstructorBlockEntity constructor) {
                        // Update the power status directly
                        constructor.setPowerStatusFromNetwork(hasPower);
                        Circuitmod.LOGGER.info("[CLIENT] Updated constructor power status: hasPower={}", hasPower);
                    }
                }
                
                // Also update the screen handler if the screen is open
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null && client.player.currentScreenHandler instanceof ConstructorScreenHandler handler) {
                    // Update the property delegate through the new method
                    handler.updatePowerStatusFromNetwork(hasPower);
                    Circuitmod.LOGGER.info("[CLIENT] Updated ConstructorScreenHandler power status: hasPower={}", hasPower);
                }
            });
        });
        
        // Register handler for constructor status message updates
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.ConstructorStatusMessagePayload.ID, (payload, context) -> {
            // Extract data from the payload
            String message = payload.message();
            var constructorPos = payload.constructorPos();
            
            // Process on the game thread
            context.client().execute(() -> {
                // If the player's world is loaded
                if (context.client().world != null) {
                    // Try to get the constructor block entity at the position
                    if (context.client().world.getBlockEntity(constructorPos) instanceof ConstructorBlockEntity constructor) {
                        // Update the status message directly
                        constructor.setStatusMessageFromNetwork(message);
                        Circuitmod.LOGGER.info("[CLIENT] Updated constructor status message: {}", message);
                    }
                }
            });
        });
        

        
        // Register handler for constructor materials sync updates
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.ConstructorMaterialsSyncPayload.ID, (payload, context) -> {
            // Extract data from the payload
            BlockPos constructorPos = payload.constructorPos();
            Map<String, Integer> required = payload.required();
            Map<String, Integer> available = payload.available();

            // Process on the game thread
            context.client().execute(() -> {
                starduster.circuitmod.screen.ConstructorScreenHandler.updateMaterialsFromServer(constructorPos, required, available);
                Circuitmod.LOGGER.info("[CLIENT] Updated ConstructorScreenHandler materials from server for {}: required={}, available={}", constructorPos, required, available);
            });
        });
        
        // Register handler for constructor build positions sync updates
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.ConstructorBuildPositionsSyncPayload.ID, (payload, context) -> {
            // Extract data from the payload
            BlockPos constructorPos = payload.constructorPos();
            List<BlockPos> buildPositions = payload.buildPositions();

            // Process on the game thread
            context.client().execute(() -> {
                // Update the constructor block entity with the build positions
                if (context.client().world != null) {
                    if (context.client().world.getBlockEntity(constructorPos) instanceof starduster.circuitmod.block.entity.ConstructorBlockEntity constructor) {
                        constructor.setBuildPositionsFromNetwork(buildPositions);
                    }
                }
                Circuitmod.LOGGER.info("[CLIENT] Updated constructor build positions from server for {}: {} positions", constructorPos, buildPositions.size());
            });
        });
        

        

    }
    
    /**
     * Send a toggle mining request to the server
     * 
     * @param machinePos The position of the machine to toggle
     */
    public static void sendToggleMiningRequest(BlockPos machinePos) {
        Circuitmod.LOGGER.info("[CLIENT] sendToggleMiningRequest called with position: {}", machinePos);
        
        if (machinePos.equals(BlockPos.ORIGIN)) {
            Circuitmod.LOGGER.error("[CLIENT] Refusing to send toggle request for invalid position (0,0,0)!");
            return;
        }
        
        try {
            ModNetworking.ToggleMiningPayload payload = new ModNetworking.ToggleMiningPayload(machinePos);
            ClientPlayNetworking.send(payload);
            Circuitmod.LOGGER.info("[CLIENT] Successfully sent toggle mining request for machine at {}", machinePos);
        } catch (Exception e) {
            Circuitmod.LOGGER.error("[CLIENT] Failed to send toggle mining request: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Send quarry dimensions to the server
     * 
     * @param quarryPos The position of the quarry
     * @param width The width of the mining area
     * @param length The length of the mining area
     */
    public static void sendQuarryDimensions(BlockPos quarryPos, int width, int length) {
        Circuitmod.LOGGER.info("[CLIENT] sendQuarryDimensions called with position: {}, width: {}, length: {}", quarryPos, width, length);
        
        if (quarryPos.equals(BlockPos.ORIGIN)) {
            Circuitmod.LOGGER.error("[CLIENT] Refusing to send dimensions for invalid position (0,0,0)!");
            return;
        }
        
        try {
            ModNetworking.QuarryDimensionsPayload payload = new ModNetworking.QuarryDimensionsPayload(quarryPos, width, length);
            ClientPlayNetworking.send(payload);
            Circuitmod.LOGGER.info("[CLIENT] Successfully sent quarry dimensions for quarry at {}", quarryPos);
        } catch (Exception e) {
            Circuitmod.LOGGER.error("[CLIENT] Failed to send quarry dimensions: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Send drill dimensions to the server
     * 
     * @param drillPos The position of the drill
     * @param height The height of the mining area
     * @param width The width of the mining area
     */
    public static void sendDrillDimensions(BlockPos drillPos, int height, int width) {
        Circuitmod.LOGGER.info("[CLIENT] sendDrillDimensions called with position: {}, height: {}, width: {}", drillPos, height, width);
        
        if (drillPos.equals(BlockPos.ORIGIN)) {
            Circuitmod.LOGGER.error("[CLIENT] Refusing to send dimensions for invalid position (0,0,0)!");
            return;
        }
        
        try {
            ModNetworking.DrillDimensionsPayload payload = new ModNetworking.DrillDimensionsPayload(drillPos, height, width);
            ClientPlayNetworking.send(payload);
            Circuitmod.LOGGER.info("[CLIENT] Successfully sent drill dimensions for drill at {}", drillPos);
        } catch (Exception e) {
            Circuitmod.LOGGER.error("[CLIENT] Failed to send drill dimensions: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Send laser mining drill depth to the server
     * 
     * @param drillPos The position of the laser mining drill
     * @param depth The depth of the mining line
     */
    public static void sendDrillDepth(BlockPos drillPos, int depth) {
        Circuitmod.LOGGER.info("[CLIENT] sendDrillDepth called with position: {}, depth: {}", drillPos, depth);
        
        if (drillPos.equals(BlockPos.ORIGIN)) {
            Circuitmod.LOGGER.error("[CLIENT] Refusing to send depth for invalid position (0,0,0)!");
            return;
        }
        
        try {
            ModNetworking.LaserDrillDepthPayload payload = new ModNetworking.LaserDrillDepthPayload(drillPos, depth);
            ClientPlayNetworking.send(payload);
            Circuitmod.LOGGER.info("[CLIENT] Successfully sent laser mining drill depth for drill at {}", drillPos);
        } catch (Exception e) {
            Circuitmod.LOGGER.error("[CLIENT] Failed to send laser mining drill depth: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Send constructor building toggle to the server
     * 
     * @param constructorPos The position of the constructor
     */
    public static void sendConstructorBuildingToggle(BlockPos constructorPos) {
        Circuitmod.LOGGER.info("[CLIENT] sendConstructorBuildingToggle called with position: {}", constructorPos);
        
        if (constructorPos.equals(BlockPos.ORIGIN)) {
            Circuitmod.LOGGER.error("[CLIENT] Refusing to send toggle request for invalid position (0,0,0)!");
            return;
        }
        
        try {
            ModNetworking.ConstructorBuildingPayload payload = new ModNetworking.ConstructorBuildingPayload(constructorPos);
            ClientPlayNetworking.send(payload);
            Circuitmod.LOGGER.info("[CLIENT] Successfully sent constructor building toggle for constructor at {}", constructorPos);
        } catch (Exception e) {
            Circuitmod.LOGGER.error("[CLIENT] Failed to send constructor building toggle: {}", e.getMessage(), e);
        }
    }
    

} 