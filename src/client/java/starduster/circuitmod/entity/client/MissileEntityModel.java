package starduster.circuitmod.entity.client;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.entity.MissileEntity;

/**
 * GeckoLib model for the missile entity, using the rocket model
 */
public class MissileEntityModel extends DefaultedEntityGeoModel<MissileEntity> {
    public MissileEntityModel() {
        // Reuse the rocket model for the missile
        super(Identifier.of(Circuitmod.MOD_ID, "rocket"));
    }

    @Override
    public RenderLayer getRenderType(GeoRenderState renderState, Identifier texture) {
        return RenderLayer.getEntityTranslucent(texture);
    }
}

