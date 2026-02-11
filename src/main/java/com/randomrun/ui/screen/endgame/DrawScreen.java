package com.randomrun.ui.screen.endgame;

import com.randomrun.ui.screen.main.AbstractRandomRunScreen;
import com.randomrun.ui.screen.main.MainModScreen;
import com.randomrun.main.RandomRunMod;
import com.randomrun.challenges.advancement.data.AdvancementLoader;
import com.randomrun.ui.widget.GlobalParticleSystem;
import com.randomrun.ui.widget.styled.ButtonDefault;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.item.ModelTransformationMode;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Quaternionf;

import java.util.List;

public class DrawScreen extends AbstractRandomRunScreen {
    private final Item targetItem;
    private final AdvancementLoader.AdvancementInfo targetAdvancement;
    private final String reason;
    private float rotationY = 0f;
    private float levitationOffset = 0f;
    private long openTime;
    
    public DrawScreen(Item item, long time, String reason) {
        super(Text.translatable("randomrun.screen.draw.title"));
        this.targetItem = item;
        this.targetAdvancement = null;
        this.reason = reason;
    }
    
    public DrawScreen(Identifier advancementId, long time, String reason) {
        super(Text.translatable("randomrun.screen.draw.title"));
        this.targetItem = null;
        this.reason = reason;
        
        List<AdvancementLoader.AdvancementInfo> all = AdvancementLoader.getAdvancements();
        AdvancementLoader.AdvancementInfo found = null;
        for (AdvancementLoader.AdvancementInfo info : all) {
            if (info.id.equals(advancementId)) {
                found = info;
                break;
            }
        }
        this.targetAdvancement = found;
    }

    @Override
    protected void init() {
        super.init();
        openTime = System.currentTimeMillis();
        
        // Звук (нейтральный)
        if (client.player != null) {
            client.player.playSound(net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.0f, 0.5f);
        }
        
        GlobalParticleSystem particleSystem = GlobalParticleSystem.getInstance();
        particleSystem.clearParticles();
        // Используем желтые партиклы
        particleSystem.setYellowMode(true);
        
        int centerX = width / 2;
        
        // Кнопка выхода
        addDrawableChild(new ButtonDefault(
            centerX - 100, height - 60,
            200, 20,
            Text.translatable("randomrun.button.to_menu"),
            button -> {
                GlobalParticleSystem.getInstance().clearParticles();
                MinecraftClient client = MinecraftClient.getInstance();
                
                if (client.world != null) {
                    client.world.disconnect();
                }
                client.disconnect();
                
                RandomRunMod.getInstance().getRunDataManager().cancelRun();
                
                client.setScreen(new MainModScreen(new TitleScreen()));
            }
        ).setColors(0xAA8800, 0xFFCC00)); // Желтые цвета
    }
    
    @Override
    protected void renderGradientBackground(DrawContext context) {
        // Желтый градиент
        int topColor = 0xFF000000;    
        int middleColor = 0xFF886600;  // Темно-желтый
        int bottomColor = 0xFF332200;  // Коричневый
        
        context.fillGradient(0, 0, width, height / 2, topColor, middleColor);
        context.fillGradient(0, height / 2, width, height, middleColor, bottomColor);
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Ensure yellow mode is active every frame
        GlobalParticleSystem.getInstance().setYellowMode(true);
        
        long elapsed = System.currentTimeMillis() - openTime;
        rotationY += delta * 2f;
        levitationOffset = (float) Math.sin(elapsed / 500.0) * 5f;
        
        renderBackground(context, mouseX, mouseY, delta);
        
        // Заголовок "НИЧЬЯ"
        float scale = 1.0f + (float) Math.sin(elapsed / 300.0) * 0.05f;
        context.getMatrices().push();
        context.getMatrices().translate(width / 2f, 40, 0);
        context.getMatrices().scale(scale * 2, scale * 2, 1);
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("randomrun.screen.draw.title_label"), 0, 0, 0xFFAA00);
        context.getMatrices().pop();
        
        // 3D Предмет
        render3DItem(context, width / 2, height / 2 - 30);
        
        // Название предмета (Желтое)
        String itemName = targetItem != null ? targetItem.getName().getString() : (targetAdvancement != null ? targetAdvancement.title.getString() : "Unknown");
        context.drawCenteredTextWithShadow(textRenderer, "§6" + itemName, width / 2, height / 2 + 50, 0xFFAA00);
        
        // Причина (Белая)
        if (reason != null && !reason.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, 
                "§f" + reason, 
                width / 2, height / 2 + 80, 0xFFFFFF);
        }
        
        super.render(context, mouseX, mouseY, delta);
    }
    
    private void render3DItem(DrawContext context, int x, int y) {
        ItemStack stack;
        if (targetItem != null) {
            stack = new ItemStack(targetItem);
        } else if (targetAdvancement != null) {
            stack = targetAdvancement.icon;
        } else {
            return;
        }
        
        context.getMatrices().push();
        context.getMatrices().translate(x, y + levitationOffset, 100);
        context.getMatrices().scale(80f, -80f, 80f);
        context.getMatrices().multiply(new Quaternionf().rotateY((float) Math.toRadians(rotationY)));
        
        MinecraftClient client = MinecraftClient.getInstance();
        // BakedModel model = client.getItemRenderer().getModels().getModel(stack);
        
        DiffuseLighting.disableGuiDepthLighting();
        
        client.getItemRenderer().renderItem(
            stack,
            ModelTransformationMode.GUI,
            15728880,
            OverlayTexture.DEFAULT_UV,
            context.getMatrices(),
            MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers(),
            client.world,
            0
        );
        context.draw();
        DiffuseLighting.enableGuiDepthLighting();
        context.getMatrices().pop();
    }
    
    @Override
    public boolean shouldCloseOnEsc() { return false; }
    @Override
    public boolean shouldPause() { return false; }
    
    @Override
    public void close() {
        GlobalParticleSystem.getInstance().setYellowMode(false);
        super.close();
    }
}
