package dev.nizay;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Весь конфиг клиента. Лежит в .minecraft/config/nizay.json */
public class Config {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("nizay.json");
	public static Config I = new Config();

	/** Настройки одного эффекта. size/density 0..100, color: 0 = родной цвет, 1..4 = палитра. */
	public static class Fx { public boolean on = false; public int size = 55, density = 70, color = 0; }

	public static class Wings {
		public boolean on = false;
		public int color = 0, size = 70, glow = 40;
		public float flap = 1.1f;
		public boolean foldIdle = true, firstPerson = false;
	}

	/** Элемент HUD: позиция левого верхнего угла в долях экрана (0..1) и масштаб. Двигается мышью в чате (T). */
	public static class HudEl {
		public boolean on; public float x, y, scale = 1f;
		public HudEl() {}
		public HudEl(boolean on, float x, float y) { this.on = on; this.x = x; this.y = y; }
	}

	public static class Opt {
		public boolean on = false;
		public boolean limiter = true;
		public int fxCap = 700;
		public boolean minParticles = true, noClouds = true, noShadows = true, noAo = false, fastGfx = true, uncap = true;
		public int entityDist = 70, renderCap = 0, simCap = 0, biomeBlend = 1;
		public boolean saved;
		public String origParticles, origClouds, origGfx;
		public boolean origShadows, origAo, origVsync, origLeaves = true, origTransp = true, origVignette = true;
		public int origWeather = 10;
		public double origEntDist = 1.0;
		public int origRender, origSim, origBiome, origFps = 120;
	}

	/** Зум: key — код клавиши GLFW (по умолчанию C). */
	public static class Zoom { public boolean on = false; public int key = 67; public int factor = 4; public boolean hold = true, smooth = true, slowMouse = true; }

	/** Fullbright: гамма level% (1500 = 15.0). origGamma хранится на случай аварийного выхода. */
	public static class Bright { public boolean on = false; public int level = 1500; public double origGamma = -1; }

	/** Эффекты удара. style 0..4, пять разных видов. */
	public static class Hit { public boolean on = false; public int style = 0, size = 60, density = 70, color = 0; }


	/** Метка цели: вращающиеся уголки на игроке под прицелом. color — индекс палитры Fx (4 = фиолетовый). */
	public static class Target { public boolean on = false, weaponOnly = true; public int size = 100, color = 4, spin = 100; }

	/** AutoSwap / ElytraSwap: key — код клавиши GLFW (G = 71, H = 72). */
	public static class Swap { public boolean on = false; public int key = 71; public Swap() {} public Swap(int key) { this.key = key; } }

	/** Траектории: жемчуг, лук, арбалет, трезубец (и тройной выстрел). */
	public static class Traj { public boolean on = false, pearl = true, bow = true, crossbow = true, trident = true; public int color = 4; }

	/** Таймеры: кулдауны предметов в плашке Cooldowns и обратный отсчёт Пласта / Трапки после установки. */
	public static class Timers { public boolean items = true, placed = true; public int plastSec = 20, trapSec = 15; }

	/** Свои руки: смещение XYZ (сотые доли блока), размер %, повороты в градусах, анимация удара, «шейдер» (0 нет, 1 свечение, 2 пульс). */
	public static class Hands {
		public boolean on = false;
		public int x = 0, y = 0, z = 0, scale = 100, rx = 0, ry = 0, rz = 0, swing = 0, shader = 0;
	}

	public static class Friends {
		public boolean arrows = true, logo = true;
		public String server = "";   // адрес relay-сервера (https://...), обычно приходит из лаунчера
	}

	public Map<String, Fx> fx = new LinkedHashMap<>();
	public boolean skyOn = true;
	public String sky = "deep_space";
	public float animSpeed = 1.2f, brightness = 0.8f;
	public Wings wings = new Wings();
	public HudEl hudMark = new HudEl(true, 0.012f, 0.018f);
	public HudEl hudCoords = new HudEl(true, 0.012f, 0.085f);
	public HudEl hudCd = new HudEl(true, 0.012f, 0.2f);
	public Opt opt = new Opt();
	public Zoom zoom = new Zoom();
	public Bright bright = new Bright();
	public Hit hit = new Hit();
	public Friends friends = new Friends();
	/** Бинды предметов хотбара (трапка, пыль, зелья и т.д.). */
	public static class BindItem { public boolean on = true; public int key = 0; }
	public static class Binds { public boolean on = false; public Map<String, BindItem> items = new LinkedHashMap<>(); }
	public Binds binds = new Binds();

	/** AucHelper: relistOn и searchOn — два независимых модуля. interval в секундах, delayMs — пауза между кликами. */
	public static class Auc { public boolean relistOn = false, searchOn = false; public int interval = 61, delayMs = 250, searchKey = 0, storageCol = 0, redCol = 1, circleCol = 7; }
	public Auc auc = new Auc();
	public HudEl hudAh = new HudEl(true, 0.38f, 0.07f);
	public BindItem bind(String id) { return binds.items.computeIfAbsent(id, k -> new BindItem()); }

	public Target target = new Target();
	public Swap autoSwap = new Swap(71), elytraSwap = new Swap(72);
	public Traj traj = new Traj();
	public Timers timers = new Timers();
	public Hands hands = new Hands();

	/** Главный выключатель. masterSaved помнит, что было включено. */
	public boolean master = true;
	public Map<String, Boolean> masterSaved = new HashMap<>();

	/** Состояние меню: последняя вкладка и прокрутка каждой. */
	public int uiTab = 0, uiFxPage = 0, uiFxSel = 0;
	public float[] uiScroll = new float[16];

	public Fx fx(String id) { return fx.computeIfAbsent(id, k -> new Fx()); }

	// ------------------------------------------------------------------ главный выключатель
	public void setMaster(boolean on) {
		if (on == master) return;
		master = on;
		if (!on) {
			masterSaved.clear();
			save1("sky", skyOn); skyOn = false;
			save1("wings", wings.on); wings.on = false;
			save1("hudMark", hudMark.on); hudMark.on = false;
			save1("hudCoords", hudCoords.on); hudCoords.on = false;
			save1("hudCd", hudCd.on); hudCd.on = false;
			save1("opt", opt.on); opt.on = false;
			save1("zoom", zoom.on); zoom.on = false;
			save1("bright", bright.on); bright.on = false;
			save1("hit", hit.on); hit.on = false;
			save1("binds", binds.on); binds.on = false;
			save1("aucRelist", auc.relistOn); auc.relistOn = false;
			save1("aucSearch", auc.searchOn); auc.searchOn = false;
			save1("target", target.on); target.on = false;
			save1("autoSwap", autoSwap.on); autoSwap.on = false;
			save1("elytraSwap", elytraSwap.on); elytraSwap.on = false;
			save1("traj", traj.on); traj.on = false;
			save1("hands", hands.on); hands.on = false;
			save1("arrows", friends.arrows); friends.arrows = false;
			save1("flogo", friends.logo); friends.logo = false;
			for (Map.Entry<String, Fx> e : fx.entrySet()) { save1("fx:" + e.getKey(), e.getValue().on); e.getValue().on = false; }
		} else {
			skyOn = g("sky", skyOn); wings.on = g("wings", wings.on);
			hudMark.on = g("hudMark", hudMark.on); hudCoords.on = g("hudCoords", hudCoords.on); hudCd.on = g("hudCd", hudCd.on);
			opt.on = g("opt", opt.on); zoom.on = g("zoom", zoom.on); bright.on = g("bright", bright.on); hit.on = g("hit", hit.on);
			auc.relistOn = g("aucRelist", auc.relistOn); auc.searchOn = g("aucSearch", auc.searchOn);
			binds.on = g("binds", binds.on); target.on = g("target", target.on); autoSwap.on = g("autoSwap", autoSwap.on); elytraSwap.on = g("elytraSwap", elytraSwap.on);
			traj.on = g("traj", traj.on); hands.on = g("hands", hands.on);
			friends.arrows = g("arrows", friends.arrows); friends.logo = g("flogo", friends.logo);
			for (Map.Entry<String, Fx> e : fx.entrySet()) e.getValue().on = g("fx:" + e.getKey(), e.getValue().on);
			masterSaved.clear();
		}
	}
	private void save1(String k, boolean v) { masterSaved.put(k, v); }
	private boolean g(String k, boolean def) { Boolean b = masterSaved.get(k); return b != null ? b : def; }

	public static void load() {
		try {
			if (Files.exists(FILE)) {
				try (Reader r = Files.newBufferedReader(FILE)) {
					Config c = GSON.fromJson(r, Config.class);
					if (c != null) { I = c; I.fix(); }
				}
			} else {
				I.fx("stardust").on = true;
				I.fx("aether").on = true;
				I.fx("halo").on = true;
				save();
			}
		} catch (Exception e) {
			System.err.println("[Nizay] config load failed: " + e);
		}
	}

	/** Достраивает поля, которых не было в старых/чужих конфигах. */
	public void fix() {
		if (fx == null) fx = new LinkedHashMap<>();
		if (wings == null) wings = new Wings();
		if (hudMark == null) hudMark = new HudEl(true, 0.012f, 0.018f);
		if (hudCoords == null) hudCoords = new HudEl(true, 0.012f, 0.085f);
		if (hudCd == null) hudCd = new HudEl(true, 0.012f, 0.2f);
		if (opt == null) opt = new Opt();
		if (zoom == null) zoom = new Zoom();
		if (bright == null) bright = new Bright();
		if (hit == null) hit = new Hit();
		if (friends == null) friends = new Friends();
		if (binds == null) binds = new Binds();
		if (auc == null) auc = new Auc();
		if (hudAh == null) hudAh = new HudEl(true, 0.38f, 0.07f);
		if (binds.items == null) binds.items = new LinkedHashMap<>();
		binds.items.keySet().removeIf(k -> !java.util.Set.of("trap", "plast", "disorient", "dust", "fireball", "divine", "snow", "pearl").contains(k));
		if (target == null) target = new Target();
		if (autoSwap == null) autoSwap = new Swap(71);
		if (elytraSwap == null) elytraSwap = new Swap(72);
		if (traj == null) traj = new Traj();
		if (timers == null) timers = new Timers();
		if (hands == null) hands = new Hands();
		if (masterSaved == null) masterSaved = new HashMap<>();
		if (uiScroll == null || uiScroll.length < 16) uiScroll = new float[16];
		if (sky == null) sky = "deep_space";
	}

	public static void save() {
		try (Writer w = Files.newBufferedWriter(FILE)) {
			GSON.toJson(I, w);
		} catch (Exception e) {
			System.err.println("[Nizay] config save failed: " + e);
		}
	}

	public static Gson gson() { return GSON; }
}
