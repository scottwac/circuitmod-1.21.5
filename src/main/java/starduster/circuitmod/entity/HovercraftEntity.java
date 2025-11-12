package starduster.circuitmod.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.PositionInterpolator;
import net.minecraft.entity.RideableInventory;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.vehicle.VehicleEntity;
import net.minecraft.entity.vehicle.VehicleInventory;
import net.minecraft.inventory.StackReference;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootTable;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.world.ServerWorld;
import starduster.circuitmod.screen.HovercraftScreenHandler;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.item.FuelRodItem;
import starduster.circuitmod.item.ModItems;

public class HovercraftEntity extends VehicleEntity implements GeoEntity, VehicleInventory, RideableInventory {
    // Tracked data for synchronization (similar to boat's paddle states and FurnaceMinecart's LIT)
    private static final TrackedData<Boolean> BOOST_ACTIVE = DataTracker.registerData(HovercraftEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> POWERED = DataTracker.registerData(HovercraftEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    
    // Movement constants (balanced like boat physics)
    private static final float ACCELERATION = 0.04F;
    private static final float DECELERATION = 0.9F; // Similar to boat on land
    private static final float MAX_SPEED = 0.35F; // Base max speed
    private static final float MAX_VERTICAL_SPEED = 0.25F;
    private static final float BOOST_MULTIPLIER = 1.5F;
    private static final float ROTATION_SPEED = 2.0F;
    
    // Fuel consumption rate (damage fuel rod every 20 ticks = 1 second, like reactor)
    private static final int FUEL_DAMAGE_INTERVAL = 20;
    
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
    
    // Inventory for fuel rod (single slot)
    private static final int INVENTORY_SIZE = 1;
    private DefaultedList<ItemStack> inventory = DefaultedList.ofSize(INVENTORY_SIZE, ItemStack.EMPTY);
    @Nullable
    private RegistryKey<LootTable> lootTable;
    private long lootTableSeed;
    
    // Fuel tracking
    private int tickCounter = 0;
    
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
        builder.add(POWERED, false);
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
        // First check if super has anything to say (e.g., leash interactions)
        if (!player.shouldCancelInteraction()) {
            ActionResult actionResult = super.interact(player, hand);
            if (actionResult != ActionResult.PASS) {
                return actionResult;
            }
        }
        
        // If player is sneaking, open the inventory
        if (player.shouldCancelInteraction()) {
            if (!this.getWorld().isClient) {
                ActionResult actionResult = this.open(player);
                if (actionResult.isAccepted()) {
                    this.emitGameEvent(GameEvent.CONTAINER_OPEN, player);
                }
                return actionResult;
            }
            return ActionResult.SUCCESS;
        }
        
        // If player is not sneaking and can mount, mount them
        if (this.canAddPassenger(player)) {
            if (!this.getWorld().isClient) {
                return player.startRiding(this) ? ActionResult.SUCCESS : ActionResult.PASS;
            }
            return ActionResult.SUCCESS;
        }
        
        // If can't mount (already has passenger), open inventory
        if (!this.getWorld().isClient) {
            ActionResult actionResult = this.open(player);
            if (actionResult.isAccepted()) {
                this.emitGameEvent(GameEvent.CONTAINER_OPEN, player);
            }
            return actionResult;
        }
        return ActionResult.SUCCESS;
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
        
        // Update powered state on server side (like FurnaceMinecart updating LIT status)
        if (!this.getWorld().isClient) {
            ItemStack fuelStack = this.getStack(0);
            boolean hasPower = !fuelStack.isEmpty() && fuelStack.getItem() == ModItems.FUEL_ROD && FuelRodItem.hasDurability(fuelStack);
            this.dataTracker.set(POWERED, hasPower);
            
            // Damage fuel rod on server side whenever powered
            if (hasPower) {
                this.damageFuelRod();
            }
        }
        
        // Handle movement (similar to boat's tick logic)
        if (this.isLogicalSideForUpdatingMovement()) {
            boolean hasPassenger = this.hasPassengers();
            boolean isPlayerPassenger = this.getFirstPassenger() instanceof PlayerEntity;
            
            // Debug logging
            if (!this.getWorld().isClient && this.age % 20 == 0) {
                Circuitmod.LOGGER.info("[HOVERCRAFT-DEBUG] Entity {}: hasPassengers={}, isPlayerPassenger={}, pressingForward={}, pressingLeft={}, pressingRight={}, pressingUp={}", 
                    this.getId(), hasPassenger, isPlayerPassenger, this.pressingForward, this.pressingLeft, this.pressingRight, this.pressingUp);
            }
            
            if (!isPlayerPassenger) {
                // Reset inputs if no player is controlling
                this.setInputs(false, false, false, false, false, false, false);
            }
            
            // Check power status (synced from server via tracked data)
            boolean powered = this.isPowered();
            
            // Only allow movement if powered
            if (powered) {
                this.updateVelocity();
                this.move(MovementType.SELF, this.getVelocity());
            } else {
                // Not powered: apply deceleration only
                Vec3d velocity = this.getVelocity();
                velocity = velocity.multiply(DECELERATION, DECELERATION, DECELERATION);
                this.setVelocity(velocity);
                this.yawVelocity *= DECELERATION;
            }
        } else {
            this.setVelocity(Vec3d.ZERO);
        }
        
        this.tickBlockCollision();
    }
    
    /**
     * Update velocity based on inputs (inspired by boat's updateVelocity and updatePaddles)
     */
    private void updateVelocity() {
        // Only process inputs if there's a passenger
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
            // No passenger: decelerate (but still allow powered state to be maintained)
            Vec3d velocity = this.getVelocity();
            velocity = velocity.multiply(DECELERATION, DECELERATION, DECELERATION);
            this.setVelocity(velocity);
            this.yawVelocity *= DECELERATION;
        }
    }
    
    /**
     * Calculate acceleration vector from inputs (inspired by boat's movement calculation)
     * Only applies acceleration if the hovercraft is powered (has fuel rod with durability)
     */
    private Vec3d calculateAcceleration(float speedMultiplier) {
        // Check power status - similar to FurnaceMinecart checking fuel > 0 before applying push
        if (!this.isPowered()) {
            return Vec3d.ZERO;
        }
        
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
        this.writeInventoryToNbt(nbt, this.getRegistryManager());
    }
    
    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        this.readInventoryFromNbt(nbt, this.getRegistryManager());
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
    
    /**
     * Check if hovercraft is powered (has fuel rod with durability)
     * Uses synced tracked data (like FurnaceMinecart's isLit())
     */
    public boolean isPowered() {
        return this.dataTracker.get(POWERED);
    }
    
    /**
     * Damage the fuel rod when the hovercraft is powered (similar to reactor)
     */
    private void damageFuelRod() {
        this.tickCounter++;
        
        // Only damage fuel rod every 20 ticks (once per second, like reactor)
        if (this.tickCounter % FUEL_DAMAGE_INTERVAL != 0) {
            return;
        }
        
        ItemStack fuelStack = this.getStack(0);
        if (!fuelStack.isEmpty() && fuelStack.getItem() == ModItems.FUEL_ROD) {
            // Damage the fuel rod by 1 second every 20 ticks
            boolean wasConsumed = FuelRodItem.reduceDurability(fuelStack, 1);
            
            if (wasConsumed) {
                // Fuel rod was consumed, clear the slot using VehicleInventory method
                this.setInventoryStack(0, ItemStack.EMPTY);
            } else {
                // Fuel rod was damaged but not consumed, update the slot using VehicleInventory method
                this.setInventoryStack(0, fuelStack);
            }
            
            this.markDirty();
        }
    }
    
    // VehicleInventory interface implementation
    @Override
    public DefaultedList<ItemStack> getInventory() {
        return this.inventory;
    }
    
    @Override
    public void resetInventory() {
        this.inventory = DefaultedList.ofSize(this.size(), ItemStack.EMPTY);
    }
    
    @Nullable
    @Override
    public RegistryKey<LootTable> getLootTable() {
        return this.lootTable;
    }
    
    @Override
    public void setLootTable(@Nullable RegistryKey<LootTable> lootTable) {
        this.lootTable = lootTable;
    }
    
    @Override
    public long getLootTableSeed() {
        return this.lootTableSeed;
    }
    
    @Override
    public void setLootTableSeed(long lootTableSeed) {
        this.lootTableSeed = lootTableSeed;
    }
    
    // RideableInventory interface implementation
    @Override
    public void openInventory(PlayerEntity player) {
        player.openHandledScreen(this);
        if (player.getWorld() instanceof ServerWorld) {
            this.emitGameEvent(GameEvent.CONTAINER_OPEN, player);
        }
    }
    
    // Inventory methods
    @Override
    public void clear() {
        this.clearInventory();
    }
    
    @Override
    public int size() {
        return INVENTORY_SIZE;
    }
    
    @Override
    public ItemStack getStack(int slot) {
        return this.getInventoryStack(slot);
    }
    
    @Override
    public ItemStack removeStack(int slot, int amount) {
        return this.removeInventoryStack(slot, amount);
    }
    
    @Override
    public ItemStack removeStack(int slot) {
        return this.removeInventoryStack(slot);
    }
    
    @Override
    public void setStack(int slot, ItemStack stack) {
        // Only accept fuel rods in slot 0 (validation also handled by screen handler)
        if (!stack.isEmpty() && stack.getItem() != ModItems.FUEL_ROD) {
            return;
        }
        this.setInventoryStack(slot, stack);
    }
    
    @Override
    public StackReference getStackReference(int mappedIndex) {
        return this.getInventoryStackReference(mappedIndex);
    }
    
    @Override
    public void markDirty() {
        // Mark as dirty for syncing
    }
    
    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return this.canPlayerAccess(player);
    }
    
    @Nullable
    @Override
    public ScreenHandler createMenu(int i, PlayerInventory playerInventory, PlayerEntity playerEntity) {
        if (this.lootTable != null && playerEntity.isSpectator()) {
            return null;
        } else {
            this.generateLoot(playerInventory.player);
            // Create custom screen handler with blocked slots
            return new HovercraftScreenHandler(i, playerInventory, this);
        }
    }
    
    public void generateLoot(@Nullable PlayerEntity player) {
        this.generateInventoryLoot(player);
    }
    
    @Override
    public void killAndDropSelf(ServerWorld world, DamageSource damageSource) {
        this.killAndDropItem(world, this.asItem());
        this.onBroken(damageSource, world, this);
    }
    
    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!this.getWorld().isClient && reason.shouldDestroy()) {
            ItemScatterer.spawn(this.getWorld(), this, this);
        }
        super.remove(reason);
    }
    
    @Override
    public void onClose(PlayerEntity player) {
        this.getWorld().emitGameEvent(GameEvent.CONTAINER_CLOSE, this.getPos(), GameEvent.Emitter.of(player));
    }
}

