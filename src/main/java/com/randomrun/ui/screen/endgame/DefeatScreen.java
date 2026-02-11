package com.randomrun.ui.screen.endgame;

import com.randomrun.ui.screen.main.AbstractRandomRunScreen;
import com.randomrun.ui.screen.main.MainModScreen;

import com.randomrun.main.RandomRunMod;
import com.randomrun.main.data.RunDataManager;
import com.randomrun.challenges.advancement.data.AdvancementLoader;
import com.randomrun.challenges.classic.world.WorldCreator;
import com.randomrun.ui.widget.GlobalParticleSystem;
import com.randomrun.ui.widget.styled.ButtonRed;
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

public class DefeatScreen extends AbstractRandomRunScreen {
    private final Item targetItem;
    private final AdvancementLoader.AdvancementInfo targetAdvancement;
    private final long elapsedTime;
    private final String reason;
    private final boolean allowRetry;
    private float rotationY = 0f;
    private float levitationOffset = 0f;
    private long openTime;
    
    public DefeatScreen(Item item, long time, String reason) {
        super(Text.translatable("randomrun.screen.defeat.title"));
        this.targetItem = item;
        this.targetAdvancement = null;
        this.elapsedTime = time;
        this.reason = reason;
        this.allowRetry = !com.randomrun.battle.BattleManager.getInstance().isInBattle();
    }
    
    public DefeatScreen(Identifier advancementId, long time, String reason) {
        super(Text.translatable("randomrun.screen.defeat.title"));
        this.targetItem = null;
        this.elapsedTime = time;
        this.reason = reason;
        this.allowRetry = !com.randomrun.battle.BattleManager.getInstance().isInBattle();
        
        // Поиск информации о достижении
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
        
        // Проигрывание звука поражения
        if (client.player != null) {
            client.player.playSound(net.minecraft.sound.SoundEvents.ENTITY_WITHER_SPAWN, 1.0f, 1.0f);
        }
        
        
        GlobalParticleSystem particleSystem = GlobalParticleSystem.getInstance();
        particleSystem.clearParticles();
        particleSystem.setRedMode(true);
        
        int centerX = width / 2;
        
        // Кнопка повтора (тот же сид и предмет) - только если не в битве
        if (allowRetry) {
            addDrawableChild(new ButtonRed(
                centerX - 100, height - 60,
                200, 20,
                Text.translatable("randomrun.button.retry_speedrun"),
                button -> {
                    GlobalParticleSystem.getInstance().setRedMode(false);
                    GlobalParticleSystem.getInstance().clearParticles();
                    
                    MinecraftClient client = MinecraftClient.getInstance();
                    
                    if (client.world != null) {
                        client.world.disconnect();
                    }
                    client.disconnect();
                    
                    RandomRunMod.getInstance().getRunDataManager().cancelRun();
                    
                    String seed = WorldCreator.getLastCreatedSeed();
                    if (targetItem != null) {
                        WorldCreator.createSpeedrunWorld(targetItem, seed);
                    } else if (targetAdvancement != null) {
                        WorldCreator.createSpeedrunWorld(targetAdvancement.id, 0, seed);
                    }
                }
            ));
        }
        
        int exitButtonY = allowRetry ? height - 35 : height - 60;
        addDrawableChild(new ButtonRed(
            centerX - 100, exitButtonY,
            200, 20,
            Text.translatable("randomrun.button.to_menu"),
            button -> {
               
                GlobalParticleSystem.getInstance().setRedMode(false);
                GlobalParticleSystem.getInstance().clearParticles();
                
                MinecraftClient client = MinecraftClient.getInstance();
                
                if (client.world != null) {
                    client.world.disconnect();
                }
                client.disconnect();
                
                RandomRunMod.getInstance().getRunDataManager().cancelRun();
                
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
        
       
        // Рендеринг 3D предмета (опущенного)
        render3DItem(context, width / 2, height / 2 - 30);
        
        // Название предмета/достижения красным
        String itemName = targetItem != null ? targetItem.getName().getString() : (targetAdvancement != null ? targetAdvancement.title.getString() : "Unknown");
        context.drawCenteredTextWithShadow(textRenderer, "§c" + itemName, width / 2, height / 2 + 50, 0xFF5555);
        
        // Время белым
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
        context.getMatrices().scale(120f, -120f, 120f);
        
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
