package starduster.circuitmod.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.entity.CustomPlayerInventory;
import starduster.circuitmod.item.OxygenTankItem;

@Mixin(PlayerEntity.class)
public class CircuitDimensionDamageMixin {
    
    private static final int DAMAGE_INTERVAL = 60; // Damage every 3 seconds (60 ticks)
    private static final float DAMAGE_AMOUNT = 1.0f; // Half a heart of damage
    private int damageTimer = 0;
    
    @Inject(method = "tick", at = @At("HEAD"))
    private void onPlayerTick(CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        World world = player.getWorld();
        
        // Check if player is in the luna dimension
        if (world.getRegistryKey().getValue().equals(Identifier.of("circuitmod", "luna"))) {
            // Check if player has oxygen tanks with oxygen
            boolean hasOxygen = false;
            int oxygenConsumed = 0;
            
            PlayerInventory inventory = player.getInventory();
            Circuitmod.LOGGER.debug("[OXYGEN] Player inventory type: {}", inventory.getClass().getName());
            
            if (inventory instanceof CustomPlayerInventory customInv) {
                Circuitmod.LOGGER.debug("[OXYGEN] Player has CustomPlayerInventory!");
                // Check both oxygen tank slots
                ItemStack tank1 = customInv.getStack(CustomPlayerInventory.OXYGEN_TANK_1_SLOT);
                ItemStack tank2 = customInv.getStack(CustomPlayerInventory.OXYGEN_TANK_2_SLOT);
                
                Circuitmod.LOGGER.debug("[OXYGEN] Tank 1: {}, Tank 2: {}", tank1.isEmpty() ? "empty" : tank1.getItem().toString(), tank2.isEmpty() ? "empty" : tank2.getItem().toString());
                
                // Try to consume oxygen from tank 1 first
                if (!tank1.isEmpty() && tank1.getItem() instanceof OxygenTankItem oxygenTank1) {
                    int currentOxygen = oxygenTank1.getOxygen(tank1);
                    Circuitmod.LOGGER.debug("[OXYGEN] Tank 1 has {} oxygen", currentOxygen);
                    if (currentOxygen > 0) {
                        hasOxygen = true;
                        // Consume 1 oxygen per tick (20 oxygen per second)
                        damageTimer++;
                        if (damageTimer >= 20) { // Every second
                            oxygenConsumed = oxygenTank1.consumeOxygen(tank1, 20);
                            Circuitmod.LOGGER.debug("[OXYGEN] Consumed {} oxygen from tank 1, {} remaining", oxygenConsumed, oxygenTank1.getOxygen(tank1));
                            damageTimer = 0;
                        }
                    }
                }
                
                // If tank 1 is empty or not present, try tank 2
                if (!hasOxygen && !tank2.isEmpty() && tank2.getItem() instanceof OxygenTankItem oxygenTank2) {
                    int currentOxygen = oxygenTank2.getOxygen(tank2);
                    Circuitmod.LOGGER.debug("[OXYGEN] Tank 2 has {} oxygen", currentOxygen);
                    if (currentOxygen > 0) {
                        hasOxygen = true;
                        damageTimer++;
                        if (damageTimer >= 20) { // Every second
                            oxygenConsumed = oxygenTank2.consumeOxygen(tank2, 20);
                            Circuitmod.LOGGER.debug("[OXYGEN] Consumed {} oxygen from tank 2, {} remaining", oxygenConsumed, oxygenTank2.getOxygen(tank2));
                            damageTimer = 0;
                        }
                    }
                }
            } else {
                Circuitmod.LOGGER.debug("[OXYGEN] Player does NOT have CustomPlayerInventory!");
            }
            
            if (!hasOxygen) {
                damageTimer++;
                
                // Damage player every DAMAGE_INTERVAL ticks
                if (damageTimer >= DAMAGE_INTERVAL) {
                    if (world instanceof ServerWorld serverWorld) {
                        player.damage(serverWorld, world.getDamageSources().generic(), DAMAGE_AMOUNT);
                    }
                    damageTimer = 0;
                    
                    // Log for debugging
                    Circuitmod.LOGGER.debug("[CIRCUIT-DIMENSION] Player {} took damage on Luna for not having oxygen", 
                        player.getName().getString());
                }
            }
        } else {
            // Reset timer if not in luna dimension
            damageTimer = 0;
        }
    }
} 