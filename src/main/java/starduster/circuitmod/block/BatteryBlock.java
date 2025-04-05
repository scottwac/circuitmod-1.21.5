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
import starduster.circuitmod.block.entity.BatteryBlockEntity;
import starduster.circuitmod.block.entity.ModBlockEntities;
import starduster.circuitmod.block.entity.PowerCableBlockEntity;
import starduster.circuitmod.power.EnergyNetwork;
import starduster.circuitmod.power.IPowerConnectable;
import starduster.circuitmod.Circuitmod;
import net.minecraft.server.world.ServerWorld;

public class BatteryBlock extends BlockWithEntity {
    public static final MapCodec<BatteryBlock> CODEC = createCodec(BatteryBlock::new);

    @Override
    public MapCodec<BatteryBlock> getCodec() {
        return CODEC;
    }

    public BatteryBlock(Settings settings) {
        super(settings);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new BatteryBlockEntity(pos, state);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof BatteryBlockEntity battery) {
                // Display battery info when right-clicked
                player.sendMessage(Text.literal("§6Battery Status:"), false);
                
                // Battery specific info
                String chargeStatus = battery.canCharge() ? "§aEnabled" : "§cDisabled";
                String dischargeStatus = battery.canDischarge() ? "§aEnabled" : "§cDisabled";
                int chargePercentage = (int)((float)battery.getStoredEnergy() / battery.getMaxCapacity() * 100);
                
                player.sendMessage(Text.literal("§7Stored energy: §e" + battery.getStoredEnergy() + "§7/§e" + battery.getMaxCapacity() 
                    + " §7(§e" + chargePercentage + "%§7)"), false);
                player.sendMessage(Text.literal("§7Max charge rate: §e" + battery.getMaxChargeRate() + "§7 energy/tick"), false);
                player.sendMessage(Text.literal("§7Max discharge rate: §e" + battery.getMaxDischargeRate() + "§7 energy/tick"), false);
                player.sendMessage(Text.literal("§7Charging: " + chargeStatus + "§7, Discharging: " + dischargeStatus), false);
                
                // Network information
                if (battery.getNetwork() != null) {
                    player.sendMessage(Text.literal("§7Network ID: §6" + battery.getNetwork().getNetworkId()), false);
                    player.sendMessage(Text.literal("§7Connected to network with §6" + battery.getNetwork().getSize() + "§7 blocks"), false);
                    player.sendMessage(Text.literal("§7Network energy: §6" + battery.getNetwork().getStoredEnergy() + "§7/§6" 
                        + battery.getNetwork().getMaxStorage()), false);
                    player.sendMessage(Text.literal("§7Last tick: §a+" + battery.getNetwork().getLastTickEnergyProduced() 
                        + "§7 produced, §c-" + battery.getNetwork().getLastTickEnergyConsumed() + "§7 consumed"), false);
                    player.sendMessage(Text.literal("§7Last tick battery activity: §a+" + battery.getNetwork().getLastTickEnergyStoredInBatteries() 
                        + "§7 stored, §c-" + battery.getNetwork().getLastTickEnergyDrawnFromBatteries() + "§7 drawn"), false);
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
            if (blockEntity instanceof BatteryBlockEntity battery) {
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
                            network.addBlock(pos, battery);
                            foundNetwork = true;
                            Circuitmod.LOGGER.info("Battery at " + pos + " joined existing network " + network.getNetworkId());
                            break;
                        }
                    }
                }
                
                // If no existing network was found, create a new one with any adjacent IPowerConnectable blocks
                if (!foundNetwork) {
                    Circuitmod.LOGGER.info("No existing network found for battery at " + pos + ", checking for other connectables");
                    
                    // Create a new network
                    EnergyNetwork newNetwork = new EnergyNetwork();
                    newNetwork.addBlock(pos, battery);
                    
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
                                battery.canConnectPower(dir)) {
                                
                                newNetwork.addBlock(neighborPos, connectable);
                                Circuitmod.LOGGER.info("Added neighbor at " + neighborPos + " to new network " + newNetwork.getNetworkId());
                            }
                        }
                    }
                    
                    Circuitmod.LOGGER.info("Created new network " + newNetwork.getNetworkId() + " with battery at " + pos);
                }
            }
        }
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return validateTicker(type, ModBlockEntities.BATTERY_BLOCK_ENTITY, BatteryBlockEntity::tick);
    }

    @Override
    protected void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
        if (!moved) {
            // Handle network updates when this block is removed
            BlockEntity entity = world.getBlockEntity(pos);
            if (entity instanceof BatteryBlockEntity battery) {
                EnergyNetwork network = battery.getNetwork();
                if (network != null) {
                    // Remove this block from the network
                    network.removeBlock(pos);
                    Circuitmod.LOGGER.info("Battery at " + pos + " removed from network " + network.getNetworkId());
                }
            }
        }
        
        super.onStateReplaced(state, world, pos, moved);
    }
} 