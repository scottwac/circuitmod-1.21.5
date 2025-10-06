package starduster.circuitmod.entity;

import net.minecraft.entity.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * A ridable rocket ship entity that can fly high into the atmosphere
 */
public class RocketEntity extends Entity {
    
    // Data tracker fields for syncing between client and server
    private static final TrackedData<Float> ROCKET_PITCH = DataTracker.registerData(RocketEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Boolean> IS_BOOSTING = DataTracker.registerData(RocketEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Float> FUEL_LEVEL = DataTracker.registerData(RocketEntity.class, TrackedDataHandlerRegistry.FLOAT);
    
    // Rocket properties
    private static final float MAX_FUEL = 1000.0f;
    private static final float FUEL_CONSUMPTION_RATE = 2.0f;
    private static final float BOOST_FUEL_CONSUMPTION_RATE = 5.0f;
    private static final float MAX_SPEED = 2.0f;
    private static final float BOOST_MULTIPLIER = 2.5f;
    private static final float ACCELERATION = 0.1f;
    private static final float DECELERATION = 0.95f;
    // private static final float TURN_SPEED = 2.0f; // Not used in simplified controls
    // private static final int MAX_ALTITUDE = 500; // Maximum height above ground - unused for now
    
    // Movement state
    private Vec3d velocity = Vec3d.ZERO;
    private float rocketPitch = 0.0f;
    // private boolean isBoosting = false; // Tracked via data tracker instead
    private int particleTimer = 0;
    private int soundTimer = 0;
    
    public RocketEntity(EntityType<? extends RocketEntity> entityType, World world) {
        super(entityType, world);
        this.setFuel(MAX_FUEL);
    }
    
    public RocketEntity(World world, double x, double y, double z) {
        this(ModEntities.ROCKET, world);
        this.setPosition(x, y, z);
    }
    
    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(ROCKET_PITCH, 0.0f);
        builder.add(IS_BOOSTING, false);
        builder.add(FUEL_LEVEL, MAX_FUEL);
    }
    
    @Override
    public void tick() {
        super.tick();
        
        if (!this.getWorld().isClient) {
            this.serverTick();
        } else {
            this.clientTick();
        }
        
        // Apply movement
        this.move(MovementType.SELF, this.velocity);
        
        // Update position and rotation
        this.setRotation(this.getYaw(), this.getRocketPitch());
    }
    
    private void serverTick() {
        Entity passenger = this.getFirstPassenger();
        
        if (passenger instanceof PlayerEntity player) {
            // Handle player input
            this.handlePlayerInput(player);
            
            // Update movement
            this.updateMovement();
            
            // Consume fuel
            this.consumeFuel();
            
            // Check if out of fuel
            if (this.getFuel() <= 0 && this.isBoosting()) {
                this.setBoosting(false);
            }
        } else {
            // No passenger, gradually slow down
            this.velocity = this.velocity.multiply(DECELERATION);
            this.setBoosting(false);
        }
        
        // Prevent going too high
       
        
        // Play engine sounds
        this.playEngineSounds();
    }
    
    private void clientTick() {
        // Spawn particles for visual effects
        this.spawnParticles();
    }
    
    private void handlePlayerInput(PlayerEntity player) {
        // Simple controls: just space bar (jumping) to accelerate upward
        // Note: player.jumping is not directly accessible, we'll use a different approach
        boolean jumping = false; // TODO: Implement proper input handling via networking
        boolean sneaking = player.isSneaking();
        
        // Handle dismounting - shift to get out
        if (sneaking) {
            player.stopRiding();
            return;
        }
        
        // Handle boosting - only when jumping and has fuel
        boolean shouldBoost = jumping && this.getFuel() > 0;
        this.setBoosting(shouldBoost);
        
        // Keep rocket level (no pitch changes)
        this.rocketPitch = 0.0f;
        this.setRocketPitch(this.rocketPitch);
    }
    
    private void updateMovement() {
        if (this.getFuel() > 0) {
            // Calculate thrust direction based on rocket orientation
            float yawRad = this.getYaw() * (float) (Math.PI / 180.0);
            float pitchRad = this.getRocketPitch() * (float) (Math.PI / 180.0);
            
            Vec3d thrustDirection = new Vec3d(
                -MathHelper.sin(yawRad) * MathHelper.cos(pitchRad),
                -MathHelper.sin(pitchRad),
                MathHelper.cos(yawRad) * MathHelper.cos(pitchRad)
            );
            
            // Apply thrust
            float thrustPower = this.isBoosting() ? ACCELERATION * BOOST_MULTIPLIER : ACCELERATION;
            Vec3d thrust = thrustDirection.multiply(thrustPower);
            
            this.velocity = this.velocity.add(thrust);
            
            // Limit maximum speed
            float maxSpeed = this.isBoosting() ? MAX_SPEED * BOOST_MULTIPLIER : MAX_SPEED;
            if (this.velocity.length() > maxSpeed) {
                this.velocity = this.velocity.normalize().multiply(maxSpeed);
            }
        }
        
        // Apply gravity (reduced when boosting)
        double gravity = this.isBoosting() ? -0.02 : -0.08;
        this.velocity = this.velocity.add(0, gravity, 0);
        
        // Apply air resistance
        this.velocity = this.velocity.multiply(0.98);
    }
    
    private void consumeFuel() {
        if (this.isBoosting()) {
            this.setFuel(Math.max(0, this.getFuel() - BOOST_FUEL_CONSUMPTION_RATE));
        } else if (this.velocity.length() > 0.1) {
            this.setFuel(Math.max(0, this.getFuel() - FUEL_CONSUMPTION_RATE));
        }
    }
    
    private void spawnParticles() {
        if (this.getFuel() > 0 && this.velocity.length() > 0.1) {
            this.particleTimer++;
            
            if (this.particleTimer >= (this.isBoosting() ? 1 : 3)) {
                this.particleTimer = 0;
                
                // Spawn exhaust particles behind the rocket
                Vec3d exhaustPos = this.getPos().add(0, -0.5, 0);
                
                for (int i = 0; i < (this.isBoosting() ? 5 : 2); i++) {
                    double offsetX = (this.random.nextDouble() - 0.5) * 0.5;
                    double offsetY = (this.random.nextDouble() - 0.5) * 0.5;
                    double offsetZ = (this.random.nextDouble() - 0.5) * 0.5;
                    
                    if (this.getWorld() instanceof ServerWorld serverWorld) {
                        serverWorld.spawnParticles(
                            this.isBoosting() ? ParticleTypes.FLAME : ParticleTypes.SMOKE,
                            exhaustPos.x + offsetX,
                            exhaustPos.y + offsetY,
                            exhaustPos.z + offsetZ,
                            1, // count
                            offsetX * 0.1,
                            -0.1,
                            offsetZ * 0.1,
                            0.1 // speed
                        );
                    }
                }
            }
        }
    }
    
    private void playEngineSounds() {
        if (this.getFuel() > 0 && this.velocity.length() > 0.1) {
            this.soundTimer++;
            
            if (this.soundTimer >= 20) {
                this.soundTimer = 0;
                
                float volume = this.isBoosting() ? 1.0f : 0.5f;
                float pitch = this.isBoosting() ? 1.2f : 0.8f;
                
                this.getWorld().playSound(
                    null,
                    this.getBlockPos(),
                    SoundEvents.ENTITY_BLAZE_SHOOT,
                    SoundCategory.NEUTRAL,
                    volume,
                    pitch
                );
            }
        }
    }
    
    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        if (!this.getWorld().isClient) {
            if (this.hasPassengers()) {
                return ActionResult.PASS;
            }
            
            // Right-click to mount the player
            player.startRiding(this);
            return ActionResult.SUCCESS;
        }
        
        return ActionResult.SUCCESS;
    }
    
    @Override
    protected boolean canStartRiding(Entity entity) {
        return !this.hasPassengers() && entity instanceof PlayerEntity;
    }
    
    // Remove this method as it doesn't exist in the Entity class
    
    @Override
    protected Vec3d getPassengerAttachmentPos(Entity passenger, EntityDimensions dimensions, float scaleFactor) {
        return new Vec3d(0, 0.5, 0);
    }
    
    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        
        
        // Eject passengers on damage
        this.removeAllPassengers();
        
        // Drop as item
        if (!this.getWorld().isClient && world != null) {
            this.dropItem(world, starduster.circuitmod.item.ModItems.ROCKET);
        }
        
        this.discard();
        return true;
    }
    
    // Data tracker getters and setters
    public float getRocketPitch() {
        return this.dataTracker.get(ROCKET_PITCH);
    }
    
    public void setRocketPitch(float pitch) {
        this.dataTracker.set(ROCKET_PITCH, pitch);
    }
    
    public boolean isBoosting() {
        return this.dataTracker.get(IS_BOOSTING);
    }
    
    public void setBoosting(boolean boosting) {
        this.dataTracker.set(IS_BOOSTING, boosting);
    }
    
    public float getFuel() {
        return this.dataTracker.get(FUEL_LEVEL);
    }
    
    public void setFuel(float fuel) {
        this.dataTracker.set(FUEL_LEVEL, MathHelper.clamp(fuel, 0, MAX_FUEL));
    }
    
    public float getMaxFuel() {
        return MAX_FUEL;
    }
    
    public void refuel(float amount) {
        this.setFuel(this.getFuel() + amount);
    }
    
    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        this.setFuel(nbt.getFloat("Fuel").orElse(MAX_FUEL));
        this.rocketPitch = nbt.getFloat("RocketPitch").orElse(0.0f);
        
        if (nbt.contains("VelocityX")) {
            this.velocity = new Vec3d(
                nbt.getDouble("VelocityX").orElse(0.0),
                nbt.getDouble("VelocityY").orElse(0.0),
                nbt.getDouble("VelocityZ").orElse(0.0)
            );
        }
    }
    
    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putFloat("Fuel", this.getFuel());
        nbt.putFloat("RocketPitch", this.rocketPitch);
        nbt.putDouble("VelocityX", this.velocity.x);
        nbt.putDouble("VelocityY", this.velocity.y);
        nbt.putDouble("VelocityZ", this.velocity.z);
    }
    
    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket(EntityTrackerEntry entityTrackerEntry) {
        return new EntitySpawnS2CPacket(this, entityTrackerEntry);
    }
    
    // Removed RideableInventory interface - not needed for basic ridable entity
    
}
