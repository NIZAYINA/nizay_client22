package dev.nizay.combat;

import dev.nizay.Config;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Таймеры для плашки Cooldowns:
 *  - кулдауны предметов (эндер-жемчуг, хорус, трапка и т.д.): сервер присылает только долю оставшегося,
 *    полную длительность вычисляем по скорости убывания за тик;
 *  - обратный отсчёт после установки Пласта / Трапки (по названию предмета в руке), длительность задаётся в настройках.
 */
public final class Timers {
	private Timers() {}

	private static final class Cd { float prev, total, left = -1; String name = ""; }
	private static final Map<String, Cd> CDS = new HashMap<>();
	private record Placed(String name, long endMs) {}
	private static final List<Placed> PLACED = new ArrayList<>();
	private static long lastUseMs; private static String lastUseName = "";

	public static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> { onUse(player, level, hand); return InteractionResult.PASS; });
		UseItemCallback.EVENT.register((player, level, hand) -> { onUse(player, level, hand); return InteractionResult.PASS; });
	}

	private static void onUse(Player player, Level level, InteractionHand hand) {
		if (!level.isClientSide() || player != Minecraft.getInstance().player || !Config.I.master || !Config.I.timers.placed) return;
		ItemStack st = player.getItemInHand(hand);
		if (st.isEmpty()) return;
		String n = st.getHoverName().getString().toLowerCase(Locale.ROOT);
		String label; int sec;
		if (n.contains("пласт") || n.contains("plast")) { label = "Пласт"; sec = Config.I.timers.plastSec; }
		else if (n.contains("трап") || n.contains("trap")) { label = "Трапка"; sec = Config.I.timers.trapSec; }
		else return;
		long now = System.currentTimeMillis();
		if (label.equals(lastUseName) && now - lastUseMs < 700) return;   // block+item события одного клика
		lastUseName = label; lastUseMs = now;
		PLACED.removeIf(p -> p.name().equals(label));
		PLACED.add(new Placed(label, now + sec * 1000L));
	}

	public static void tick(Minecraft mc) {
		LocalPlayer pl = mc.player;
		if (pl == null) { CDS.clear(); PLACED.clear(); return; }
		if (!Config.I.timers.items) { CDS.clear(); return; }
		Set<String> seen = new HashSet<>();
		int n = pl.getInventory().getContainerSize();
		for (int i = 0; i < n; i++) {
			ItemStack s = pl.getInventory().getItem(i);
			if (s.isEmpty()) continue;
			String name = s.getHoverName().getString();
			String key = BuiltInRegistries.ITEM.getKey(s.getItem()) + "|" + name;
			if (seen.contains(key)) continue;
			float p = pl.getCooldowns().getCooldownPercent(s, 0f);
			if (p <= 0f) continue;
			seen.add(key);
			Cd c = CDS.computeIfAbsent(key, k -> new Cd());
			if (c.prev > 0f && p < c.prev) {
				float tot = 1f / (c.prev - p);
				c.total = c.total == 0 ? tot : c.total * .7f + tot * .3f;
			}
			c.prev = p; c.name = name;
			c.left = c.total > 0 ? p * c.total / 20f : -1f;
		}
		CDS.keySet().removeIf(k -> !seen.contains(k));
	}

	private static String fmt(float sec) {
		if (sec >= 60) { int s = Math.round(sec); return String.format("%d:%02d", s / 60, s % 60); }
		return String.format(Locale.ROOT, "%.1fs", sec);
	}

	/** Сколько секунд осталось до повторного использования предмета (или -1, если кулдауна нет / он ещё измеряется). */
	public static float leftFor(ItemStack s) {
		if (!Config.I.timers.items || s.isEmpty()) return -1f;
		Cd c = CDS.get(BuiltInRegistries.ITEM.getKey(s.getItem()) + "|" + s.getHoverName().getString());
		return c == null ? -1f : c.left;
	}

	/** Компактный текст для иконки в хотбаре: 12 / 4.5 / 1:05. */
	public static String shortFmt(float sec) {
		if (sec >= 60) { int t = Math.round(sec); return String.format("%d:%02d", t / 60, t % 60); }
		if (sec >= 10) return String.valueOf(Math.round(sec));
		return String.format(Locale.ROOT, "%.1f", Math.max(0.1f, sec));
	}

	/** Строки {название, время} для плашки Cooldowns. */
	public static List<String[]> rows() {
		List<String[]> r = new ArrayList<>();
		if (!Config.I.master) return r;
		long now = System.currentTimeMillis();
		for (Iterator<Placed> it = PLACED.iterator(); it.hasNext(); ) {
			Placed p = it.next();
			if (p.endMs() <= now) { it.remove(); continue; }
			r.add(new String[]{p.name(), fmt((p.endMs() - now) / 1000f)});
		}
		for (Cd c : CDS.values()) if (c.left >= 0) r.add(new String[]{c.name, fmt(c.left)});
		return r;
	}
}
