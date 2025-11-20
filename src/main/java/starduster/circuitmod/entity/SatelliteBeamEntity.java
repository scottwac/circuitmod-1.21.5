package starduster.circuitmod.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

/**
 * Entity that represents a satellite mining beam from the sky.
 * This entity is purely visual and serves to render the beam effect.
 */
public class SatelliteBeamEntity extends Entity {
    private static final TrackedData<BlockPos> TARGET_POS = DataTracker.registerData(SatelliteBeamEntity.class, TrackedDataHandlerRegistry.BLOCK_POS);
    private static final TrackedData<Float> BEAM_HEIGHT = DataTracker.registerData(SatelliteBeamEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> BEAM_RADIUS = DataTracker.registerData(SatelliteBeamEntity.class, TrackedDataHandlerRegistry.FLOAT);
    
    private int lifetime = 20; // How many ticks the beam lasts (default 1 second)
    private int age = 0;
    
    public SatelliteBeamEntity(EntityType<?> type, World world) {
        super(type, world);
        this.noClip = true;
    }
    
    public SatelliteBeamEntity(World world, BlockPos targetPos, float beamHeight, float beamRadius) {
        this(ModEntityTypes.SATELLITE_BEAM, world);
        this.lifetime = Integer.MAX_VALUE / 4;
        configureBeam(targetPos, beamHeight, beamRadius);
    }
    
    @Override
    public void tick() {
        super.tick();
        age++;
        
        // Remove entity after lifetime expires
        if (age >= lifetime) {
            this.discard();
        }
    }
    
    public BlockPos getTargetPos() {
        return this.dataTracker.get(TARGET_POS);
    }
    
    public float getBeamHeight() {
        return this.dataTracker.get(BEAM_HEIGHT);
    }
    
    public float getBeamRadius() {
        return this.dataTracker.get(BEAM_RADIUS);
    }
    
    public int getAge() {
        return age;
    }
    
    public int getLifetime() {
        return lifetime;
    }
    
    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(TARGET_POS, BlockPos.ORIGIN);
        builder.add(BEAM_HEIGHT, 32f);
        builder.add(BEAM_RADIUS, 0.5f);
    }
    
    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        // Cannot be damaged
        return false;
    }
    
    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        nbt.getInt("TargetX").ifPresent(x -> {
            nbt.getInt("TargetY").ifPresent(y -> {
                nbt.getInt("TargetZ").ifPresent(z -> setTargetPos(new BlockPos(x, y, z)));
            });
        });
        nbt.getInt("Lifetime").ifPresent(value -> lifetime = value);
        nbt.getInt("Age").ifPresent(value -> age = value);
        nbt.getFloat("BeamHeight").ifPresent(this::setBeamHeight);
        nbt.getFloat("BeamRadius").ifPresent(this::setBeamRadius);
    }
    
    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        BlockPos trackedPos = getTargetPos();
        if (trackedPos != null) {
            nbt.putInt("TargetX", trackedPos.getX());
            nbt.putInt("TargetY", trackedPos.getY());
            nbt.putInt("TargetZ", trackedPos.getZ());
        }
        nbt.putInt("Lifetime", lifetime);
        nbt.putInt("Age", age);
        nbt.putFloat("BeamHeight", getBeamHeight());
        nbt.putFloat("BeamRadius", getBeamRadius());
    }
    
    private void setTargetPos(BlockPos pos) {
        BlockPos safePos = pos == null ? BlockPos.ORIGIN : pos;
        this.dataTracker.set(TARGET_POS, safePos);
        updateBoundingBox();
    }
    
    private void setBeamHeight(float height) {
        this.dataTracker.set(BEAM_HEIGHT, Math.max(16f, height));
        updateBoundingBox();
    }
    
    private void setBeamRadius(float radius) {
        this.dataTracker.set(BEAM_RADIUS, Math.max(0.5f, radius));
        updateBoundingBox();
    }
    
    public void configureBeam(BlockPos targetPos, float height, float radius) {
        BlockPos safePos = targetPos == null ? BlockPos.ORIGIN : targetPos;
        this.dataTracker.set(TARGET_POS, safePos);
        this.dataTracker.set(BEAM_HEIGHT, Math.max(16f, height));
        this.dataTracker.set(BEAM_RADIUS, Math.max(0.5f, radius));
        updateBoundingBox();
    }
    
    private void updateBoundingBox() {
        BlockPos pos = this.dataTracker.get(TARGET_POS);
        float height = this.dataTracker.get(BEAM_HEIGHT);
        float radius = this.dataTracker.get(BEAM_RADIUS);
        double centerX = pos.getX() + 0.5;
        double topY = pos.getY() + 0.5;
        double bottomY = topY - height;
        double centerZ = pos.getZ() + 0.5;
        this.setPosition(centerX, topY, centerZ);
        this.setBoundingBox(new Box(
            centerX - radius,
            bottomY,
            centerZ - radius,
            centerX + radius,
            topY,
            centerZ + radius
        ));
    }
    
    public void setLifetime(int lifetime) {
        this.lifetime = lifetime;
    }
}

