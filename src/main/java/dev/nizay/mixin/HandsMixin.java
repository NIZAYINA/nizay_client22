package dev.nizay.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.nizay.Config;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Свои руки (предметы в руках от первого лица): смещение XYZ, размер, поворот, анимации удара и подсветка.
 * Хуки с require = 0: если у метода другое имя, функция просто не работает, но игра запускается.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class HandsMixin {
	/** Смещение / размер / поворот. Вызывается внутри отрисовки конкретной руки, поэтому не «протекает» на другую. */
	@Inject(method = "applyItemArmTransform", at = @At("HEAD"), require = 0)
	private void nizay$transform(PoseStack ps, HumanoidArm arm, float equip, CallbackInfo ci) {
		Config.Hands h = Config.I.hands;
		if (!h.on || !Config.I.master) return;
		float side = arm == HumanoidArm.RIGHT ? 1f : -1f;
		ps.translate(side * h.x / 100f, h.y / 100f, h.z / 100f);
		if (h.rx != 0) ps.mulPose(Axis.XP.rotationDegrees(h.rx));
		if (h.ry != 0) ps.mulPose(Axis.YP.rotationDegrees(side * h.ry));
		if (h.rz != 0) ps.mulPose(Axis.ZP.rotationDegrees(side * h.rz));
		if (h.scale != 100) { float s = h.scale / 100f; ps.scale(s, s, s); }
	}

	/** Анимации удара (вместо ванильного взмаха). 0 — ванильный. */
	@Inject(method = "applyItemArmAttackTransform", at = @At("HEAD"), cancellable = true, require = 0)
	private void nizay$swing(PoseStack ps, HumanoidArm arm, float sw, CallbackInfo ci) {
		Config.Hands h = Config.I.hands;
		if (!h.on || !Config.I.master || h.swing == 0) return;
		ci.cancel();
		float side = arm == HumanoidArm.RIGHT ? 1f : -1f;
		float p = Mth.sin(sw * (float) Math.PI);              // 0 -> 1 -> 0
		float q = Mth.sin(Mth.sqrt(sw) * (float) Math.PI);
		switch (h.swing) {
			case 1 -> {   // Side punch: боковой удар
				ps.translate(side * -.35f * p, -.05f * p, -.45f * q);
				ps.mulPose(Axis.YP.rotationDegrees(side * 55f * p));
				ps.mulPose(Axis.ZP.rotationDegrees(side * -28f * p));
			}
			case 2 -> {   // Jab: прямой выпад вперёд
				ps.translate(0, 0, -.95f * q);
				ps.mulPose(Axis.XP.rotationDegrees(-12f * p));
			}
			case 3 -> {   // Slash: горизонтальный взмах
				ps.translate(side * (.3f - .6f * sw), 0, -.35f * p);
				ps.mulPose(Axis.YP.rotationDegrees(side * (55f - 110f * sw)));
				ps.mulPose(Axis.ZP.rotationDegrees(side * -20f * p));
			}
			case 4 -> {   // Chop: рубящий сверху вниз
				ps.translate(0, .25f - .55f * sw, -.4f * p);
				ps.mulPose(Axis.XP.rotationDegrees(-70f + 110f * sw));
			}
			case 5 -> {   // Spin: оборот вокруг оси предмета
				ps.translate(0, 0, -.3f * p);
				ps.mulPose(Axis.ZP.rotationDegrees(360f * sw));
			}
			default -> { }   // 6: без анимации
		}
	}

	/** Подсветка предметов в руках: 1 — всегда ярко, 2 — пульсация. */
	@ModifyVariable(method = "renderArmWithItem", at = @At("HEAD"), argsOnly = true, require = 0)
	private int nizay$light(int light) {
		Config.Hands h = Config.I.hands;
		if (!h.on || !Config.I.master || h.shader == 0) return light;
		int lvl = 15;
		if (h.shader == 2) lvl = 8 + Math.round(7f * (.5f + .5f * (float) Math.sin(System.nanoTime() / 1e9 * 3.0)));
		return (lvl << 20) | (lvl << 4);
	}
}
