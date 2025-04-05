package starduster.circuitmod.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.block.entity.QuarryBlockEntity;
import starduster.circuitmod.screen.QuarryScreenHandler;

public class ClientNetworking {
    /**
     * Initialize client-side networking
     */
    public static void initialize() {   
        // Register handler for quarry mining speed updates
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.QuarryMiningSpeedPayload.ID, (payload, context) -> {
            // Extract data from the payload
            int miningSpeed = payload.miningSpeed();
            var quarryPos = payload.quarryPos();        
            // Process on the game thread
            context.client().execute(() -> {
                // If the player's world is loaded
                if (context.client().world != null) {
                    // Try to get the quarry block entity at the position
                    if (context.client().world.getBlockEntity(quarryPos) instanceof QuarryBlockEntity quarry) {
                        
                        // Update the mining speed directly via a new method we'll add
                        quarry.setMiningSpeedFromNetwork(miningSpeed);
                        // Additionally, update the screen if it's open
                        MinecraftClient client = MinecraftClient.getInstance();
                        Screen currentScreen = client.currentScreen;
                        if (client.player != null && client.player.currentScreenHandler instanceof QuarryScreenHandler handler) {
                            // Force the screen handler to refresh its cached value
                            int currentSpeed = handler.getMiningSpeed();
                            
                            
                            // Directly update the handler's cached value
                            handler.updateMiningSpeed(miningSpeed);
                            
                            // Force a UI refresh next tick
                            client.execute(() -> {
                                if (client.currentScreen != null) {
                                    if (client.currentScreen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen) {
                                        
                                    }
                                }
                            });
                        }
                    }
                }
            });
        });







        
    }
} 