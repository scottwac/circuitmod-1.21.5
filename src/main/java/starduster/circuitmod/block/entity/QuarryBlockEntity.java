package starduster.circuitmod.block.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import starduster.circuitmod.Circuitmod;

public class QuarryBlockEntity extends BlockEntity {
    // The current progress of the quarry (example property)
    private int progress = 0;
    private static final int MAX_PROGRESS = 100;

    public QuarryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.QUARRY_BLOCK_ENTITY, pos, state);
    }

    // Save data to NBT when the block is saved
    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        nbt.putInt("progress", this.progress);
    }

    // Load data from NBT when the block is loaded
    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        this.progress = nbt.getInt("progress").orElse(0);
    }

    // The tick method called by the ticker in QuarryBlock
    public static void tick(World world, BlockPos pos, BlockState state, QuarryBlockEntity blockEntity) {
        if (world.isClient()) {
            return;
        }

        // Simple progress increment logic
        if (blockEntity.progress < MAX_PROGRESS) {
            blockEntity.progress++;
            blockEntity.markDirty();

            // Log every 20 ticks (about 1 second)
            if (blockEntity.progress % 20 == 0) {
                Circuitmod.LOGGER.info("Quarry progress: " + blockEntity.progress + "/" + MAX_PROGRESS);
            }
        } else {
            // Reset progress when complete
            blockEntity.progress = 0;
            Circuitmod.LOGGER.info("Quarry operation complete!");
        }
    }
} 