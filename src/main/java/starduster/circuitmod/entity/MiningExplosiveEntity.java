package starduster.circuitmod.entity;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import starduster.circuitmod.item.ModItems;

public class MiningExplosiveEntity extends ThrownItemEntity {
    
    public MiningExplosiveEntity(EntityType<? extends MiningExplosiveEntity> entityType, World world) {
        super(entityType, world);
    }

    public MiningExplosiveEntity(World world, LivingEntity owner, ItemStack stack) {
        super(ModEntityTypes.MINING_EXPLOSIVE, owner, world, stack);
    }

    public MiningExplosiveEntity(World world, double x, double y, double z, ItemStack stack) {
        super(ModEntityTypes.MINING_EXPLOSIVE, x, y, z, world, stack);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.MINING_EXPLOSIVE;
    }

    private ParticleEffect getParticleParameters() {
        ItemStack itemStack = this.getStack();
        return (ParticleEffect)(itemStack.isEmpty() ? ParticleTypes.ITEM_SNOWBALL : new ItemStackParticleEffect(ParticleTypes.ITEM, itemStack));
    }

    @Override
    public void handleStatus(byte status) {
        if (status == EntityStatuses.PLAY_DEATH_SOUND_OR_ADD_PROJECTILE_HIT_PARTICLES) {
            ParticleEffect particleEffect = this.getParticleParameters();

            for (int i = 0; i < 8; i++) {
                this.getWorld().addParticleClient(particleEffect, this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
            }
        }
    }

    @Override
    protected void onCollision(HitResult hitResult) {
        super.onCollision(hitResult);
        if (!this.getWorld().isClient) {
            // Create explosion sound and particles
            this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), 
                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.NEUTRAL, 1.0F, 1.0F);
            
            // Mine blocks in a 3x3x3 radius
            mineBlocksInRadius(hitResult.getPos(), 2);
            
            // Add explosion particles
            if (this.getWorld() instanceof ServerWorld serverWorld) {
                serverWorld.spawnParticles(ParticleTypes.EXPLOSION, 
                    this.getX(), this.getY(), this.getZ(), 
                    1, 0.0, 0.0, 0.0, 0.0);
            }
            
            this.getWorld().sendEntityStatus(this, EntityStatuses.PLAY_DEATH_SOUND_OR_ADD_PROJECTILE_HIT_PARTICLES);
            this.discard();
        }
    }
    
    private void mineBlocksInRadius(Vec3d center, int radius) {
        World world = this.getWorld();
        BlockPos centerPos = BlockPos.ofFloored(center);
        
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = centerPos.add(x, y, z);
                    BlockState state = world.getBlockState(pos);
                    
                    // Skip air blocks and bedrock
                    if (state.isAir() || state.getHardness(world, pos) < 0) {
                        continue;
                    }
                    
                    // Drop the block as an item
                    if (world instanceof ServerWorld serverWorld) {
                        // Use the block's loot table to get proper drops
                        state.getBlock().onBreak(serverWorld, pos, state, null);
                        Block.dropStacks(state, serverWorld, pos, null, null, ItemStack.EMPTY);
                    }
                    
                    // Replace block with air
                    world.setBlockState(pos, net.minecraft.block.Blocks.AIR.getDefaultState(), 3);
                }
            }
        }
    }
} 