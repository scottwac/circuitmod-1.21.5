package starduster.circuitmod.power;

import net.minecraft.util.math.Direction;

/**
 * Interface for blocks that produce energy for the network.
 */
public interface IEnergyProducer extends IPowerConnectable {
    /**
     * Produces energy for the network.
     * 
     * @param maxRequested The maximum amount of energy that can be accepted
     * @return The amount of energy actually produced
     */
    int produceEnergy(int maxRequested);
    
    /**
     * Gets the maximum amount of energy this producer can output.
     * 
     * @return The maximum energy output per tick
     */
    int getMaxOutput();
    
    /**
     * Gets the sides from which this producer can output energy.
     * 
     * @return Array of directions where output is possible
     */
    Direction[] getOutputSides();
} 