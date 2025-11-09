package starduster.circuitmod.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.state.EntityRenderState;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import starduster.circuitmod.entity.HovercraftEntity;

import java.util.Map;

@Environment(EnvType.CLIENT)
public class HovercraftEntityRenderer extends GeoEntityRenderer<HovercraftEntity, HovercraftEntityRenderer.HovercraftRenderState> {

    public HovercraftEntityRenderer(EntityRendererFactory.Context context) {
        super(context, new HovercraftEntityModel());
        this.shadowRadius = 0.7F;
    }

    @Override
    public HovercraftRenderState createRenderState() {
        return new HovercraftRenderState();
    }

    public static class HovercraftRenderState extends EntityRenderState implements GeoRenderState {
        private final GeoRenderState delegate = new GeoRenderState.Impl();

        @Override
        public <D> void addGeckolibData(DataTicket<D> dataTicket, @Nullable D data) {
            delegate.addGeckolibData(dataTicket, data);
        }

        @Override
        public boolean hasGeckolibData(DataTicket<?> dataTicket) {
            return delegate.hasGeckolibData(dataTicket);
        }

        @Nullable
        @Override
        public <D> D getGeckolibData(DataTicket<D> dataTicket) {
            return delegate.getGeckolibData(dataTicket);
        }

        @Override
        public Map<DataTicket<?>, Object> getDataMap() {
            return delegate.getDataMap();
        }
    }
}
