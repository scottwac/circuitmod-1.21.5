package starduster.circuitmod.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.PositionInterpolator;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;
import starduster.circuitmod.Circuitmod;

/**
 * Missile entity that travels in an arc trajectory to a target coordinate
 */
public class MissileEntity extends Entity implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final PositionInterpolator interpolator = new PositionInterpolator(this, 3);
    
    // Target coordinates (stored in NBT, not synced via tracked data)
    private Vec3d targetPos;
    
    // Launch position (for arc calculation)
    private Vec3d launchPos;
    
    // Control block position (missile stays fixed here until launched)
    private net.minecraft.util.math.BlockPos controlBlockPos;
    
    // Whether the missile has been launchedhh toi the next 
    private boolean isLaunched = false;
    
    // Movement parameters
    private static final float SPEED = 0.45F; // 3x speed
    private int ticks = 0;
    
    public MissileEntity(EntityType<?> type, World world) {
        super(type, world);
        this.setNoGravity(true); // No gravity, we control movement
    }
    
    public MissileEntity(World world, Vec3d pos) {
        this(ModEntityTypes.MISSILE, world);
        this.setPosition(pos);
        this.launchPos = pos;
        
        // Set target to 100 blocks north of spawn position (default)
        this.setTargetPosition(pos.add(0, 0, -100));
        
        // By default, missile is launched immediately (for backwards compatibility)
        this.isLaunched = true;
    }
    
    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        // No tracked data needed
    }
    
    /**
     * Set the target position for the missile
     */
    public void setTargetPosition(Vec3d target) {
        this.targetPos = target;
    }
    
    /**
     * Get the target position
     */
    public Vec3d getTargetPosition() {
        return targetPos != null ? targetPos : this.getPos();
    }
    
    /**
     * Set the control block position - missile will stay fixed here until launched
     */
    public void setControlBlock(net.minecraft.util.math.BlockPos pos) {
        this.controlBlockPos = pos;
        this.isLaunched = false;
        Circuitmod.LOGGER.info("[MISSILE] Attached to control block at {}", pos);
    }
    
    /**
     * Launch the missile - starts movement toward target
     */
    public void launch() {
        if (!isLaunched) {
            this.isLaunched = true;
            this.launchPos = this.getPos();
            Circuitmod.LOGGER.info("[MISSILE] Launched from {} toward target {}", launchPos, targetPos);
        }
    }
    
    /**
     * Check if the missile has been launched
     */
    public boolean isLaunched() {
        return isLaunched;
    }
    
    @Override
    public PositionInterpolator getInterpolator() {
        return this.interpolator;
    }
    
    @Override
    public void tick() {
        super.tick();
        
        // Tick interpolator for smooth client-side rendering
        this.interpolator.tick();
        
        // If missile is not launched yet, stay fixed at control block position
        if (!isLaunched && controlBlockPos != null) {
            // Keep missile positioned above the control block
            Vec3d fixedPos = new Vec3d(controlBlockPos.getX() + 0.5, controlBlockPos.getY() + 1.0, controlBlockPos.getZ() + 0.5);
            this.setPosition(fixedPos);
            this.setVelocity(Vec3d.ZERO);
            return; // Don't move until launched
        }
        
        // Only update movement on the logical side (server for missiles)
        if (this.isLogicalSideForUpdatingMovement()) {
            if (launchPos == null) {
                launchPos = this.getPos();
            }
            
            ticks++;
            
            Vec3d currentPos = this.getPos();
            Vec3d target = getTargetPosition();
            
            // Calculate horizontal distance and progress
            Vec3d launchPosFlat = new Vec3d(launchPos.x, 0, launchPos.z);
            Vec3d targetFlat = new Vec3d(target.x, 0, target.z);
            Vec3d currentFlat = new Vec3d(currentPos.x, 0, currentPos.z);
            
            double totalDistance = launchPosFlat.distanceTo(targetFlat);
            double traveledDistance = launchPosFlat.distanceTo(currentFlat);
            double progress = totalDistance > 0 ? traveledDistance / totalDistance : 0;
            
            // Calculate horizontal direction
            Vec3d horizontalDir = targetFlat.subtract(launchPosFlat).normalize();
            
            // Calculate arc height using parabolic trajectory
            // Peak height is 20 blocks at 50% progress
            double arcHeight = 20.0;
            double heightOffset = 4.0 * arcHeight * progress * (1.0 - progress);
            
            // Target Y position with arc
            double targetY = MathHelper.lerp(progress, launchPos.y, target.y) + heightOffset;
            
            // Calculate next position
            Vec3d nextPos = currentFlat.add(horizontalDir.multiply(SPEED));
            nextPos = new Vec3d(nextPos.x, targetY, nextPos.z);
            
            // Calculate velocity for this tick
            Vec3d velocity = nextPos.subtract(currentPos);
            this.setVelocity(velocity);
            
            // Calculate rotation to face movement direction (for model orientation)
            double dx = velocity.x;
            double dy = velocity.y;
            double dz = velocity.z;
            
            // Yaw: horizontal rotation (side to side)
            // atan2(dz, dx) gives angle from +X axis
            // Subtract 90° to convert to Minecraft's yaw system
            float yaw = (float) (MathHelper.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
            yaw = MathHelper.wrapDegrees(yaw);
            
            // Pitch: vertical rotation (up/down angle)
            // Calculate the flight angle from horizontal
            double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
            float flightAngle = (float) (MathHelper.atan2(dy, horizontalDistance) * (180.0 / Math.PI));
            
            // Since model points UP by default (0° pitch = vertical nose-up):
            // - When flying horizontally (flightAngle = 0°), we need pitch = 90° to rotate it horizontal
            // - When flying up at 45° (flightAngle = 45°), we need pitch = 45° 
            // - When flying straight up (flightAngle = 90°), we need pitch = 0° (keep it vertical)
            float pitch = 90.0f - flightAngle;
            
            // Set rotation - this syncs to client automatically
            this.setYaw(yaw);
            this.setPitch(pitch);
            
            // Apply movement and check for collision
            Vec3d oldPos = this.getPos();
            this.move(MovementType.SELF, this.getVelocity());
            Vec3d newPos = this.getPos();
            
            // Check if we hit something (position didn't change as expected)
            boolean hitSomething = oldPos.distanceTo(newPos) < velocity.length() * 0.5;
            
            // Check if we're in water or hit a block
            if (hitSomething || this.isTouchingWater() || this.isOnGround()) {
                detonate();
                return;
            }
            
            // Debug logging - print velocity components (tangent direction) as missile travels
            if (ticks % 20 == 0) {
                Circuitmod.LOGGER.info("[MISSILE] Velocity (tangent direction) - X: {}, Y: {}, Z: {} | Yaw: {}, Pitch: {}", 
                    String.format("%.6f", velocity.x),
                    String.format("%.6f", velocity.y),
                    String.format("%.6f", velocity.z),
                    String.format("%.1f", yaw),
                    String.format("%.1f", pitch));
            }
        }
    }
    
    /**
     * Detonate the missile - creates a powerful explosion (3x TNT power)
     */
    public void detonate() {
        if (!this.getWorld().isClient) {
            Circuitmod.LOGGER.info("Missile detonating at [{}, {}, {}]", 
                String.format("%.1f", this.getX()), 
                String.format("%.1f", this.getY()), 
                String.format("%.1f", this.getZ()));
            
            // Create explosion with 3x TNT power (TNT = 4.0, missile = 12.0)
            this.getWorld().createExplosion(
                this,                           // Entity causing explosion
                this.getX(),                    // X position
                this.getY(),                    // Y position
                this.getZ(),                    // Z position
                12.0F,                          // Power (3x TNT's 4.0)
                true,                           // Creates fire
                World.ExplosionSourceType.TNT   // Explosion type
            );
            
            // Remove the missile
            this.discard();
        }
    }
    
    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.contains("LaunchX")) {
            this.launchPos = new Vec3d(
                nbt.getDouble("LaunchX").orElse(0.0),
                nbt.getDouble("LaunchY").orElse(0.0),
                nbt.getDouble("LaunchZ").orElse(0.0)
            );
        }
        if (nbt.contains("TargetX")) {
            this.targetPos = new Vec3d(
                nbt.getDouble("TargetX").orElse(0.0),
                nbt.getDouble("TargetY").orElse(0.0),
                nbt.getDouble("TargetZ").orElse(0.0)
            );
        }
        if (nbt.contains("ControlBlockX")) {
            this.controlBlockPos = new net.minecraft.util.math.BlockPos(
                nbt.getInt("ControlBlockX").orElse(0),
                nbt.getInt("ControlBlockY").orElse(0),
                nbt.getInt("ControlBlockZ").orElse(0)
            );
        }
        this.isLaunched = nbt.getBoolean("IsLaunched").orElse(false);
        this.ticks = nbt.getInt("Ticks").orElse(0);
    }
    
    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        if (launchPos != null) {
            nbt.putDouble("LaunchX", launchPos.x);
            nbt.putDouble("LaunchY", launchPos.y);
            nbt.putDouble("LaunchZ", launchPos.z);
        }
        if (targetPos != null) {
            nbt.putDouble("TargetX", targetPos.x);
            nbt.putDouble("TargetY", targetPos.y);
            nbt.putDouble("TargetZ", targetPos.z);
        }
        if (controlBlockPos != null) {
            nbt.putInt("ControlBlockX", controlBlockPos.getX());
            nbt.putInt("ControlBlockY", controlBlockPos.getY());
            nbt.putInt("ControlBlockZ", controlBlockPos.getZ());
        }
        nbt.putBoolean("IsLaunched", isLaunched);
        nbt.putInt("Ticks", ticks);
    }
    
    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        // Missile cannot be damaged, just explodes if hit
        if (!this.isRemoved()) {
            detonate();
            return true;
        }
        return false;
    }
    
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // No animations for now - static model
    }
    
    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}

