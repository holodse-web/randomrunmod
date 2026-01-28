package com.randomrun.gui.screen;

import com.randomrun.RandomRunMod;
import com.randomrun.battle.BattleManager;
import com.randomrun.battle.BattleRoom;
import com.randomrun.gui.widget.StyledButton2;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class BattleWaitingScreen extends AbstractRandomRunScreen {
    private final Screen parent;
    private final String roomCode;
    private final boolean isHost;
    private boolean guestJoined = false;
    
    public BattleWaitingScreen(Screen parent, String roomCode, boolean isHost) {
        super(Text.translatable("randomrun.battle.waiting"));
        this.parent = parent;
        this.roomCode = roomCode;
        this.isHost = isHost;
    }
    
    @Override
    protected void init() {
        super.init();
        
        int centerX = width / 2;
        
        BattleRoom room = BattleManager.getInstance().getCurrentRoom();
        
        if (isHost && room != null && room.getGuest() != null) {
            addDrawableChild(new StyledButton2(
                centerX - 100, height - 70,
                200, 20,
                Text.literal("§a" + Text.translatable("randomrun.battle.start_game").getString()),
                button -> {
                    BattleManager.getInstance().setStatusLoading();
                },
                0, 0.1f
            ));
        }
        
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
            Text.literal("§l§6" + Text.translatable("randomrun.battle.waiting").getString().toUpperCase()), 
            centerX, 30, 0xFFFFFF);
        
        BattleRoom room = BattleManager.getInstance().getCurrentRoom();
        
        
        if (room != null && room.getGuest() != null && !guestJoined) {
            guestJoined = true;
            clearChildren();
            init();
        }
        
        if (room != null) {
            if (isHost) {
                context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("§e" + Text.translatable("randomrun.battle.room_code", "§f§l" + roomCode).getString()),
                    centerX, centerY - 40, 0xFFFFFF);
                
                context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("§7" + Text.translatable("randomrun.battle.send_code").getString()),
                    centerX, centerY - 25, 0xAAAAAA);
            }
            
            if (room.getGuest() == null) {
                context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("§7" + Text.translatable("randomrun.battle.waiting_player").getString()),
                    centerX, centerY, 0xAAAAAA);
            } else {
                context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("§a✓ " + Text.translatable("randomrun.battle.player_joined").getString()),
                    centerX, centerY, 0x55FF55);
                
                if (isHost) {
                    context.drawCenteredTextWithShadow(textRenderer,
                        Text.literal("§7" + Text.translatable("randomrun.battle.click_ready").getString()),
                        centerX, centerY + 15, 0xAAAAAA);
                } else {
                    context.drawCenteredTextWithShadow(textRenderer,
                        Text.literal("§7" + Text.translatable("randomrun.battle.waiting_host").getString()),
                        centerX, centerY + 15, 0xAAAAAA);
                }
            }
            
            Item targetItem = Registries.ITEM.get(new Identifier(room.getTargetItem()));
            if (targetItem != null) {
                String itemName = targetItem.getName().getString();
                context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("§e" + Text.translatable("randomrun.battle.target", "§f" + itemName).getString()),
                    centerX, centerY + 40, 0xFFFFFF);
            }
        }
    }
    
    @Override
    public void close() {
        if (BattleManager.getInstance().isInBattle()) {
            BattleManager.getInstance().deleteRoom();
            BattleManager.getInstance().stopBattle();
        }
        super.close();
    }
}
