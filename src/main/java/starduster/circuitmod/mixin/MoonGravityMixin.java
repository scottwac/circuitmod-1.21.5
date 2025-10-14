package starduster.circuitmod.mixin;

import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reduces gravity-like vertical acceleration for players in the Luna dimension.
 * This scales vertical velocity after vanilla applies physics to simulate lower gravity.
 */
@Mixin(PlayerEntity.class)
public abstract class MoonGravityMixin {

    // 0.0 = no downward acceleration, 1.0 = vanilla
    private static final double LUNA_GRAVITY_MULTIPLIER = 0.16; // < 1.0 = weaker gravity than vanilla
    private static final double VANILLA_GRAVITY_PER_TICK = 0.08; // matches vanilla gravity step

    @Inject(method = "tick", at = @At("TAIL"))
    private void circuitmod$applyMoonGravity(CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        World world = player.getWorld();


        boolean isMoon = world.getRegistryKey().getValue().equals(Identifier.of("circuitmod", "luna"));

        // Restore vanilla behavior outside the moon
        if (!isMoon) {
            if (player.hasNoGravity()) {
                player.setNoGravity(false);
            }
            return;
        }

        // Skip when the player is flying in creative/spectator
        if (player.getAbilities().flying || player.isSpectator()) {
            return;
        }

        // If player has Slow Falling, let vanilla handle gravity and fall damage prevention
        if (player.hasStatusEffect(StatusEffects.SLOW_FALLING)) {
            if (player.hasNoGravity()) {
                player.setNoGravity(false);
            }
            return;
        }

        // Fully override gravity by disabling vanilla gravity and applying our own
        player.setNoGravity(true);

        // Apply Luna gravity without launch pad interference (launch pads removed)

        Vec3d velocity = player.getVelocity();
        // Apply reduced gravity every tick (even on ground) to match vanilla behavior
        double newY = velocity.y - (VANILLA_GRAVITY_PER_TICK * LUNA_GRAVITY_MULTIPLIER);
        // Only change Y to avoid affecting forward movement or sprint logic
        if (newY != velocity.y) {
            player.setVelocity(velocity.x, newY, velocity.z);
        }
        // Do NOT flag velocityModified every tick; it spams velocity packets and breaks sprint
    }
}

