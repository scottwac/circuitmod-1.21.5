package starduster.circuitmod.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.PositionInterpolator;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.VehicleEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;
import starduster.circuitmod.item.ModItems;

public class HovercraftEntity extends VehicleEntity implements GeoEntity {
    // Tracked data for synchronization (similar to boat's paddle states)
    private static final TrackedData<Boolean> BOOST_ACTIVE = DataTracker.registerData(HovercraftEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    
    // Movement constants (balanced like boat physics)
    private static final float ACCELERATION = 0.04F;
    private static final float DECELERATION = 0.9F; // Similar to boat on land
    private static final float MAX_SPEED = 0.35F; // Base max speed
    private static final float MAX_VERTICAL_SPEED = 0.25F;
    private static final float BOOST_MULTIPLIER = 1.5F;
    private static final float ROTATION_SPEED = 2.0F;
    
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    private final PositionInterpolator interpolator = new PositionInterpolator(this, 3);
    
    // Input states (similar to boat's paddle states)
    private boolean pressingForward;
    private boolean pressingBack;
    private boolean pressingLeft;
    private boolean pressingRight;
    private boolean pressingUp;
    private boolean pressingDown;
    private boolean pressingBoost;
    
    // Physics state
    private float yawVelocity;
    
    public HovercraftEntity(EntityType<? extends HovercraftEntity> entityType, World world) {
        super(entityType, world);
        this.intersectionChecked = true;
    }
    
    public HovercraftEntity(World world, double x, double y, double z) {
        this(ModEntityTypes.HOVERCRAFT, world);
        this.setPosition(x, y, z);
        this.lastX = x;
        this.lastY = y;
        this.lastZ = z;
    }
    
    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(BOOST_ACTIVE, false);
    }
    
    @Override
    protected Entity.MoveEffect getMoveEffect() {
        return Entity.MoveEffect.EVENTS;
    }
    
    /**
     * Set input states from client packet (similar to boat's setInputs)
     */
    public void setInputs(boolean forward, boolean back, boolean left, boolean right, boolean up, boolean down, boolean boost) {
        this.pressingForward = forward;
        this.pressingBack = back;
        this.pressingLeft = left;
        this.pressingRight = right;
        this.pressingUp = up;
        this.pressingDown = down;
        this.pressingBoost = boost;
        this.dataTracker.set(BOOST_ACTIVE, boost);
    }
    
    public boolean isBoostActive() {
        return this.dataTracker.get(BOOST_ACTIVE);
    }
    
    @Override
    protected Item asItem() {
        return ModItems.HOVERCRAFT;
    }
    
    @Override
    public ItemStack getPickBlockStack() {
        return new ItemStack(ModItems.HOVERCRAFT);
    }
    
    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        ActionResult actionResult = super.interact(player, hand);
        if (actionResult != ActionResult.PASS) {
            return actionResult;
        } else if (player.shouldCancelInteraction()) {
            return ActionResult.PASS;
        } else {
            // Similar to boat: allow mounting if not client and can start riding
            return !this.getWorld().isClient && player.startRiding(this) ? ActionResult.SUCCESS : ActionResult.PASS;
        }
    }
    
    @Override
    @Nullable
    public LivingEntity getControllingPassenger() {
        Entity firstPassenger = this.getFirstPassenger();
        return firstPassenger instanceof LivingEntity livingEntity ? livingEntity : super.getControllingPassenger();
    }
    
    @Override
    public Direction getMovementDirection() {
        return this.getHorizontalFacing().rotateYClockwise();
    }
    
    @Override
    public PositionInterpolator getInterpolator() {
        return this.interpolator;
    }
    
    @Override
    public void tick() {
        // Handle damage wobble (from VehicleEntity)
        if (this.getDamageWobbleTicks() > 0) {
            this.setDamageWobbleTicks(this.getDamageWobbleTicks() - 1);
        }
        if (this.getDamageWobbleStrength() > 0.0F) {
            this.setDamageWobbleStrength(this.getDamageWobbleStrength() - 1.0F);
        }
        
        super.tick();
        this.interpolator.tick();
        
        // Handle movement (similar to boat's tick logic)
        if (this.isLogicalSideForUpdatingMovement()) {
            if (!(this.getFirstPassenger() instanceof PlayerEntity)) {
                // Reset inputs if no player is controlling
                this.setInputs(false, false, false, false, false, false, false);
            }
            
            this.updateVelocity();
            this.move(MovementType.SELF, this.getVelocity());
        } else {
            this.setVelocity(Vec3d.ZERO);
        }
        
        this.tickBlockCollision();
    }
    
    /**
     * Update velocity based on inputs (inspired by boat's updateVelocity and updatePaddles)
     */
    private void updateVelocity() {
        if (this.hasPassengers() && this.getFirstPassenger() instanceof PlayerEntity) {
            // Handle rotation (similar to boat turning)
            if (this.pressingLeft) {
                this.yawVelocity -= ROTATION_SPEED;
            }
            if (this.pressingRight) {
                this.yawVelocity += ROTATION_SPEED;
            }
            
            this.setYaw(this.getYaw() + this.yawVelocity);
            this.yawVelocity *= DECELERATION; // Apply friction to rotation
            
            // Calculate movement acceleration
            float speedMultiplier = this.pressingBoost ? BOOST_MULTIPLIER : 1.0F;
            Vec3d acceleration = this.calculateAcceleration(speedMultiplier);
            
            // Apply acceleration to velocity
            Vec3d velocity = this.getVelocity();
            velocity = velocity.add(acceleration);
            
            // Apply deceleration (air friction)
            velocity = velocity.multiply(DECELERATION, DECELERATION, DECELERATION);
            
            // Clamp to max speeds
            velocity = this.clampVelocity(velocity, speedMultiplier);
            
            this.setVelocity(velocity);
        } else {
            // No passenger: decelerate
            Vec3d velocity = this.getVelocity();
            velocity = velocity.multiply(DECELERATION, DECELERATION, DECELERATION);
            this.setVelocity(velocity);
            this.yawVelocity *= DECELERATION;
        }
    }
    
    /**
     * Calculate acceleration vector from inputs (inspired by boat's movement calculation)
     */
    private Vec3d calculateAcceleration(float speedMultiplier) {
        float yawRad = -this.getYaw() * (float)(Math.PI / 180.0);
        
        // Forward/backward in facing direction
        Vec3d forwardVec = new Vec3d(
            MathHelper.sin(yawRad),
            0,
            MathHelper.cos(yawRad)
        );
        
        // Right/left perpendicular to facing direction  
        Vec3d rightVec = new Vec3d(
            MathHelper.cos(yawRad),
            0,
            MathHelper.sin(yawRad)
        );
        
        Vec3d acceleration = Vec3d.ZERO;
        
        // Horizontal movement
        if (this.pressingForward) {
            acceleration = acceleration.add(forwardVec.multiply(ACCELERATION * speedMultiplier));
        }
        if (this.pressingBack) {
            acceleration = acceleration.subtract(forwardVec.multiply(ACCELERATION * speedMultiplier * 0.5)); // Reverse slower
        }
        if (this.pressingLeft) {
            acceleration = acceleration.subtract(rightVec.multiply(ACCELERATION * speedMultiplier * 0.7)); // Strafe slower
        }
        if (this.pressingRight) {
            acceleration = acceleration.add(rightVec.multiply(ACCELERATION * speedMultiplier * 0.7));
        }
        
        // Vertical movement (unique to hovercraft)
        if (this.pressingUp) {
            acceleration = acceleration.add(0, ACCELERATION * speedMultiplier, 0);
        }
        if (this.pressingDown) {
            acceleration = acceleration.subtract(0, ACCELERATION * speedMultiplier, 0);
        }
        
        return acceleration;
    }
    
    /**
     * Clamp velocity to maximum speeds
     */
    private Vec3d clampVelocity(Vec3d velocity, float speedMultiplier) {
        // Clamp horizontal speed
        Vec3d horizontal = new Vec3d(velocity.x, 0, velocity.z);
        double horizontalSpeed = horizontal.length();
        double maxHorizontal = MAX_SPEED * speedMultiplier;
        
        if (horizontalSpeed > maxHorizontal) {
            horizontal = horizontal.normalize().multiply(maxHorizontal);
        }
        
        // Clamp vertical speed
        double maxVertical = MAX_VERTICAL_SPEED * speedMultiplier;
        double verticalSpeed = MathHelper.clamp(velocity.y, -maxVertical, maxVertical);
        
        return new Vec3d(horizontal.x, verticalSpeed, horizontal.z);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Static model - no animation controllers required
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }
    
    /**
     * Passenger attachment (similar to boat's implementation)
     */
    @Override
    protected Vec3d getPassengerAttachmentPos(Entity passenger, EntityDimensions dimensions, float scaleFactor) {
        return new Vec3d(0.0, dimensions.height() / 2.0, 0.0);
    }
    
    @Override
    public boolean collidesWith(Entity other) {
        return canCollide(this, other);
    }
    
    public static boolean canCollide(Entity entity, Entity other) {
        return (other.isCollidable() || other.isPushable()) && !entity.isConnectedThroughVehicle(other);
    }
    
    @Override
    public boolean isCollidable() {
        return true;
    }
    
    @Override
    public boolean isPushable() {
        return true;
    }
    
    @Override
    public void pushAwayFrom(Entity entity) {
        if (entity instanceof HovercraftEntity) {
            if (entity.getBoundingBox().minY < this.getBoundingBox().maxY) {
                super.pushAwayFrom(entity);
            }
        } else if (entity.getBoundingBox().minY <= this.getBoundingBox().minY) {
            super.pushAwayFrom(entity);
        }
    }
    
    @Override
    public void animateDamage(float yaw) {
        this.setDamageWobbleSide(-this.getDamageWobbleSide());
        this.setDamageWobbleTicks(10);
        this.setDamageWobbleStrength(this.getDamageWobbleStrength() * 11.0F);
    }
    
    @Override
    public boolean canHit() {
        return !this.isRemoved();
    }
    
    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        if (this.isInvulnerable() || this.isRemoved()) {
            return false;
        }
        
        // Use boat-style damage handling
        this.setDamageWobbleSide(-this.getDamageWobbleSide());
        this.setDamageWobbleTicks(10);
        this.setDamageWobbleStrength(this.getDamageWobbleStrength() + amount * 10.0F);
        
        boolean isCreativePlayer = source.getAttacker() instanceof PlayerEntity player && player.getAbilities().creativeMode;
        
        if (isCreativePlayer || this.getDamageWobbleStrength() > 40.0F) {
            this.removeAllPassengers();
            if (isCreativePlayer) {
                this.discard();
            } else {
                this.killAndDropSelf(world, source);
            }
        }
        
        return true;
    }
    
    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        // Minimal NBT needed - inputs are transient
    }
    
    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        // Minimal NBT needed - inputs are transient
    }
    
    @Override
    public boolean shouldDismountUnderwater() {
        return false; // Hovercrafts work underwater
    }
    
    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengerList().size() < 1;
    }
    
    @Override
    protected double getGravity() {
        return 0.0; // No gravity - hovers
    }
    
    @Override
    public boolean hasNoGravity() {
        return true;
    }
    
    @Override
    protected void applyGravity() {
        // No gravity - we control vertical movement manually
    }
    
    @Override
    public boolean isOnGround() {
        return false;
    }
    
    @Override
    public Vec3d updatePassengerForDismount(LivingEntity passenger) {
        // Reset inputs when passenger dismounts
        this.setInputs(false, false, false, false, false, false, false);
        return super.updatePassengerForDismount(passenger);
    }
}

