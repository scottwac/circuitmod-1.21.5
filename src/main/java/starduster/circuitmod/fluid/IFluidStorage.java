package starduster.circuitmod.fluid;

import net.minecraft.fluid.Fluid;
import net.minecraft.util.math.Direction;

/**
 * Interface for blocks that can store fluids.
 */
public interface IFluidStorage extends IFluidConnectable {
    /**
     * Attempts to insert fluid into this storage.
     * 
     * @param fluid The type of fluid to insert
     * @param amount Amount of fluid in millibuckets
     * @param direction The direction from which the fluid is being inserted
     * @return Amount of fluid actually inserted in millibuckets
     */
    int insertFluid(Fluid fluid, int amount, Direction direction);
    
    /**
     * Attempts to extract fluid from this storage.
     * 
     * @param fluid The type of fluid to extract (null for any)
     * @param amount Amount of fluid requested in millibuckets
     * @param direction The direction from which the fluid is being extracted
     * @return Amount of fluid actually extracted in millibuckets
     */
    int extractFluid(Fluid fluid, int amount, Direction direction);
    
    /**
     * Gets the type of fluid currently stored.
     * 
     * @return The stored fluid type, or null if empty
     */
    Fluid getStoredFluidType();
    
    /**
     * Gets the amount of fluid currently stored.
     * 
     * @return Amount in millibuckets
     */
    int getStoredFluidAmount();
    
    /**
     * Gets the maximum fluid capacity.
     * 
     * @return Maximum capacity in millibuckets
     */
    int getMaxFluidCapacity();
    
    /**
     * Gets whether this storage can accept the given fluid type.
     * 
     * @param fluid The fluid type to test
     * @return True if this fluid can be stored
     */
    boolean canAcceptFluid(Fluid fluid);
    
    /**
     * Gets the sides from which fluid can be inserted.
     * 
     * @return Array of directions for input
     */
    Direction[] getFluidInputSides();
    
    /**
     * Gets the sides from which fluid can be extracted.
     * 
     * @return Array of directions for output
     */
    Direction[] getFluidOutputSides();
} 