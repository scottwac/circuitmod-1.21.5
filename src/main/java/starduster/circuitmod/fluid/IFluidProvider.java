package starduster.circuitmod.fluid;

import net.minecraft.fluid.Fluid;
import net.minecraft.util.math.Direction;

/**
 * Interface for blocks that can provide fluids to the network.
 */
public interface IFluidProvider extends IFluidConnectable {
    /**
     * Provides fluid to the network.
     * 
     * @param maxRequested The maximum amount of fluid that can be accepted
     * @return The amount of fluid actually provided
     */
    int provideFluid(int maxRequested);
    
    /**
     * Gets the type of fluid this provider can output.
     * 
     * @return The fluid type, or null if no fluid available
     */
    Fluid getProvidedFluidType();
    
    /**
     * Gets the maximum amount of fluid this provider can output per tick.
     * 
     * @return The maximum fluid output per tick in millibuckets
     */
    int getMaxFluidOutput();
    
    /**
     * Gets the sides from which this provider can output fluid.
     * 
     * @return Array of directions where output is possible
     */
    Direction[] getFluidOutputSides();
} 