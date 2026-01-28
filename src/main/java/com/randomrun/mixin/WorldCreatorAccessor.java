package com.randomrun.mixin;

import net.minecraft.client.gui.screen.world.WorldCreator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(WorldCreator.class)
public interface WorldCreatorAccessor {
    // Вызываем сам метод установки режима, а не просто меняем переменную
    @Invoker("setGameMode")
    void invokeSetGameMode(WorldCreator.Mode mode);
}
