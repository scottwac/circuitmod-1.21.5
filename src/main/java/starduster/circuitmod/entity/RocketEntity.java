package starduster.circuitmod.entity;

import net.minecraft.entity.*;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
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
    
    // Sound and effect state
    private boolean playingFlyingSound = false;
    private int particleTimer = 0;
    
    // Track if rocket came from Luna (for despawning logic)
    private boolean cameFromLuna = false;
    private int landingTimer = 0;
    private static final int DESPAWN_DELAY_TICKS = 100; // 5 seconds at 20 ticks/second

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
        // First call the parent implementation for basic positioning
        super.updatePassengerPosition(passenger, positionUpdater);
        
        // Then apply rocket-specific positioning adjustments
        if (passenger instanceof PlayerEntity) {
            // Position player higher so their head is at window height
            // Rocket window should be at about 2.5 blocks, player head is 1.8 blocks from feet
            // So we need to position the player's feet at about 0.7 blocks above rocket base
            double adjustedY = getY() + 1.2;
            positionUpdater.accept(passenger, getX(), adjustedY, getZ());
        }
    }
    
    // Launch mechanics
    private void startLaunch() {
        Circuitmod.LOGGER.info("Starting rocket launch!");
        isLaunching = true;
        launchTicks = 0;
        
        // Play launch sound using existing Minecraft sounds
        if (!getWorld().isClient) {
            // Use firework launch sound for rocket launch
            getWorld().playSound(null, getBlockPos(), SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, 
                SoundCategory.NEUTRAL, 2.0f, 0.8f);
        }
    }
    
    
    @Override
    public void tick() {
        super.tick();
        
        // Handle sound and particle effects
        handleSoundsAndParticles();
        
        // Handle landing and despawn logic for rockets returning from Luna
        handleLandingAndDespawn();
        
        // Stop launching if no passengers (but don't stop at altitude - let the mixin handle Luna teleport)
        if (isLaunching) {
            if (!this.hasPassengers()) {
                stopLaunch();
            }
        }
    }
    
    private void handleSoundsAndParticles() {
        // Handle flying sound
        if (isLaunching && this.hasPassengers()) {
            if (!playingFlyingSound) {
                // Start playing flying sound
                if (!getWorld().isClient) {
                    getWorld().playSound(null, getBlockPos(), SoundEvents.ITEM_ELYTRA_FLYING, 
                        SoundCategory.NEUTRAL, 0.8f, 1.2f);
                }
                playingFlyingSound = true;
            }
            
            // Play flying sound periodically while launching
            if (launchTicks % 40 == 0 && !getWorld().isClient) { // Every 2 seconds
                getWorld().playSound(null, getBlockPos(), SoundEvents.ITEM_ELYTRA_FLYING, 
                    SoundCategory.NEUTRAL, 0.6f, 1.2f);
            }
        } else {
            playingFlyingSound = false;
        }
        
        // Handle particle effects
        particleTimer++;
        if (isLaunching && particleTimer % 3 == 0) { // Every 3 ticks for smooth particles
            spawnThrustParticles();
        }
    }
    
    private void spawnThrustParticles() {
        // Only spawn particles on server side - they will be synchronized to clients
        if (!getWorld().isClient && getWorld() instanceof ServerWorld serverWorld) {
            double x = getX();
            double y = getY() - 0.5; // Below the rocket
            double z = getZ();
            
            // Create multiple particles for better effect
            for (int i = 0; i < 3; i++) {
                double offsetX = (random.nextDouble() - 0.5) * 0.3;
                double offsetZ = (random.nextDouble() - 0.5) * 0.3;
                double offsetY = -random.nextDouble() * 0.2;
                
                // Spawn flame particles on server for synchronization
                serverWorld.spawnParticles(ParticleTypes.FLAME,
                    x + offsetX, y + offsetY, z + offsetZ,
                    1, offsetX * 0.1, -0.1, offsetZ * 0.1, 0.0);
                    
                // Spawn smoke particles
                serverWorld.spawnParticles(ParticleTypes.LARGE_SMOKE,
                    x + offsetX, y + offsetY - 0.2, z + offsetZ,
                    1, offsetX * 0.05, -0.05, offsetZ * 0.05, 0.0);
            }
        }
    }
    
    private void handleLandingAndDespawn() {
        // Only handle on server side
        if (getWorld().isClient) {
            return;
        }
        
        // Check if rocket came from Luna and is now in Overworld
        if (cameFromLuna) {
            boolean isInOverworld = getWorld().getRegistryKey().getValue().equals(Identifier.of("minecraft", "overworld"));
            
            if (isInOverworld && !isLaunching) {
                // Check if rocket has landed (is on ground and has low velocity)
                boolean hasLanded = this.isOnGround() && this.getVelocity().lengthSquared() < 0.1;
                
                if (hasLanded) {
                    landingTimer++;
                    
                    // Log landing progress
                    if (landingTimer == 1) {
                        Circuitmod.LOGGER.info("Rocket from Luna has landed on Earth. Starting despawn timer...");
                    }
                    
                    // Despawn after delay
                    if (landingTimer >= DESPAWN_DELAY_TICKS) {
                        Circuitmod.LOGGER.info("Despawning rocket that returned from Luna");
                        
                        // Dismount any passengers first
                        if (this.hasPassengers()) {
                            this.removeAllPassengers();
                        }
                        
                        // Play despawn sound effect
                        getWorld().playSound(null, getBlockPos(), SoundEvents.ENTITY_ITEM_PICKUP, 
                            SoundCategory.NEUTRAL, 1.0f, 0.8f);
                        
                        // Spawn some particles for visual effect
                        if (getWorld() instanceof ServerWorld serverWorld) {
                            for (int i = 0; i < 10; i++) {
                                double offsetX = (random.nextDouble() - 0.5) * 2.0;
                                double offsetY = random.nextDouble() * 2.0;
                                double offsetZ = (random.nextDouble() - 0.5) * 2.0;
                                
                                serverWorld.spawnParticles(ParticleTypes.POOF,
                                    getX() + offsetX, getY() + offsetY, getZ() + offsetZ,
                                    1, 0.0, 0.0, 0.0, 0.0);
                            }
                        }
                        
                        // Remove the rocket
                        this.discard();
                    }
                } else {
                    // Reset timer if rocket is no longer landed
                    landingTimer = 0;
                }
            }
        }
    }
    
    private void stopLaunch() {
        isLaunching = false;
        launchTicks = 0;
        playingFlyingSound = false;
        
        // Play landing sound
        if (!getWorld().isClient) {
            getWorld().playSound(null, getBlockPos(), SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, 
                SoundCategory.NEUTRAL, 0.5f, 1.5f);
        }
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
    
    // Methods for Luna origin tracking
    public boolean getCameFromLuna() {
        return this.cameFromLuna;
    }
    
    public void setCameFromLuna(boolean cameFromLuna) {
        this.cameFromLuna = cameFromLuna;
        Circuitmod.LOGGER.info("Rocket Luna origin flag set to: {}", cameFromLuna);
    }
}