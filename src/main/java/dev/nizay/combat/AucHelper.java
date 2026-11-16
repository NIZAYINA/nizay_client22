package dev.nizay.combat;

import dev.nizay.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import org.lwjgl.glfw.GLFW;

/**
 * AucHelper (FunTime). Два независимых модуля:
 *  1) Relist — раз в N секунд (по умолчанию 61) тихо отправляет /ah, жмёт «Хранилище» (слева снизу), затем кнопку
 *     перевыставления (справа снизу) и закрывает окно. За 10 секунд до цикла показывается плашка с отсчётом.
 *  2) Search — по клавише сразу отправляет /ah search.
 * Команды уходят пакетом, в чат ничего не пишется.
 */
public final class AucHelper {
	private AucHelper() {}

	private enum St { IDLE, WAIT_AH, WAIT_STORAGE, WAIT_RED, WAIT_RELIST }
	private static St st = St.IDLE;
	private static long nextAt, stageAt;
	private static int lastId;
	private static boolean wasOn, searchDown;

	/** Секунд до следующего перевыставления (или -1). */
	public static int secondsLeft() {
		if (!Config.I.auc.relistOn || !wasOn) return -1;
		return (int) Math.max(0, (nextAt - System.currentTimeMillis() + 999) / 1000);
	}

	public static boolean busy() { return st != St.IDLE; }

	/** Показывать ли плашку: за 10 секунд до цикла и во время самого перевыставления. */
	public static boolean plaque() {
		int s = secondsLeft();
		return Config.I.auc.relistOn && Config.I.master && (busy() || (s >= 0 && s <= 10));
	}

	public static String statusText() {
		if (busy()) return "Перевыставляю предметы...";
		int s = secondsLeft();
		return s < 0 ? "Перевыставление через 10 сек" : "Перевыставление через " + s + " сек";
	}

	public static void tick(Minecraft mc) {
		LocalPlayer pl = mc.player;
		Config.Auc a = Config.I.auc;
		long now = System.currentTimeMillis();
		if (pl == null || mc.gameMode == null || mc.getConnection() == null) { st = St.IDLE; wasOn = false; searchDown = false; return; }
		boolean master = Config.I.master;

		// ---- Search по клавише
		long win = GLFW.glfwGetCurrentContext();
		boolean down = master && a.searchOn && a.searchKey > 0 && win != 0 && mc.screen == null && GLFW.glfwGetKey(win, a.searchKey) == GLFW.GLFW_PRESS;
		if (down && !searchDown) mc.getConnection().sendCommand("ah search");
		searchDown = down;

		// ---- Relist
		if (!(master && a.relistOn)) { if (st != St.IDLE) abort(pl); st = St.IDLE; wasOn = false; return; }
		if (!wasOn) { wasOn = true; nextAt = now + 3000; }
		long delay = Math.max(100, a.delayMs);
		switch (st) {
			case IDLE -> {
				if (now >= nextAt && mc.screen == null && pl.containerMenu == pl.inventoryMenu) {
					mc.getConnection().sendCommand("ah");
					st = St.WAIT_AH; stageAt = now;
				}
			}
			case WAIT_AH -> {
				if (pl.containerMenu instanceof ChestMenu cm && cm.containerId != pl.inventoryMenu.containerId && now - stageAt > delay) {
					click(mc, pl, cm, (cm.getRowCount() - 1) * 9 + a.storageCol);   // слева снизу: «Хранилище»
					lastId = cm.containerId; st = St.WAIT_STORAGE; stageAt = now;
				} else if (now - stageAt > 6000) abort(pl);
			}
			case WAIT_STORAGE -> {
				// клики по кнопкам хранилища — только когда реально открыто окно «Хранилище» (защита от случайных нажатий)
				if (pl.containerMenu instanceof ChestMenu cm && storageOpen(mc) && now - stageAt > delay) {
					click(mc, pl, cm, (cm.getRowCount() - 1) * 9 + a.redCol);       // красная кнопка слева
					st = St.WAIT_RED; stageAt = now;
				} else if (now - stageAt > 5000) abort(pl);
			}
			case WAIT_RED -> {
				if (pl.containerMenu instanceof ChestMenu cm && storageOpen(mc) && now - stageAt > delay) {
					click(mc, pl, cm, (cm.getRowCount() - 1) * 9 + a.circleCol);    // кружок с красной точкой
					st = St.WAIT_RELIST; stageAt = now;
				} else if (now - stageAt > 5000) abort(pl);
			}
			case WAIT_RELIST -> {
				if (now - stageAt > delay) {
					pl.closeContainer();
					st = St.IDLE; nextAt = now + Math.max(30, a.interval) * 1000L;
				}
			}
		}
	}

	private static boolean storageOpen(Minecraft mc) {
		return mc.screen != null && mc.screen.getTitle().getString().toLowerCase(java.util.Locale.ROOT).contains("хранилищ");
	}

	private static void click(Minecraft mc, LocalPlayer pl, ChestMenu cm, int slot) {
		mc.gameMode.handleInventoryMouseClick(cm.containerId, slot, 0, ClickType.PICKUP, pl);
	}

	private static void abort(LocalPlayer pl) {
		if (pl.containerMenu != pl.inventoryMenu) pl.closeContainer();
		st = St.IDLE; nextAt = System.currentTimeMillis() + 10000;
	}
}
