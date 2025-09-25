package starduster.circuitmod.block.machines;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.block.entity.DrillBlockEntity;
import starduster.circuitmod.block.entity.ModBlockEntities;
import starduster.circuitmod.power.EnergyNetworkManager;
import starduster.circuitmod.power.IPowerConnectable;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;

public class DrillBlock extends BlockWithEntity {
    // Use the FACING property from HorizontalFacingBlock
    public static final MapCodec<DrillBlock> CODEC = createCodec(DrillBlock::new);
    public static final BooleanProperty RUNNING = BooleanProperty.of("running");


    @Override
    public MapCodec<DrillBlock> getCodec() {
        return CODEC;
    }

    public DrillBlock(Settings settings) {
        super(settings);
        // Set the default facing direction to north
        this.setDefaultState(this.stateManager.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH));
        setDefaultState(getDefaultState().with(RUNNING, false));
    }


    // Create the block entity
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new DrillBlockEntity(pos, state);
    }

    // Set the facing direction when placed
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        Direction facing = ctx.getHorizontalPlayerFacing().getOpposite();
        Circuitmod.LOGGER.info("Drill placed with facing direction: " + facing);
        return this.getDefaultState().with(HorizontalFacingBlock.FACING, facing);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.onPlaced(world, pos, state, placer, stack);
        if (!world.isClient) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof DrillBlockEntity drill) {
                int fortuneLevel = 0;
                Registry<Enchantment> reg = world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
                Enchantment ench = reg.get(Identifier.ofVanilla("fortune"));
                if (ench != null) {
                    int raw = reg.getRawId(ench);
                    java.util.Optional<RegistryEntry.Reference<Enchantment>> ref = reg.getEntry(raw);
                    if (ref.isPresent()) {
                        fortuneLevel = EnchantmentHelper.getLevel(ref.get(), stack);
                    }
                }
                drill.setFortuneLevel(fortuneLevel);
                
                // Use the standardized network connection method
                if (be instanceof IPowerConnectable) {
                    EnergyNetworkManager.onBlockPlaced(world, pos, (IPowerConnectable) be);
                    Circuitmod.LOGGER.info("[DRILL] Block placed at {}, attempting network connection", pos);
                }
            }
        }
    }

    // Make sure to append the facing property to the block state
    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(HorizontalFacingBlock.FACING);
        builder.add(RUNNING);
    }

    // Update onUse to open the GUI
    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient) {
            // Access the block entity
            BlockEntity blockEntity = world.getBlockEntity(pos);
            
            if (blockEntity instanceof DrillBlockEntity) {
                Direction facing = state.get(HorizontalFacingBlock.FACING);
                Circuitmod.LOGGER.info("Drill at " + pos + " has facing direction: " + facing);
                
                // Open the screen handler through the named screen handler factory
                player.openHandledScreen((DrillBlockEntity) blockEntity);
            }
        }
        return ActionResult.SUCCESS;
    }


    // Allow the block to be rendered normally (not invisible like typical BlockEntities)
    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    // Set up ticker for the block entity
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if(world.isClient()) {
            return null;
        }
        return validateTicker(type, ModBlockEntities.DRILL_BLOCK_ENTITY,
                (world1, pos, state1, blockEntity) -> blockEntity.tick(world1, pos, state1, blockEntity));
    }
    
    @Override
    protected BlockState getStateForNeighborUpdate(
        BlockState state,
        net.minecraft.world.WorldView world,
        net.minecraft.world.tick.ScheduledTickView tickView,
        BlockPos pos,
        Direction direction,
        BlockPos neighborPos,
        BlockState neighborState,
        net.minecraft.util.math.random.Random random
    ) {
        // Schedule a block tick to handle network connections (avoid modifying world during neighbor update)
        if (world instanceof World realWorld && !realWorld.isClient()) {
            realWorld.scheduleBlockTick(pos, this, 1);
        }
        
        return state;
    }
    
    @Override
    public void scheduledTick(BlockState state, net.minecraft.server.world.ServerWorld world, BlockPos pos, net.minecraft.util.math.random.Random random) {
        super.scheduledTick(state, world, pos, random);
        
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof IPowerConnectable) {
            // Try to connect to networks when neighbors change
            EnergyNetworkManager.findAndJoinNetwork(world, pos, (IPowerConnectable) blockEntity);
            Circuitmod.LOGGER.info("[DRILL] Scheduled tick at {}, checking network connections", pos);
        }
    }

    @Override
    protected void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
        if (!moved) {
            // Only handle network removal if the block is actually being removed, not moved
            EnergyNetworkManager.onBlockRemoved(world, pos);
        }
        
        super.onStateReplaced(state, world, pos, moved);
    }
} 