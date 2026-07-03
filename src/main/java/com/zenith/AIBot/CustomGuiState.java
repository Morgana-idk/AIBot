package com.zenith.AIBot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

public class CustomGuiState {
    public static double x, y, z;
    public static boolean desenhar = false;

    @SubscribeEvent
    public static void CreateGUI(RenderWorldLastEvent event) {
        if (!desenhar) return;

        Minecraft mc = Minecraft.getMinecraft();
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder bufferBuilder = tess.getBuffer();

        RenderManager renderManager = mc.getRenderManager();

        Entity renderViewEntity = mc.getRenderViewEntity();
        if (renderViewEntity == null) return;

        double parcialTicks = event.getPartialTicks();
        double camX = renderViewEntity.lastTickPosX + (renderViewEntity.posX - renderViewEntity.lastTickPosX) * parcialTicks;
        double camY = renderViewEntity.lastTickPosY + (renderViewEntity.posY - renderViewEntity.lastTickPosY) * parcialTicks;
        double camZ = renderViewEntity.lastTickPosZ + (renderViewEntity.posZ - renderViewEntity.lastTickPosZ) * parcialTicks;

        double xRelativo = x - camX;
        double yRelativo = y - camY + 3.2D;
        double zRelativo = z - camZ;

        GlStateManager.pushMatrix();
        GlStateManager.translate(xRelativo, yRelativo, zRelativo);

        GlStateManager.glNormal3f(0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(-renderManager.playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(renderManager.playerViewX, 1.0F, 0.0F, 0.0F);

        GlStateManager.scale(-0.025F, -0.025F, 0.025F);

        GlStateManager.SourceFactor srcAlpha = GlStateManager.SourceFactor.SRC_ALPHA;
        GlStateManager.SourceFactor srcAlpha1 = GlStateManager.SourceFactor.ONE_MINUS_SRC_ALPHA;

        GlStateManager.disableTexture2D(); // desabilita textura 2D
        GlStateManager.enableBlend(); // habilita transparencia
        GlStateManager.disableLighting(); // desabilita luz

        GlStateManager.blendFunc(srcAlpha.ordinal(), srcAlpha1.ordinal()); // transparencia

        double esquerda = -40.0D; // tamanho -X
        double direita = 40.0D; // tamanho +X
        double topo = -6.0D; // tamanho +Y
        double fundo = 6.0D; // tamanho -Y

        int r = 10;
        int g = 10;
        int b = 10;
        int a = 150;

        bufferBuilder.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);

        bufferBuilder.pos(esquerda, fundo, 0.0D).color(r, g, b, a).endVertex();
        bufferBuilder.pos(direita, fundo, 0.0D).color(r, g, b, a).endVertex();
        bufferBuilder.pos(direita, topo, 0.0D).color(r, g, b, a).endVertex();
        bufferBuilder.pos(esquerda, topo, 0.0D).color(r, g, b, a).endVertex();

        tess.draw();

        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }
}
