package com.muoniumplayer.mixin;

import com.muoniumplayer.core.ncm.music.MusicSpin;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 「陀螺模式」的实体旋转：把绕 Y 的旋转矩阵包住原版的模型渲染括号（1.21.8 混合世代）。
 *
 * <p>括号落点与 {@link TransparentEntityMixin} 同步搬家：1.21.8 的 dispatcher
 * render 已按调用形拆成四个重载，名牌/牵引绳下放进了 renderer 内部的
 * {@code super.render}（基类只画绳+名牌），火焰/阴影/碰撞箱仍留在 dispatcher 侧
 * 读渲染态字段——所以括号落在 {@code LivingEntityRenderer.render
 * (LivingEntityRenderState,……)} 的 HEAD…super.render 之间：setupAnim+模型+
 * 渲染层在括号内，名牌、牵引绳、火焰、阴影、碰撞箱调试框都在括号外，不受旋转
 * 污染（与 1.21.1「translate 后推、火焰读取前还原」合同等价）。旋转只动本机
 * 画面，不碰实体本体与服务端状态。</p>
 *
 * <p>实体身份从渲染态取（与 {@link SpinHeadMixin} 同模式）：{@code PlayerRenderState.id}
 * 是 extract 期填入的网络实体 id，与本地玩家 id 比较即 isSelf；非玩家实体拿不到
 * PlayerRenderState，isSelf/isPlayer 双 false，交共享层档位判定。LivingEntityRenderer
 * 里 isLiving 恒真（非活体实体根本不进本类）。push/pop 两端的 entitySpins 判定
 * 必须逐字对称（纯判定、无状态推进，隔着一次模型渲染结果也恒等——
 * TransparentEntityMixin 注释里那个 Pose stack not empty 的实测坑，同款纪律）；
 * {@code entityModelAngle} 只在 push 端调（推进弹簧），pop 端不调。</p>
 */
@Mixin(LivingEntityRenderer.class)
abstract class SpinEntityMixin {

    @Inject(method = "render(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"))
    private void spin$begin(LivingEntityRenderState state, PoseStack pose, MultiBufferSource buffers, int light,
                            CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        boolean isPlayer = state instanceof PlayerRenderState;
        boolean isSelf = isPlayer && mc.player != null
                && ((PlayerRenderState) state).id == mc.player.getId();
        // push/pop 的条件必须逐字对称（TransparentEntityMixin 的实测坑）：两端都走
        // entitySpins —— 纯判定、无状态推进，隔着一次模型渲染结果也恒等。
        if (!MusicSpin.entitySpins(isSelf, isPlayer, true)) {
            return;
        }
        // isSelf 蕴含 mc.player != null；非自己的实体 selfRealYaw 传任意值即可。
        float selfRealYaw = isSelf ? mc.player.getYRot() : 0f;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(MusicSpin.entityModelAngle(isSelf, selfRealYaw)));
    }

    @Inject(method = "render(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void spin$end(LivingEntityRenderState state, PoseStack pose, MultiBufferSource buffers, int light,
                          CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        boolean isPlayer = state instanceof PlayerRenderState;
        boolean isSelf = isPlayer && mc.player != null
                && ((PlayerRenderState) state).id == mc.player.getId();
        if (MusicSpin.entitySpins(isSelf, isPlayer, true)) {
            pose.popPose();
        }
    }
}
