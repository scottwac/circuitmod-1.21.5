package starduster.circuitmod.block.entity;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.power.EnergyNetwork;
import starduster.circuitmod.power.IEnergyConsumer;
import starduster.circuitmod.screen.XpGeneratorScreenHandler;

public class XpGeneratorBlockEntity extends BlockEntity implements NamedScreenHandlerFactory, IEnergyConsumer {
    // Debug logging control - set to true only when debugging
    private static final boolean DEBUG_LOGGING = false;
    
    // XP generation properties
    private int storedXp = 0; // XP stored in the machine
    private int generationProgress = 0; // Progress towards next XP point
    private static final int TICKS_PER_XP = 10; // 51 ticks (~2.55 seconds) per XP point
    private static final int MAX_STORED_XP = 1395; // Maximum XP that can be stored (30 levels worth)
    
    // Energy properties
    private static final int ENERGY_DEMAND_PER_TICK = 6; // Consumes 6 energy per tick when powered (matches combustion generator)
    private EnergyNetwork network;
    private boolean needsNetworkRefresh = false;
    private int energyReceived = 0; // Energy received this tick
    private boolean isPowered = false; // Whether we're receiving power
    
    // Client-side state tracking (for GUI updates)
    private int clientStoredXp = 0;
    private int clientGenerationProgress = 0;
    private boolean clientIsPowered = false;
    
    // Property delegate for GUI synchronization
    private final PropertyDelegate propertyDelegate = new PropertyDelegate() {
        @Override
        public int get(int index) {
            if (world != null && world.isClient()) {
                // Return client-side values for GUI
                switch (index) {
                    case 0: return clientIsPowered ? 1 : 0;
                    case 1: return clientStoredXp;
                    case 2: return clientGenerationProgress;
                    case 3: return TICKS_PER_XP;
                    case 4: return MAX_STORED_XP;
                    case 5: return ENERGY_DEMAND_PER_TICK;
                    default: return 0;
                }
            } else {
                // Return server-side values
                switch (index) {
                    case 0: return isPowered ? 1 : 0;
                    case 1: return storedXp;
                    case 2: return generationProgress;
                    case 3: return TICKS_PER_XP;
                    case 4: return MAX_STORED_XP;
                    case 5: return ENERGY_DEMAND_PER_TICK;
                    default: return 0;
                }
            }
        }

        @Override
        public void set(int index, int value) {
            // This method is called by Minecraft's built-in synchronization
            // Update client-side values when server sends updates
            if (world != null && world.isClient()) {
                switch (index) {
                    case 0: 
                        clientIsPowered = (value == 1); 
                        break;
                    case 1: 
                        clientStoredXp = value; 
                        break;
                    case 2: 
                        clientGenerationProgress = value; 
                        break;
                }
            }
        }

        @Override
        public int size() {
            return 6;
        }
    };

    public XpGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.XP_GENERATOR_BLOCK_ENTITY, pos, state);
    }
    
    @Override
    public Text getDisplayName() {
        return Text.translatable("block.circuitmod.xp_generator");
    }
    
    @Override
    public @Nullable ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new XpGeneratorScreenHandler(syncId, playerInventory, this, this.propertyDelegate);
    }
    
    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        nbt.putInt("stored_xp", storedXp);
        nbt.putInt("generation_progress", generationProgress);
        nbt.putBoolean("is_powered", isPowered);
    }
    
    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        this.storedXp = nbt.getInt("stored_xp").orElse(0);
        this.generationProgress = nbt.getInt("generation_progress").orElse(0);
        this.isPowered = nbt.getBoolean("is_powered").orElse(false);
        
        // Update client-side values if on client
        if (world != null && world.isClient()) {
            this.clientStoredXp = this.storedXp;
            this.clientGenerationProgress = this.generationProgress;
            this.clientIsPowered = this.isPowered;
        }
        
        this.needsNetworkRefresh = true;
    }
    
    public void onRemoved() {
        if (network != null) {
            network.removeBlock(pos);
        }
    }
    
    private void findAndJoinNetwork() {
        if (world == null || world.isClient()) {
            return;
        }
        
        // Try to find an adjacent power block to join its network
        for (Direction direction : Direction.values()) {
            BlockPos adjacentPos = pos.offset(direction);
            BlockEntity adjacentEntity = world.getBlockEntity(adjacentPos);
            
            if (adjacentEntity instanceof starduster.circuitmod.power.IPowerConnectable powerConnectable) {
                EnergyNetwork adjacentNetwork = powerConnectable.getNetwork();
                if (adjacentNetwork != null) {
                    adjacentNetwork.addBlock(pos, this);
                    return;
                }
            }
        }
    }
    
    // Main tick method
    public static void tick(World world, BlockPos pos, BlockState state, XpGeneratorBlockEntity entity) {
        if (world.isClient()) return;
        
        // Handle network refresh
        if (entity.needsNetworkRefresh) {
            entity.needsNetworkRefresh = false;
            if (entity.network != null) {
                entity.network.addBlock(pos, entity);
            }
        }
        
        // Periodically check if we should be in a network
        if (world.getTime() % 20 == 0) {  // Check every second
            if (entity.network == null) {
                entity.findAndJoinNetwork();
            }
        }
        
        boolean poweredBefore = entity.isPowered;
        
        // Set powered state based on energy received
        entity.isPowered = entity.isReceivingPower();
        
        // XP generation logic - only generate if powered and not at max capacity
        if (entity.isPowered && entity.storedXp < MAX_STORED_XP) {
            entity.generationProgress++;
            if (entity.generationProgress >= TICKS_PER_XP) {
                entity.storedXp++;
                entity.generationProgress = 0;
                if (DEBUG_LOGGING) {
                    Circuitmod.LOGGER.info("[XP-GENERATOR-DEBUG] Generated 1 XP at {}, total stored: {}", pos, entity.storedXp);
                }
            }
            entity.markDirty();
        } else if (!entity.isPowered) {
            // Reset progress when not powered
            if (entity.generationProgress != 0) {
                entity.generationProgress = 0;
                entity.markDirty();
            }
        }
        
        // Update block state if powered state changed
        if (poweredBefore != entity.isPowered) {
            world.setBlockState(pos, state.with(starduster.circuitmod.block.machines.XpGenerator.LIT, entity.isPowered), Block.NOTIFY_ALL);
            entity.markDirty();
        }
        
        // Reset energy received at the end of the tick
        entity.energyReceived = 0;
        
        // Debug logging every 40 ticks (2 seconds)
        if (DEBUG_LOGGING && world.getTime() % 40 == 0) {
            Circuitmod.LOGGER.info("[XP-GENERATOR-DEBUG] Tick at {}: network={}, energyReceived={}, isPowered={}, storedXp={}, progress={}/{}", 
                pos, entity.network != null ? entity.network.getNetworkId() : "null", 
                entity.energyReceived, entity.isPowered, entity.storedXp, entity.generationProgress, TICKS_PER_XP);
        }
    }
    
    public boolean isReceivingPower() {
        return this.energyReceived > 0;
    }
    
    public boolean isBurning() {
        return this.isPowered;
    }
    
    // XP collection method
    public void collectXp(PlayerEntity player) {
        if (world == null || world.isClient()) {
            return; // Only process on server side
        }
        
        if (storedXp > 0) {
            player.addExperience(storedXp);
            if (DEBUG_LOGGING) {
                Circuitmod.LOGGER.info("[XP-GENERATOR-DEBUG] Player {} collected {} XP from generator at {}", 
                    player.getName().getString(), storedXp, pos);
            }
            storedXp = 0;
            generationProgress = 0; // Reset progress when collected
            markDirty();
            
            // Force sync to client
            if (world instanceof net.minecraft.server.world.ServerWorld) {
                ((net.minecraft.server.world.ServerWorld) world).getChunkManager().markForUpdate(pos);
            }
        }
    }
    
    public int getStoredXp() {
        return storedXp;
    }
    
    public int getGenerationProgress() {
        return generationProgress;
    }
    
    public int getMaxProgress() {
        return TICKS_PER_XP;
    }
    
    public int getMaxStoredXp() {
        return MAX_STORED_XP;
    }
    
    public boolean canPlayerUse(PlayerEntity player) {
        if (this.world == null || this.world.getBlockEntity(this.pos) != this) {
            return false;
        } else {
            return player.squaredDistanceTo((double)this.pos.getX() + 0.5D, (double)this.pos.getY() + 0.5D, (double)this.pos.getZ() + 0.5D) <= 64.0D;
        }
    }
    
    // IEnergyConsumer implementation
    @Override
    public boolean canConnectPower(Direction side) {
        return true; // Can connect from any side
    }
    
    @Override
    public EnergyNetwork getNetwork() {
        return network;
    }
    
    @Override
    public void setNetwork(EnergyNetwork network) {
        if (world != null && world.isClient()) {
            return;
        }
        
        this.network = network;
    }
    
    @Override
    public int consumeEnergy(int energyOffered) {
        if (world == null || world.isClient()) {
            return 0;
        }
        
        int energyToConsume = Math.min(energyOffered, ENERGY_DEMAND_PER_TICK);
        if (energyToConsume > 0) {
            this.energyReceived += energyToConsume;
        }
        
        return energyToConsume;
    }
    
    @Override
    public int getEnergyDemand() {
        return ENERGY_DEMAND_PER_TICK;
    }
    
    @Override
    public Direction[] getInputSides() {
        return Direction.values(); // Can receive from all sides
    }
    
    public PropertyDelegate getPropertyDelegate() {
        return propertyDelegate;
    }
} 