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
import starduster.circuitmod.entity.CustomPlayerInventory;
import starduster.circuitmod.item.EmuSuitArmorItem;
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
            // Check if player is wearing the full emu suit
            boolean hasFullSuit = isWearingFullEmuSuit(player);
            
            // Check if player has oxygen tanks with oxygen
            boolean hasOxygen = false;
            
            PlayerInventory inventory = player.getInventory();
            
            // Only check for oxygen if wearing full emu suit
            if (hasFullSuit && inventory instanceof CustomPlayerInventory customInv) {
                // Check both oxygen tank slots
                ItemStack tank1 = customInv.getStack(CustomPlayerInventory.OXYGEN_TANK_1_SLOT);
                ItemStack tank2 = customInv.getStack(CustomPlayerInventory.OXYGEN_TANK_2_SLOT);
                
                // Try to consume oxygen from tank 1 first
                if (!tank1.isEmpty() && tank1.getItem() instanceof OxygenTankItem oxygenTank1) {
                    int currentOxygen = oxygenTank1.getOxygen(tank1);
                    if (currentOxygen > 0) {
                        hasOxygen = true;
                        // Consume 1 oxygen per tick (20 oxygen per second)
                        damageTimer++;
                        if (damageTimer >= 20) { // Every second
                            oxygenTank1.consumeOxygen(tank1, 20);
                            damageTimer = 0;
                        }
                    }
                }
                
                // If tank 1 is empty or not present, try tank 2
                if (!hasOxygen && !tank2.isEmpty() && tank2.getItem() instanceof OxygenTankItem oxygenTank2) {
                    int currentOxygen = oxygenTank2.getOxygen(tank2);
                    if (currentOxygen > 0) {
                        hasOxygen = true;
                        damageTimer++;
                        if (damageTimer >= 20) { // Every second
                            oxygenTank2.consumeOxygen(tank2, 20);
                            damageTimer = 0;
                        }
                    }
                }
            }
            
            // Damage player if they don't have full suit or oxygen
            if (!hasFullSuit || !hasOxygen) {
                damageTimer++;
                
                // Damage player every DAMAGE_INTERVAL ticks
                if (damageTimer >= DAMAGE_INTERVAL) {
                    if (world instanceof ServerWorld serverWorld) {
                        player.damage(serverWorld, world.getDamageSources().generic(), DAMAGE_AMOUNT);
                    }
                    damageTimer = 0;
                }
            }
        } else {
            // Reset timer if not in luna dimension
            damageTimer = 0;
        }
    }
    
    /**
     * Checks if the player is wearing all four pieces of the emu suit armor.
     */
    private boolean isWearingFullEmuSuit(PlayerEntity player) {
        ItemStack helmet = player.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD);
        ItemStack chestplate = player.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST);
        ItemStack leggings = player.getEquippedStack(net.minecraft.entity.EquipmentSlot.LEGS);
        ItemStack boots = player.getEquippedStack(net.minecraft.entity.EquipmentSlot.FEET);
        
        return helmet.getItem() instanceof EmuSuitArmorItem &&
               chestplate.getItem() instanceof EmuSuitArmorItem &&
               leggings.getItem() instanceof EmuSuitArmorItem &&
               boots.getItem() instanceof EmuSuitArmorItem;
    }
} 