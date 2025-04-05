package starduster.circuitmod.power;

import net.minecraft.util.math.Direction;

/**
 * Interface for blocks that consume energy from the network.
 */
public interface IEnergyConsumer extends IPowerConnectable {
    /**
     * Consumes energy from the network.
     * 
     * @param energyOffered The amount of energy being offered to this consumer
     * @return The amount of energy actually consumed
     */
    int consumeEnergy(int energyOffered);
    
    /**
     * Gets the amount of energy this consumer currently needs.
     * 
     * @return The energy demand per tick
     */
    int getEnergyDemand();
    
    /**
     * Gets the sides from which this consumer can receive energy.
     * 
     * @return Array of directions where input is possible
     */
    Direction[] getInputSides();
} 