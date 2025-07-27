package starduster.circuitmod.fluid;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Base interface for blocks that can connect to fluid networks.
 */
public interface IFluidConnectable {
    /**
     * Gets the position of this connectable block.
     * 
     * @return The block position
     */
    BlockPos getPos();
    
    /**
     * Gets the world this block is in.
     * 
     * @return The world
     */
    World getWorld();
    
    /**
     * Called when this block is added to a fluid network.
     */
    void onNetworkUpdate();
    
    /**
     * Gets whether this block can connect to fluid networks.
     * 
     * @return True if this block can connect to fluid networks
     */
    boolean canConnectFluid();
} 