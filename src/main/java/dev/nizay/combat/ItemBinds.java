package dev.nizay.combat;

import dev.nizay.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Бинды предметов хотбара (FunTime): по клавише находит предмет в хотбаре по названию, берёт его в руку,
 * использует (ПКМ) и возвращает прежний слот. Это обычные пакеты выбора слота и использования предмета,
 * поэтому работает на ходу и на любой скорости.
 */
public final class ItemBinds {
	private ItemBinds() {}

	public record Def(String id, String en, String ru, String[] words, Item icon, Item type) {}

	private static Def d(String id, String en, String ru, Item icon, Item type, String... words) { return new Def(id, en, ru, words, icon, type); }

	public static final Def[] DEFS = {
		d("trap", "Trap", "Трапка", Items.NETHERITE_SCRAP, null, "трапк", "trap"),
		d("plast", "Plast", "Пласт", Items.OBSIDIAN, null, "пласт", "plast"),
		d("disorient", "Disorientation", "Дезориентация", Items.ENDER_EYE, null, "дезориент", "disorient"),
		d("dust", "Revealing Dust", "Явная пыль", Items.GLOWSTONE_DUST, null, "явная пыль", "явная", "revealing"),
		d("fireball", "Fireball", "Огненный шар", Items.FIRE_CHARGE, null, "огненный шар", "огненн", "fireball"),
		d("divine", "Divine Aura", "Божья аура", Items.NETHER_STAR, null, "божья аура", "божья", "divine"),
		d("snow", "Freeze Snowball", "Снежок заморозки", Items.SNOWBALL, null, "заморозк", "ледян", "freeze"),
		d("pearl", "Ender Pearl", "Эндер-жемчуг", Items.ENDER_PEARL, Items.ENDER_PEARL),
	};

	private static final Map<String, ItemStack> ICONS = new HashMap<>();
	private static final Map<String, Boolean> DOWN = new HashMap<>();
	private static int cooldown, pendingBack = -1, pendingWait;

	public static ItemStack icon(Def d) { return ICONS.computeIfAbsent(d.id(), k -> new ItemStack(d.icon())); }

	public static boolean matches(Def d, ItemStack s) {
		if (s.isEmpty()) return false;
		if (d.type() != null && s.is(d.type())) return true;
		if (d.words().length == 0) return false;
		String n = s.getHoverName().getString().toLowerCase(Locale.ROOT);
		for (String w : d.words()) if (n.contains(w)) return true;
		return false;
	}

	/** Номер слота хотбара (0..8) с подходящим предметом или -1. */
	public static int find(LocalPlayer pl, Def d) {
		for (int i = 0; i < 9; i++) if (matches(d, pl.getInventory().getItem(i))) return i;
		return -1;
	}

	public static void tick(Minecraft mc) {
		LocalPlayer pl = mc.player;
		if (pl == null || mc.gameMode == null) return;
		if (cooldown > 0) cooldown--;
		if (pendingBack >= 0 && --pendingWait <= 0 && !pl.isUsingItem()) { pl.getInventory().setSelectedSlot(pendingBack); pendingBack = -1; }
		Config c = Config.I;
		long win = GLFW.glfwGetCurrentContext();
		boolean ok = win != 0 && mc.screen == null && c.master && c.binds.on;
		for (Def d : DEFS) {
			Config.BindItem b = c.bind(d.id());
			boolean down = ok && b.on && b.key > 0 && GLFW.glfwGetKey(win, b.key) == GLFW.GLFW_PRESS;
			boolean was = DOWN.getOrDefault(d.id(), false);
			DOWN.put(d.id(), down);
			if (down && !was && cooldown == 0 && pendingBack < 0) {
				int slot = find(pl, d);
				if (slot >= 0) { use(mc, pl, slot); cooldown = 2; }
			}
		}
	}

	private static void use(Minecraft mc, LocalPlayer pl, int slot) {
		var inv = pl.getInventory(); int prev = inv.getSelectedSlot();
		inv.setSelectedSlot(slot);
		mc.gameMode.useItem(pl, InteractionHand.MAIN_HAND);   // сам досылает смену слота на сервер
		if (prev != slot) { pendingBack = prev; pendingWait = 2; }
	}
}
