package starduster.circuitmod.block.machines;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import org.jetbrains.annotations.Nullable;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.block.entity.BlueprintDeskBlockEntity;
import starduster.circuitmod.block.entity.ModBlockEntities;

/**
 * Primary blueprinter block that handles area scanning and blueprint creation.
 * Players place two of these to define the opposite corners of the area to scan.
 */
public class BlueprintDeskBlock extends BlockWithEntity {
    public static final MapCodec<BlueprintDeskBlock> CODEC = createCodec(BlueprintDeskBlock::new);
    public static final BooleanProperty SCANNING = BooleanProperty.of("scanning");
    public static final BooleanProperty CONNECTED = BooleanProperty.of("connected");
    
    @Override
    public MapCodec<BlueprintDeskBlock> getCodec() {
        return CODEC;
    }
    
    public BlueprintDeskBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState()
            .with(HorizontalFacingBlock.FACING, Direction.NORTH)
            .with(SCANNING, false)
            .with(CONNECTED, false));
    }
    
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        Direction facing = ctx.getHorizontalPlayerFacing().getOpposite();
        return this.getDefaultState().with(HorizontalFacingBlock.FACING, facing);
    }
    
    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(HorizontalFacingBlock.FACING);
        builder.add(SCANNING);
        builder.add(CONNECTED);
    }
    
    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new BlueprintDeskBlockEntity(pos, state);
    }
    
    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }
    
    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient()) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof BlueprintDeskBlockEntity blueprintDesk) {
                // Open the GUI or handle right-click actions
                player.openHandledScreen(blueprintDesk);
                return ActionResult.SUCCESS;
            }
        }
        return ActionResult.SUCCESS;
    }
    
    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        
        if (!world.isClient()) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof BlueprintDeskBlockEntity blueprintDesk) {
                // Try to find a partner desk
                blueprintDesk.findPartnerDesk();
                Circuitmod.LOGGER.info("[BLUEPRINT-DESK] Placed blueprint desk at {}", pos);
            }
        }
    }
    
    @Override
    public void onBroken(WorldAccess world, BlockPos pos, BlockState state) {
        if (!world.isClient()) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof BlueprintDeskBlockEntity blueprintDesk) {
                // Disconnect from partner
                blueprintDesk.disconnectFromPartner();
            }
        }
        super.onBroken(world, pos, state);
    }
    
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient() ? null : 
            validateTicker(type, ModBlockEntities.BLUEPRINT_DESK_BLOCK_ENTITY, BlueprintDeskBlockEntity::tick);
    }
} 