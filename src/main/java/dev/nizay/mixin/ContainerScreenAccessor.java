package dev.nizay.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Положение окна инвентаря на экране — нужно, чтобы нарисовать кулдауны поверх слотов. */
@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccessor {
	@Accessor("leftPos") int nizay$left();
	@Accessor("topPos") int nizay$top();
}
