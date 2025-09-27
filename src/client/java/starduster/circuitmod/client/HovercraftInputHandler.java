package starduster.circuitmod.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.Entity;
import starduster.circuitmod.entity.HovercraftEntity;
import starduster.circuitmod.network.ClientNetworking;

@Environment(EnvType.CLIENT)
public class HovercraftInputHandler {
    private static boolean lastForward = false;
    private static boolean lastBackward = false;
    private static boolean lastLeft = false;
    private static boolean lastRight = false;
    private static boolean lastUp = false;
    private static boolean lastDown = false;
    
    /**
     * Initialize the hovercraft input handler
     */
    public static void initialize() {
        // Register tick event to check for input changes
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            
            // Check if player is riding a hovercraft
            Entity vehicle = client.player.getVehicle();
            if (!(vehicle instanceof HovercraftEntity hovercraft)) {
                // Reset last states when not in hovercraft
                resetLastStates();
                return;
            }
            
            // Get current input states
            boolean forward = client.options.forwardKey.isPressed();
            boolean backward = client.options.backKey.isPressed();
            boolean left = client.options.leftKey.isPressed();
            boolean right = client.options.rightKey.isPressed();
            boolean up = client.options.jumpKey.isPressed();
            boolean down = client.options.sneakKey.isPressed();
            
            // Only send packet if inputs changed
            if (forward != lastForward || backward != lastBackward || 
                left != lastLeft || right != lastRight || 
                up != lastUp || down != lastDown) {
                
                System.out.println("[HOVERCRAFT CLIENT] Input change detected: F=" + forward + 
                    " B=" + backward + " L=" + left + " R=" + right + " U=" + up + " D=" + down);
                
                ClientNetworking.sendHovercraftInput(
                    hovercraft.getId(), 
                    forward, backward, 
                    left, right, 
                    up, down
                );
                
                System.out.println("[HOVERCRAFT CLIENT] Sent input packet for entity ID: " + hovercraft.getId());
                
                // Update last states
                lastForward = forward;
                lastBackward = backward;
                lastLeft = left;
                lastRight = right;
                lastUp = up;
                lastDown = down;
            }
        });
    }
    
    private static void resetLastStates() {
        lastForward = false;
        lastBackward = false;
        lastLeft = false;
        lastRight = false;
        lastUp = false;
        lastDown = false;
    }
}
