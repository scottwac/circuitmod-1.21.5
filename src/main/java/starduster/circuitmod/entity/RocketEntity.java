package starduster.circuitmod.entity;

import net.minecraft.entity.*;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.block.BlockState;
import org.jetbrains.annotations.Nullable;
import starduster.circuitmod.Circuitmod;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Example {@link GeoAnimatable} implementation of a rocket entity
 */
public class RocketEntity extends AnimalEntity implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    
    // Rocket launch state
    private boolean isLaunching = false;
    private int launchTicks = 0;
    
    // Spacebar input state from client
    private boolean spacePressed = false;

    public RocketEntity(EntityType<? extends AnimalEntity> entityType, World world) {
        super(entityType, world);
    }

    // Let the player ride the entity
    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (!this.hasPassengers()) {
            player.startRiding(this);
            return ActionResult.SUCCESS;
        }

        return ActionResult.PASS;
    }

    // Turn off step sounds since it's a rocket
    @Override
    protected void playStepSound(BlockPos pos, BlockState block) {}

    // Apply player-controlled movement
    @Override
    public void travel(Vec3d pos) {
        if (this.isAlive()) {
            if (this.hasPassengers()) {
                LivingEntity passenger = (LivingEntity)getControllingPassenger();
                
                // Debug: Log movement input values
                Circuitmod.LOGGER.info("Rocket travel - pos.x: {}, pos.y: {}, pos.z: {}", pos.x, pos.y, pos.z);
                Circuitmod.LOGGER.info("Rocket travel - sidewaysSpeed: {}, forwardSpeed: {}", passenger.sidewaysSpeed, passenger.forwardSpeed);
                Circuitmod.LOGGER.info("Rocket travel - isLaunching: {}, spacePressed: {}", isLaunching, spacePressed);
                
                this.lastYaw = this.getYaw();
                this.lastPitch = this.getPitch();

                setYaw(passenger.getYaw());
                setPitch(passenger.getPitch() * 0.5f);
                this.setRotation(getYaw(), getPitch());

                this.bodyYaw = this.getYaw();
                this.headYaw = this.bodyYaw;
                float x = passenger.sidewaysSpeed * 0.5F;
                float z = passenger.forwardSpeed;
                float y = 0.0f;

                // Check if spacebar is pressed to trigger launch
                if (spacePressed && !isLaunching) {
                    Circuitmod.LOGGER.info("Rocket launch triggered by spacebar!");
                    startLaunch();
                }

                // Normal movement (launch mechanics handled by mixin)
                if (!isLaunching) {
                    if (z <= 0)
                        z *= 0.25f;
                } else {
                    // Disable horizontal movement during launch for more dramatic effect
                    x = 0;
                    z = 0;
                }

                this.setMovementSpeed(0.3f);
                super.travel(new Vec3d(x, y, z));
            }
        }
    }

    // Get the controlling passenger
    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        return getFirstPassenger() instanceof LivingEntity entity ? entity : null;
    }

    @Override
    public boolean canMoveVoluntarily() {
        return true;
    }

    // Adjust the rider's position while riding
    @Override
    public void updatePassengerPosition(Entity passenger, PositionUpdater positionUpdater) {
        positionUpdater.accept(passenger, getX(), getY() + 0.5f, getZ());
    }
    
    // Launch mechanics
    private void startLaunch() {
        Circuitmod.LOGGER.info("Starting rocket launch!");
        isLaunching = true;
        launchTicks = 0;
        // Play launch sound or effects here if desired
    }
    
    
    @Override
    public void tick() {
        super.tick();
        
        // Stop launching if no passengers (but don't stop at altitude - let the mixin handle Luna teleport)
        if (isLaunching) {
            if (!this.hasPassengers()) {
                stopLaunch();
            }
        }
    }
    
    private void stopLaunch() {
        isLaunching = false;
        launchTicks = 0;
        // Return control to player
    }

    // GeckoLib implementation - no animations for now
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // No animation controllers - just render the static model
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public PassiveEntity createChild(ServerWorld world, PassiveEntity entity) {
        return null;
    }

    @Override
    public boolean isBreedingItem(ItemStack stack) {
        return false;
    }

    @Override
    public double getEyeY() {
        return this.getY() + 0.5f;
    }
    
    // Method to set spacebar state from networking
    public void setSpacePressed(boolean spacePressed) {
        this.spacePressed = spacePressed;
        Circuitmod.LOGGER.info("Rocket spacebar state set to: {}", spacePressed);
    }
    
    // Public method for mixin to check launch state
    public boolean isLaunching() {
        return this.isLaunching;
    }
    
    // Public method for mixin to set launch state
    public void setLaunching(boolean launching) {
        this.isLaunching = launching;
    }
    
    // Public method for mixin to get launch ticks
    public int getLaunchTicks() {
        return this.launchTicks;
    }
    
    // Public method for mixin to increment launch ticks
    public void incrementLaunchTicks() {
        this.launchTicks++;
    }
    
    // Override gravity behavior during launch
    @Override
    public boolean hasNoGravity() {
        if (this.isLaunching) {
            return true; // No gravity during launch
        }
        return super.hasNoGravity();
    }
    
    // Override ground detection during launch
    @Override
    public boolean isOnGround() {
        if (this.isLaunching) {
            return false; // Never on ground during launch
        }
        return super.isOnGround();
    }
}