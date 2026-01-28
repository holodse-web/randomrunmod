package com.randomrun.gui.screen;

import com.randomrun.RandomRunMod;
import com.randomrun.data.RunDataManager;
import com.randomrun.gui.widget.StyledButton2;
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
import org.joml.Quaternionf;

public class VictoryScreen extends AbstractRandomRunScreen {
    private final Item completedItem;
    private final long completionTime;
    private float rotationY = 0f;
    private float levitationOffset = 0f;
    private long openTime;
    
    public VictoryScreen(Item item, long time) {
        super(Text.translatable("randomrun.screen.victory.title"));
        this.completedItem = item;
        this.completionTime = time;
    }
    
    @Override
    protected void init() {
        super.init();
        openTime = System.currentTimeMillis();
        
        int centerX = width / 2;
        
        // Play again button (same item, different seed)
        addDrawableChild(new StyledButton2(
            centerX - 100, height - 60,
            200, 20,
            Text.translatable("randomrun.button.play_again"),
            button -> {
                MinecraftClient client = MinecraftClient.getInstance();
                // Отменяем текущий ран
                RandomRunMod.getInstance().getRunDataManager().cancelRun();
                // Выходим из мира
                if (client.world != null) {
                    client.world.disconnect();
                }
                client.disconnect();
                // Показываем экран выбора предмета с тем же предметом
                client.setScreen(new ItemRevealScreen(new MainModScreen(new TitleScreen()), completedItem));
            }
        ));
        
        // Exit to mod menu button
        addDrawableChild(new StyledButton2(
            centerX - 100, height - 30,
            200, 20,
            Text.translatable("randomrun.button.exit"),
            button -> {
                MinecraftClient client = MinecraftClient.getInstance();
                RandomRunMod.getInstance().getRunDataManager().cancelRun();
                // Выходим из мира
                if (client.world != null) {
                    client.world.disconnect();
                }
                client.disconnect();
                client.setScreen(new MainModScreen(new TitleScreen()));
            }
        ));
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Update animations
        long elapsed = System.currentTimeMillis() - openTime;
        rotationY += delta * 2f;
        levitationOffset = (float) Math.sin(elapsed / 500.0) * 5f;
        
        // Render beautiful background with particles (from AbstractRandomRunScreen)
        renderBackground(context, mouseX, mouseY, delta);
        
        // Victory title with animation
        float scale = 1.0f + (float) Math.sin(elapsed / 300.0) * 0.05f;
        context.getMatrices().push();
        context.getMatrices().translate(width / 2f, 40, 0);
        context.getMatrices().scale(scale * 2, scale * 2, 1);
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("randomrun.screen.victory.title_label"), 0, 0, 0x55FF55);
        context.getMatrices().pop();
        
        // Render 3D item
        render3DItem(context, width / 2, height / 2 - 30);
        
        // Item name
        String itemName = completedItem.getName().getString();
        context.drawCenteredTextWithShadow(textRenderer, "§6" + itemName, width / 2, height / 2 + 50, 0xFFAA00);
        
        // Completion time
        String timeStr = RunDataManager.formatTime(completionTime);
        context.drawCenteredTextWithShadow(textRenderer, 
            Text.translatable("randomrun.victory.time", "§e" + timeStr), 
            width / 2, height / 2 + 70, 0xFFFFFF);
        
        // Check for new record
        RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
        RunDataManager.RunResult result = runManager.getResultForItem(
            net.minecraft.registry.Registries.ITEM.getId(completedItem).toString()
        );
        
        if (result != null && result.bestTime == completionTime) {
            context.drawCenteredTextWithShadow(textRenderer, 
                "§d§l★ NEW RECORD! ★", 
                width / 2, height / 2 + 90, 0xFF55FF);
        }
        
        // Предупреждение о черном экране
        context.drawCenteredTextWithShadow(textRenderer, 
            "§7Черный экран при нажатии - это выход из мира", 
            width / 2, height - 80, 0x888888);
        
        super.render(context, mouseX, mouseY, delta);
    }
    
    private void render3DItem(DrawContext context, int x, int y) {
        ItemStack stack = new ItemStack(completedItem);
        
        context.getMatrices().push();
        context.getMatrices().translate(x, y + levitationOffset, 100);
        context.getMatrices().scale(64f, -64f, 64f);
        
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
        return false; // Не ставим игру на паузу, чтобы мышь не привязывалась к центру
    }
}
