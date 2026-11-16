package dev.nizay;

import dev.nizay.combat.Swapper;
import dev.nizay.combat.Timers;
import dev.nizay.fx.Fx;
import dev.nizay.fx.HitFx;
import dev.nizay.gui.NizayMenuScreen;
import dev.nizay.gui.NizayTitleScreen;
import dev.nizay.friends.Friends;
import dev.nizay.util.Utility;
import dev.nizay.hud.NizayHud;
import dev.nizay.opt.Optimizer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.ChatVisiblity;
import org.lwjgl.glfw.GLFW;

public class NizayClient implements ClientModInitializer {
	public static final String VERSION = "0.5.1";
	private boolean rshiftDown, optApplied;

	@Override
	public void onInitializeClient() {
		Config.load();
		Friends.start();
		Fx.register();
		HitFx.register();
		Timers.register();
		NizayHud.register();
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		// кулдауны поверх слотов в инвентаре / сундуках
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> cs)
				ScreenEvents.afterRender(screen).register((sc, g, mx, my, dt) -> NizayHud.containerCooldowns(g, cs));
		});
		ClientTickEvents.START_CLIENT_TICK.register(mc -> {
			if (!optApplied && mc.options != null) { optApplied = true; Optimizer.apply(); }
			if (mc.screen instanceof TitleScreen) mc.setScreen(new NizayTitleScreen());
		});
		Runtime.getRuntime().addShutdownHook(new Thread(() -> { Utility.shutdown(); Config.save(); }));
	}

	private void tick(Minecraft mc) {
		// Right Shift — меню. Опрашиваем GLFW напрямую, чтобы не зависеть от API KeyMapping.
		long win = GLFW.glfwGetCurrentContext();
		boolean down = win != 0 && GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
		if (down && !rshiftDown && mc.level != null) {
			if (mc.screen == null) mc.setScreen(new NizayMenuScreen());
			else if (mc.screen instanceof NizayMenuScreen) mc.setScreen(null);
		}
		rshiftDown = down;

		chatGuard(mc);
		Friends.updatePos(mc);
		Utility.tick(mc);
		Fx.tick(mc);
		Swapper.tick(mc);
		dev.nizay.combat.ItemBinds.tick(mc);
		dev.nizay.combat.AucHelper.tick(mc);
		Timers.tick(mc);
	}

	/**
	 * Чат должен показываться всегда: если в настройках игры он скрыт («Скрыт» / «Только команды»)
	 * или включено «Только защищённый чат» (на серверах с офлайн-аккаунтами все сообщения игроков
	 * не подписаны и из-за этой галочки полностью пропадают) — возвращаем нормальные значения.
	 */
	private void chatGuard(Minecraft mc) {
		if (mc.options == null) return;
		try {
			if (mc.options.chatVisibility().get() != ChatVisiblity.FULL) {
				System.err.println("[Nizay] chat visibility was " + mc.options.chatVisibility().get() + " -> FULL");
				mc.options.chatVisibility().set(ChatVisiblity.FULL);
			}
			if (mc.options.onlyShowSecureChat().get()) {
				System.err.println("[Nizay] onlyShowSecureChat was ON -> OFF");
				mc.options.onlyShowSecureChat().set(Boolean.FALSE);
			}
		} catch (Throwable t) { System.err.println("[Nizay] chatGuard failed: " + t); }
	}
}
