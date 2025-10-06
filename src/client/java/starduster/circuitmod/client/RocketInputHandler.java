package starduster.circuitmod.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.entity.Entity;
import starduster.circuitmod.entity.RocketEntity;
import starduster.circuitmod.network.ClientNetworking;

@Environment(EnvType.CLIENT)
public class RocketInputHandler {
    private static boolean lastSpacePressed = false;
    
    /**
     * Initialize the rocket input handler
     */
    public static void initialize() {
        // Register tick event to check for spacebar input changes
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            
            // Check if player is riding a rocket
            Entity vehicle = client.player.getVehicle();
            if (!(vehicle instanceof RocketEntity rocket)) {
                // Reset last state when not in rocket
                lastSpacePressed = false;
                return;
            }
            
            // Get current spacebar state
            boolean spacePressed = client.options.jumpKey.isPressed();
            
            // Only send packet if spacebar state changed
            if (spacePressed != lastSpacePressed) {
                System.out.println("[ROCKET CLIENT] Spacebar state changed: " + spacePressed);
                
                ClientNetworking.sendRocketSpacebarInput(rocket.getId(), spacePressed);
                
                // Update last state
                lastSpacePressed = spacePressed;
            }
        });
    }
}
