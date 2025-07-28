package starduster.circuitmod.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

public class PulseStickItem extends Item {
    public static final String PULSE_VELOCITY_KEY = "PulseVelocity";
    public static final String PULSE_ACTIVE_KEY = "PulseActive";
    
    public PulseStickItem(Settings settings) {
        super(settings);
    }
    
    /**
     * Stores the pulse velocity in the player's NBT data
     */
    public static void setPulseVelocity(PlayerEntity player, Vec3d velocity) {
        NbtCompound nbt = player.writeNbt(new NbtCompound());
        nbt.putDouble(PULSE_VELOCITY_KEY + "_X", velocity.x);
        nbt.putDouble(PULSE_VELOCITY_KEY + "_Z", velocity.z);
        nbt.putBoolean(PULSE_ACTIVE_KEY, true);
        player.readNbt(nbt);
    }
    
    /**
     * Gets the stored pulse velocity from player's NBT data
     */
    public static Vec3d getPulseVelocity(PlayerEntity player) {
        NbtCompound nbt = player.writeNbt(new NbtCompound());
        if (!nbt.getBoolean(PULSE_ACTIVE_KEY).orElse(false)) {
            return Vec3d.ZERO;
        }
        double x = nbt.getDouble(PULSE_VELOCITY_KEY + "_X").orElse(0.0);
        double z = nbt.getDouble(PULSE_VELOCITY_KEY + "_Z").orElse(0.0);
        return new Vec3d(x, 0, z);
    }
    
    /**
     * Clears the pulse velocity effect
     */
    public static void clearPulseVelocity(PlayerEntity player) {
        NbtCompound nbt = player.writeNbt(new NbtCompound());
        nbt.putBoolean(PULSE_ACTIVE_KEY, false);
        nbt.remove(PULSE_VELOCITY_KEY + "_X");
        nbt.remove(PULSE_VELOCITY_KEY + "_Z");
        player.readNbt(nbt);
    }
    
    /**
     * Checks if the player is currently under pulse effect
     */
    public static boolean isPulseActive(PlayerEntity player) {
        NbtCompound nbt = player.writeNbt(new NbtCompound());
        return nbt.getBoolean(PULSE_ACTIVE_KEY).orElse(false);
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        ItemStack itemStack = user.getStackInHand(hand);

        if (!world.isClient) {
            // If player is already under pulse effect, stop it
            if (isPulseActive(user)) {
                clearPulseVelocity(user);
                world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    SoundEvents.BLOCK_PISTON_CONTRACT, SoundCategory.PLAYERS, 
                    0.5F, 1.2F);
                return ActionResult.SUCCESS;
            }
            
            // Perform raycast to find where the player is looking
            Vec3d start = user.getEyePos();
            Vec3d direction = user.getRotationVec(1.0F);
            Vec3d end = start.add(direction.multiply(10.0)); // 10 block range

            BlockHitResult hitResult = world.raycast(new RaycastContext(
                start, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, user
            ));

            if (hitResult.getType() == HitResult.Type.BLOCK) {
                // Calculate the direction from hit point to player
                Vec3d hitPos = hitResult.getPos();
                Vec3d playerPos = user.getPos();
                
                // Get the horizontal direction (ignore Y component for more predictable movement)
                Vec3d launchDirection = playerPos.subtract(hitPos).normalize();
                
                // Apply upward component for the launch
                double horizontalForce =6.0; // Horizontal launch strength
                double verticalForce = 3.0;   // Upward launch strength
                
                Vec3d velocity = new Vec3d(
                    launchDirection.x * horizontalForce,
                    verticalForce,
                    launchDirection.z * horizontalForce
                );

                // Launch the player
                user.addVelocity(velocity.x, velocity.y, velocity.z);
                user.velocityModified = true;
                
                // Store the horizontal velocity for continuous application
                Vec3d horizontalVelocity = new Vec3d(
                    launchDirection.x * horizontalForce,
                    0,
                    launchDirection.z * horizontalForce
                );
                setPulseVelocity(user, horizontalVelocity);

                // Play sound effect
                world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    SoundEvents.ENTITY_ENDER_DRAGON_FLAP, SoundCategory.PLAYERS, 
                    0.7F, 1.5F);

                // Damage the item slightly
                itemStack.damage(1, user);

                // Cooldown to prevent spam
                user.getItemCooldownManager().set(itemStack, 20); // 2 second cooldown
            }
        }

        return ActionResult.SUCCESS;
    }
} 