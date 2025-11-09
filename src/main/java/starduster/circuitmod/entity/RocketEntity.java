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
    
    // Rocket landing state
    private boolean isLanding = false;
    private int landingTicks = 0;
    
    // Spacebar input state from client
    private boolean spacePressed = false;
    
    // Sound and effect state
    private boolean playingFlyingSound = false;
    private int particleTimer = 0;
    
    // Track if rocket came from Luna (for despawning logic)
    private boolean cameFromLuna = false;
    private int landingTimer = 0;
    private static final int DESPAWN_DELAY_TICKS = 20; // 1 second at 20 ticks/second

    public RocketEntity(EntityType<? extends AnimalEntity> entityType, World world) {
        super(entityType, world);
    }

    // Let the player ride the entity
    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (!this.hasPassengers()) {
            // Set rocket rotation to face the direction it was placed, not player direction
            // This keeps the rocket orientation consistent
            player.startRiding(this);
            Circuitmod.LOGGER.info("Player {} mounted rocket. Rocket rotation fixed at yaw: {}, pitch: {}", 
                player.getName().getString(), this.getYaw(), this.getPitch());
            return ActionResult.SUCCESS;
        }

        return ActionResult.PASS;
    }

    // Turn off step sounds since it's a rocket
    @Override
    protected void playStepSound(BlockPos pos, BlockState block) {}

    // Apply player-controlled movement (WASD disabled, only spacebar for launch)
    @Override
    public void travel(Vec3d pos) {
        if (this.isAlive()) {
            if (this.hasPassengers()) {
                // Check for spacebar input to trigger launch
                // Only allow launch if rocket is not currently landing
                if (spacePressed && !isLaunching && !isLanding && canLaunch()) {
                    Circuitmod.LOGGER.info("Rocket launch triggered by spacebar!");
                    startLaunch();
                } else if (spacePressed && (isLanding || !canLaunch())) {
                    // Log when player tries to launch during landing or before rocket has settled
                    if (this.age % 40 == 0) { // Log every 2 seconds to avoid spam
                        if (isLanding) {
                            Circuitmod.LOGGER.info("Rocket launch blocked - rocket is currently landing. Please wait for landing to complete.");
                        } else if (cameFromLuna && !this.isOnGround()) {
                            Circuitmod.LOGGER.info("Rocket launch blocked - rocket hasn't fully landed yet. Please wait for rocket to settle on ground.");
                        }
                    }
                }
                
                // Keep rocket rotation fixed - don't follow player camera
                // Don't update yaw/pitch from passenger
                
                // Disable all WASD movement - rocket doesn't respond to movement keys
                // Ignore passenger.sidewaysSpeed and passenger.forwardSpeed
                float x = 0.0f;
                float z = 0.0f;
                float y = 0.0f;


                // Rocket only moves vertically when launching (handled by mixin)
                // No horizontal movement allowed at any time
                
                this.setMovementSpeed(0.0f); // Disable movement speed
                super.travel(new Vec3d(x, y, z));
            } else {
                // No passengers - use default travel behavior
                super.travel(pos);
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
        return true; // Allow input tracking for spacebar, but ignore WASD in travel()
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
        
        // Clear Luna origin flag when launching again
        if (cameFromLuna) {
            Circuitmod.LOGGER.info("Clearing Luna origin flag - rocket is launching again");
            cameFromLuna = false;
        }
        
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
        
        // Handle controlled landing
        handleControlledLanding();
        
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
    
    private void handleControlledLanding() {
        // Only handle on server side
        if (getWorld().isClient) {
            return;
        }
        
        if (!isLanding) {
            return;
        }
        
        // Controlled descent with safe landing speed
        Vec3d velocity = this.getVelocity();
        
        // Safe descent speed - slow enough to prevent fall damage
        float safeDescentSpeed = -0.5f; // Negative because we're going down
        
        // Apply controlled descent velocity
        this.setVelocity(velocity.x * 0.9, safeDescentSpeed, velocity.z * 0.9); // Reduce horizontal drift
        this.velocityModified = true;
        
        // Check if rocket would collide with a block in the next tick
        double nextY = this.getY() + safeDescentSpeed; // Where we'll be next tick
        BlockPos nextPos = new BlockPos((int)this.getX(), (int)Math.floor(nextY), (int)this.getZ());
        
        // Check if the block at the next position is solid
        boolean wouldHitBlock = !this.getWorld().getBlockState(nextPos).isAir();
        
        // Also check the block directly below the rocket's current position
        BlockPos belowPos = new BlockPos((int)this.getX(), (int)Math.floor(this.getY() - 1), (int)this.getZ());
        boolean blockDirectlyBelow = !this.getWorld().getBlockState(belowPos).isAir();
        
        // Stop landing if we would hit a block or there's a block directly below us
        boolean shouldStopLanding = wouldHitBlock || blockDirectlyBelow;
        
        // Log landing progress
        if (landingTicks % 20 == 0) { // Every second
            Circuitmod.LOGGER.info("Controlled landing - Y: {}, NextY: {}, Velocity: {}, WouldHitBlock: {}, BlockBelow: {}, Ticks: {}", 
                String.format("%.1f", this.getY()), String.format("%.1f", nextY), String.format("%.3f", velocity.y), 
                wouldHitBlock, blockDirectlyBelow, landingTicks);
        }
        
        // Stop controlled landing when we would hit a solid block
        if (shouldStopLanding) {
            Circuitmod.LOGGER.info("Controlled landing complete - rocket touching solid ground");
            this.isLanding = false;
            this.landingTicks = 0;
            
            // Position rocket properly on the ground
            if (blockDirectlyBelow) {
                // If there's a block below, position rocket just above it
                double groundY = Math.floor(this.getY()) + 1.0; // 1 block above the ground
                this.setPosition(this.getX(), groundY, this.getZ());
            }
            
            // Stop all movement for a clean landing
            this.setVelocity(0, 0, 0);
            this.velocityModified = true;
            
            // Play landing sound
            if (!getWorld().isClient) {
                getWorld().playSound(null, getBlockPos(), SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, 
                    SoundCategory.NEUTRAL, 0.5f, 1.5f);
            }
        }
        
        incrementLandingTicks();
    }
    
    private void handleLandingAndDespawn() {
        // Only handle on server side
        if (getWorld().isClient) {
            return;
        }
        
        // Debug logging every 100 ticks (5 seconds) when rocket came from Luna
        if (cameFromLuna && this.age % 100 == 0) {
            boolean isInOverworld = getWorld().getRegistryKey().getValue().equals(Identifier.of("minecraft", "overworld"));
            double velocitySquared = this.getVelocity().lengthSquared();
            Circuitmod.LOGGER.info("Rocket from Luna debug - InOverworld: {}, IsLaunching: {}, OnGround: {}, VelocitySquared: {}, LandingTimer: {}", 
                isInOverworld, isLaunching, this.isOnGround(), velocitySquared, landingTimer);
        }
        
        // Check if rocket came from Luna and is now in Overworld
        if (cameFromLuna) {
            boolean isInOverworld = getWorld().getRegistryKey().getValue().equals(Identifier.of("minecraft", "overworld"));
            
            if (isInOverworld && !isLaunching) {
                // More lenient landing detection - check if rocket has low velocity and is close to ground
                double velocitySquared = this.getVelocity().lengthSquared();
                boolean hasLowVelocity = velocitySquared < 1.0; // Increased threshold
                boolean isCloseToGround = this.isOnGround() || this.getVelocity().y > -0.5; // Also check if falling slowly
                boolean hasLanded = hasLowVelocity && isCloseToGround;
                
                if (hasLanded) {
                    landingTimer++;
                    
                    // Log landing progress more frequently
                    if (landingTimer == 1) {
                        Circuitmod.LOGGER.info("Rocket from Luna has landed on Earth! Starting despawn timer... (Velocity: {}, OnGround: {})", 
                            Math.sqrt(velocitySquared), this.isOnGround());
                    }
                    
                    // Log countdown every second
                    if (landingTimer % 20 == 0) {
                        int secondsLeft = (DESPAWN_DELAY_TICKS - landingTimer) / 20;
                        Circuitmod.LOGGER.info("Rocket despawn countdown: {} seconds remaining", secondsLeft);
                    }
                    
                    // Despawn after delay
                    if (landingTimer >= DESPAWN_DELAY_TICKS) {
                        Circuitmod.LOGGER.info("Despawning rocket that returned from Luna");
                        
                        // Dismount any passengers first
                        if (this.hasPassengers()) {
                            Circuitmod.LOGGER.info("Dismounting {} passengers before despawn", this.getPassengerList().size());
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
                        Circuitmod.LOGGER.info("Rocket successfully despawned!");
                        this.discard();
                    }
                } else {
                    // Reset timer if rocket is no longer landed, but log why
                    if (landingTimer > 0) {
                        Circuitmod.LOGGER.info("Rocket landing timer reset - HasLowVelocity: {}, IsCloseToGround: {}", 
                            hasLowVelocity, isCloseToGround);
                    }
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
    
    // Public methods for landing state management
    public boolean isLanding() {
        return this.isLanding;
    }
    
    public void setLanding(boolean landing) {
        this.isLanding = landing;
        if (landing) {
            this.landingTicks = 0;
            Circuitmod.LOGGER.info("Rocket entering controlled landing mode");
        }
    }
    
    public int getLandingTicks() {
        return this.landingTicks;
    }
    
    public void incrementLandingTicks() {
        this.landingTicks++;
    }
    
    // Check if rocket is ready to launch
    public boolean canLaunch() {
        // If rocket came from Luna, it must be on the ground and not landing
        if (cameFromLuna) {
            boolean onGround = this.isOnGround();
            boolean hasLowVelocity = this.getVelocity().lengthSquared() < 0.1; // Very low velocity
            boolean notLanding = !this.isLanding;
            
            boolean canLaunch = onGround && hasLowVelocity && notLanding;
            
            // Debug logging for launch readiness
            if (!canLaunch && this.age % 60 == 0) { // Log every 3 seconds
                Circuitmod.LOGGER.info("Rocket launch readiness - OnGround: {}, LowVelocity: {}, NotLanding: {}, CanLaunch: {}", 
                    onGround, hasLowVelocity, notLanding, canLaunch);
            }
            
            return canLaunch;
        }
        
        // For rockets that didn't come from Luna, normal launch rules apply
        return !this.isLanding;
    }
    
    // Override gravity behavior during launch and landing
    @Override
    public boolean hasNoGravity() {
        if (this.isLaunching || this.isLanding) {
            return true; // No gravity during launch or controlled landing
        }
        return super.hasNoGravity();
    }
    
    // Override ground detection during launch and landing
    @Override
    public boolean isOnGround() {
        if (this.isLaunching || this.isLanding) {
            return false; // Never on ground during launch or controlled landing
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
    
    // Debug method to force despawn (for testing)
    public void forceDespawn() {
        if (!getWorld().isClient) {
            Circuitmod.LOGGER.info("Force despawning rocket for debugging");
            
            // Dismount passengers
            if (this.hasPassengers()) {
                this.removeAllPassengers();
            }
            
            // Play effects
            getWorld().playSound(null, getBlockPos(), SoundEvents.ENTITY_ITEM_PICKUP, 
                SoundCategory.NEUTRAL, 1.0f, 0.8f);
                
            // Spawn particles
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
    }
}