package starduster.circuitmod.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import starduster.circuitmod.block.entity.CreativeGeneratorBlockEntity;
import starduster.circuitmod.block.entity.ModBlockEntities;
import starduster.circuitmod.power.EnergyNetwork;
import starduster.circuitmod.power.IPowerConnectable;

public class CreativeGeneratorBlock extends BlockWithEntity {
    public static final MapCodec<CreativeGeneratorBlock> CODEC = createCodec(CreativeGeneratorBlock::new);

    @Override
    public MapCodec<CreativeGeneratorBlock> getCodec() {
        return CODEC;
    }

    public CreativeGeneratorBlock(Settings settings) {
        super(settings);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new CreativeGeneratorBlockEntity(pos, state);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof CreativeGeneratorBlockEntity generator) {
                // Display network info when right-clicked
                player.sendMessage(Text.literal("§aCreative Generator Status:"), false);
                
                if (generator.getNetwork() != null) {
                    player.sendMessage(Text.literal("§7Connected to network with " + generator.getNetwork().getSize() + " blocks"), false);
                    player.sendMessage(Text.literal("§7Energy stored: " + generator.getNetwork().getStoredEnergy() + "/" + generator.getNetwork().getMaxStorage()), false);
                    player.sendMessage(Text.literal("§7Last tick energy produced: " + generator.getNetwork().getLastTickEnergyProduced()), false);
                    player.sendMessage(Text.literal("§7Last tick energy consumed: " + generator.getNetwork().getLastTickEnergyConsumed()), false);
                } else {
                    player.sendMessage(Text.literal("§cNot connected to any network!"), false);
                }
            }
        }
        return ActionResult.SUCCESS;
    }

    // Add method to connect to network when placed
    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        
        if (!world.isClient) {
            // Check for adjacent power cables and connect to their network
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof CreativeGeneratorBlockEntity generator) {
                // Look for adjacent networks
                for (Direction dir : Direction.values()) {
                    BlockPos neighborPos = pos.offset(dir);
                    BlockEntity be = world.getBlockEntity(neighborPos);
                    
                    if (be instanceof IPowerConnectable) {
                        IPowerConnectable connectable = (IPowerConnectable) be;
                        EnergyNetwork network = connectable.getNetwork();
                        
                        if (network != null) {
                            // Found a network, join it
                            network.addBlock(pos, generator);
                            break;
                        }
                    }
                }
            }
        }
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return validateTicker(type, ModBlockEntities.CREATIVE_GENERATOR_BLOCK_ENTITY, CreativeGeneratorBlockEntity::tick);
    }
} 