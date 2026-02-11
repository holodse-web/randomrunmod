package com.randomrun.ui.screen.endgame;

import com.randomrun.ui.screen.main.AbstractRandomRunScreen;
import com.randomrun.ui.screen.main.MainModScreen;

import com.randomrun.main.RandomRunMod;
import com.randomrun.main.data.RunDataManager;
import com.randomrun.challenges.classic.world.WorldCreator;
import com.randomrun.ui.widget.GlobalParticleSystem;
import com.randomrun.ui.widget.styled.ButtonGreen;
import com.randomrun.battle.BattleManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.item.ModelTransformationMode;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.joml.Quaternionf;

public class VictoryScreen extends AbstractRandomRunScreen {
    private final Item completedItem;
    private final net.minecraft.util.Identifier completedAdvancementId;
    private final long completionTime;
    private final boolean allowRetry;
    private final String winnerName; // Добавленное поле
    private float rotationY = 0f;
    private float levitationOffset = 0f;
    private long openTime;
    
    public VictoryScreen(Item item, long time, String winnerName) { // Обновленный конструктор
        super(Text.translatable("randomrun.screen.victory.title"));
        this.completedItem = item;
        this.completedAdvancementId = null;
        this.completionTime = time;
        this.winnerName = winnerName;
        this.allowRetry = !BattleManager.getInstance().isInBattle();
    }
    
    public VictoryScreen(net.minecraft.util.Identifier advancementId, long time, String winnerName) { // Обновленный конструктор
        super(Text.translatable("randomrun.screen.victory.title"));
        this.completedItem = null;
        this.completedAdvancementId = advancementId;
        this.completionTime = time;
        this.winnerName = winnerName;
        this.allowRetry = !BattleManager.getInstance().isInBattle();
    }

    // Конструкторы для обратной совместимости
    public VictoryScreen(Item item, long time) {
        this(item, time, MinecraftClient.getInstance().getSession().getUsername());
    }

    public VictoryScreen(net.minecraft.util.Identifier advancementId, long time) {
        this(advancementId, time, MinecraftClient.getInstance().getSession().getUsername());
    }
    
    @Override
    protected void init() {
        super.init();
        openTime = System.currentTimeMillis();
        
        // Настройка системы зеленых частиц
        GlobalParticleSystem particleSystem = GlobalParticleSystem.getInstance();
        particleSystem.clearParticles();
        particleSystem.setGreenMode(true);
        
        int centerX = width / 2;
        
        // Кнопка повтора (тот же сид и предмет) - только если не в битве
        if (allowRetry) {
            addDrawableChild(new ButtonGreen(
                centerX - 100, height - 60,
                200, 20,
                Text.translatable("randomrun.button.retry_speedrun"),
                button -> {
                    GlobalParticleSystem.getInstance().setGreenMode(false);
                    GlobalParticleSystem.getInstance().clearParticles();
                    
                    MinecraftClient client = MinecraftClient.getInstance();
                    
                    if (client.world != null) {
                        client.world.disconnect();
                    }
                    client.disconnect();
                    
                    RandomRunMod.getInstance().getRunDataManager().cancelRun();
                    
                    String seed = WorldCreator.getLastCreatedSeed();
                    if (completedItem != null) {
                        WorldCreator.createSpeedrunWorld(completedItem, seed);
                    } else if (completedAdvancementId != null) {
                        WorldCreator.createSpeedrunWorld(completedAdvancementId, 0, seed);
                    }
                }
            ));
        }
        
        // Кнопка выхода в меню мода
        int exitButtonY = allowRetry ? height - 35 : height - 60;
        addDrawableChild(new ButtonGreen(
            centerX - 100, exitButtonY,
            200, 20,
            Text.translatable("randomrun.button.to_menu"),
            button -> {
                GlobalParticleSystem.getInstance().setGreenMode(false);
                GlobalParticleSystem.getInstance().clearParticles();
                
                MinecraftClient client = MinecraftClient.getInstance();
                
                // Выходим из мира
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
        int middleColor = 0xFF145514;  // Темно-зеленый
        int bottomColor = 0xFF0a2e0a;  // Более темный зеленый
        
        
        context.fillGradient(0, 0, width, height / 2, topColor, middleColor);
       
        context.fillGradient(0, height / 2, width, height, middleColor, bottomColor);
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Обновление анимаций
        long elapsed = System.currentTimeMillis() - openTime;
        rotationY += delta * 2f;
        levitationOffset = (float) Math.sin(elapsed / 500.0) * 5f;
        
        // Рендеринг красивого фона с частицами (из AbstractRandomRunScreen)
        renderBackground(context, mouseX, mouseY, delta);
        
        // Заголовок победы с анимацией
        float scale = 1.0f + (float) Math.sin(elapsed / 300.0) * 0.05f;
        context.getMatrices().push();
        context.getMatrices().translate(width / 2f, 40, 0);
        context.getMatrices().scale(scale * 2, scale * 2, 1);
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("randomrun.screen.victory.title_label"), 0, 0, 0x55FF55);
        context.getMatrices().pop();
        
        // Показать имя победителя, если в битве
        if (BattleManager.getInstance().isInBattle() && winnerName != null) {
            context.drawCenteredTextWithShadow(textRenderer, 
                Text.literal("Winner: " + winnerName), 
                width / 2, 70, 0xFFFF00); // Желтый текст
        }
        
        // Подготовка данных для отображения
        ItemStack displayStack = ItemStack.EMPTY;
        Text displayName = Text.empty();
        String recordId = null;
        
        if (completedItem != null) {
            displayStack = new ItemStack(completedItem);
            displayName = Text.literal("§6" + completedItem.getName().getString());
            recordId = net.minecraft.registry.Registries.ITEM.getId(completedItem).toString();
        } else if (completedAdvancementId != null) {
            if (MinecraftClient.getInstance().getNetworkHandler() != null) {
                 var manager = MinecraftClient.getInstance().getNetworkHandler().getAdvancementHandler();
                 var entry = manager.get(completedAdvancementId);
                 if (entry != null && entry.value().display().isPresent()) {
                     displayStack = entry.value().display().get().getIcon();
                     displayName = entry.value().display().get().getTitle();
                 }
            }
            if (displayStack.isEmpty()) {
                displayStack = new ItemStack(net.minecraft.item.Items.BOOK);
                displayName = Text.literal(completedAdvancementId.toString());
            }
            recordId = completedAdvancementId.toString();
        }

        // Рендеринг 3D предмета
        render3DItem(context, width / 2, height / 2 - 30, displayStack);
        
        // Название предмета
        context.drawCenteredTextWithShadow(textRenderer, displayName, width / 2, height / 2 + 50, 0x006400);
        
        // Время прохождения
        String timeStr = RunDataManager.formatTime(completionTime);
        context.drawCenteredTextWithShadow(textRenderer, 
            Text.translatable("randomrun.victory.time", "§e" + timeStr), 
            width / 2, height / 2 + 70, 0xFFFFFF);
        
        // Проверка на новый рекорд
        RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
        if (recordId != null) {
            RunDataManager.RunResult result = runManager.getResultForItem(recordId);
            
            if (result != null && result.bestTime == completionTime) {
                context.drawCenteredTextWithShadow(textRenderer, 
                    Text.translatable("randomrun.victory.new_record"), 
                    width / 2, height / 2 + 90, 0xA8FFB5);
            }
        }
        
        // Предупреждение о черном экране
        context.drawCenteredTextWithShadow(textRenderer, 
            Text.translatable("randomrun.victory.warning"), 
            width / 2, height - 80, 0x888888);
        
        super.render(context, mouseX, mouseY, delta);
    }
    
    private void render3DItem(DrawContext context, int x, int y, ItemStack stack) {
        if (stack.isEmpty()) return;
        
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
        return false; // Не ставим игру на паузу, чтобы мышь не привязывалась к центру
    }
    
    @Override
    public void close() {
        GlobalParticleSystem.getInstance().setGreenMode(false);
        super.close();
    }
}
