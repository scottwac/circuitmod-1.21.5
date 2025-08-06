package starduster.circuitmod.mixin;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import starduster.circuitmod.Circuitmod;
import net.minecraft.item.Items;

@Mixin(PlayerEntity.class)
public class CircuitDimensionDamageMixin {
    
    private static final int DAMAGE_INTERVAL = 60; // Damage every 3 seconds (60 ticks)
    private static final float DAMAGE_AMOUNT = 1.0f; // Half a heart of damage
    private int damageTimer = 0;
    
    @Inject(method = "tick", at = @At("HEAD"))
    private void onPlayerTick(CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        World world = player.getWorld();
        
        // Check if player is in the circuit dimension
        if (world.getRegistryKey().getValue().equals(Identifier.of("circuitmod", "circuit_dimension"))) {
            // Check if player has a conduit in their offhand
            ItemStack offHand = player.getStackInHand(Hand.OFF_HAND);
            boolean hasConduit = offHand.getItem() == Items.CONDUIT;
            
            if (!hasConduit) {
                damageTimer++;
                
                // Damage player every DAMAGE_INTERVAL ticks
                if (damageTimer >= DAMAGE_INTERVAL) {
                    if (world instanceof ServerWorld serverWorld) {
                        player.damage(serverWorld, world.getDamageSources().generic(), DAMAGE_AMOUNT);
                    }
                    damageTimer = 0;
                    
                    // Log for debugging
                    Circuitmod.LOGGER.debug("[CIRCUIT-DIMENSION] Player {} took damage for not having conduit in offhand", 
                        player.getName().getString());
                }
            } else {
                // Reset timer if player has conduit
                damageTimer = 0;
            }
        } else {
            // Reset timer if not in circuit dimension
            damageTimer = 0;
        }
    }
} 