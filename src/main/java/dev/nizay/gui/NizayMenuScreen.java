package dev.nizay.gui;

import dev.nizay.Config;
import dev.nizay.NizayClient;
import dev.nizay.accounts.Accounts;
import dev.nizay.fx.Fx;
import dev.nizay.fx.HitFx;
import dev.nizay.friends.Friends;
import dev.nizay.opt.Optimizer;
import dev.nizay.util.ConfigCodec;
import dev.nizay.wings.WingsFx;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Меню по Right Shift. Слева вкладки, справа прокручиваемое содержимое. Названия — английские, пояснения — русские. */
public class NizayMenuScreen extends Screen {
	static final int PANEL = 0xF4101013, SIDE = 0xFF0C0C0F, CARD = 0xFF17171B, CARD_HI = 0xFF1E1E23, LINE = 0xFF2A2A31;
	static final int TXT = 0xFFF2F2F6, DIM = 0xFF8E8E99, DIMMER = 0xFF63636E, WHITE = 0xFFEDEDF2, DARK = 0xFF0B0B0D, ON = 0xFFEDEDF2, OFF = 0xFF2A2A31;
	static final int W = 560, H = 400, SB = 152, CX = 176, CW = 362, VY0 = 76, VY1 = H - 14;

	static final String[] TABS = {"Visual", "Effects", "Wings", "HUD", "Function", "Combat", "Binds", "AucHelper", "Hands", "Friends", "Optimization", "Configs", "Accounts", "Settings"};
	static final String[] SUB = {"Небо и атмосфера", "30 эффектов, все нарисованы с нуля", "Анимированные крылья за спиной",
		"Плашки на экране. Двигайте их в чате (T)", "Зум, Fullbright и эффекты ударов", "Метка цели, свапы, траектории, таймеры", "Бинды предметов хотбара: трапка, пыль, зелья", "Автоперевыставление на аукционе и поиск по клавише", "Положение, размер и анимации рук", "Друзья, группа и стрелки к ним",
		"Прибавка FPS без изменений на сервере", "Сохранение и передача настроек кодом", "Синхронизация с лаунчером", "Параметры клиента"};

	record Sky(String id, String name, String kind) {}
	static final Sky[] SKIES = {
		new Sky("deep_space", "Deep Space", "Animated"), new Sky("nebula", "Nebula", "Animated"), new Sky("aurora", "Aurora", "Animated"),
		new Sky("eclipse", "Eclipse", "Animated"), new Sky("crystal_dusk", "Crystal Dusk", "Animated"), new Sky("sunset", "Sunset", "Tinted"),
		new Sky("dawn_haze", "Dawn Haze", "Tinted"), new Sky("midnight", "Midnight", "Plain"), new Sky("mono", "Mono", "Plain")
	};

	private static final class Hit { float x, y, w, h; boolean clip; Runnable click; Consumer<Float> drag; }
	private final List<Hit> hits = new ArrayList<>();
	private Hit dragging;
	private final Map<String, Float> anim = new HashMap<>();
	private long lastNs = System.nanoTime();
	private float dt, fit, ogx, ogy, mxD, myD, contentH;
	private boolean inContent, binding;
	private int bindId;
	private float tabScroll;
	private static float tabMax() { return Math.max(0, 82 + TABS.length * 26.5f - (H - 48) + 4); }
	private static final int[] ICON = {0, 1, 2, 3, 4, 9, 12, 7, 10, 5, 6, 7, 8, 11};
	private String notice = "";
	private int tab;

	public NizayMenuScreen() {
		super(Component.literal("Nizay client"));
		tab = Math.max(0, Math.min(TABS.length - 1, Config.I.uiTab));
		tabScroll = Math.max(0, Math.min(tabMax(), 82 + tab * 26.5f - (H - 48 - 30)));
	}

	@Override public boolean isPauseScreen() { return false; }

	/** Без размытия фона: оно заметно роняет FPS. */
	@Override
	public void renderBackground(GuiGraphics g, int mx, int my, float pt) { g.fill(0, 0, width, height, 0x88000000); }

	// ------------------------------------------------------------------ кадр
	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
		super.render(g, mouseX, mouseY, partial);
		long now = System.nanoTime(); dt = Math.min(.1f, (now - lastNs) / 1e9f); lastNs = now;
		fit = Math.min(1f, Math.min((width - 16f) / W, (height - 16f) / H));
		ogx = (width - W * fit) / 2f; ogy = (height - H * fit) / 2f;
		mxD = Ui.mx(mouseX, ogx, fit); myD = Ui.mx(mouseY, ogy, fit);
		hits.clear();
		Ui.begin(g, ogx, ogy, fit);
		try { draw(); } finally { Ui.end(); }
	}

	private float scroll() { return Config.I.uiScroll[tab]; }

	private void draw() {
		Config c = Config.I;
		Ui.shadow(0, 0, W, H, 16, 0x000000, 150, 22);
		Ui.rectB(0, 0, W, H, 16, PANEL, LINE);
		// боковая колонка
		Ui.rect(6, 6, SB - 6, H - 12, 12, SIDE);
		Ui.image(Identifier.fromNamespaceAndPath("nizay", "textures/gui/logo.png"), 128, 128, 22, 20, 32, 32);
		Ui.text("Nizay", 64, 25, 13, WHITE, true);
		Ui.text("client", 64 + Ui.width("Nizay ", 13, true), 25, 13, DIM, false);
		Ui.text("MODULES", 22, 66, 8, DIMMER, true);
		Ui.clipOn(8, 78, SB - 10, H - 48 - 78);
		for (int i = 0; i < TABS.length; i++) {
			float ty = 82 + i * 26.5f - tabScroll;
			if (ty + 24 < 78 || ty > H - 48) continue;
			boolean sel = i == tab, hov = mxD < SB && inside(12, ty, SB - 18, 24) && myD > 78 && myD < H - 48;
			float a = anim("tab" + i, sel ? 1 : hov ? .45f : 0);
			int bg = Ui.lerp(SIDE, 0xFF1C1C21, a);
			if (a > .01f) Ui.rect(12, ty, SB - 18, 24, 8, bg);
			if (sel) Ui.rect(12, ty + 6, 2.2f, 12, 1.1f, WHITE);
			icon(ICON[i], 29, ty + 12, sel ? WHITE : (hov ? 0xFFC8C8D0 : DIM), bg);
			Ui.text(TABS[i], 46, ty + 6.5f, 10, sel ? WHITE : (hov ? 0xFFD0D0D8 : DIM), sel);
			final int ti = i; hit(12, Math.max(ty, 78), SB - 18, Math.min(24, Math.min(ty + 24 - 78, H - 48 - ty)), () -> { tab = ti; c.uiTab = ti; notice = ""; binding = false; }, null);
		}
		Ui.clipOff();
		float maxTab = tabMax();
		if (maxTab > 0) {
			float vh = H - 48 - 78, th = Math.max(20, vh * vh / (vh + maxTab)), tyy = 78 + (vh - th) * (tabScroll / maxTab);
			Ui.rect(SB - 8, tyy, 2.4f, th, 1.2f, 0x55EDEDF2);
		}
		Ui.rectB(18, H - 40, SB - 30, 26, 9, 0xFF101014, LINE);
		Ui.rectB(24, H - 35, 54, 16, 6, 0xFF1B1B20, LINE);
		Ui.textC("R Shift", 51, H - 32, 8.5f, TXT, true);
		Ui.text("toggle", 88, H - 32, 8.5f, DIM, false);

		// шапка
		Ui.text(TABS[tab], CX, 20, 20, WHITE, true);
		Ui.text(SUB[tab], CX, 46, 9.5f, DIM, false);
		// главный выключатель
		Ui.textR("Enable all", W - 70, 22, 9.5f, c.master ? TXT : DIM, true);
		Ui.textR("Все функции сразу", W - 70, 34, 8, DIMMER, false);
		toggle("master", W - 58, 22, c.master, () -> { c.setMaster(!c.master); Optimizer.apply(); Config.save(); });

		// содержимое с прокруткой
		Ui.clipOn(CX - 8, VY0 - 4, W - CX - 4, VY1 - VY0 + 4);
		inContent = true;
		float y = VY0 - scroll();
		float end;
		switch (tab) {
			case 0 -> end = visual(CX, y, CW);
			case 1 -> end = effects(CX, y, CW);
			case 2 -> end = wings(CX, y, CW);
			case 3 -> end = hud(CX, y, CW);
			case 4 -> end = function(CX, y, CW);
			case 5 -> end = combat(CX, y, CW);
			case 6 -> end = binds(CX, y, CW);
			case 7 -> end = aucHelper(CX, y, CW);
			case 8 -> end = hands(CX, y, CW);
			case 9 -> end = friends(CX, y, CW);
			case 10 -> end = optimization(CX, y, CW);
			case 11 -> end = configs(CX, y, CW);
			case 12 -> end = accounts(CX, y, CW);
			default -> end = settings(CX, y, CW);
		}
		inContent = false;
		contentH = end - y;
		Ui.clipOff();
		float maxScroll = Math.max(0, contentH - (VY1 - VY0) + 6);
		if (scroll() > maxScroll) c.uiScroll[tab] = maxScroll;
		if (maxScroll > 0) {   // полоса прокрутки
			float th = Math.max(24, (VY1 - VY0) * (VY1 - VY0) / contentH), ty = VY0 + (VY1 - VY0 - th) * (scroll() / maxScroll);
			Ui.rect(W - 9, ty, 3, th, 1.5f, 0x55EDEDF2);
		}
		if (!c.master) {
			Ui.rect(CX - 8, VY0 - 4, W - CX - 4, VY1 - VY0 + 4, 10, 0xC00C0C0F);
			Ui.textC("All functions are off", CX + CW / 2f, 170, 13, WHITE, true);
			Ui.textC("Включите главный переключатель справа сверху", CX + CW / 2f, 190, 9, DIM, false);
		}
	}

	private void icon(int i, float cx, float cy, int c, int bg) {
		switch (i) {
			case 0 -> { Ui.rect(cx - 6, cy - 3.5f, 12, 7, 3.5f, c); Ui.circle(cx, cy, 1.8f, bg); }
			case 1 -> { Ui.rect(cx - 1, cy - 6, 2, 12, 1, c); Ui.rect(cx - 6, cy - 1, 12, 2, 1, c); Ui.circle(cx, cy, 1.5f, c); }
			case 2 -> { Ui.rect(cx - 5, cy - 5, 2, 11, 1, c); Ui.rect(cx - 1, cy - 6, 2, 11, 1, c); Ui.rect(cx + 3, cy - 5, 2, 11, 1, c); }
			case 3 -> { Ui.rect(cx - 6, cy - 4.5f, 12, 9, 2.5f, c); Ui.rect(cx - 5, cy - 3.5f, 10, 7, 1.8f, bg); Ui.rect(cx - 3.5f, cy - 1.5f, 7, 1.5f, .7f, c); }
			case 4 -> { Ui.rect(cx - 6, cy - 6, 5, 5, 1.5f, c); Ui.rect(cx + 1, cy - 6, 5, 5, 1.5f, c); Ui.rect(cx - 6, cy + 1, 5, 5, 1.5f, c); Ui.rect(cx + 1, cy + 1, 5, 5, 1.5f, c); }
			case 5 -> { Ui.circle(cx - 3, cy - 2.5f, 2.6f, c); Ui.circle(cx + 3.5f, cy - 2, 2.2f, c); Ui.rect(cx - 7, cy + 1.5f, 8, 4.5f, 2.2f, c); Ui.rect(cx + 1, cy + 2, 6.5f, 4, 2, c); }
			case 6 -> { Ui.rect(cx - 6, cy + 1, 3, 5, 1, c); Ui.rect(cx - 1.5f, cy - 3, 3, 9, 1, c); Ui.rect(cx + 3, cy - 6, 3, 12, 1, c); }
			case 7 -> { Ui.rect(cx - 5, cy - 6, 10, 12, 2.5f, c); Ui.rect(cx - 3, cy - 3.5f, 6, 1.4f, .7f, bg); Ui.rect(cx - 3, cy, 6, 1.4f, .7f, bg); Ui.rect(cx - 3, cy + 3, 4, 1.4f, .7f, bg); }
			case 8 -> { Ui.circle(cx, cy - 2.5f, 3, c); Ui.rect(cx - 6, cy + 1.5f, 12, 5, 2.5f, c); }
			case 9 -> { Ui.rect(cx - 6, cy - 6, 5, 2, 1, c); Ui.rect(cx - 6, cy - 6, 2, 5, 1, c); Ui.rect(cx + 1, cy - 6, 5, 2, 1, c); Ui.rect(cx + 4, cy - 6, 2, 5, 1, c); Ui.rect(cx - 6, cy + 4, 5, 2, 1, c); Ui.rect(cx - 6, cy + 1, 2, 5, 1, c); Ui.rect(cx + 1, cy + 4, 5, 2, 1, c); Ui.rect(cx + 4, cy + 1, 2, 5, 1, c); Ui.circle(cx, cy, 1.6f, c); }
			case 12 -> { Ui.rect(cx - 6, cy - 4.5f, 12, 9, 2.5f, c); Ui.rect(cx - 4, cy - 2.5f, 3, 3, 1, bg); Ui.rect(cx + 1, cy - 2.5f, 3, 3, 1, bg); Ui.rect(cx - 3, cy + 1.5f, 6, 1.6f, .8f, bg); }
			case 10 -> { Ui.rect(cx - 5, cy - 2, 10, 8, 3.5f, c); Ui.rect(cx - 4, cy - 6, 2.4f, 5, 1.2f, c); Ui.rect(cx - 1.2f, cy - 7, 2.4f, 6, 1.2f, c); Ui.rect(cx + 1.6f, cy - 6, 2.4f, 5, 1.2f, c); }
			default -> { Ui.circle(cx, cy, 6, c); Ui.circle(cx, cy, 4.2f, bg); Ui.circle(cx, cy, 2, c); }
		}
	}

	// ------------------------------------------------------------------ вкладки
	private float visual(float x, float y, float w) {
		Config c = Config.I;
		row(x, y, w, "Sky", "Заменяет ванильное небо полностью", "sky", c.skyOn, () -> c.skyOn = !c.skyOn);
		y += 38;
		float cw = (w - 16) / 3f, ih = 44;
		for (int i = 0; i < SKIES.length; i++) {
			Sky s = SKIES[i];
			float cx = x + (i % 3) * (cw + 8), cy = y + (i / 3) * (ih + 26);
			boolean sel = s.id().equals(c.sky), hov = inside(cx, cy, cw, ih + 20);
			float a = anim("sky" + i, sel ? 1 : hov ? .5f : 0);
			if (a > .02f) Ui.rect(cx - 2, cy - 2, cw + 4, ih + 4, 9, Ui.lerp(0x00FFFFFF, 0xFFEDEDF2, a));
			Ui.image(Identifier.fromNamespaceAndPath("nizay", "textures/gui/sky_" + s.id() + ".png"), 256, 128, cx, cy, cw, ih);
			if (sel) { Ui.circle(cx + cw - 10, cy + 10, 6, 0xFFF2F2F6); Ui.rect(cx + cw - 12.5f, cy + 9.5f, 2.2f, 2.2f, .5f, DARK); Ui.rect(cx + cw - 10.3f, cy + 6.8f, 1.8f, 5.6f, .5f, DARK); }
			Ui.text(s.name(), cx + 2, cy + ih + 5, 9.5f, sel ? WHITE : TXT, sel);
			Ui.textR(s.kind(), cx + cw - 1, cy + ih + 6, 8, DIM, false);
			hit(cx, cy, cw, ih + 20, () -> c.sky = s.id(), null);
		}
		y += 3 * (ih + 26) + 4;
		y = slider("spd", x, y, w, "Animation speed", "Скорость анимации неба", String.format("%.1fx", c.animSpeed), (c.animSpeed - .2f) / 2.8f, v -> c.animSpeed = .2f + v * 2.8f);
		y = slider("brt", x, y, w, "Brightness", "Яркость неба", Math.round(c.brightness * 100) + "%", c.brightness, v -> c.brightness = Math.max(.1f, v));
		return y;
	}

	private float effects(float x, float y, float w) {
		Config c = Config.I;
		int per = 15, pages = Fx.IDS.length / per;
		float tw = (w - 32) / 5f, th = 56;
		for (int k = 0; k < per; k++) {
			int idx = c.uiFxPage * per + k; String id = Fx.IDS[idx];
			Config.Fx f = c.fx(id);
			float tx = x + (k % 5) * (tw + 8), ty = y + (k / 5) * (th + 8);
			boolean sel = idx == c.uiFxSel, hov = inside(tx, ty, tw, th);
			float a = anim("fx" + idx, sel ? 1 : hov ? .4f : 0);
			Ui.rectB(tx, ty, tw, th, 9, hov ? CARD_HI : CARD, Ui.lerp(f.on ? 0xFF3C3C45 : LINE, 0xFFEDEDF2, a));
			Ui.sprite(Identifier.fromNamespaceAndPath("nizay", "textures/particle/" + id + ".png"), 256, tx + tw / 2 - 13, ty + 8, 26);
			Ui.textC(Ui.fit(Fx.NAMES[idx], tw - 6, 8, false), tx + tw / 2, ty + th - 14, 8, f.on ? TXT : DIM, false);
			final int fi = idx; hit(tx, ty, tw, th, () -> c.uiFxSel = fi, null);
			mini(tx + tw - 26, ty + 5, f.on, "m" + idx);
			hit(tx + tw - 34, ty, 34, 22, () -> f.on = !f.on, null);
		}
		float by = y + 3 * (th + 8) + 2;
		Ui.rectB(x, by, w, 84, 11, CARD, LINE);
		Config.Fx f = c.fx(Fx.IDS[c.uiFxSel]);
		Ui.text(Fx.NAMES[c.uiFxSel], x + 14, by + 11, 11.5f, TXT, true);
		Ui.text(Ui.fit(Fx.DESCS_RU[c.uiFxSel], 190, 8.5f, false), x + 14, by + 27, 8.5f, DIMMER, false);
		toggle("fxsel", x + 14, by + 46, f.on, () -> f.on = !f.on);
		Ui.text(f.on ? "On" : "Off", x + 54, by + 50, 9.5f, DIM, false);
		float sx = x + 214, sw = 120;
		Ui.text("Size", sx, by + 10, 9, DIM, false); Ui.textR(f.size + "%", sx + sw, by + 10, 9, TXT, true);
		slider0("fsz", sx, by + 28, sw, f.size / 100f, v -> f.size = Math.round(v * 100));
		Ui.text("Density", sx, by + 40, 9, DIM, false); Ui.textR(f.density + "%", sx + sw, by + 40, 9, TXT, true);
		slider0("fdn", sx, by + 58, sw, f.density / 100f, v -> f.density = Math.round(v * 100));
		for (int i = 0; i < Fx.PALETTE_RGB.length; i++) {
			final int ci = i; swatch(x + 18 + i * 20, by + 74, 6, 0xFF000000 | Fx.PALETTE_RGB[i], f.color == i, () -> f.color = ci);
		}
		float ny = by + 92;
		arrow(x + w - 84, ny, "<", () -> c.uiFxPage = (c.uiFxPage + pages - 1) % pages);
		Ui.textC((c.uiFxPage + 1) + "/" + pages, x + w - 50, ny + 5, 9, DIM, false);
		arrow(x + w - 36, ny, ">", () -> c.uiFxPage = (c.uiFxPage + 1) % pages);
		return ny + 26;
	}

	private float wings(float x, float y, float w) {
		Config.Wings c = Config.I.wings;
		row(x, y, w, "Wings", "Aether Wings: лёгкие крылья, покачиваются вверх-вниз", "wg", c.on, () -> c.on = !c.on);
		y += 40;
		Ui.text("Color", x, y, 9.5f, DIM, false); Ui.text("Цвет перьев", x + 34, y + 1, 8.5f, DIMMER, false);
		for (int i = 0; i < WingsFx.COLORS_RGB.length; i++) { final int ci = i; swatch(x + 9 + i * 26, y + 26, 8.5f, 0xFF000000 | WingsFx.COLORS_RGB[i], c.color == i, () -> c.color = ci); }
		y += 46;
		y = slider("wsz", x, y, w, "Size", "Размер крыльев", c.size + "%", c.size / 100f, v -> c.size = Math.round(v * 100));
		y = slider("wfl", x, y, w, "Flap speed", "Скорость покачивания", String.format("%.1fx", c.flap), (c.flap - .3f) / 2.2f, v -> c.flap = .3f + v * 2.2f);
		y = slider("wgl", x, y, w, "Glow", "Свечение вокруг крыльев", c.glow + "%", c.glow / 100f, v -> c.glow = Math.round(v * 100));
		row(x, y, w, "Show in first person", "Показывать крылья от первого лица", "wfp", c.firstPerson, () -> c.firstPerson = !c.firstPerson);
		return y + 36;
	}

	private float hud(float x, float y, float w) {
		Config c = Config.I;
		row(x, y, w, "Watermark", "Логотип, пинг и FPS в плашке", "hw", c.hudMark.on, () -> c.hudMark.on = !c.hudMark.on); y += 36;
		y = slider("hws", x, y, w, "Scale", "Размер плашки", Math.round(c.hudMark.scale * 100) + "%", (c.hudMark.scale - .6f) / 1.4f, v -> c.hudMark.scale = .6f + v * 1.4f);
		row(x, y, w, "Coordinates", "Координаты XYZ и направление взгляда", "hc", c.hudCoords.on, () -> c.hudCoords.on = !c.hudCoords.on); y += 36;
		y = slider("hcs", x, y, w, "Scale", "Размер плашки", Math.round(c.hudCoords.scale * 100) + "%", (c.hudCoords.scale - .6f) / 1.4f, v -> c.hudCoords.scale = .6f + v * 1.4f);
		row(x, y, w, "Cooldowns", "Эффекты и зелья на вас и время до конца", "hcd", c.hudCd.on, () -> c.hudCd.on = !c.hudCd.on); y += 36;
		y = slider("hds", x, y, w, "Scale", "Размер плашки", Math.round(c.hudCd.scale * 100) + "%", (c.hudCd.scale - .6f) / 1.4f, v -> c.hudCd.scale = .6f + v * 1.4f);
		button(x, y + 2, 130, 22, "Reset positions", false, () -> {
			c.hudMark.x = .012f; c.hudMark.y = .018f; c.hudMark.scale = 1f;
			c.hudCoords.x = .012f; c.hudCoords.y = .085f; c.hudCoords.scale = 1f;
			c.hudCd.x = .012f; c.hudCd.y = .2f; c.hudCd.scale = 1f;
		});
		Ui.text("Откройте чат (T), чтобы перетаскивать плашки и менять размер.", x, y + 34, 8.5f, DIMMER, false);
		return y + 50;
	}

	private float function(float x, float y, float w) {
		Config c = Config.I;
		// --- Zoom
		Ui.rectB(x, y, w, 196, 11, CARD, LINE);
		row(x + 14, y + 12, w - 28, "Zoom", "Приближение по нажатию выбранной клавиши", "zm", c.zoom.on, () -> c.zoom.on = !c.zoom.on);
		Ui.text("Key", x + 14, y + 54, 9.5f, DIM, false);
		button(x + 50, y + 49, 110, 22, binding && bindId == 1 ? "Press a key..." : keyName(c.zoom.key), binding && bindId == 1, () -> { binding = !(binding && bindId == 1); bindId = 1; });
		Ui.text("Бинд клавиши зума", x + 170, y + 55, 8.5f, DIMMER, false);
		slider("zf", x + 14, y + 80, w - 28, "Zoom factor", "Во сколько раз приближать (FOV ≥ 30)", c.zoom.factor + "x", (c.zoom.factor - 2) / 6f, v -> c.zoom.factor = 2 + Math.round(v * 6));
		row(x + 14, y + 118, w - 28, "Hold to zoom", "Зум, пока клавиша зажата (иначе вкл/выкл)", "zh", c.zoom.hold, () -> c.zoom.hold = !c.zoom.hold);
		row(x + 14, y + 148, w - 28, "Smooth zoom", "Плавное приближение", "zs", c.zoom.smooth, () -> c.zoom.smooth = !c.zoom.smooth);
		y += 206;
		// --- Fullbright
		Ui.rectB(x, y, w, 98, 11, CARD, LINE);
		row(x + 14, y + 12, w - 28, "Fullbright", "Убирает темноту: яркость до 1500%, видно в любой пещере", "fb", c.bright.on, () -> c.bright.on = !c.bright.on);
		slider("fbl", x + 14, y + 50, w - 28, "Brightness level", "Уровень яркости", c.bright.level + "%", (c.bright.level - 100) / 1400f, v -> c.bright.level = 100 + Math.round(v * 1400));
		y += 108;
		// --- Hit Effects
		Ui.rectB(x, y, w, 196, 11, CARD, LINE);
		row(x + 14, y + 12, w - 28, "Hit Effects", "Эффекты при ударе по игроку или мобу", "ht", c.hit.on, () -> c.hit.on = !c.hit.on);
		float cw = (w - 28 - 16) / 5f;
		for (int i = 0; i < 5; i++) {
			final int si = i; float bx = x + 14 + i * (cw + 4);
			boolean sel = c.hit.style == i, hov = inside(bx, y + 50, cw, 26);
			Ui.rectB(bx, y + 50, cw, 26, 8, sel ? 0xFFEDEDF2 : (hov ? CARD_HI : 0xFF1B1B20), sel ? 0xFFEDEDF2 : LINE);
			Ui.textC(Ui.fit(HitFx.NAMES[i], cw - 4, 7.5f, true), bx + cw / 2, y + 59, 7.5f, sel ? DARK : TXT, true);
			hit(bx, y + 50, cw, 26, () -> c.hit.style = si, null);
		}
		Ui.text(HitFx.DESCS[c.hit.style], x + 14, y + 82, 8.5f, DIMMER, false);
		slider("hsz", x + 14, y + 96, w - 28, "Size", "Размер частиц", c.hit.size + "%", c.hit.size / 100f, v -> c.hit.size = Math.round(v * 100));
		slider("hdn", x + 14, y + 130, w - 28, "Density", "Количество частиц", c.hit.density + "%", c.hit.density / 100f, v -> c.hit.density = Math.round(v * 100));
		for (int i = 0; i < Fx.PALETTE_RGB.length; i++) { final int ci = i; swatch(x + 22 + i * 22, y + 178, 6.5f, 0xFF000000 | Fx.PALETTE_RGB[i], c.hit.color == i, () -> c.hit.color = ci); }
		y += 206;
		Ui.text("Метка цели, ItemSwap, траектории и таймеры — во вкладке Combat; руки — во вкладке Hands.", x, y, 8.5f, DIMMER, false);
		return y + 24;
	}


	// ------------------------------------------------------------------ Combat / Hands
	private float sec(String t, float x, float y, float w) {
		Ui.text(t, x, y, 8, DIMMER, true);
		Ui.rect(x + Ui.width(t, 8, true) + 8, y + 5, w - Ui.width(t, 8, true) - 8, 1, 0, LINE);
		return y + 18;
	}

	private float keyRow(float x, float y, int id, int key) {
		boolean on = binding && bindId == id;
		Ui.text("Key", x, y + 5, 9.5f, DIM, false);
		button(x + 50, y, 110, 22, on ? "Press a key..." : keyName(key), on, () -> { binding = !(binding && bindId == id); bindId = id; });
		Ui.text("Backspace — убрать клавишу", x + 170, y + 6, 8.5f, DIMMER, false);
		return y + 32;
	}

	private float swatches(float x, float y, int sel, Consumer<Integer> set) {
		for (int i = 0; i < Fx.PALETTE_RGB.length; i++) { final int ci = i; swatch(x + 8 + i * 22, y + 6, 6.5f, 0xFF000000 | Fx.PALETTE_RGB[i], sel == i, () -> set.accept(ci)); }
		return y + 22;
	}

	private float combat(float x, float y, float w) {
		Config c = Config.I;
		y = sec("TARGET MARKER", x, y, w);
		row(x, y, w, "Target marker", "Вращающиеся уголки на игроке под прицелом", "tm", c.target.on, () -> c.target.on = !c.target.on); y += 36;
		row(x, y, w, "Weapon only", "Показывать, только когда в руке оружие", "tmw", c.target.weaponOnly, () -> c.target.weaponOnly = !c.target.weaponOnly); y += 36;
		y = slider("tmz", x, y, w, "Size", "Размер метки", c.target.size + "%", (c.target.size - 50) / 100f, v -> c.target.size = 50 + Math.round(v * 100));
		y = slider("tms", x, y, w, "Spin", "Скорость вращения", c.target.spin + "%", c.target.spin / 300f, v -> c.target.spin = Math.round(v * 300));
		y = swatches(x, y, c.target.color, v -> c.target.color = v) + 8;

		y = sec("ITEMSWAP", x, y, w);
		row(x, y, w, "ItemSwap", "Левая рука: тотем ⇄ сфера, из хотбара и инвентаря. Берёт лучший тотем", "as", c.autoSwap.on, () -> c.autoSwap.on = !c.autoSwap.on); y += 36;
		y = keyRow(x, y, 2, c.autoSwap.key) + 4;

		y = sec("ELYTRASWAP", x, y, w);
		row(x, y, w, "ElytraSwap", "Нагрудник ⇄ элитры одной клавишей", "es", c.elytraSwap.on, () -> c.elytraSwap.on = !c.elytraSwap.on); y += 36;
		y = keyRow(x, y, 3, c.elytraSwap.key) + 4;

		y = sec("TRAJECTORY", x, y, w);
		row(x, y, w, "Trajectory", "Показывает, куда полетит предмет из руки (виден сквозь блоки)", "tj", c.traj.on, () -> c.traj.on = !c.traj.on); y += 36;
		row(x, y, w, "Ender pearl", "Эндер-жемчуг, снежок, яйцо", "tjp", c.traj.pearl, () -> c.traj.pearl = !c.traj.pearl); y += 36;
		row(x, y, w, "Bow", "Лук (учитывает натяжение; тройной выстрел — три линии)", "tjb", c.traj.bow, () -> c.traj.bow = !c.traj.bow); y += 36;
		row(x, y, w, "Crossbow", "Арбалет, в том числе с Multishot", "tjc", c.traj.crossbow, () -> c.traj.crossbow = !c.traj.crossbow); y += 36;
		row(x, y, w, "Trident", "Трезубец", "tjt", c.traj.trident, () -> c.traj.trident = !c.traj.trident); y += 36;
		y = swatches(x, y, c.traj.color, v -> c.traj.color = v) + 8;

		y = sec("TIMERS", x, y, w);
		row(x, y, w, "Item cooldowns", "Кулдауны жемчуга, хоруса, трапки и т.д. в плашке Cooldowns (HUD)", "tmi", c.timers.items, () -> c.timers.items = !c.timers.items); y += 36;
		row(x, y, w, "Placed timers", "Обратный отсчёт после установки Пласта / Трапки", "tmp", c.timers.placed, () -> c.timers.placed = !c.timers.placed); y += 36;
		y = slider("tpl", x, y, w, "Plast time", "Сколько секунд держится Пласт", c.timers.plastSec + " s", (c.timers.plastSec - 5) / 115f, v -> c.timers.plastSec = 5 + Math.round(v * 115));
		y = slider("ttr", x, y, w, "Trap time", "Сколько секунд держится Трапка", c.timers.trapSec + " s", (c.timers.trapSec - 5) / 115f, v -> c.timers.trapSec = 5 + Math.round(v * 115));
		Ui.text("Плашка Cooldowns должна быть включена во вкладке HUD.", x, y, 8.5f, DIMMER, false);
		return y + 22;
	}

	private float aucHelper(float x, float y, float w) {
		Config c = Config.I;
		y = sec("RELIST", x, y, w);
		row(x, y, w, "AucHelper", "Раз в минуту тихо заходит на /ah, жмёт «Хранилище» и перевыставляет предметы", "ahr", c.auc.relistOn, () -> c.auc.relistOn = !c.auc.relistOn); y += 40;
		y = slider("ahi", x, y, w, "Cooldown", "Пауза между перевыставлениями", c.auc.interval + " s", (c.auc.interval - 30) / 90f, v -> c.auc.interval = 30 + Math.round(v * 90));
		y = slider("ahd", x, y, w, "Click delay", "Задержка между кликами по меню", c.auc.delayMs + " ms", (c.auc.delayMs - 100) / 700f, v -> c.auc.delayMs = 100 + Math.round(v * 700));
		y = slider("ahc0", x, y, w, "Storage button", "Колонка кнопки «Хранилище» в нижнем ряду /ah", String.valueOf(c.auc.storageCol + 1), c.auc.storageCol / 8f, v -> c.auc.storageCol = Math.round(v * 8));
		y = slider("ahc1", x, y, w, "Red button", "Колонка красной кнопки в «Хранилище»", String.valueOf(c.auc.redCol + 1), c.auc.redCol / 8f, v -> c.auc.redCol = Math.round(v * 8));
		y = slider("ahc2", x, y, w, "Circle button", "Колонка кружка с красной точкой", String.valueOf(c.auc.circleCol + 1), c.auc.circleCol / 8f, v -> c.auc.circleCol = Math.round(v * 8));
		Ui.text("За 10 секунд до цикла на экране появляется плашка (двигается в чате).", x, y, 8.5f, DIMMER, false); y += 26;
		y = sec("SEARCH", x, y, w);
		row(x, y, w, "AucSearch", "Клавиша мгновенно и незаметно вводит команду /ah search", "ahs", c.auc.searchOn, () -> c.auc.searchOn = !c.auc.searchOn); y += 40;
		y = keyRow(x, y, 4, c.auc.searchKey) + 4;
		Ui.text("Перевыставление и поиск включаются независимо друг от друга.", x, y, 8.5f, DIMMER, false);
		return y + 24;
	}

	private float binds(float x, float y, float w) {
		Config c = Config.I;
		row(x, y, w, "Item binds", "Клавиша берёт предмет из хотбара, использует его и возвращает слот", "ib", c.binds.on, () -> c.binds.on = !c.binds.on); y += 40;
		Ui.text("Предмет ищется в хотбаре по названию, как на FunTime. Backspace — убрать клавишу.", x, y, 8.5f, DIMMER, false); y += 20;
		for (int i = 0; i < dev.nizay.combat.ItemBinds.DEFS.length; i++) {
			var d = dev.nizay.combat.ItemBinds.DEFS[i];
			Config.BindItem b = c.bind(d.id());
			int slot = minecraft.player != null ? dev.nizay.combat.ItemBinds.find(minecraft.player, d) : -1;
			boolean hov = inside(x, y, w, 32);
			Ui.rectB(x, y, w, 32, 9, hov ? CARD_HI : CARD, slot >= 0 ? 0xFF3C5A44 : LINE);
			Ui.item(slot >= 0 ? minecraft.player.getInventory().getItem(slot) : dev.nizay.combat.ItemBinds.icon(d), x + 10, y + 7, 18);
			Ui.text(d.en(), x + 38, y + 5, 10, b.on ? TXT : DIM, true);
			Ui.text(d.ru() + (slot >= 0 ? "  ·  в хотбаре" : ""), x + 38, y + 18, 8, slot >= 0 ? 0xFF8FCB9B : DIMMER, false);
			final int id = 100 + i;
			boolean on = binding && bindId == id;
			button(x + w - 134, y + 5, 86, 22, on ? "Press a key..." : (b.key == 0 ? "None" : keyName(b.key)), on, () -> { binding = !(binding && bindId == id); bindId = id; });
			mini(x + w - 36, y + 10, b.on, "bi" + i);
			hit(x + w - 42, y + 3, 36, 26, () -> b.on = !b.on, null);
			y += 36;
		}
		return y + 6;
	}

	private static final String[] SWINGS = {"Vanilla", "Side punch", "Jab", "Slash", "Chop", "Spin", "None"};
	private static final String[] SHADERS = {"Off", "Glow", "Pulse"};

	private float hands(float x, float y, float w) {
		Config.Hands h = Config.I.hands;
		row(x, y, w, "Custom hands", "Свои руки: положение, размер, поворот, анимации удара", "hn", h.on, () -> h.on = !h.on); y += 40;
		y = sec("POSITION", x, y, w);
		y = slider("hx", x, y, w, "X", "Влево / вправо", String.valueOf(h.x), (h.x + 100) / 200f, v -> h.x = Math.round(v * 200) - 100);
		y = slider("hy", x, y, w, "Y", "Вниз / вверх", String.valueOf(h.y), (h.y + 100) / 200f, v -> h.y = Math.round(v * 200) - 100);
		y = slider("hz", x, y, w, "Z", "Ближе / дальше", String.valueOf(h.z), (h.z + 100) / 200f, v -> h.z = Math.round(v * 200) - 100);
		y = slider("hsc", x, y, w, "Size", "Размер предмета в руке", h.scale + "%", (h.scale - 30) / 170f, v -> h.scale = 30 + Math.round(v * 170));
		y = sec("ROTATION", x, y + 2, w);
		y = slider("hrx", x, y, w, "Rot X", "Наклон вперёд / назад", h.rx + "°", (h.rx + 180) / 360f, v -> h.rx = Math.round(v * 360) - 180);
		y = slider("hry", x, y, w, "Rot Y", "Поворот влево / вправо", h.ry + "°", (h.ry + 180) / 360f, v -> h.ry = Math.round(v * 360) - 180);
		y = slider("hrz", x, y, w, "Rot Z", "Крен", h.rz + "°", (h.rz + 180) / 360f, v -> h.rz = Math.round(v * 360) - 180);
		y = sec("SWING", x, y + 2, w);
		Ui.text("Анимация удара (заменяет ванильный взмах)", x, y - 2, 8.5f, DIMMER, false); y += 14;
		float bw = (w - 18) / 4f;
		for (int i = 0; i < SWINGS.length; i++) {
			final int si = i; float bx = x + (i % 4) * (bw + 6), by = y + (i / 4) * 30;
			boolean sel = h.swing == i, hov = inside(bx, by, bw, 24);
			Ui.rectB(bx, by, bw, 24, 8, sel ? 0xFFEDEDF2 : (hov ? CARD_HI : 0xFF1B1B20), sel ? 0xFFEDEDF2 : LINE);
			Ui.textC(Ui.fit(SWINGS[i], bw - 4, 8, true), bx + bw / 2, by + 8, 8, sel ? DARK : TXT, true);
			hit(bx, by, bw, 24, () -> h.swing = si, null);
		}
		y += 66;
		y = sec("SHADER", x, y, w);
		Ui.text("Подсветка рук. Полноценные GLSL-шейдеры на руки пока недоступны", x, y - 2, 8.5f, DIMMER, false); y += 14;
		float sw = (w - 12) / 3f;
		for (int i = 0; i < SHADERS.length; i++) {
			final int si = i; float bx = x + i * (sw + 6);
			boolean sel = h.shader == i, hov = inside(bx, y, sw, 24);
			Ui.rectB(bx, y, sw, 24, 8, sel ? 0xFFEDEDF2 : (hov ? CARD_HI : 0xFF1B1B20), sel ? 0xFFEDEDF2 : LINE);
			Ui.textC(SHADERS[i], bx + sw / 2, y + 8, 8.5f, sel ? DARK : TXT, true);
			hit(bx, y, sw, 24, () -> h.shader = si, null);
		}
		y += 38;
		button(x, y, 120, 22, "Reset hands", false, () -> { h.x = h.y = h.z = h.rx = h.ry = h.rz = 0; h.scale = 100; h.swing = 0; h.shader = 0; });
		return y + 36;
	}

	private float friends(float x, float y, float w) {
		Config c = Config.I;
		Friends.Snap s = Friends.snap;
		Ui.rectB(x, y, w, 64, 11, CARD, LINE);
		Ui.text("Your ID", x + 14, y + 11, 9, DIM, false);
		Ui.text(s.myId.isEmpty() ? "-" : s.myId, x + 14, y + 25, 17, TXT, true);
		Ui.text(s.myNick.isEmpty() ? "" : s.myNick, x + 14 + Ui.width(s.myId.isEmpty() ? "-" : s.myId, 17, true) + 10, y + 32, 9.5f, DIM, false);
		Ui.textR(s.status, x + w - 14, y + 13, 8.5f, s.ok ? 0xFF9AE6A8 : DIM, false);
		Ui.text("Друзей добавляют в лаунчере: по нику или по этому ID.", x + 14, y + 49, 8.5f, DIMMER, false);
		y += 74;
		row(x, y, w, "Direction arrows", "Стрелки на экране к друзьям из вашей группы", "fa", c.friends.arrows, () -> c.friends.arrows = !c.friends.arrows); y += 36;
		row(x, y, w, "Nizay logo", "Логотип слева от ника игроков с лаунчером Nizay", "fl", c.friends.logo, () -> c.friends.logo = !c.friends.logo); y += 40;

		if (!s.invites.isEmpty()) {
			Ui.text("Invites", x, y, 10.5f, TXT, true); Ui.text("Приглашения в группу", x + 46, y + 1.5f, 8.5f, DIMMER, false); y += 18;
			for (String from : s.invites) {
				Ui.rectB(x, y, w, 28, 9, CARD, LINE);
				Ui.text(from, x + 14, y + 8, 10.5f, TXT, true);
				button(x + w - 74, y + 4, 62, 20, "Accept", true, () -> Friends.action("/group/accept", "from", from));
				y += 32;
			}
			y += 6;
		}
		Ui.text("Friends", x, y, 10.5f, TXT, true); Ui.text("Список друзей", x + 44, y + 1.5f, 8.5f, DIMMER, false); y += 18;
		if (s.friends.isEmpty()) { Ui.text("Пока пусто. Добавьте друга в лаунчере.", x, y + 2, 9, DIMMER, false); y += 24; }
		for (Friends.Friend f : s.friends) {
			Ui.rectB(x, y, w, 28, 9, CARD, LINE);
			Ui.circle(x + 16, y + 14, 3.5f, f.online() ? 0xFF7CE08F : 0xFF4A4A54);
			Ui.text(f.nick(), x + 28, y + 8, 10.5f, TXT, true);
			Ui.text("#" + f.id(), x + 34 + Ui.width(f.nick(), 10.5f, true), y + 10, 8.5f, DIMMER, false);
			if (f.online()) button(x + w - 74, y + 4, 62, 20, "Invite", false, () -> Friends.action("/group/invite", "target", f.nick()));
			else Ui.textR("offline", x + w - 14, y + 10, 8.5f, DIMMER, false);
			y += 32;
		}
		y += 8;
		Ui.text("Group", x, y, 10.5f, TXT, true); Ui.text("Ваша группа", x + 38, y + 1.5f, 8.5f, DIMMER, false);
		if (!s.group.isEmpty()) button(x + w - 70, y - 4, 70, 20, "Leave", false, () -> Friends.action("/group/leave", null, null));
		y += 20;
		if (s.group.isEmpty()) { Ui.text("Вы не в группе. Пригласите друга кнопкой Invite.", x, y + 2, 9, DIMMER, false); y += 22; }
		for (Friends.Member m : s.group) {
			Ui.rectB(x, y, w, 26, 9, CARD, LINE);
			Ui.circle(x + 16, y + 13, 3.5f, m.online() ? 0xFF7CE08F : 0xFF4A4A54);
			Ui.text(m.nick(), x + 28, y + 7, 10, TXT, true);
			y += 30;
		}
		return y + 6;
	}

	private float optimization(float x, float y, float w) {
		Config.Opt o = Config.I.opt;
		row(x, y, w, "Optimization", "Главный переключатель. Прежние настройки вернутся при выключении", "o_on", o.on, () -> { o.on = !o.on; Optimizer.apply(); }); y += 38;
		Ui.rect(x, y - 4, w, 1, 0, LINE);
		y = orow(x, y, w, "Uncap FPS", "Выключает VSync и снимает лимит кадров", "o_uc", o.uncap, () -> o.uncap = !o.uncap);
		y = orow(x, y, w, "Fast graphics", "Быстрая графика: листва и прозрачность дешевле", "o_fg", o.fastGfx, () -> o.fastGfx = !o.fastGfx);
		y = orow(x, y, w, "Adaptive effects", "Снижает плотность эффектов Nizay при просадке FPS", "o_lim", o.limiter, () -> o.limiter = !o.limiter);
		y = orow(x, y, w, "Minimal particles", "Минимум ванильных частиц", "o_mp", o.minParticles, () -> o.minParticles = !o.minParticles);
		y = orow(x, y, w, "Clouds off", "Отключает облака", "o_cl", o.noClouds, () -> o.noClouds = !o.noClouds);
		y = orow(x, y, w, "Entity shadows off", "Без теней под сущностями — легче на спавне", "o_sh", o.noShadows, () -> o.noShadows = !o.noShadows);
		y = orow(x, y, w, "Flat lighting", "Выключает плавное освещение", "o_ao", o.noAo, () -> o.noAo = !o.noAo);
		y += 4;
		y = slider("o_ed", x, y, w, "Entity distance", "Дальность отрисовки сущностей", o.entityDist + "%", (o.entityDist - 50) / 50f, v -> { o.entityDist = 50 + Math.round(v * 50); Optimizer.apply(); });
		y = slider("o_fc", x, y, w, "Effects cap", "Максимум частиц Nizay одновременно", o.fxCap + "", (o.fxCap - 100) / 1400f, v -> o.fxCap = 100 + Math.round(v * 1400));
		y = slider("o_rd", x, y, w, "Render distance cap", "Ограничение дальности прорисовки чанков", o.renderCap == 0 ? "off" : o.renderCap + "", o.renderCap == 0 ? 0 : (o.renderCap - 4) / 28f, v -> { o.renderCap = v < .02f ? 0 : 4 + Math.round(v * 28); Optimizer.apply(); });
		y = slider("o_sd", x, y, w, "Simulation cap", "Ограничение дальности симуляции", o.simCap == 0 ? "off" : o.simCap + "", o.simCap == 0 ? 0 : (o.simCap - 5) / 27f, v -> { o.simCap = v < .02f ? 0 : 5 + Math.round(v * 27); Optimizer.apply(); });
		return y;
	}

	private float orow(float x, float y, float w, String t, String ru, String id, boolean on, Runnable r) {
		row(x, y, w, t, ru, id, on, () -> { r.run(); Optimizer.apply(); });
		return y + 34;
	}

	private float configs(float x, float y, float w) {
		Ui.rectB(x, y, w, 96, 11, CARD, LINE);
		Ui.text("Config code", x + 14, y + 11, 11.5f, TXT, true);
		Ui.text("Весь конфиг одной строкой: эффекты, крылья, небо, HUD, функции", x + 14, y + 27, 8.5f, DIMMER, false);
		button(x + 14, y + 46, 150, 24, "Copy my code", true, () -> { minecraft.keyboardHandler.setClipboard(ConfigCodec.encode(Config.I)); notice = "Код скопирован в буфер обмена"; });
		button(x + 174, y + 46, 174, 24, "Paste code & apply", false, () -> {
			String err = ConfigCodec.apply(minecraft.keyboardHandler.getClipboard());
			notice = err == null ? "Конфиг применён" : "Ошибка: " + err;
		});
		Ui.text(notice, x + 14, y + 78, 8.5f, 0xFF9AE6A8, false);
		y += 108;
		Ui.text("Saved configs", x, y, 10.5f, TXT, true); Ui.text("Сохранённые конфиги", x + 70, y + 1.5f, 8.5f, DIMMER, false);
		button(x + w - 120, y - 5, 120, 20, "Save current", false, () -> {
			String n = "Config " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM HH-mm-ss"));
			notice = ConfigCodec.save(n) != null ? "Сохранено: " + n : "Не удалось сохранить";
		});
		y += 22;
		List<String> names = ConfigCodec.list();
		if (names.isEmpty()) { Ui.text("Пока нет сохранённых конфигов.", x, y + 2, 9, DIMMER, false); y += 24; }
		for (String n : names) {
			Ui.rectB(x, y, w, 28, 9, CARD, LINE);
			Ui.text(Ui.fit(n, 150, 10, true), x + 14, y + 8, 10, TXT, true);
			button(x + w - 204, y + 4, 56, 20, "Load", true, () -> { String e = ConfigCodec.apply(ConfigCodec.read(n)); notice = e == null ? "Загружен: " + n : "Ошибка: " + e; });
			button(x + w - 142, y + 4, 56, 20, "Copy", false, () -> { minecraft.keyboardHandler.setClipboard(ConfigCodec.read(n)); notice = "Код скопирован: " + n; });
			button(x + w - 80, y + 4, 66, 20, "Delete", false, () -> { ConfigCodec.delete(n); notice = "Удалён: " + n; });
			y += 32;
		}
		return y + 6;
	}

	private float accounts(float x, float y, float w) {
		List<Accounts.Acc> list = Accounts.list();
		String cur = Accounts.currentName();
		Ui.rectB(x, y, w, 62, 11, CARD, LINE);
		Ui.text("Current account", x + 16, y + 12, 9, DIM, false);
		Ui.text(cur, x + 16, y + 27, 14, TXT, true);
		Ui.text("Смена аккаунта вступает в силу при следующем заходе на сервер", x + 16, y + 46, 8.5f, DIMMER, false);
		y += 74;
		if (list.isEmpty()) {
			Ui.text("Аккаунты лаунчера не найдены.", x, y + 6, 10, DIM, false);
			Ui.text("Запустите игру из лаунчера Nizay и добавьте аккаунты там.", x, y + 22, 9, DIMMER, false);
			return y + 40;
		}
		for (Accounts.Acc a : list) {
			boolean isCur = a.name().equals(cur);
			Ui.rectB(x, y, w, 28, 9, CARD, isCur ? 0xFF4A4A55 : LINE);
			Ui.circle(x + 18, y + 14, 8, 0xFF24242B);
			Ui.textC(a.name().isEmpty() ? "?" : a.name().substring(0, 1).toUpperCase(), x + 18, y + 8, 10, TXT, true);
			Ui.text(a.name(), x + 34, y + 6, 10.5f, TXT, true);
			Ui.text(a.type(), x + 34 + Ui.width(a.name() + "  ", 10.5f, true), y + 8, 8.5f, DIM, false);
			if (isCur) Ui.textR("In use", x + w - 14, y + 9, 9, DIM, false);
			else button(x + w - 70, y + 4, 58, 20, "Use", true, () -> Accounts.select(a));
			y += 32;
		}
		return y + 4;
	}

	private float settings(float x, float y, float w) {
		Ui.rectB(x, y, w, 110, 11, CARD, LINE);
		Ui.text("Nizay client v" + NizayClient.VERSION, x + 16, y + 14, 12, TXT, true);
		Ui.text("Конфиг: .minecraft/config/nizay.json", x + 16, y + 32, 9, DIM, false);
		button(x + 16, y + 56, 96, 24, "Save", true, Config::save);
		button(x + 122, y + 56, 96, 24, "Reset all", false, () -> { Config.I.opt.on = false; Optimizer.apply(); Config.I = new Config(); Config.save(); });
		Ui.text("Открыть меню: Right Shift", x + 16, y + 90, 9, DIMMER, false);
		return y + 120;
	}

	// ------------------------------------------------------------------ виджеты
	private boolean inside(float x, float y, float w, float h) {
		if (inContent && (myD < VY0 - 4 || myD > VY1)) return false;
		return mxD >= x && mxD < x + w && myD >= y && myD < y + h;
	}

	private void hit(float x, float y, float w, float h, Runnable click, Consumer<Float> drag) {
		Hit hh = new Hit(); hh.x = x; hh.y = y; hh.w = w; hh.h = h; hh.click = click; hh.drag = drag; hh.clip = inContent; hits.add(hh);
	}

	private float anim(String id, float target) {
		float v = anim.getOrDefault(id, target);
		v += (target - v) * Math.min(1f, dt * 14f);
		anim.put(id, v); return v;
	}

	private void toggle(String id, float x, float y, boolean on, Runnable click) {
		float a = anim(id, on ? 1 : 0);
		Ui.rect(x, y, 32, 18, 9, Ui.lerp(OFF, ON, a));
		Ui.circle(x + 9 + 14 * a, y + 9, 6.2f, Ui.lerp(0xFF8A8A94, DARK, a));
		hit(x - 4, y - 4, 40, 26, click, null);
	}

	private void mini(float x, float y, boolean on, String id) {
		float a = anim(id, on ? 1 : 0);
		Ui.rect(x, y, 22, 12, 6, Ui.lerp(OFF, ON, a));
		Ui.circle(x + 6 + 10 * a, y + 6, 4, Ui.lerp(0xFF8A8A94, DARK, a));
	}

	/** Строка-переключатель: английское название, под ним — русское пояснение. */
	private void row(float x, float y, float w, String title, String ru, String id, boolean on, Runnable click) {
		boolean hov = inside(x - 4, y - 3, w + 8, 32);
		if (hov) Ui.rect(x - 4, y - 3, w + 8, 32, 8, 0x14FFFFFF);
		Ui.text(title, x, y, 10.5f, TXT, true);
		Ui.text(Ui.fit(ru, w - 52, 8.5f, false), x, y + 14, 8.5f, DIMMER, false);
		toggle(id, x + w - 32, y + 3, on, click);
		hit(x - 4, y - 3, w - 40, 32, click, null);
	}

	/** Слайдер с подписью (английской) и русским пояснением. Возвращает следующий y. */
	private float slider(String id, float x, float y, float w, String label, String ru, String val, float v, Consumer<Float> set) {
		Ui.text(label, x, y, 9.5f, DIM, true);
		Ui.text(ru, x + Ui.width(label, 9.5f, true) + 8, y + 1.2f, 8, DIMMER, false);
		Ui.textR(val, x + w, y, 9.5f, TXT, true);
		slider0(id, x, y + 19, w, v, set);
		return y + 34;
	}

	private void slider0(String id, float x, float y, float w, float v, Consumer<Float> set) {
		v = Math.max(0, Math.min(1, v));
		boolean act = dragging != null && dragging.drag != null && dragging.x == x - 8 && dragging.y == y - 9;
		float a = anim("s" + id, act || inside(x - 8, y - 9, w + 16, 20) ? 1 : 0);
		Ui.rect(x, y - 2, w, 4, 2, OFF);
		Ui.rect(x, y - 2, Math.max(4, w * v), 4, 2, WHITE);
		Ui.circle(x + w * v, y, 6f + a * 1.2f, 0x50000000);
		Ui.circle(x + w * v, y, 5f + a * 1.2f, WHITE);
		final float fx = x, fw = w;
		hit(x - 8, y - 9, w + 16, 20, null, mx -> set.accept(Math.max(0, Math.min(1, (mx - fx) / fw))));
	}

	private void swatch(float cx, float cy, float r, int color, boolean sel, Runnable click) {
		if (sel) Ui.circle(cx, cy, r + 3.2f, WHITE);
		if (sel) Ui.circle(cx, cy, r + 1.6f, 0xFF17171B);
		Ui.circle(cx, cy, r, color);
		hit(cx - r - 3, cy - r - 3, 2 * r + 6, 2 * r + 6, click, null);
	}

	private void button(float x, float y, float w, float h, String label, boolean primary, Runnable click) {
		boolean hov = inside(x, y, w, h);
		Ui.rectB(x, y, w, h, 7, primary ? (hov ? 0xFFFFFFFF : WHITE) : (hov ? CARD_HI : 0xFF1B1B20), primary ? WHITE : LINE);
		Ui.textC(label, x + w / 2, y + h / 2 - 5, 9.5f, primary ? DARK : TXT, true);
		hit(x, y, w, h, click, null);
	}

	private void arrow(float x, float y, String s, Runnable click) {
		boolean hov = inside(x, y, 24, 20);
		Ui.rectB(x, y, 24, 20, 7, hov ? CARD_HI : 0xFF1B1B20, LINE);
		Ui.textC(s, x + 12, y + 4.5f, 10, TXT, true);
		hit(x, y, 24, 20, click, null);
	}

	static String keyName(int k) {
		if (k >= 65 && k <= 90 || k >= 48 && k <= 57) return String.valueOf((char) k);
		if (k >= 290 && k <= 301) return "F" + (k - 289);
		return switch (k) {
			case 32 -> "Space"; case 340 -> "L Shift"; case 344 -> "R Shift"; case 341 -> "L Ctrl"; case 345 -> "R Ctrl";
			case 342 -> "L Alt"; case 346 -> "R Alt"; case 258 -> "Tab"; case 96 -> "`"; case 20 -> "Caps";
			default -> k <= 0 ? "None" : "Key " + k;
		};
	}

	// ------------------------------------------------------------------ ввод
	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
		if (e.button() != 0) return false;
		float mx = Ui.mx(e.x(), ogx, fit), my = Ui.mx(e.y(), ogy, fit);
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit h = hits.get(i);
			if (h.clip && (!Config.I.master || my < VY0 - 4 || my > VY1)) continue;
			if (mx >= h.x && mx < h.x + h.w && my >= h.y && my < h.y + h.h) {
				if (h.click != null) h.click.run();
				if (h.drag != null) { dragging = h; h.drag.accept(mx); }
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
		if (dragging != null && dragging.drag != null) { dragging.drag.accept(Ui.mx(e.x(), ogx, fit)); return true; }
		return false;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent e) {
		if (dragging != null) { dragging = null; Config.save(); return true; }
		return false;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		if (Ui.mx(mx, ogx, fit) < SB) { tabScroll = Math.max(0, Math.min(tabMax(), tabScroll - (float) sy * 26f)); return true; }
		float max = Math.max(0, contentH - (VY1 - VY0) + 6);
		float v = Config.I.uiScroll[tab] - (float) sy * 26f;
		Config.I.uiScroll[tab] = Math.max(0, Math.min(max, v));
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		if (binding) {
			int k = e.key() == 259 ? 0 : e.key();
			if (e.key() != 256) {
				if (bindId >= 100) Config.I.bind(dev.nizay.combat.ItemBinds.DEFS[bindId - 100].id()).key = k; else if (bindId == 4) Config.I.auc.searchKey = k; else if (bindId == 2) Config.I.autoSwap.key = k; else if (bindId == 3) Config.I.elytraSwap.key = k; else Config.I.zoom.key = k;
			}
			binding = false; Config.save();
			return true;
		}
		return super.keyPressed(e);
	}

	@Override public void removed() { Config.save(); }
}
