package starduster.circuitmod.client.model;

import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import starduster.circuitmod.item.EmuSuitItem;

/**
 * GeoModel for the EMU Space Suit armor
 */
public class EmuSuitModel extends GeoModel<EmuSuitItem> {
    
    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        return Identifier.of("circuitmod", "geckolib/models/emu_suit.geo.json");
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        // Use the default EMU suit texture
        return Identifier.of("circuitmod", "textures/armor/emu_suit/emu_suit.png");
    }

    @Override
    public Identifier getAnimationResource(EmuSuitItem animatable) {
        // No animations for now, can be added later
        return Identifier.of("circuitmod", "geckolib/animations/emu_suit.animation.json");
    }
}
