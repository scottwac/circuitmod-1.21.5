package starduster.circuitmod.block;

import net.minecraft.block.Block;
import net.minecraft.block.AbstractBlock;

/**
 * Simple launch pad block. Behavior is handled in a player tick mixin that
 * detects a 3x3 of this block under the player and applies launch/teleport.
 */
public class LaunchPadBlock extends Block {
    public LaunchPadBlock(AbstractBlock.Settings settings) {
        super(settings);
    }
}

