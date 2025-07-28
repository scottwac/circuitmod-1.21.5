package starduster.circuitmod.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import net.minecraft.world.tick.ScheduledTickView;
import org.jetbrains.annotations.Nullable;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.block.entity.ModBlockEntities;
import starduster.circuitmod.block.entity.ItemPipeBlockEntity;
import starduster.circuitmod.item.network.ItemNetworkManager;
import starduster.circuitmod.item.network.ItemNetwork;
import starduster.circuitmod.power.EnergyNetwork;
import starduster.circuitmod.power.IPowerConnectable;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;

import java.util.List;
import java.util.Map;

public class ItemPipeBlock extends BasePipeBlock {
    public static final MapCodec<ItemPipeBlock> CODEC = createCodec(ItemPipeBlock::new);

    @Override
    public MapCodec<ItemPipeBlock> getCodec() {
        return CODEC;
    }

    public ItemPipeBlock(Settings settings) {
        super(settings);
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new ItemPipeBlockEntity(pos, state);
    }
    
    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        
        // Connect to item network
        if (!world.isClient && world.getBlockEntity(pos) instanceof ItemPipeBlockEntity blockEntity) {
            blockEntity.onPlaced();
            
            // Force a network rescan to ensure proper connectivity after rebuilding
            Circuitmod.LOGGER.info("[PIPE-PLACE] Pipe placed at {}, forcing network rescan", pos);
            ItemNetwork network = blockEntity.getNetwork();
            if (network != null) {
                network.forceRescanAllInventories();
            }
        }
    }
    
    @Override
    protected void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
        if (!moved) {
            // Disconnect from item network before removal
            BlockEntity entity = world.getBlockEntity(pos);
            if (entity instanceof ItemPipeBlockEntity blockEntity) {
                blockEntity.onRemoved();
            }
        }
        
        super.onStateReplaced(state, world, pos, moved);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient) {
            return null;
        }
        
        // Circuitmod.LOGGER.info("[PIPE] Registering ticker for ItemPipeBlockEntity at {}", world.getTime());
        return validateTicker(type, ModBlockEntities.ITEM_PIPE, (tickWorld, pos, tickState, blockEntity) -> {
            // Circuitmod.LOGGER.info("[PIPE] Ticking pipe at {}", pos);
            ItemPipeBlockEntity.tick(tickWorld, pos, tickState, blockEntity);
        });
    }
    


    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof ItemPipeBlockEntity pipe) {
                // Force a network rescan for debugging
                if (pipe.getNetwork() != null) {
                    Circuitmod.LOGGER.info("[PIPE-DEBUG] Player {} right-clicked pipe at {}, forcing network rescan", 
                        player.getName().getString(), pos);
                    pipe.getNetwork().forceRescanAllInventories();
                    player.sendMessage(net.minecraft.text.Text.literal("§6Network rescanned! Check logs for details."), false);
                } else {
                    Circuitmod.LOGGER.info("[PIPE-DEBUG] Pipe at {} has no network", pos);
                    player.sendMessage(net.minecraft.text.Text.literal("§cPipe has no network!"), false);
                }
            }
        }
        return ActionResult.SUCCESS;
    }

    @Override
    protected void handleScheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random, BlockEntity blockEntity) {
        // Force an immediate tick of the item pipe
        if (blockEntity instanceof ItemPipeBlockEntity itemPipe) {
            ItemPipeBlockEntity.tick(world, pos, state, itemPipe);
        }
    }


} 