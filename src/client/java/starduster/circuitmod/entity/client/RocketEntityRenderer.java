package starduster.circuitmod.entity.client;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import starduster.circuitmod.entity.RocketEntity;

/**
 * GeckoLib renderer for the rocket entity
 */
public class RocketEntityRenderer<R extends LivingEntityRenderState & GeoRenderState> extends GeoEntityRenderer<RocketEntity, R> {
    
    public RocketEntityRenderer(EntityRendererFactory.Context context) {
        super(context, new RocketEntityModel());
    }
}
