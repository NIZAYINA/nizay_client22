package dev.nizay.combat;

import dev.nizay.Config;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * ItemSwap (бывший AutoSwap): левая рука тотем <-> сфера (из хотбара и из инвентаря), при пустой руке берёт лучший тотем.
 * ElytraSwap: элитры <-> нагрудник. Работают по клавише, обмен идёт обычными кликами по инвентарю (как руками).
 */
public final class Swapper {
	private Swapper() {}

	private static boolean aDown, eDown;
	private static int cooldown, pendingBack = -1, pendingWait, stage, stageWait, act;
	private static final int FIRST = 9, LAST = 44, CHEST = 6;

	public static void tick(Minecraft mc) {
		LocalPlayer pl = mc.player;
		if (pl == null || mc.gameMode == null) return;
		if (cooldown > 0) cooldown--;
		if (pendingBack >= 0 && --pendingWait <= 0 && !pl.isUsingItem()) { pl.getInventory().setSelectedSlot(pendingBack); pendingBack = -1; }
		long win = GLFW.glfwGetCurrentContext();
		boolean ok = win != 0 && mc.screen == null && Config.I.master && pl.containerMenu == pl.inventoryMenu;
		Config c = Config.I;
		boolean a = ok && c.autoSwap.on && c.autoSwap.key > 0 && GLFW.glfwGetKey(win, c.autoSwap.key) == GLFW.GLFW_PRESS;
		boolean e = ok && c.elytraSwap.on && c.elytraSwap.key > 0 && GLFW.glfwGetKey(win, c.elytraSwap.key) == GLFW.GLFW_PRESS;
		// Защита сервера от кликов «на бегу»: на пару тиков отпускаем клавиши движения, делаем обмен и возвращаем управление.
		if (stage > 0) {
			hold(mc);
			if (--stageWait <= 0) {
				if (stage == 1) { run(mc, pl); stage = 2; stageWait = 2; }
				else { KeyMapping.setAll(); stage = 0; }
			}
		} else {
			if (a && !aDown && cooldown == 0) start(mc, pl, 1);
			if (e && !eDown && cooldown == 0) start(mc, pl, 2);
		}
		aDown = a; eDown = e;
	}

	private static void start(Minecraft mc, LocalPlayer pl, int action) {
		act = action;
		if (moving(mc, pl)) { stage = 1; stageWait = 2; hold(mc); }
		else run(mc, pl);
	}

	private static void run(Minecraft mc, LocalPlayer pl) {
		if (act == 1) offhand(mc, pl); else if (act == 2) elytra(mc, pl);
		act = 0; cooldown = 3;
	}

	private static boolean moving(Minecraft mc, LocalPlayer pl) {
		var o = mc.options;
		return o.keyUp.isDown() || o.keyDown.isDown() || o.keyLeft.isDown() || o.keyRight.isDown() || o.keyJump.isDown()
			|| pl.getDeltaMovement().horizontalDistanceSqr() > 0.0004;
	}

	private static void hold(Minecraft mc) {
		var o = mc.options;
		o.keyUp.setDown(false); o.keyDown.setDown(false); o.keyLeft.setDown(false); o.keyRight.setDown(false);
		o.keyJump.setDown(false); o.keySprint.setDown(false);
	}

	private static void click(Minecraft mc, LocalPlayer pl, int slot, int button, ClickType type) {
		mc.gameMode.handleInventoryMouseClick(pl.inventoryMenu.containerId, slot, button, type, pl);
	}

	private static ItemStack at(LocalPlayer pl, int slot) { return pl.inventoryMenu.getSlot(slot).getItem(); }

	// ------------------------------------------------------------------ AutoSwap
	private static boolean totem(ItemStack s) { return s.is(Items.TOTEM_OF_UNDYING); }

	private static boolean sphere(ItemStack s) {
		if (s.isEmpty()) return false;
		String n = s.getHoverName().getString().toLowerCase(Locale.ROOT);
		return n.contains("сфер") || n.contains("sphere");
	}

	/** Серверные тотемы (с названием, описанием, зачарованием) ценнее обычного. */
	private static int totemScore(ItemStack s) {
		return (s.has(DataComponents.CUSTOM_NAME) ? 4 : 0) + (s.has(DataComponents.LORE) ? 2 : 0) + (s.isEnchanted() ? 1 : 0);
	}

	private static int bestTotem(LocalPlayer pl) {
		int best = -1, score = -1;
		for (int i = LAST; i >= FIRST; i--) {   // хотбар (36..44) просматривается первым и выигрывает при равенстве
			ItemStack s = at(pl, i);
			if (!totem(s)) continue;
			int sc = totemScore(s);
			if (sc > score) { score = sc; best = i; }
		}
		return best;
	}

	private static int firstSphere(LocalPlayer pl) {
		for (int i = LAST; i >= FIRST; i--) if (sphere(at(pl, i))) return i;
		return -1;
	}

	private static void offhand(Minecraft mc, LocalPlayer pl) {
		ItemStack off = pl.getOffhandItem();
		int slot;
		if (totem(off)) slot = firstSphere(pl);
		else if (sphere(off)) slot = bestTotem(pl);
		else { slot = bestTotem(pl); if (slot < 0) slot = firstSphere(pl); }
		if (slot < 0) return;
		if (slot >= 36) {
			// предмет в хотбаре: обычный пакет «обмен с левой рукой» (клавиша F) — работает на бегу, без кликов по инвентарю
			var inv = pl.getInventory(); int prev = inv.getSelectedSlot(), hb = slot - 36;
			if (prev != hb) { inv.setSelectedSlot(hb); mc.getConnection().send(new ServerboundSetCarriedItemPacket(hb)); }
			mc.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
			if (prev != hb) { inv.setSelectedSlot(prev); mc.getConnection().send(new ServerboundSetCarriedItemPacket(prev)); }
		} else click(mc, pl, slot, 40, ClickType.SWAP);   // 40 = левая рука
	}

	// ------------------------------------------------------------------ ElytraSwap
	private static boolean chestplate(ItemStack s) {
		return !s.isEmpty() && BuiltInRegistries.ITEM.getKey(s.getItem()).getPath().endsWith("_chestplate");
	}

	private static boolean goodElytra(ItemStack s) { return s.is(Items.ELYTRA) && s.getDamageValue() < s.getMaxDamage() - 1; }

	private static int plateScore(ItemStack s) {
		String p = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
		int base = p.startsWith("netherite") ? 50 : p.startsWith("diamond") ? 40 : p.startsWith("iron") ? 30 : p.startsWith("chainmail") ? 20 : p.startsWith("golden") ? 10 : 0;
		return base + (s.isEnchanted() ? 5 : 0) + (s.has(DataComponents.CUSTOM_NAME) ? 1 : 0);
	}

	private static void elytra(Minecraft mc, LocalPlayer pl) {
		ItemStack chest = at(pl, CHEST);
		int slot = -1, score = Integer.MIN_VALUE;
		boolean wantPlate = chest.is(Items.ELYTRA);
		for (int i = LAST; i >= FIRST; i--) {
			ItemStack s = at(pl, i);
			int sc;
			if (wantPlate) { if (!chestplate(s)) continue; sc = plateScore(s); }
			else { if (!goodElytra(s)) continue; sc = s.getMaxDamage() - s.getDamageValue(); }
			boolean hot = i >= 36;
			if (sc + (hot ? 1000 : 0) > score) { score = sc + (hot ? 1000 : 0); slot = i; }
		}
		if (slot < 0) return;
		if (slot >= 36) {
			// из хотбара: правый клик по броне/элитрам меняет их с надетыми — это обычное использование предмета, работает на ходу
			var inv = pl.getInventory(); int prev = inv.getSelectedSlot(), hb = slot - 36;
			inv.setSelectedSlot(hb);
			mc.gameMode.useItem(pl, InteractionHand.MAIN_HAND);
			if (prev != hb) { pendingBack = prev; pendingWait = 2; }
			return;
		}
		click(mc, pl, slot, 0, ClickType.PICKUP);
		click(mc, pl, CHEST, 0, ClickType.PICKUP);
		click(mc, pl, slot, 0, ClickType.PICKUP);
	}
}
