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
import net.minecraft.inventory.Inventory;

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
        
        if (!world.isClient()) {
            // Connect to pipe network
            ItemNetworkManager.connectPipe(world, pos);
            
            // Notify the block entity that it was placed
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof ItemPipeBlockEntity itemPipe) {
                itemPipe.onPlaced();
            }
            
            Circuitmod.LOGGER.debug("[PIPE-PLACE] Item pipe placed at {}", pos);
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
        
        return validateTicker(type, ModBlockEntities.ITEM_PIPE, (tickWorld, pos, tickState, blockEntity) -> {
            ItemPipeBlockEntity.tick(tickWorld, pos, tickState, blockEntity);
        });
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof ItemPipeBlockEntity pipe) {
                // Display pipe and network info when right-clicked
                player.sendMessage(Text.literal("§6Item Pipe Status:"), false);
                
                ItemNetwork network = pipe.getNetwork();
                if (network != null) {
                    player.sendMessage(Text.literal("§7Network ID: §9" + network.getNetworkId()), false);
                    player.sendMessage(Text.literal("§7Connected to network with §9" + network.getSize() + "§7 pipes"), false);
                    
                    // Show inventory information
                    Map<BlockPos, Inventory> connectedInventories = network.getConnectedInventories();
                    player.sendMessage(Text.literal("§7Connected inventories: §9" + connectedInventories.size()), false);
                    
                    if (!connectedInventories.isEmpty()) {
                        player.sendMessage(Text.literal("§7Inventory positions:"), false);
                        int count = 0;
                        for (BlockPos invPos : connectedInventories.keySet()) {
                            if (count >= 3) { // Limit display to avoid spam
                                player.sendMessage(Text.literal("§7  ... and " + (connectedInventories.size() - 3) + " more"), false);
                                break;
                            }
                            Inventory inv = connectedInventories.get(invPos);
                            String invType = inv.getClass().getSimpleName();
                            player.sendMessage(Text.literal("§7  §9" + invPos + "§7 (" + invType + ")"), false);
                            count++;
                        }
                    }
                    
                    // Show current pipe state
                    if (!pipe.isEmpty()) {
                        ItemStack currentItem = pipe.getStack(0);
                        player.sendMessage(Text.literal("§7Current item: §9" + currentItem.getItem().getName().getString() + "§7 x" + currentItem.getCount()), false);
                    } else {
                        player.sendMessage(Text.literal("§7Current item: §9Empty"), false);
                    }
                    
                    // Show transfer cooldown
                    player.sendMessage(Text.literal("§7Transfer cooldown: §9" + pipe.getTransferCooldown()), false);
                    
                    // Show movement direction (specific to new system)
                    if (pipe.getMovementDirection() != null) {
                        player.sendMessage(Text.literal("§7Movement direction: §9" + pipe.getMovementDirection()), false);
                    } else {
                        player.sendMessage(Text.literal("§7Movement direction: §9None"), false);
                    }
                    
                } else {
                    player.sendMessage(Text.literal("§cNot connected to any network!"), false);
                    player.sendMessage(Text.literal("§7Try breaking and replacing the pipe"), false);
                }
            }
        }
        return ActionResult.SUCCESS;
    }

    @Override
    protected void handleScheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random, BlockEntity blockEntity) {
        // Let the normal tick system handle pipe ticking
        // The hop-by-hop system doesn't need forced scheduled ticks
        if (blockEntity instanceof ItemPipeBlockEntity itemPipe) {
            Circuitmod.LOGGER.debug("[PIPE-SCHEDULED-TICK] Scheduled tick for item pipe at {}", pos);
            // The normal tick method will be called by the ticker - no need to force it
        }
    }
}