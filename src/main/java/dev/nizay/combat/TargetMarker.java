package dev.nizay.combat;

import dev.nizay.Config;
import dev.nizay.fx.Fx;
import dev.nizay.gui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** Метка цели: четыре скруглённых уголка, медленно вращаются на игроке, на которого вы навелись оружием. */
public final class TargetMarker {
	private TargetMarker() {}

	private static final Identifier TEX = Identifier.fromNamespaceAndPath("nizay", "textures/gui/target.png");
	private static Entity last;
	private static float alpha, sx, sy;
	private static boolean hasPos;
	private static long lastNs = System.nanoTime();

	private static boolean weapon(ItemStack s) {
		return s.is(ItemTags.SWORDS) || s.is(ItemTags.AXES) || s.is(Items.MACE) || s.is(Items.TRIDENT);
	}

	public static void render(GuiGraphics g, float pt) {
		Config.Target t = Config.I.target;
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer pl = mc.player;
		long now = System.nanoTime();
		float dt = Math.min(.1f, (now - lastNs) / 1e9f); lastNs = now;
		boolean want = false;
		if (t.on && pl != null && mc.screen == null && mc.options.getCameraType().isFirstPerson()) {
			Entity e = mc.crosshairPickEntity;
			if (e instanceof Player p && p != pl && p.isAlive() && !p.isSpectator() && (!t.weaponOnly || weapon(pl.getMainHandItem()))) { last = e; want = true; }
		}
		alpha += ((want ? 1f : 0f) - alpha) * Math.min(1f, dt * (want ? 18f : 7f));
		if (alpha < .02f) { hasPos = false; return; }
		if (last == null || !last.isAlive() || pl == null) return;
		Projection.Cam cam = Projection.cam(mc, pt);
		if (cam == null) return;
		Vec3 c = last.getPosition(pt).add(0, last.getBbHeight() * .55, 0);
		float[] s = cam.project(c);
		if (s == null) return;
		if (!hasPos) { sx = s[0]; sy = s[1]; hasPos = true; }
		float k = Math.min(1f, dt * 35f);
		sx += (s[0] - sx) * k; sy += (s[1] - sy) * k;
		double sec = now / 1e9;
		float side = last.getBbHeight() * .42f * s[2] * (t.size / 100f) * (1f + .04f * (float) Math.sin(sec * 4));
		float ang = (float) (sec * .9 * (t.spin / 100f));
		int rgb = Fx.PALETTE_RGB[Math.max(0, Math.min(Fx.PALETTE_RGB.length - 1, t.color))];
		int a = Math.round(255 * Math.min(1f, alpha));
		Ui.begin(g, 0, 0, 1f);
		try { Ui.tinted(TEX, 128, sx, sy, side, ang, (a << 24) | rgb); } finally { Ui.end(); }
	}
}
