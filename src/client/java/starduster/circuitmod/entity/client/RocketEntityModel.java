package starduster.circuitmod.entity.client;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.entity.RocketEntity;

/**
 * Example {@link GeoModel} for the {@link RocketEntity}
 * @see RocketEntityRenderer
 */
public class RocketEntityModel extends DefaultedEntityGeoModel<RocketEntity> {
    public RocketEntityModel() {
        super(Identifier.of(Circuitmod.MOD_ID, "rocket"));
    }

    // We want our model to render using the translucent render type
    @Override
    public RenderLayer getRenderType(GeoRenderState renderState, Identifier texture) {
        return RenderLayer.getEntityTranslucent(texture);
    }
}
