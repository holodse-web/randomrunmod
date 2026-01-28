package com.randomrun.gui.screen;

import com.randomrun.battle.BattleManager;
import com.randomrun.battle.BattleRoom;
import com.randomrun.gui.widget.StyledButton2;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class MatchReadyScreen extends AbstractRandomRunScreen {
    private final Screen parent;
    private boolean isReady = false;
    
    public MatchReadyScreen(Screen parent, String matchId) {
        super(Text.translatable("randomrun.battle.match_ready"));
        this.parent = parent;
    }
    
    @Override
    protected void init() {
        super.init();
        
        
        this.clearChildren();
        
        int centerX = width / 2;
        
        addDrawableChild(new StyledButton2(
            centerX - 100, height - 70,
            200, 20,
            Text.literal((isReady ? "§a✓ " : "§e") + Text.translatable("randomrun.battle.ready").getString()),
            button -> {
                if (!isReady) {
                    isReady = true;
                    BattleManager.getInstance().sendLobbyReady();
                    init();
                }
            },
            0, 0.1f
        ));
        
        addDrawableChild(new StyledButton2(
            centerX - 100, height - 30,
            200, 20,
            Text.translatable("randomrun.button.cancel"),
            button -> {
                BattleManager.getInstance().deleteRoom();
                BattleManager.getInstance().stopBattle();
                MinecraftClient.getInstance().setScreen(parent);
            },
            1, 0.15f
        ));
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        
        int centerX = width / 2;
        int centerY = height / 2;
        
        context.drawCenteredTextWithShadow(textRenderer, 
            Text.literal("§l§6" + Text.translatable("randomrun.battle.match_ready").getString().toUpperCase()), 
            centerX, 30, 0xFFFFFF);
        
        BattleRoom room = BattleManager.getInstance().getCurrentRoom();
        
        if (room != null) {
            Item targetItem = Registries.ITEM.get(new Identifier(room.getTargetItem()));
            
            int itemBoxSize = 60;
            int itemBoxX = centerX - itemBoxSize / 2;
            int itemBoxY = centerY - 80;
            
            context.fill(itemBoxX - 3, itemBoxY - 3, itemBoxX + itemBoxSize + 3, itemBoxY + itemBoxSize + 3, 0xFFFFD700);
            context.fill(itemBoxX, itemBoxY, itemBoxX + itemBoxSize, itemBoxY + itemBoxSize, 0xFF1a1a1a);
            
            context.getMatrices().push();
            context.getMatrices().translate(centerX, itemBoxY + itemBoxSize / 2, 0);
            context.getMatrices().scale(3.0f, 3.0f, 1.0f);
            context.drawItem(new ItemStack(targetItem), -8, -8);
            context.getMatrices().pop();
            
            String itemName = targetItem.getName().getString();
            context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("§e§lЦель: §f" + itemName),
                centerX, itemBoxY + itemBoxSize + 10, 0xFFFFFF);
            
            String seedText = String.valueOf(room.getSeed());
            context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("§b§lСид: §7" + seedText),
                centerX, centerY + 20, 0xFFFFFF);
            
            String playerName = MinecraftClient.getInstance().getSession().getUsername();
            boolean isHost = room.isHost(playerName);
            
            int statusY = centerY + 50;
            
            context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("§7Вы: " + (isReady ? "§a✓ Готов" : "§e⏳ Ожидание")),
                centerX, statusY, 0xFFFFFF);
            
          
            boolean opponentReady = isHost ? room.isGuestReady() : room.isHostReady();
            
            context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("§7Противник: " + (opponentReady ? "§a✓ Готов" : "§e⏳ Ожидание")),
                centerX, statusY + 15, 0xFFFFFF);
            
         
            int actualReadyCount = 0;
            if (room.isHostReady()) actualReadyCount++;
            if (room.isGuestReady()) actualReadyCount++;
            
            context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("§7Готовность: §f" + actualReadyCount + "/2"),
                centerX, statusY + 30, 0xAAAAAA);
            
       
        }
    }
    
    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
