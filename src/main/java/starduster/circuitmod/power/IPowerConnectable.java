package starduster.circuitmod.power;

import net.minecraft.util.math.Direction;

/**
 * Base interface for any block that can connect to a power network.
 */
public interface IPowerConnectable {
    /**
     * Checks if this block can connect to power network from the given side.
     * 
     * @param side The side to check connection from
     * @return True if can connect from this side, false otherwise
     */
    boolean canConnectPower(Direction side);
    
    /**
     * Gets the current energy network this block is connected to.
     * 
     * @return The energy network, or null if not connected
     */
    EnergyNetwork getNetwork();
    
    /**
     * Sets the energy network for this block.
     * 
     * @param network The energy network to connect to
     */
    void setNetwork(EnergyNetwork network);
} 