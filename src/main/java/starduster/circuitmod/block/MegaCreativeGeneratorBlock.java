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
import starduster.circuitmod.block.entity.MegaCreativeGeneratorBlockEntity;
import starduster.circuitmod.block.entity.ModBlockEntities;
import starduster.circuitmod.block.entity.PowerCableBlockEntity;
import starduster.circuitmod.power.EnergyNetwork;
import starduster.circuitmod.power.IPowerConnectable;
import starduster.circuitmod.power.EnergyNetworkManager;
import starduster.circuitmod.Circuitmod;
import net.minecraft.server.world.ServerWorld;

public class MegaCreativeGeneratorBlock extends BlockWithEntity {
    public static final MapCodec<MegaCreativeGeneratorBlock> CODEC = createCodec(MegaCreativeGeneratorBlock::new);

    @Override
    public MapCodec<MegaCreativeGeneratorBlock> getCodec() {
        return CODEC;
    }

    public MegaCreativeGeneratorBlock(Settings settings) {
        super(settings);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new MegaCreativeGeneratorBlockEntity(pos, state);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof MegaCreativeGeneratorBlockEntity generator) {
                player.sendMessage(Text.literal("§aMega Creative Generator Status:"), false);
                if (generator.getNetwork() != null) {
                    player.sendMessage(Text.literal("§7Network ID: §a" + generator.getNetwork().getNetworkId()), false);
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

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (!world.isClient) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof MegaCreativeGeneratorBlockEntity generator) {
                boolean foundNetwork = false;
                for (Direction dir : Direction.values()) {
                    BlockPos neighborPos = pos.offset(dir);
                    BlockEntity be = world.getBlockEntity(neighborPos);
                    if (be instanceof IPowerConnectable) {
                        IPowerConnectable connectable = (IPowerConnectable) be;
                        EnergyNetwork network = connectable.getNetwork();
                        if (network != null) {
                            network.addBlock(pos, generator);
                            foundNetwork = true;
                            Circuitmod.LOGGER.info("Mega Generator at " + pos + " joined existing network " + network.getNetworkId());
                            break;
                        }
                    }
                }
                if (!foundNetwork) {
                    Circuitmod.LOGGER.info("No existing network found for mega generator at " + pos + ", checking for other connectables");
                    EnergyNetwork newNetwork = new EnergyNetwork();
                    newNetwork.addBlock(pos, generator);
                    for (Direction dir : Direction.values()) {
                        BlockPos neighborPos = pos.offset(dir);
                        BlockEntity be = world.getBlockEntity(neighborPos);
                        if (be instanceof IPowerConnectable && !(be instanceof PowerCableBlockEntity)) {
                            IPowerConnectable connectable = (IPowerConnectable) be;
                            if (connectable.getNetwork() == null && connectable.canConnectPower(dir.getOpposite()) && generator.canConnectPower(dir)) {
                                newNetwork.addBlock(neighborPos, connectable);
                                Circuitmod.LOGGER.info("Added neighbor at " + neighborPos + " to new network " + newNetwork.getNetworkId());
                            }
                        }
                    }
                    Circuitmod.LOGGER.info("Created new network " + newNetwork.getNetworkId() + " with mega generator at " + pos);
                }
            }
        }
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return validateTicker(type, ModBlockEntities.MEGA_CREATIVE_GENERATOR_BLOCK_ENTITY, MegaCreativeGeneratorBlockEntity::tick);
    }

    @Override
    protected void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
        if (!moved) {
            BlockEntity entity = world.getBlockEntity(pos);
            if (entity instanceof MegaCreativeGeneratorBlockEntity generator) {
                EnergyNetwork network = generator.getNetwork();
                if (network != null) {
                    network.removeBlock(pos);
                    Circuitmod.LOGGER.info("Mega Generator at " + pos + " removed from network " + network.getNetworkId());
                }
            }
        }
        super.onStateReplaced(state, world, pos, moved);
    }
} 