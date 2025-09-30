package starduster.circuitmod.entity;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.recipebook.ClientRecipeBook;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.stat.StatHandler;

/**
 * Custom client player entity that uses CustomPlayerInventory.
 * This replaces the standard ClientPlayerEntity to add oxygen tank slots.
 */
public class CustomClientPlayerEntity extends ClientPlayerEntity {
    
    public CustomClientPlayerEntity(MinecraftClient client, ClientWorld world, ClientPlayNetworkHandler networkHandler, StatHandler stats, ClientRecipeBook recipeBook, boolean lastSneaking, boolean lastSprinting) {
        super(client, world, networkHandler, stats, recipeBook, lastSneaking, lastSprinting);
        // Note: The inventory is already created in PlayerEntity constructor before this runs
        // We need to use a mixin to replace it at creation time
        System.out.println("[CircuitMod] CustomClientPlayerEntity created");
    }
    
    /**
     * Gets the custom inventory if available.
     */
    public CustomPlayerInventory getCustomInventory() {
        if (this.getInventory() instanceof CustomPlayerInventory customInv) {
            return customInv;
        }
        return null;
    }
}
