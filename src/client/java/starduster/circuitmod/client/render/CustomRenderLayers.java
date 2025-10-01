//package starduster.circuitmod.client.render;
//
//import com.mojang.blaze3d.vertex.VertexFormat;
//import net.minecraft.client.render.GameRenderer;
//import net.minecraft.client.render.RenderLayer;
//import net.minecraft.client.render.RenderPhase;
//import net.minecraft.client.render.VertexFormats;
//
//public class CustomRenderLayers {
//    public static final RenderLayer SKY_TEXTURED = RenderLayer.of(
//            "sky_textured",
//            VertexFormats.POSITION_COLOR_TEXTURE,
//            VertexFormat.DrawMode.QUADS,
//            256,
//            false, // no crumbling
//            true,  // translucent
//            RenderLayer.MultiPhaseParameters.builder()
//                    .program(new RenderPhase.ShaderProgram(GameRenderer::getPositionColorTexProgram))
//                    .texture(RenderPhase.Textures.create().add(texture, false, false).build())
//                    .transparency(RenderPhase.TRANSLUCENT_TRANSPARENCY)
//                    .depthTest(RenderPhase.LEQUAL_DEPTH_TEST)
//                    .cull(RenderPhase.DISABLE_CULLING)
//                    .writeMaskState(RenderPhase.ALL_MASK)
//                    .build(true)
//    );
//}
