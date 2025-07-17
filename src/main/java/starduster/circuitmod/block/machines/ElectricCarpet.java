package starduster.circuitmod.block.machines;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import starduster.circuitmod.block.entity.ElectricCarpetBlockEntity;
import starduster.circuitmod.block.entity.ModBlockEntities;
import starduster.circuitmod.power.EnergyNetwork;
import starduster.circuitmod.power.IPowerConnectable;

public class ElectricCarpet extends BlockWithEntity {
    public static final MapCodec<ElectricCarpet> CODEC = createCodec(ElectricCarpet::new);
    private static final VoxelShape SHAPE = Block.createCuboidShape(0.0,0.0,0.0,16.0,1.0,16.0);

    @Override
    public MapCodec<ElectricCarpet> getCodec() {
        return CODEC;
    }

    public ElectricCarpet(Settings settings) {
        super(settings);
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new ElectricCarpetBlockEntity(pos, state);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return validateTicker(type, ModBlockEntities.ELECTRIC_CARPET_BLOCK_ENTITY, ElectricCarpetBlockEntity::tick);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient()) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof ElectricCarpetBlockEntity carpet) {
                // Display carpet status when right-clicked
                String status = carpet.isActive() ? "§aActive" : "§cInactive";
                player.sendMessage(net.minecraft.text.Text.literal("§6Electric Carpet Status: " + status), false);
                
                if (carpet.getNetwork() != null) {
                    EnergyNetwork network = carpet.getNetwork();
                    player.sendMessage(net.minecraft.text.Text.literal("§7Network: §9" + network.getNetworkId()), false);
                    player.sendMessage(net.minecraft.text.Text.literal("§7Energy demand: §e1§7 energy/tick"), false);
                } else {
                    player.sendMessage(net.minecraft.text.Text.literal("§cNot connected to any network!"), false);
                }
            }
        }
        return ActionResult.SUCCESS;
    }

    // Add method to connect to network when placed
    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        // The block entity will handle network connection in its tick method
    }

    @Override
    protected void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
        if (!moved) {
            // Handle network updates when this block is removed
            BlockEntity entity = world.getBlockEntity(pos);
            if (entity instanceof ElectricCarpetBlockEntity carpet) {
                // Use the new onRemoved method to properly handle network cleanup
                carpet.onRemoved();
            }
        }
        
        super.onStateReplaced(state, world, pos, moved);
    }
} 