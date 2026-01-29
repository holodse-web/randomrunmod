package com.randomrun.ui.screen;

import com.randomrun.main.RandomRunMod;
import com.randomrun.main.data.RunDataManager;
import com.randomrun.challenges.advancement.data.AdvancementLoader;
import com.randomrun.challenges.advancement.screen.AchievementRevealScreen;
import com.randomrun.challenges.classic.screen.ItemRevealScreen;
import com.randomrun.ui.widget.GlobalParticleSystem;
import com.randomrun.ui.widget.RedStyledButton;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Quaternionf;

import java.util.List;

public class DefeatScreen extends AbstractRandomRunScreen {
    private final Item targetItem;
    private final AdvancementLoader.AdvancementInfo targetAdvancement;
    private final long elapsedTime;
    private final String reason;
    private float rotationY = 0f;
    private float levitationOffset = 0f;
    private long openTime;
    
    public DefeatScreen(Item item, long time, String reason) {
        super(Text.translatable("randomrun.screen.defeat.title"));
        this.targetItem = item;
        this.targetAdvancement = null;
        this.elapsedTime = time;
        this.reason = reason;
    }
    
    public DefeatScreen(Identifier advancementId, long time, String reason) {
        super(Text.translatable("randomrun.screen.defeat.title"));
        this.targetItem = null;
        this.elapsedTime = time;
        this.reason = reason;
        
        // Find advancement info
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

    public DefeatScreen(Item item, long time) {
        this(item, time, null);
    }
    
    public DefeatScreen(Identifier advancementId, long time) {
        this(advancementId, time, null);
    }
    
    @Override
    protected void init() {
        super.init();
        openTime = System.currentTimeMillis();
        
        // Play defeat sound
        if (client.player != null) {
            client.player.playSound(net.minecraft.sound.SoundEvents.ENTITY_WITHER_SPAWN, 1.0f, 1.0f);
        }
        
        
        GlobalParticleSystem particleSystem = GlobalParticleSystem.getInstance();
        particleSystem.clearParticles();
        particleSystem.setRedMode(true);
        
        int centerX = width / 2;
        
       
        addDrawableChild(new RedStyledButton(
            centerX - 100, height - 60,
            200, 20,
            Text.translatable("randomrun.button.try_again"),
            button -> {
                // Очищаем частицы перед выходом
                GlobalParticleSystem.getInstance().setRedMode(false);
                GlobalParticleSystem.getInstance().clearParticles();
                
                MinecraftClient client = MinecraftClient.getInstance();
                RandomRunMod.getInstance().getRunDataManager().cancelRun();
                if (client.world != null) {
                    client.world.disconnect();
                }
                client.disconnect();
                
                if (targetItem != null) {
                    client.setScreen(new ItemRevealScreen(new MainModScreen(new TitleScreen()), targetItem));
                } else if (targetAdvancement != null) {
                    client.setScreen(new AchievementRevealScreen(new MainModScreen(new TitleScreen()), targetAdvancement));
                } else {
                    client.setScreen(new MainModScreen(new TitleScreen()));
                }
            }
        ));
        
        
        addDrawableChild(new RedStyledButton(
            centerX - 100, height - 30,
            200, 20,
            Text.translatable("randomrun.button.exit"),
            button -> {
               
                GlobalParticleSystem.getInstance().setRedMode(false);
                GlobalParticleSystem.getInstance().clearParticles();
                
                MinecraftClient client = MinecraftClient.getInstance();
                RandomRunMod.getInstance().getRunDataManager().cancelRun();
                if (client.world != null) {
                    client.world.disconnect();
                }
                client.disconnect();
                client.setScreen(new MainModScreen(new TitleScreen()));
            }
        ));
    }
    
    @Override
    protected void renderGradientBackground(DrawContext context) {
       
        int topColor = 0xFF000000;    
        int middleColor = 0xFF8c1414;  
        int bottomColor = 0xFF2e1a1a;  
        
        
        context.fillGradient(0, 0, width, height / 2, topColor, middleColor);
       
        context.fillGradient(0, height / 2, width, height, middleColor, bottomColor);
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
     
        long elapsed = System.currentTimeMillis() - openTime;
        rotationY += delta * 2f;
        levitationOffset = (float) Math.sin(elapsed / 500.0) * 5f;
        
        
        renderBackground(context, mouseX, mouseY, delta);
        
        
        float scale = 1.0f + (float) Math.sin(elapsed / 300.0) * 0.05f;
        context.getMatrices().push();
        context.getMatrices().translate(width / 2f, 40, 0);
        context.getMatrices().scale(scale * 2, scale * 2, 1);
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("randomrun.screen.defeat.title_label"), 0, 0, 0xFF5555);
        context.getMatrices().pop();
        
       
        // Render 3D item (lowered)
        render3DItem(context, width / 2, height / 2 - 30);
        
        // Item/Advancement name in red
        String itemName = targetItem != null ? targetItem.getName().getString() : (targetAdvancement != null ? targetAdvancement.title.getString() : "Unknown");
        context.drawCenteredTextWithShadow(textRenderer, "§c" + itemName, width / 2, height / 2 + 50, 0xFF5555);
        
        // Time in white
        String timeStr = RunDataManager.formatTime(elapsedTime);
        context.drawCenteredTextWithShadow(textRenderer, 
            Text.translatable("randomrun.defeat.time", "§e" + timeStr), 
            width / 2, height / 2 + 70, 0xFFFFFF);
        
      
        if (reason != null && !reason.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, 
                "§7" + reason, 
                width / 2, height / 2 + 90, 0xAAAAAA);
        }
        
        
        context.drawCenteredTextWithShadow(textRenderer, 
            Text.translatable("randomrun.victory.warning"), 
            width / 2, height - 80, 0x888888);
        
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
        BakedModel model = client.getItemRenderer().getModel(stack, null, null, 0);
        
        DiffuseLighting.disableGuiDepthLighting();
        
        client.getItemRenderer().renderItem(
            stack,
            ModelTransformationMode.GUI,
            false,
            context.getMatrices(),
            context.getVertexConsumers(),
            15728880,
            OverlayTexture.DEFAULT_UV,
            model
        );
        
        context.draw();
        DiffuseLighting.enableGuiDepthLighting();
        
        context.getMatrices().pop();
    }
    
    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
    
    @Override
    public boolean shouldPause() {
        return false;
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        
        if (keyCode == 256) { 
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    
    @Override
    public void close() {
        
        GlobalParticleSystem.getInstance().setRedMode(false);
        super.close();
    }
}
