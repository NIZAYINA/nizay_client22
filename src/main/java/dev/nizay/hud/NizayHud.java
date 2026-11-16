package dev.nizay.hud;

import dev.nizay.Config;
import dev.nizay.combat.TargetMarker;
import dev.nizay.combat.Timers;
import dev.nizay.combat.Trajectory;
import dev.nizay.friends.Friends;
import dev.nizay.gui.Ui;
import dev.nizay.util.Zoom;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * HUD: ватермарк (логотип | пинг | FPS), координаты, Cooldowns (эффекты и зелья с таймером), стрелки к друзьям из группы.
 * Пока открыт чат (T) и зажат Alt элементы можно таскать мышью, а за точку в правом нижнем углу менять размер.
 */
public final class NizayHud {
	private NizayHud() {}

	private static final int BG = 0xF0111115, LINE = 0xFF2A2A31, TXT = 0xFFF2F2F6, DIM = 0xFF8E8E99, WHITE = 0xFFEDEDF2;
	private static final Identifier ARROW = Identifier.fromNamespaceAndPath("nizay", "textures/gui/arrow.png");
	private static final Identifier LOGO = Identifier.fromNamespaceAndPath("nizay", "textures/gui/logo.png");

	public static void register() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
			Identifier.fromNamespaceAndPath("nizay", "hud"), NizayHud::render);
	}

	// ------------------------------------------------------------------ данные
	private static String pingText() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.getConnection() != null && mc.player != null) {
			PlayerInfo pi = mc.getConnection().getPlayerInfo(mc.player.getUUID());
			if (pi != null && pi.getLatency() > 0) return pi.getLatency() + " ms";
		}
		return "-- ms";
	}

	private record Row(String name, String time, int color) {}
	private static final String[] ROMAN = {"", "", " II", " III", " IV", " V", " VI", " VII", " VIII", " IX", " X"};

	private static List<Row> effects(boolean edit) {
		List<Row> rows = new ArrayList<>();
		LocalPlayer p = Minecraft.getInstance().player;
		if (p != null) for (MobEffectInstance e : p.getActiveEffects()) {
			String name = e.getEffect().value().getDisplayName().getString();
			int a = e.getAmplifier();
			if (a > 0) name += a < ROMAN.length ? ROMAN[a] : " " + (a + 1);
			String time;
			if (e.isInfiniteDuration()) time = "∞";
			else { int s = Math.max(0, e.getDuration() / 20); time = String.format("%d:%02d", s / 60, s % 60); }
			int col = e.getDuration() > 0 && e.getDuration() < 200 ? 0xFFFF9A9A : TXT;
			rows.add(new Row(name, time, col));
		}
		for (String[] t : Timers.rows()) rows.add(new Row(t[0], t[1], TXT));
		if (rows.isEmpty() && edit) rows.add(new Row("No effects", "0:00", DIM));
		return rows;
	}

	// ------------------------------------------------------------------ размеры (при scale = 1)
	private static float markW() {
		return 14 + 22 + Ui.width("Nizay", 10, true) + 20 + Ui.width(pingText(), 9.5f, false) + 20 + Ui.width(Minecraft.getInstance().getFps() + " FPS", 9.5f, false) + 6;
	}
	private static final float MARK_H = 28, COORDS_W = 156, COORDS_H = 50, CD_W = 170, AH_W = 200, AH_H = 36;

	// ------------------------------------------------------------------ рисование
	private static void drawMark(float w) {
		Ui.rectB(0, 0, w, MARK_H, 14, BG, LINE);
		Ui.image(LOGO, 128, 128, 5, 5, 18, 18);
		float x = 29;
		Ui.text("Nizay", x, 8.6f, 10, WHITE, true);
		x += Ui.width("Nizay", 10, true) + 9;
		Ui.text("/", x, 8.6f, 10, 0xFF4A4A55, false); x += 10;
		String ms = pingText(); Ui.text(ms, x, 9, 9.5f, DIM, false); x += Ui.width(ms, 9.5f, false) + 9;
		Ui.text("/", x, 8.6f, 10, 0xFF4A4A55, false); x += 10;
		Ui.text(Minecraft.getInstance().getFps() + " FPS", x, 9, 9.5f, DIM, false);
	}

	private static void drawCoords(float w) {
		LocalPlayer p = Minecraft.getInstance().player;
		Ui.rectB(0, 0, w, COORDS_H, 12, BG, LINE);
		Ui.rectB(9, 9, 14, 14, 5, 0xFF17171C, LINE);
		Ui.circle(16, 16, 2.2f, WHITE);
		Ui.text("Coordinates", 30, 10, 10, TXT, true);
		String xyz = p == null ? "-" : String.format("%d  %d  %d", (int) Math.floor(p.getX()), (int) Math.floor(p.getY()), (int) Math.floor(p.getZ()));
		Ui.text("XYZ", 12, 30, 9, DIM, false);
		Ui.text(xyz, 38, 29.5f, 9.5f, TXT, true);
		if (p != null) {
			String d = p.getDirection().getName(); d = Character.toUpperCase(d.charAt(0)) + d.substring(1);
			Ui.textR(d, w - 12, 30, 9, DIM, false);
		}
	}

	private static void drawAh(float w) {
		Ui.rectB(0, 0, w, AH_H, 12, BG, LINE);
		Ui.rectB(9, 9, 18, 18, 6, 0xFF17171C, LINE);
		Ui.textC("A", 18, 12.5f, 10, WHITE, true);
		Ui.text("AucHelper", 34, 7, 10, TXT, true);
		Ui.text(dev.nizay.combat.AucHelper.statusText(), 34, 21, 8.5f, 0xFFFFC078, false);
	}

	private static float cdH(List<Row> rows) { return 30 + Math.max(1, rows.size()) * 17 + 4; }

	private static void drawCd(float w, List<Row> rows) {
		float h = cdH(rows);
		Ui.rectB(0, 0, w, h, 12, BG, LINE);
		Ui.rectB(9, 9, 14, 14, 5, 0xFF17171C, LINE);
		Ui.circle(16, 16, 4.5f, 0xFF3A3A44); Ui.circle(16, 16, 2.2f, WHITE);
		Ui.text("Cooldowns", 30, 10, 10, TXT, true);
		float y = 31;
		for (Row r : rows) {
			Ui.text(Ui.fit(r.name(), w - 70, 9.5f, false), 12, y, 9.5f, r.color(), false);
			Ui.textR(r.time(), w - 12, y, 9.5f, r.color(), true);
			y += 17;
		}
	}

	private static boolean altDown() {
		long win = GLFW.glfwGetCurrentContext();
		return win != 0 && (GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS);
	}

	// ------------------------------------------------------------------ состояние редактора
	private static int dragEl = -1;
	private static boolean resizing, wasDown;
	private static float grabX, grabY, startScale, startW;

	private static void render(GuiGraphics g, net.minecraft.client.DeltaTracker dt) {
		Minecraft mc = Minecraft.getInstance();
		Zoom.frame(mc);
		if (mc.player == null || mc.options.hideGui) return;
		float pt = dt.getGameTimeDeltaPartialTick(false);
		Trajectory.render(g, pt);
		TargetMarker.render(g, pt);
		boolean edit = mc.screen instanceof ChatScreen && altDown();   // редактор HUD — только при зажатом Alt, чтобы не мешать обычному чату
		int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
		Config c = Config.I;
		Config.HudEl[] els = {c.hudMark, c.hudCoords, c.hudCd, c.hudAh};
		List<Row> rows = c.hudCd.on ? effects(edit) : new ArrayList<>();
		float[] ws = {markW(), COORDS_W, CD_W, AH_W}, hs = {MARK_H, COORDS_H, cdH(rows), AH_H};

		if (edit && c.master) handleMouse(mc, els, ws, hs, sw, sh); else { if (dragEl >= 0) Config.save(); dragEl = -1; wasDown = false; }

		for (int i = 0; i < 4; i++) {
			Config.HudEl e = els[i];
			if (!e.on) continue;
			if (i == 2 && rows.isEmpty()) continue;
			if (i == 3 && !edit && !dev.nizay.combat.AucHelper.plaque()) continue;   // пустой список вне режима редактирования не показываем
			Ui.begin(g, e.x * sw, e.y * sh, e.scale);
			try {
				if (i == 0) drawMark(ws[0]); else if (i == 1) drawCoords(ws[1]); else if (i == 2) drawCd(ws[2], rows); else drawAh(ws[3]);
				if (edit) {
					Ui.frame(-3, -3, ws[i] + 6, hs[i] + 6, dragEl == i ? 0xCCEDEDF2 : 0x66EDEDF2);
					Ui.circle(ws[i] + 3, hs[i] + 3, 4.6f, 0xFF3A3A44);
					Ui.circle(ws[i] + 3, hs[i] + 3, 3.2f, WHITE);
				}
			} finally { Ui.end(); }
		}
		if (edit) {
			Ui.begin(g, sw / 2f - 125, sh - 64, 1f);
			try {
				Ui.rectB(0, 0, 250, 18, 9, BG, LINE);
				Ui.textC("Alt + drag: move  |  corner dot: resize", 125, 4.5f, 8.5f, DIM, false);
			} finally { Ui.end(); }
		}
		if (c.master && c.timers.items) hotbarCooldowns(g, mc, sw, sh);
		if (c.friends.arrows && c.master && !edit) arrows(g, mc, sw, sh);
	}

	/** Секунды до повторного использования прямо поверх предмета в хотбаре. */
	private static void hotbarCooldowns(GuiGraphics g, Minecraft mc, int sw, int sh) {
		LocalPlayer p = mc.player;
		if (p == null) return;
		Ui.begin(g, 0, 0, 1f);
		try {
			for (int i = 0; i < 9; i++) {
				float left = dev.nizay.combat.Timers.leftFor(p.getInventory().getItem(i));
				if (left <= 0f) continue;
				float x = sw / 2f - 88 + i * 20, y = sh - 19;
				Ui.rect(x, y, 16, 16, 3, 0xB0000000);
				Ui.textC(dev.nizay.combat.Timers.shortFmt(left), x + 8, y + 4, 8, 0xFFFFFFFF, true);
			}
		} finally { Ui.end(); }
	}

	/** То же самое поверх слотов в инвентаре, сундуках и т.д. */
	public static void containerCooldowns(GuiGraphics g, net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> cs) {
		if (!Config.I.master || !Config.I.timers.items) return;
		var acc = (dev.nizay.mixin.ContainerScreenAccessor) cs;
		int lx = acc.nizay$left(), ty = acc.nizay$top();
		Ui.begin(g, 0, 0, 1f);
		try {
			for (var slot : cs.getMenu().slots) {
				float left = dev.nizay.combat.Timers.leftFor(slot.getItem());
				if (left <= 0f) continue;
				float x = lx + slot.x, y = ty + slot.y;
				Ui.rect(x, y, 16, 16, 3, 0xB0000000);
				Ui.textC(dev.nizay.combat.Timers.shortFmt(left), x + 8, y + 4, 8, 0xFFFFFFFF, true);
			}
		} finally { Ui.end(); }
	}

	private static void arrows(GuiGraphics g, Minecraft mc, int sw, int sh) {
		Friends.Snap s = Friends.snap;
		if (!s.ok || s.group.isEmpty() || mc.player == null || mc.level == null) return;
		String dim = mc.level.dimension().toString();
		String srv = mc.getCurrentServer() != null ? mc.getCurrentServer().ip : "singleplayer";
		LocalPlayer p = mc.player;
		float cx = sw / 2f, cy = sh / 2f, rad = Math.min(sw, sh) * .28f;
		Ui.begin(g, 0, 0, 1f);
		try {
			for (Friends.Member m : s.group) {
				if (!m.online() || m.nick().equalsIgnoreCase(s.myNick) || m.nick().equalsIgnoreCase(mc.getUser().getName())) continue;
				if (!dim.equals(m.dim()) || !srv.equals(m.srv())) continue;
				double dx = m.x() - p.getX(), dz = m.z() - p.getZ();
				double target = Math.toDegrees(Math.atan2(-dx, dz));
				double rel = Math.toRadians(Mth.wrapDegrees(target - p.getYRot()));
				float ax = cx + (float) Math.sin(rel) * rad, ay = cy - (float) Math.cos(rel) * rad;
				int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz + Math.pow(m.y() - p.getY(), 2)));
				Ui.tinted(ARROW, 64, ax, ay, 16, (float) rel, 0xFFEDEDF2);
				String label = m.nick() + "  " + dist + "m";
				Ui.textC(label, ax, ay + 11, 8.5f, 0xFFEDEDF2, true);
			}
		} finally { Ui.end(); }
	}

	private static void handleMouse(Minecraft mc, Config.HudEl[] els, float[] ws, float[] hs, int sw, int sh) {
		long win = GLFW.glfwGetCurrentContext();
		if (win == 0) return;
		boolean down = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
		float mx = (float) (mc.mouseHandler.xpos() * sw / mc.getWindow().getScreenWidth());
		float my = (float) (mc.mouseHandler.ypos() * sh / mc.getWindow().getScreenHeight());
		if (down && !wasDown) {
			for (int i = els.length - 1; i >= 0; i--) {
				Config.HudEl e = els[i];
				if (!e.on) continue;
				float x = e.x * sw, y = e.y * sh, w = ws[i] * e.scale, h = hs[i] * e.scale;
				if (Math.hypot(mx - (x + w), my - (y + h)) <= 7) { dragEl = i; resizing = true; startScale = e.scale; startW = w; grabX = mx; break; }
				if (mx >= x - 3 && mx <= x + w + 3 && my >= y - 3 && my <= y + h + 3) { dragEl = i; resizing = false; grabX = mx - x; grabY = my - y; break; }
			}
		} else if (down && dragEl >= 0) {
			Config.HudEl e = els[dragEl];
			if (resizing) {
				float nw = Math.max(24, startW + (mx - grabX));
				e.scale = Math.max(.5f, Math.min(2.5f, startScale * nw / startW));
			} else {
				float w = ws[dragEl] * e.scale, h = hs[dragEl] * e.scale;
				e.x = Math.max(0, Math.min(sw - w, mx - grabX)) / sw; e.y = Math.max(0, Math.min(sh - h, my - grabY)) / sh;
			}
		} else if (!down && wasDown && dragEl >= 0) {
			Config.save(); dragEl = -1;
		}
		wasDown = down;
	}
}
