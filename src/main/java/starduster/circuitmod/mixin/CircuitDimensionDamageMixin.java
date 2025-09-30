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
        
        // Only run on server side
        if (world.isClient) {
            return;
        }
        
        // Check if player is in the luna dimension
        if (world.getRegistryKey().getValue().equals(Identifier.of("circuitmod", "luna"))) {
            // Log every 60 ticks (3 seconds) to avoid spam
            if (damageTimer % 60 == 0) {
                System.out.println("[CircuitMod] Player on Luna - checking oxygen...");
            }
            // Check if player has oxygen tanks with oxygen
            boolean hasOxygen = false;
            int oxygenConsumed = 0;
            
            PlayerInventory inventory = player.getInventory();
            System.out.println("[CircuitMod] Player inventory type: " + inventory.getClass().getName());
            
            if (inventory instanceof CustomPlayerInventory customInv) {
                System.out.println("[CircuitMod] Player has CustomPlayerInventory!");
                // Check both oxygen tank slots
                ItemStack tank1 = customInv.getStack(CustomPlayerInventory.OXYGEN_TANK_1_SLOT);
                ItemStack tank2 = customInv.getStack(CustomPlayerInventory.OXYGEN_TANK_2_SLOT);
                
                System.out.println("[CircuitMod] Tank 1: " + (tank1.isEmpty() ? "empty" : tank1.getItem().toString()));
                System.out.println("[CircuitMod] Tank 2: " + (tank2.isEmpty() ? "empty" : tank2.getItem().toString()));
                
                // Try to consume oxygen from tank 1 first
                if (!tank1.isEmpty() && tank1.getItem() instanceof OxygenTankItem oxygenTank1) {
                    int currentOxygen = oxygenTank1.getOxygen(tank1);
                    System.out.println("[CircuitMod] Tank 1 has " + currentOxygen + " oxygen");
                    if (currentOxygen > 0) {
                        hasOxygen = true;
                        System.out.println("[CircuitMod] Found oxygen in tank 1! hasOxygen = true");
                        // Consume 1 oxygen per tick (20 oxygen per second)
                        damageTimer++;
                        if (damageTimer >= 20) { // Every second
                            oxygenConsumed = oxygenTank1.consumeOxygen(tank1, 20);
                            System.out.println("[CircuitMod] Consumed " + oxygenConsumed + " oxygen from tank 1, " + oxygenTank1.getOxygen(tank1) + " remaining");
                            damageTimer = 0;
                        }
                    }
                }
                
                // If tank 1 is empty or not present, try tank 2
                if (!hasOxygen && !tank2.isEmpty() && tank2.getItem() instanceof OxygenTankItem oxygenTank2) {
                    int currentOxygen = oxygenTank2.getOxygen(tank2);
                    System.out.println("[CircuitMod] Tank 2 has " + currentOxygen + " oxygen");
                    if (currentOxygen > 0) {
                        hasOxygen = true;
                        System.out.println("[CircuitMod] Found oxygen in tank 2! hasOxygen = true");
                        damageTimer++;
                        if (damageTimer >= 20) { // Every second
                            oxygenConsumed = oxygenTank2.consumeOxygen(tank2, 20);
                            System.out.println("[CircuitMod] Consumed " + oxygenConsumed + " oxygen from tank 2, " + oxygenTank2.getOxygen(tank2) + " remaining");
                            damageTimer = 0;
                        }
                    }
                }
            } else {
                System.out.println("[CircuitMod] Player does NOT have CustomPlayerInventory!");
            }
            
            System.out.println("[CircuitMod] hasOxygen = " + hasOxygen);
            
            if (!hasOxygen) {
                damageTimer++;
                System.out.println("[CircuitMod] No oxygen! damageTimer = " + damageTimer);
                
                // Damage player every DAMAGE_INTERVAL ticks
                if (damageTimer >= DAMAGE_INTERVAL) {
                    if (world instanceof ServerWorld serverWorld) {
                        player.damage(serverWorld, world.getDamageSources().generic(), DAMAGE_AMOUNT);
                        System.out.println("[CircuitMod] Player took damage for no oxygen!");
                    }
                    damageTimer = 0;
                }
            }
        } else {
            // Reset timer if not in luna dimension
            damageTimer = 0;
        }
    }
} 