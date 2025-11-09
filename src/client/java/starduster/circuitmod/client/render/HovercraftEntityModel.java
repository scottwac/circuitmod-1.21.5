package starduster.circuitmod.client.render;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.entity.HovercraftEntity;

public class HovercraftEntityModel extends DefaultedEntityGeoModel<HovercraftEntity> {

    public HovercraftEntityModel() {
        super(Identifier.of(Circuitmod.MOD_ID, "rocket"));
    }

    @Override
    public RenderLayer getRenderType(GeoRenderState renderState, Identifier texture) {
        return RenderLayer.getEntityTranslucent(texture);
    }
}

