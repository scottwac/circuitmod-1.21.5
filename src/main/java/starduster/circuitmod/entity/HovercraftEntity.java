package starduster.circuitmod.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.VehicleEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.item.ModItems;

public class HovercraftEntity extends VehicleEntity implements GeoEntity {
    private static final float MOVEMENT_SPEED = 0.35F; // Base movement speed multiplier
    private static final float VERTICAL_SPEED = 0.25F; // Vertical movement speed
    private static final float DECELERATION = 0.85F; // Deceleration when no input
    private static final float MAX_SPEED = 1.5F; // Maximum horizontal speed
    private static final float MAX_VERTICAL_SPEED = 0.8F; // Maximum vertical speed
    private static final float BOOST_MULTIPLIER = 2.0F; // Speed multiplier when boosting
    
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    
    // Input states (set by server from client packets)
    private boolean inputForward = false;
    private boolean inputBackward = false;
    private boolean inputLeft = false;
    private boolean inputRight = false;
    private boolean inputUp = false;
    private boolean inputDown = false;
    private boolean inputBoost = false;
    
    public HovercraftEntity(EntityType<? extends HovercraftEntity> entityType, World world) {
        super(entityType, world);
        this.intersectionChecked = true;
    }
    
    public HovercraftEntity(World world, double x, double y, double z) {
        this(ModEntityTypes.HOVERCRAFT, world);
        this.setPosition(x, y, z);
    }
    
    /**
     * Set input states from client packet
     */
    public void setInputs(boolean forward, boolean backward, boolean left, boolean right, boolean up, boolean down, boolean boost) {
        this.inputForward = forward;
        this.inputBackward = backward;
        this.inputLeft = left;
        this.inputRight = right;
        this.inputUp = up;
        this.inputDown = down;
        this.inputBoost = boost;
    }
    
    /**
     * Get current input states (for debugging)
     */
    public String getInputStates() {
        return String.format("F=%s B=%s L=%s R=%s U=%s D=%s BOOST=%s", 
            inputForward, inputBackward, inputLeft, inputRight, inputUp, inputDown, inputBoost);
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
        if (player.shouldCancelInteraction()) {
            return ActionResult.PASS;
        }
        
        if (!this.getWorld().isClient) {
            if (this.hasPassengers()) {
                // If someone is already riding, they get off
                this.removeAllPassengers();
                return ActionResult.SUCCESS;
            } else {
                // Player gets on
                return player.startRiding(this) ? ActionResult.SUCCESS : ActionResult.PASS;
            }
        }
        
        return ActionResult.SUCCESS;
    }
    
    @Override
    public Vec3d getMovement() {
        // Return our custom velocity for movement processing
        // This ensures Entity.tick() uses our velocity for movement
        return this.getVelocity();
    }
    
    @Override
    @Nullable
    public LivingEntity getControllingPassenger() {
        // Return null to prevent Entity from using passenger's movement
        // We handle movement ourselves based on input packets
        return null;
    }
    
    @Override
    public void tick() {
        if (!this.getWorld().isClient) {
            tickServer();
        }
        // Call super.tick() - this will process the velocity we set above via getMovement()
        super.tick();
    }

    private void tickServer() {
        if (this.hasPassengers() && this.getFirstPassenger() instanceof PlayerEntity player) {
            handleMovement(player, true, true, true);
        } else {
            applyServerDeceleration();
        }
    }

    private void handleMovement(PlayerEntity player, boolean applyMove, boolean markVelocityDirty, boolean logInputs) {
        // Update rotation to match player's view
        this.setYaw(player.getYaw());
        this.setPitch(MathHelper.clamp(player.getPitch() * 0.5F, -45.0F, 45.0F));
        this.setHeadYaw(this.getYaw());

        double speedMultiplier = inputBoost ? BOOST_MULTIPLIER : 1.0D;

        Vec3d currentVelocity = this.getVelocity();
        Vec3d calculatedMovement = calculateMovementInput(player);
        Vec3d newVelocity = applyMovementAcceleration(currentVelocity, calculatedMovement, speedMultiplier);
        newVelocity = clampVelocity(newVelocity, speedMultiplier);

        this.setVelocity(newVelocity);
        if (markVelocityDirty) {
            this.velocityDirty = true;
        }

        if (applyMove && newVelocity.lengthSquared() > 0.0001) {
            this.move(MovementType.SELF, newVelocity);
        }

        if (logInputs && this.age % 20 == 0 && (inputForward || inputBackward || inputLeft || inputRight || inputUp || inputDown || inputBoost)) {
            Circuitmod.LOGGER.info("[HOVERCRAFT] Pos: ({}, {}, {}) Vel: ({}, {}, {}) Inputs: {}", 
                String.format("%.2f", this.getX()), String.format("%.2f", this.getY()), String.format("%.2f", this.getZ()),
                String.format("%.2f", newVelocity.x), String.format("%.2f", newVelocity.y), String.format("%.2f", newVelocity.z),
                getInputStates());
        }
    }

    private void applyServerDeceleration() {
        Vec3d velocity = this.getVelocity();
        Vec3d deceleratedVelocity = velocity.multiply(DECELERATION, DECELERATION, DECELERATION);
        deceleratedVelocity = clampVelocity(deceleratedVelocity, 1.0D);
        this.setVelocity(deceleratedVelocity);
        this.velocityDirty = true;

        if (deceleratedVelocity.lengthSquared() > 0.0001) {
            this.move(MovementType.SELF, deceleratedVelocity);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Static model - no animation controllers required
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }
    
    private Vec3d calculateMovementInput(PlayerEntity player) {
        float yaw = player.getYaw();
        
        // Convert yaw to radians
        float yawRad = yaw * 0.017453292F;
        
        // Calculate forward direction vector (in horizontal plane)
        Vec3d forwardVec = new Vec3d(
            -MathHelper.sin(yawRad),
            0,
            MathHelper.cos(yawRad)
        ).normalize();
        
        // Calculate right direction vector (perpendicular to forward)
        Vec3d rightVec = new Vec3d(
            MathHelper.cos(yawRad),
            0,
            MathHelper.sin(yawRad)
        ).normalize();
        
        // Calculate up direction (always vertical)
        Vec3d upVec = new Vec3d(0, 1, 0);
        
        // Build movement vector from inputs
        Vec3d movement = Vec3d.ZERO;
        
        // Forward/backward movement
        if (inputForward) {
            movement = movement.add(forwardVec);
        }
        if (inputBackward) {
            movement = movement.subtract(forwardVec);
        }
        
        // Left/right movement (strafe)
        if (inputLeft) {
            movement = movement.add(rightVec);
        }
        if (inputRight) {
            movement = movement.subtract(rightVec);
        }
        
        // Vertical movement
        if (inputUp) {
            movement = movement.add(upVec);
        }
        if (inputDown) {
            movement = movement.subtract(upVec);
        }
        
        // Normalize if there's any movement to maintain consistent speed
        if (movement.lengthSquared() > 0.01) {
            movement = movement.normalize();
        }
        
        return movement;
    }
    
    private Vec3d applyMovementAcceleration(Vec3d currentVelocity, Vec3d movementInput, double speedMultiplier) {
        // Separate horizontal and vertical components
        Vec3d horizontalInput = new Vec3d(movementInput.x, 0, movementInput.z);
        double verticalInput = movementInput.y;
        
        // Apply horizontal acceleration
        Vec3d currentHorizontal = new Vec3d(currentVelocity.x, 0, currentVelocity.z);
        Vec3d desiredHorizontal = horizontalInput.multiply(MOVEMENT_SPEED * speedMultiplier);
        
        // Interpolate horizontal velocity towards desired velocity
        Vec3d newHorizontal = currentHorizontal.multiply(DECELERATION).add(desiredHorizontal.multiply(1.0 - DECELERATION));
        
        // Apply vertical acceleration
        double desiredVertical = verticalInput * VERTICAL_SPEED * speedMultiplier;
        double newVertical = currentVelocity.y * DECELERATION + desiredVertical * (1.0 - DECELERATION);
        
        return new Vec3d(newHorizontal.x, newVertical, newHorizontal.z);
    }
    
    private Vec3d clampVelocity(Vec3d velocity, double speedMultiplier) {
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
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        if (this.isInvulnerable() || this.isRemoved()) {
            return false;
        }
        
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
        // Input states don't need to be saved - they're reset when player dismounts
    }
    
    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        // Input states don't need to be loaded - they're reset when player dismounts
    }
    
    
    @Override
    public boolean shouldDismountUnderwater() {
        return false; // Don't auto-dismount underwater
    }
    
    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengerList().size() < 1; // Only allow one passenger
    }
    
    @Override
    public boolean canHit() {
        return !this.isRemoved();
    }
    
    @Override
    public boolean isPushable() {
        return true;
    }
    
    @Override
    public boolean isCollidable() {
        return true;
    }
    
    @Override
    protected double getGravity() {
        return 0.0; // Override gravity since we handle it ourselves
    }
    
    @Override
    public boolean hasNoGravity() {
        return true; // Disable gravity completely
    }
    
    @Override
    protected void applyGravity() {
        // Do nothing - we handle our own hovering physics
    }
    
    @Override
    public boolean isOnGround() {
        return false; // Always consider airborne
    }
    
    @Override
    public Vec3d updatePassengerForDismount(LivingEntity passenger) {
        // Reset inputs when passenger dismounts
        this.setInputs(false, false, false, false, false, false, false);
        return super.updatePassengerForDismount(passenger);
    }
}
