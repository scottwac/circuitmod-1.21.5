package starduster.circuitmod.client.renderer;

import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import starduster.circuitmod.client.model.EmuSuitModel;
import starduster.circuitmod.item.EmuSuitItem;

/**
 * GeoArmorRenderer for the EMU Space Suit
 */
public class EmuSuitRenderer<R extends BipedEntityRenderState & GeoRenderState> extends GeoArmorRenderer<EmuSuitItem, R> {
    
    public EmuSuitRenderer() {
        super(new EmuSuitModel());
    }
}
