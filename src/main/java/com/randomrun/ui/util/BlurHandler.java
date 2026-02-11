package com.randomrun.ui.util;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL20;

public class BlurHandler {
    private static final String VERTEX_SHADER = """
        #version 150
        
        in vec3 Position;
        
        out vec2 texCoord;
        
        void main() {
            gl_Position = vec4(Position, 1.0);
            texCoord = Position.xy * 0.5 + 0.5;
        }
    """;

    private static final String FRAGMENT_SHADER = """
        #version 150
        
        uniform sampler2D DiffuseSampler;
        uniform vec2 TexelSize;
        uniform vec2 Direction;
        
        in vec2 texCoord;
        out vec4 fragColor;
        
        void main() {
            vec4 color = vec4(0.0);
            
            // Gaussian weights for 5-tap blur
            float weights[5] = float[](0.227027, 0.1945946, 0.1216216, 0.054054, 0.016216);
            
            // Center sample
            color += texture(DiffuseSampler, texCoord) * weights[0];
            
            // Blur in given direction
            for(int i = 1; i < 5; i++) {
                vec2 offset = Direction * float(i) * TexelSize;
                color += texture(DiffuseSampler, texCoord + offset) * weights[i];
                color += texture(DiffuseSampler, texCoord - offset) * weights[i];
            }
            
            fragColor = color;
        }
    """;

    private SimpleFramebuffer smallFbo;
    private SimpleFramebuffer tempFbo;
    private int programId = -1;
    private int uniformTexelSize;
    private int uniformDirection;
    private int uniformDiffuseSampler;
    private int vao = -1;
    private int vbo = -1;

    private int lastWidth = -1;
    private int lastHeight = -1;

    public void renderBlur(int x, int y, int width, int height, int downsampleFactor) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getFramebuffer() == null) return;

        // Ensure we're on render thread
        RenderSystem.assertOnRenderThreadOrInit();
        
        try {
            initShaders();
            initQuad();
        resize(width / downsampleFactor, height / downsampleFactor);

        // 1. Copy from Main FB to Small FB (Downsample)
        // 1. Копирование из основного FB в маленький FB (Уменьшение)
        blitFromScreen(x, y, width, height, smallFbo);

        // 2. Horizontal Blur: Small FB -> Temp FB
        // 2. Горизонтальное размытие: Маленький FB -> Временный FB
        applyBlurPass(smallFbo, tempFbo, 1.0f, 0.0f);

        // 3. Vertical Blur: Temp FB -> Small FB
        // 3. Вертикальное размытие: Временный FB -> Маленький FB
        applyBlurPass(tempFbo, smallFbo, 0.0f, 1.0f);

        // 4. Draw Small FB to Screen (Upsample)
        // 4. Отрисовка маленького FB на экран (Увеличение)
        drawFramebufferToScreen(smallFbo, x, y, width, height);
        
        // Restore main framebuffer
        client.getFramebuffer().beginWrite(true);
        restoreRenderState();
    } catch (Exception e) {
        // Ensure we restore state even if something fails
        client.getFramebuffer().beginWrite(true);
        restoreRenderState();
        e.printStackTrace();
    }
    }

    private void restoreRenderState() {
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableCull();
    }

    private void initShaders() {
        if (programId == -1) {
            try {
                programId = ShaderUtils.createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
                uniformTexelSize = GL20.glGetUniformLocation(programId, "TexelSize");
                uniformDirection = GL20.glGetUniformLocation(programId, "Direction");
                uniformDiffuseSampler = GL20.glGetUniformLocation(programId, "DiffuseSampler");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void initQuad() {
        if (vao == -1) {
            // Create VAO
            vao = GL30.glGenVertexArrays();
            GL30.glBindVertexArray(vao);
            
            // Create VBO with full-screen quad
            float[] vertices = {
                -1.0f, -1.0f, 0.0f,
                 1.0f, -1.0f, 0.0f,
                 1.0f,  1.0f, 0.0f,
                -1.0f,  1.0f, 0.0f
            };
            
            vbo = GL30.glGenBuffers();
            GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, vbo);
            GL30.glBufferData(GL30.GL_ARRAY_BUFFER, vertices, GL30.GL_STATIC_DRAW);
            
            // Setup vertex attributes
            GL30.glVertexAttribPointer(0, 3, GL30.GL_FLOAT, false, 12, 0);
            GL30.glEnableVertexAttribArray(0);
            
            GL30.glBindVertexArray(0);
        }
    }

    private void resize(int w, int h) {
        if (w < 1) w = 1;
        if (h < 1) h = 1;
        
        if (w != lastWidth || h != lastHeight) {
            if (smallFbo != null) smallFbo.delete();
            if (tempFbo != null) tempFbo.delete();
            
            smallFbo = new SimpleFramebuffer(w, h, false);
            tempFbo = new SimpleFramebuffer(w, h, false);
            
            smallFbo.setClearColor(0f, 0f, 0f, 0f);
            tempFbo.setClearColor(0f, 0f, 0f, 0f);
            
            lastWidth = w;
            lastHeight = h;
        }
    }
    
    private void blitFromScreen(int x, int y, int w, int h, SimpleFramebuffer dest) {
        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer mainFb = client.getFramebuffer();
        
        dest.beginWrite(true);
        
        double scale = client.getWindow().getScaleFactor();
        int px = (int)(x * scale);
        // Flip Y coordinate (OpenGL bottom-left origin)
        int py = (int)((client.getWindow().getFramebufferHeight() - (y + h) * scale));
        int pw = (int)(w * scale);
        int ph = (int)(h * scale);
        
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, mainFb.fbo);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, dest.fbo);
        
        GL30.glBlitFramebuffer(
            px, py, px + pw, py + ph,
            0, 0, dest.textureWidth, dest.textureHeight,
            GL30.GL_COLOR_BUFFER_BIT, GL30.GL_LINEAR
        );
        
        // Do not restore main framebuffer here, do it in renderBlur
    }
    
    private void applyBlurPass(SimpleFramebuffer src, SimpleFramebuffer dst, float dirX, float dirY) {
        dst.beginWrite(true);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, dst.fbo);
        
        if (programId != -1 && vao != -1) {
            GL20.glUseProgram(programId);
            
            // Set uniforms
            GL20.glUniform2f(uniformTexelSize, 1.0f / src.textureWidth, 1.0f / src.textureHeight);
            GL20.glUniform2f(uniformDirection, dirX, dirY);
            GL20.glUniform1i(uniformDiffuseSampler, 0);
            
            // Bind source texture
            RenderSystem.activeTexture(GL30.GL_TEXTURE0);
            RenderSystem.bindTexture(src.getColorAttachment());
            
            // Disable depth test and blending
            RenderSystem.disableDepthTest();
            RenderSystem.disableBlend();
            
            // Draw quad
            GL30.glBindVertexArray(vao);
            GL30.glDrawArrays(GL30.GL_TRIANGLE_FAN, 0, 4);
            GL30.glBindVertexArray(0);
            
            GL20.glUseProgram(0);
        }
        // Do not restore main framebuffer here, do it in renderBlur
    }
    
    private void drawFramebufferToScreen(SimpleFramebuffer src, int x, int y, int w, int h) {
        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer mainFb = client.getFramebuffer();
        
        double scale = client.getWindow().getScaleFactor();
        int px = (int)(x * scale);
        int py = (int)((client.getWindow().getFramebufferHeight() - (y + h) * scale));
        int pw = (int)(w * scale);
        int ph = (int)(h * scale);
        
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, src.fbo);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, mainFb.fbo);
        
        GL30.glBlitFramebuffer(
            0, 0, src.textureWidth, src.textureHeight,
            px, py, px + pw, py + ph,
            GL30.GL_COLOR_BUFFER_BIT, GL30.GL_LINEAR
        );
        
        // Do not restore main framebuffer here, do it in renderBlur
    }
    
    public void cleanup() {
        if (smallFbo != null) {
            smallFbo.delete();
            smallFbo = null;
        }
        if (tempFbo != null) {
            tempFbo.delete();
            tempFbo = null;
        }
        if (programId != -1) {
            GL20.glDeleteProgram(programId);
            programId = -1;
        }
        if (vao != -1) {
            GL30.glDeleteVertexArrays(vao);
            vao = -1;
        }
        if (vbo != -1) {
            GL30.glDeleteBuffers(vbo);
            vbo = -1;
        }
    }
}