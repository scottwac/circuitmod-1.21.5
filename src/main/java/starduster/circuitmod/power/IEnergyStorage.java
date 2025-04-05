package starduster.circuitmod.power;

import net.minecraft.util.math.Direction;

/**
 * Interface for blocks that can store energy (batteries).
 * These blocks can both accept energy when the network has a surplus,
 * and provide energy when the network has a deficit.
 */
public interface IEnergyStorage extends IPowerConnectable {
    /**
     * Attempts to charge the battery with the given amount of energy.
     * 
     * @param energyToCharge Amount of energy offered
     * @return Amount of energy actually charged
     */
    int chargeEnergy(int energyToCharge);
    
    /**
     * Attempts to discharge the battery by the requested amount.
     * 
     * @param energyRequested Amount of energy requested
     * @return Amount of energy actually discharged
     */
    int dischargeEnergy(int energyRequested);
    
    /**
     * Gets the maximum amount of energy this battery can charge per tick.
     * 
     * @return Maximum charge rate per tick
     */
    int getMaxChargeRate();
    
    /**
     * Gets the maximum amount of energy this battery can discharge per tick.
     * 
     * @return Maximum discharge rate per tick
     */
    int getMaxDischargeRate();
    
    /**
     * Gets the current stored energy.
     * 
     * @return Current stored energy
     */
    int getStoredEnergy();
    
    /**
     * Gets the maximum energy capacity.
     * 
     * @return Maximum energy capacity
     */
    int getMaxCapacity();
    
    /**
     * Gets whether the battery is allowed to charge.
     * 
     * @return True if charging is enabled
     */
    boolean canCharge();
    
    /**
     * Gets whether the battery is allowed to discharge.
     * 
     * @return True if discharging is enabled
     */
    boolean canDischarge();
    
    /**
     * Gets the sides from which the battery can accept energy.
     * 
     * @return Directions for input
     */
    Direction[] getInputSides();
    
    /**
     * Gets the sides from which the battery can output energy.
     * 
     * @return Directions for output
     */
    Direction[] getOutputSides();
} 