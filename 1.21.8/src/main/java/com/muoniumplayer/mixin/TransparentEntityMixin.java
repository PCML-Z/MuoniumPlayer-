package com.muoniumplayer.mixin;

import com.muoniumplayer.core.ncm.music.TransparentMode;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 「通透模式」的实体压缩：把果冻矩阵包住原版的模型渲染括号（1.21.8 混合世代）。
 *
 * <p>1.20.1~1.21.11 的合同是「首次 translate 落地后推矩阵、火焰动画读取前还原——
 * 火焰、阴影、碰撞箱调试框都画在括号外」。1.21.8 的 dispatcher
 * {@code render(Entity,……)} 虽仍是单段式，但已按调用形拆成四个重载，干活的
 * state 形重载里名牌/牵引绳下放进了 renderer 内部（基类
 * {@code EntityRenderer.render} 只画绳+名牌），火焰/阴影/碰撞箱仍留在 dispatcher
 * 侧读渲染态字段——translate/fire 旧锚点落在 dispatcher 会把名牌卷进旋转。所以
 * 括号搬进本类：{@code render(LivingEntityRenderState,……)} 的 HEAD 推矩阵、
 * {@code super.render}（名牌/绳的总入口）前还原——setupAnim+模型+渲染层（盔甲
 * 跟随）都包在括号内，名牌、牵引绳、火焰、阴影、碰撞箱调试框全部在括号外，
 * 与 1.21.1 合同等价。缩放只动本机画面，不碰碰撞箱与服务端状态。</p>
 *
 * <p>方法选择器必须带 {@code LivingEntityRenderState} 擦除描述符：同名桥接
 * {@code render(EntityRenderState,……)} 只转发；而裸名 {@code render} 会被
 * remapper 钉到四个重载里的转发形（零注入点，Scanned 0 target(s) 直接崩，实测）。
 * push/pop 两端的条件必须逐字对称——一开一关会在帧尾 checkPoseStack 崩
 * （Pose stack not empty，实测）。</p>
 */
@Mixin(LivingEntityRenderer.class)
abstract class TransparentEntityMixin {

    @Inject(method = "render(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"))
    private void transmode$begin(LivingEntityRenderState state, PoseStack pose, MultiBufferSource buffers, int light,
                                 CallbackInfo ci) {
        // push/pop 的条件必须逐字对称：两端都只问开关这一个纯判定，
        // begin 多带一个别的条件而 end 不带的话，错位的那帧每个生物多 pop 一次，
        // 帧尾 checkPoseStack 直接崩（Pose stack not empty，实测）。
        if (!TransparentMode.isEnabled()) return;
        pose.pushPose();
        pose.scale(TransparentMode.widthScale(), TransparentMode.heightScale(), 1f);
    }

    @Inject(method = "render(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void transmode$end(LivingEntityRenderState state, PoseStack pose, MultiBufferSource buffers, int light,
                               CallbackInfo ci) {
        if (TransparentMode.isEnabled()) pose.popPose();
    }
}
