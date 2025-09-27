package starduster.circuitmod.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
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
import starduster.circuitmod.item.ModItems;

public class HovercraftEntity extends VehicleEntity {
    private static final double HOVER_HEIGHT = 1.5; // Height above ground to hover
    private static final double HOVER_FORCE = 0.1; // Upward force to maintain hover
    private static final float MOVEMENT_SPEED = 0.4F; // Base movement speed
    
    private double targetHoverHeight = HOVER_HEIGHT;
    
    
    public HovercraftEntity(EntityType<? extends HovercraftEntity> entityType, World world) {
        super(entityType, world);
        this.intersectionChecked = true;
        this.targetHoverHeight = HOVER_HEIGHT; // Initialize to default
        System.out.println("[HOVERCRAFT] Created with target hover height: " + this.targetHoverHeight);
    }
    
    public HovercraftEntity(World world, double x, double y, double z) {
        this(ModEntityTypes.HOVERCRAFT, world);
        this.setPosition(x, y, z);
        this.targetHoverHeight = HOVER_HEIGHT; // Ensure it's set
        System.out.println("[HOVERCRAFT] Spawned at position: " + x + ", " + y + ", " + z + " with hover height: " + this.targetHoverHeight);
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
    public void tick() {
        // Handle movement and hover physics
        if (!this.getWorld().isClient) {
            this.updateMovement();
            this.updateHoverHeight();
        }
        
        // Call normal entity tick
        super.tick();
    }
    
    private void updateMovement() {
        if (this.hasPassengers() && this.getFirstPassenger() instanceof PlayerEntity player) {
            // Update rotation to match player's view
            this.setYaw(player.getYaw());
            this.setPitch(player.getPitch() * 0.5F);
            this.setHeadYaw(this.getYaw());
            
            // Calculate movement based on player input and view direction
            Vec3d currentVelocity = this.getVelocity();
            Vec3d newVelocity = this.calculateMovementVelocity(player, currentVelocity);
            this.setVelocity(newVelocity);
        } else {
            // No passenger - apply deceleration
            Vec3d velocity = this.getVelocity();
            this.setVelocity(velocity.multiply(0.8, 1.0, 0.8)); // Decelerate horizontally
        }
    }
    
    private Vec3d calculateMovementVelocity(PlayerEntity player, Vec3d currentVelocity) {
        // We'll use a simpler approach - check if player is moving based on velocity
        Vec3d playerMovement = player.getVelocity();
        double forward = 0;
        
        if (playerMovement.lengthSquared() > 0.01) {
            // Player is trying to move, convert their view direction to movement
            forward = playerMovement.length();
        }
        
        // Apply movement in the direction the player is looking
        Vec3d movementVec = Vec3d.ZERO;
        if (forward != 0) {
            float yawRadians = player.getYaw() * 0.017453292F;
            movementVec = new Vec3d(
                -Math.sin(yawRadians) * forward * MOVEMENT_SPEED,
                0,
                Math.cos(yawRadians) * forward * MOVEMENT_SPEED
            );
        }
        
        // Combine with current velocity (preserve Y component for hover physics)
        return new Vec3d(
            movementVec.x,
            currentVelocity.y, // Keep existing Y velocity for hover
            movementVec.z
        );
    }
    
    private void updateHoverHeight() {
        // Apply hover physics to maintain altitude
        Vec3d currentVelocity = this.getVelocity();
        Vec3d newVelocity = this.applyHoverPhysics(currentVelocity);
        this.setVelocity(newVelocity);
    }
    
    
    private Vec3d applyHoverPhysics(Vec3d currentVelocity) {
        // Get the ground level below the hovercraft
        double groundLevel = getGroundLevel();
        double currentHeight = this.getY() - groundLevel;
        
        // For now, just maintain the target hover height
        // TODO: Add space/shift controls through networking later if needed
        
        // Calculate desired vertical velocity to reach target hover height
        double heightDifference = this.targetHoverHeight - currentHeight;
        double desiredVerticalVelocity = heightDifference * HOVER_FORCE;
        
        // Smooth the vertical velocity transition
        double newVerticalVelocity = MathHelper.lerp(0.1, currentVelocity.y, desiredVerticalVelocity);
        
        // Clamp vertical velocity to prevent excessive movement
        newVerticalVelocity = MathHelper.clamp(newVerticalVelocity, -0.3, 0.3);
        
        // Return velocity with updated Y component
        return new Vec3d(currentVelocity.x, newVerticalVelocity, currentVelocity.z);
    }
    
    private double getGroundLevel() {
        // Find the highest solid block below the hovercraft
        int startY = (int) this.getY();
        
        for (int y = startY; y >= this.getWorld().getBottomY(); y--) {
            if (!this.getWorld().getBlockState(this.getBlockPos().withY(y)).isAir()) {
                return y + 1; // Return the block above the solid block
            }
        }
        
        return this.getWorld().getBottomY(); // Fallback to bedrock level
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
        nbt.putDouble("TargetHoverHeight", this.targetHoverHeight);
    }
    
    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        this.targetHoverHeight = MathHelper.clamp(nbt.getDouble("TargetHoverHeight", HOVER_HEIGHT), 0.5, 5.0);
        System.out.println("[HOVERCRAFT] Loaded from NBT with hover height: " + this.targetHoverHeight);
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
        // Make sure passengers don't fall when dismounting
        Vec3d dismountPos = super.updatePassengerForDismount(passenger);
        double groundLevel = getGroundLevel();
        if (dismountPos.y < groundLevel) {
            dismountPos = new Vec3d(dismountPos.x, groundLevel, dismountPos.z);
        }
        return dismountPos;
    }
}
