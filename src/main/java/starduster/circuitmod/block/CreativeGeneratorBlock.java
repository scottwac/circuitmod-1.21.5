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
import starduster.circuitmod.block.entity.PowerCableBlockEntity;
import starduster.circuitmod.power.EnergyNetwork;
import starduster.circuitmod.power.IPowerConnectable;
import starduster.circuitmod.Circuitmod;

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

    // Add method to connect to network when placed
    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        
        if (!world.isClient) {
            // Check for adjacent power cables and connect to their network
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof CreativeGeneratorBlockEntity generator) {
                // First, try to find an existing network to join
                boolean foundNetwork = false;
                
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
                            foundNetwork = true;
                            Circuitmod.LOGGER.info("Generator at " + pos + " joined existing network " + network.getNetworkId());
                            break;
                        }
                    }
                }
                
                // If no existing network was found, create a new one with any adjacent IPowerConnectable blocks
                if (!foundNetwork) {
                    Circuitmod.LOGGER.info("No existing network found for generator at " + pos + ", checking for other connectables");
                    
                    // Create a new network
                    EnergyNetwork newNetwork = new EnergyNetwork();
                    newNetwork.addBlock(pos, generator);
                    
                    // Try to add adjacent connectables to this new network
                    for (Direction dir : Direction.values()) {
                        BlockPos neighborPos = pos.offset(dir);
                        BlockEntity be = world.getBlockEntity(neighborPos);
                        
                        if (be instanceof IPowerConnectable && !(be instanceof PowerCableBlockEntity)) {
                            IPowerConnectable connectable = (IPowerConnectable) be;
                            
                            // Only add if it doesn't already have a network
                            if (connectable.getNetwork() == null && 
                                // Check both sides can connect
                                connectable.canConnectPower(dir.getOpposite()) && 
                                generator.canConnectPower(dir)) {
                                
                                newNetwork.addBlock(neighborPos, connectable);
                                Circuitmod.LOGGER.info("Added neighbor at " + neighborPos + " to new network " + newNetwork.getNetworkId());
                            }
                        }
                    }
                    
                    Circuitmod.LOGGER.info("Created new network " + newNetwork.getNetworkId() + " with generator at " + pos);
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