package starduster.circuitmod.client.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.util.TriState;

public class CustomRenderLayers extends RenderPipelines {

    public static final RenderPipeline POSITION_TEX_COLOR_ALPHA_CELESTIAL = register(
            RenderPipeline.builder(MATRICES_COLOR_SNIPPET)
                    .withLocation("pipeline/celestial")
                    .withVertexShader("core/position_tex_color")
                    .withFragmentShader("core/position_tex_color")
                    .withSampler("Sampler0")
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthWrite(false)
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                    .build()
    );

    public static final RenderLayer alphaCelestial(Identifier texture) {
        return RenderLayer.of(
                "alpha_celestial",
                1536,
                false,
                true,
                POSITION_TEX_COLOR_ALPHA_CELESTIAL,
                RenderLayer.MultiPhaseParameters.builder().texture(new RenderPhase.Texture(texture, TriState.FALSE, false)).build(false)
        );
    }

}
