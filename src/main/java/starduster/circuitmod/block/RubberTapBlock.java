package starduster.circuitmod.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

public class RubberTapBlock extends BlockWithEntity implements BlockEntityProvider {
    public static final IntProperty FILL_LEVEL = IntProperty.of("fill_level",0,3);
    private static final VoxelShape BASE_SHAPE = Block.createCuboidShape(4.5, 0.0, 8.5, 11.5, 5.5, 16.0);
    private static final Map<Direction, VoxelShape> SHAPES = createShapes();

    private static Map<Direction, VoxelShape> createShapes() {
        Map<Direction, VoxelShape> map = new EnumMap<>(Direction.class);
        for (Direction dir : Direction.Type.HORIZONTAL) {
            map.put(dir, rotateShape(BASE_SHAPE, dir));
        }
        return map;
    }

    private static VoxelShape rotateShape(VoxelShape shape, Direction direction) {
        VoxelShape[] buffer = new VoxelShape[]{shape, VoxelShapes.empty()};

        // Calculate how many clockwise 90-degree rotations needed from NORTH
        int times;
        switch (direction) {
            case NORTH -> times = 0;
            case EAST  -> times = 1;
            case SOUTH -> times = 2;
            case WEST  -> times = 3;
            default    -> times = 0; // Should not happen
        }

        for (int i = 0; i < times; i++) {
            buffer[0].forEachBox((minX, minY, minZ, maxX, maxY, maxZ) -> {
                // Rotate 90° clockwise around Y axis
                buffer[1] = VoxelShapes.union(buffer[1], VoxelShapes.cuboid(
                        1.0 - maxZ, minY, minX,
                        1.0 - minZ, maxY, maxX
                ));
            });
            buffer[0] = buffer[1];
            buffer[1] = VoxelShapes.empty();
        }

        return buffer[0];
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        Direction facing = state.get(Properties.HORIZONTAL_FACING);
        return SHAPES.getOrDefault(facing, BASE_SHAPE);
    }

    protected RubberTapBlock(Settings settings) {
        super(settings);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        Direction facing = state.get(Properties.HORIZONTAL_FACING);
        BlockPos attachedPos = pos.offset(facing.getOpposite());
        BlockState attachedState = world.getBlockState(attachedPos);
        return attachedState.isSolidBlock(world, attachedPos);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(Properties.HORIZONTAL_FACING, FILL_LEVEL);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        Direction side = ctx.getSide();

        // Only allow placement on horizontal sides
        if (!side.getAxis().isHorizontal()) {
            return null;
        }

        Direction facing = side;
        BlockState state = this.getDefaultState()
                .with(Properties.HORIZONTAL_FACING, facing)
                .with(FILL_LEVEL, 0);

        return state.canPlaceAt(ctx.getWorld(), ctx.getBlockPos()) ? state : null;
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return null;
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return null;
    }
}
