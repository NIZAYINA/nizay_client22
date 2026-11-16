package dev.nizay.combat;

import dev.nizay.Config;
import dev.nizay.fx.Fx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Траектории: эндер-жемчуг (и снежок/яйцо), стрелы лука и арбалета (с тройным выстрелом — по линии на каждую стрелу), трезубец.
 * Рисуется точками поверх мира в HUD (проекция точек на экран), поэтому видна и сквозь блоки.
 */
public final class Trajectory {
	private Trajectory() {}

	private static final class Path { final List<Vec3> pts = new ArrayList<>(); Vec3 end; boolean entity; }

	private static boolean multishot(ItemStack s) {
		try {
			for (Holder<Enchantment> h : s.getEnchantments().keySet()) if (h.is(Enchantments.MULTISHOT)) return true;
		} catch (Throwable ignored) {}
		return false;
	}

	public static void render(GuiGraphics g, float pt) {
		Config.Traj t = Config.I.traj;
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer pl = mc.player; ClientLevel lv = mc.level;
		if (!t.on || pl == null || lv == null || mc.screen != null || !mc.options.getCameraType().isFirstPerson()) return;

		float speed = 0, grav = .05f; boolean arrow = false; ItemStack held = null;
		for (ItemStack s : new ItemStack[]{pl.getMainHandItem(), pl.getOffhandItem()}) {
			if (s.isEmpty()) continue;
			if (t.pearl && (s.is(Items.ENDER_PEARL) || s.is(Items.SNOWBALL) || s.is(Items.EGG))) { speed = 1.5f; grav = .03f; held = s; break; }
			if (t.bow && s.is(Items.BOW)) {
				float f = 1f;
				if (pl.isUsingItem() && pl.getUseItem().is(Items.BOW)) { f = pl.getTicksUsingItem() / 20f; f = Math.min(1f, (f * f + f * 2f) / 3f); }
				speed = 3f * f; arrow = true; held = s; break;
			}
			if (t.crossbow && s.is(Items.CROSSBOW)) { speed = 3.15f; arrow = true; held = s; break; }
			if (t.trident && s.is(Items.TRIDENT)) { speed = 2.5f; arrow = true; held = s; break; }
		}
		if (held == null || speed <= 0.05f) return;

		Projection.Cam cam = Projection.cam(mc, pt);
		if (cam == null) return;
		Vec3 start = pl.getEyePosition(pt).subtract(0, .1, 0);
		Vec3 look = pl.getViewVector(pt), up = pl.getUpVector(pt);
		Vec3 mv = pl.getDeltaMovement(); Vec3 add = new Vec3(mv.x, pl.onGround() ? 0 : mv.y, mv.z);
		double[] angles = (arrow && !held.is(Items.TRIDENT) && multishot(held)) ? new double[]{-10, 0, 10} : new double[]{0};

		float S = (float) mc.getWindow().getGuiScale();
		int rgb = Fx.PALETTE_RGB[Math.max(0, Math.min(Fx.PALETTE_RGB.length - 1, t.color))];
		g.pose().pushMatrix();
		g.pose().scale(1f / S, 1f / S);
		try {
			for (double deg : angles) draw(g, cam, S, rgb, simulate(lv, pl, start, rotate(look, up, Math.toRadians(deg)).scale(speed).add(add), grav));
		} finally { g.pose().popMatrix(); }
	}

	/** Поворот вектора v вокруг оси u (единичной) на угол a. */
	private static Vec3 rotate(Vec3 v, Vec3 u, double a) {
		if (a == 0) return v;
		double c = Math.cos(a), s = Math.sin(a);
		return v.scale(c).add(u.cross(v).scale(s)).add(u.scale(u.dot(v) * (1 - c)));
	}

	private static Path simulate(ClientLevel lv, LocalPlayer pl, Vec3 pos, Vec3 vel, float grav) {
		Path p = new Path();
		p.pts.add(pos);
		for (int i = 0; i < 160; i++) {
			Vec3 next = pos.add(vel);
			Vec3 stop = null; boolean ent = false;
			BlockHitResult bh = lv.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, pl));
			if (bh.getType() != HitResult.Type.MISS) stop = bh.getLocation();
			AABB box = new AABB(pos, next).inflate(1.0);
			for (Entity en : lv.getEntities(pl, box, e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator())) {
				Optional<Vec3> o = en.getBoundingBox().inflate(.3).clip(pos, stop != null ? stop : next);
				if (o.isPresent() && (stop == null || o.get().distanceToSqr(pos) < stop.distanceToSqr(pos))) { stop = o.get(); ent = true; }
			}
			if (stop != null) { p.pts.add(stop); p.end = stop; p.entity = ent; break; }
			p.pts.add(next);
			pos = next;
			vel = vel.scale(.99).subtract(0, grav, 0);
			if (pos.y < -80) break;
		}
		return p;
	}

	private static void draw(GuiGraphics g, Projection.Cam cam, float S, int rgb, Path p) {
		int d = Math.max(2, Math.round(S * .75f));
		float[] prev = null; int n = p.pts.size();
		for (int i = 0; i < n; i++) {
			float[] cur = cam.project(p.pts.get(i));
			if (cur != null && prev != null) {
				float x0 = prev[0] * S, y0 = prev[1] * S, x1 = cur[0] * S, y1 = cur[1] * S;
				float len = (float) Math.hypot(x1 - x0, y1 - y0);
				if (len < 4000) {
					int steps = Math.max(1, Math.round(len / (d * 2f)));
					int a = Math.round(235 - 120f * i / n);
					for (int k = 0; k < steps; k++) {
						float f = k / (float) steps;
						int x = Math.round(x0 + (x1 - x0) * f - d / 2f), y = Math.round(y0 + (y1 - y0) * f - d / 2f);
						g.fill(x, y, x + d, y + d, (a << 24) | rgb);
					}
				}
			}
			prev = cur;
		}
		if (p.end != null) {
			float[] e = cam.project(p.end);
			if (e != null) {
				int col = p.entity ? 0xFFFF5A5A : (0xFF000000 | rgb);
				int r = Math.round(5 * S), th = Math.max(1, Math.round(S * .6f));
				int cx = Math.round(e[0] * S), cy = Math.round(e[1] * S);
				g.fill(cx - r, cy - r, cx + r, cy - r + th, col); g.fill(cx - r, cy + r - th, cx + r, cy + r, col);
				g.fill(cx - r, cy - r + th, cx - r + th, cy + r - th, col); g.fill(cx + r - th, cy - r + th, cx + r, cy + r - th, col);
				g.fill(cx - th, cy - th, cx + th + 1, cy + th + 1, col);
			}
		}
	}
}
