package starduster.circuitmod.item;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import starduster.circuitmod.effect.ModStatusEffects;
import starduster.circuitmod.effect.PulseVelocityStorage;

public class PulseStickItem extends Item {
    
    public PulseStickItem(Settings settings) {
        super(settings);
    }
    
    /**
     * Stores the pulse velocity using status effects and velocity storage
     */
    public static void setPulseVelocity(PlayerEntity player, Vec3d velocity) {
        // Store the velocity in our storage system
        PulseVelocityStorage.setVelocity(player, velocity);
        
        // Apply the status effect for 3 seconds (60 ticks)
        StatusEffectInstance effect = new StatusEffectInstance(ModStatusEffects.PULSE_VELOCITY, 60, 0);
        player.addStatusEffect(effect);
        
        System.out.println("[PULSE-STICK-DEBUG] Applied pulse velocity effect and stored velocity: " + velocity + " for player: " + player.getName().getString());
    }
    
    /**
     * Gets the stored pulse velocity from status effect and storage
     */
    public static Vec3d getPulseVelocity(PlayerEntity player) {
        if (player.hasStatusEffect(ModStatusEffects.PULSE_VELOCITY)) {
            return PulseVelocityStorage.getVelocity(player);
        }
        return Vec3d.ZERO;
    }
    
    /**
     * Clears the pulse velocity effect
     */
    public static void clearPulseVelocity(PlayerEntity player) {
        player.removeStatusEffect(ModStatusEffects.PULSE_VELOCITY);
        PulseVelocityStorage.clearVelocity(player);
        
        System.out.println("[PULSE-STICK-DEBUG] Cleared pulse velocity for player: " + player.getName().getString());
    }
    
    /**
     * Checks if the player is currently under pulse effect
     */
    public static boolean isPulseActive(PlayerEntity player) {
        boolean active = player.hasStatusEffect(ModStatusEffects.PULSE_VELOCITY);
        boolean hasVelocity = PulseVelocityStorage.hasVelocity(player);
        System.out.println("[PULSE-STICK-DEBUG] isPulseActive check for " + player.getName().getString() + ": " + active + " | Has velocity data: " + hasVelocity);
        return active && hasVelocity;
    }

    @Override
    public ActionResult useOnBlock(net.minecraft.item.ItemUsageContext context) {
        PlayerEntity player = context.getPlayer();
        World world = context.getWorld();
        
        if (player == null || world.isClient) {
            return ActionResult.PASS;
        }
        
        System.out.println("[PULSE-STICK-DEBUG] Pulse stick used by: " + player.getName().getString());
        
        // Get the player's looking direction (not where they clicked)
        Vec3d lookDirection = player.getRotationVecClient();
        
        // Ignore Y component for horizontal movement, amplify for launching effect
        Vec3d horizontalVelocity = new Vec3d(lookDirection.x * 2.0, 0, lookDirection.z * 2.0);
        
        // Store the velocity for the handler to use
        setPulseVelocity(player, horizontalVelocity);
        
        // Create launch velocity with upward component
        Vec3d launchVelocity = new Vec3d(horizontalVelocity.x, 1.0, horizontalVelocity.z);
        
        // Apply initial launch
        player.setVelocity(launchVelocity);
        player.velocityModified = true;
        
        // Play sound effect
        world.playSound(null, player.getX(), player.getY(), player.getZ(), 
                        SoundEvents.ENTITY_ENDER_DRAGON_FLAP, SoundCategory.PLAYERS, 0.8f, 1.5f);
        
        // Damage the item (1 durability per use)
        ItemStack itemStack = context.getStack();
        itemStack.damage(1, player);
        
        System.out.println("[PULSE-STICK-DEBUG] Pulse activated! Launch velocity: " + launchVelocity + " | Stored horizontal: " + horizontalVelocity);
        
        return ActionResult.SUCCESS;
    }
} 