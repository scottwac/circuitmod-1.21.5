package starduster.circuitmod.block.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class ConstructorBlockEntity extends BlockEntity {

    public ConstructorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CONSTRUCTOR_BLOCK_ENTITY, pos, state);
    }
    
    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);

    }
    
    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);

    }

    // Add network handling logic to the tick method
    public static void tick(World world, BlockPos pos, BlockState state, ConstructorBlockEntity entity) {

    }
} 