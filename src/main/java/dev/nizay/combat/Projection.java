package dev.nizay.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/** Перевод точек мира в координаты экрана (GUI-единицы) по взгляду игрока и FOV. Работает от первого лица. */
public final class Projection {
	private Projection() {}

	public static final class Cam {
		double ex, ey, ez, fx, fy, fz, rx, rz, ux, uy, uz, tan, aspect, w, h;

		/** Возвращает {x, y, пикселей на 1 блок на этой глубине} или null, если точка за камерой. */
		public float[] project(Vec3 p) {
			double dx = p.x - ex, dy = p.y - ey, dz = p.z - ez;
			double z = dx * fx + dy * fy + dz * fz;
			if (z < .05) return null;
			double x = dx * rx + dz * rz, y = dx * ux + dy * uy + dz * uz;
			double nx = x / (z * tan * aspect), ny = y / (z * tan);
			return new float[]{(float) ((nx * .5 + .5) * w), (float) ((.5 - ny * .5) * h), (float) (h * .5 / (z * tan))};
		}
	}

	public static Cam cam(Minecraft mc, float pt) {
		LocalPlayer pl = mc.player;
		if (pl == null) return null;
		Cam c = new Cam();
		Vec3 eye = pl.getEyePosition(pt);
		c.ex = eye.x; c.ey = eye.y; c.ez = eye.z;
		double yaw = Math.toRadians(pl.getViewYRot(pt)), pitch = Math.toRadians(pl.getViewXRot(pt)), cp = Math.cos(pitch);
		c.fx = -Math.sin(yaw) * cp; c.fy = -Math.sin(pitch); c.fz = Math.cos(yaw) * cp;
		c.rx = -Math.cos(yaw); c.rz = -Math.sin(yaw);
		c.ux = -c.rz * c.fy; c.uy = c.rz * c.fx - c.rx * c.fz; c.uz = c.rx * c.fy;
		c.tan = Math.tan(Math.toRadians(mc.options.fov().get()) / 2);
		c.w = mc.getWindow().getGuiScaledWidth(); c.h = mc.getWindow().getGuiScaledHeight();
		c.aspect = c.w / c.h;
		return c;
	}
}
