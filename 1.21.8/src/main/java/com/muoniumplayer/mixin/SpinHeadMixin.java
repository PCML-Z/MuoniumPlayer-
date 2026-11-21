package com.muoniumplayer.mixin;

import com.muoniumplayer.core.ncm.music.MusicSpin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 「陀螺模式·头乱晃」的头部动画（1.21.8 混合世代）：{@code setupAnim} 之后、绘制
 * 之前，直接给头部件加 yaw/pitch 差值。
 *
 * <p>1.21.8 是过渡代际混合体：模型动画已是 render-state 形态——{@code setupAnim}
 * 只有单参 {@code setupAnim(EntityRenderState)}（六参旧形态 1.21.8 确实没有，
 * javap/mappings 核实 {@code HumanoidModel.setupAnim} 内部按 ×0.017453292 把
 * 渲染态角度换算进 {@code ModelPart.xRot/yRot}），但实体渲染调度还是单段式
 * {@code EntityRenderDispatcher.render(Entity,……)}（无 extractEntity/submit）。
 * 所以头乱晃仍走「包住 setupAnim、在其后给 {@link HeadedModel#getHead()} 叠
 * 差值」的模型部件路线（与 1.20.1/1.21.1 同语义，差值总能落在最终头姿上，不被
 * 原版对头部件的后写覆盖），只把 @Redirect 目标换成 1.21.8 的单参描述符。
 * 模型角度是弧度、共享层给的是度，换算系数 2π/180 与原版 setupAnim 的写法一致。</p>
 *
 * <p>实体身份从渲染态取：{@link PlayerRenderState#id} 是 extract 期从
 * {@code entity.getId()} 填入的网络实体 id（javap 核实 PlayerRenderer），与本地
 * 玩家 id 比较即 isSelf；非玩家实体（RANGE_ALL 档的生物）拿不到
 * PlayerRenderState，isSelf/isPlayer 双 false，交共享层档位判定。render 的入口
 * 是 LivingEntityRenderState 没有 Entity 形参，@Redirect 方法选择器必须带完整
 * 擦除描述符（同名桥接 render(EntityRenderState,…) 不含 setupAnim 调用点）。
 * {@code SpinEntityMixin} 的 pose 括号转的是整模型，头部差值叠在部件上，两者
 * 自然复合。头乱晃是纯本机视觉，不碰实体本体与服务端状态。</p>
 */
@Mixin(LivingEntityRenderer.class)
abstract class SpinHeadMixin {

    @Redirect(method = "render(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;)V"))
    private void spin$headAnim(EntityModel model, EntityRenderState state) {
        model.setupAnim(state);
        Minecraft mc = Minecraft.getInstance();
        boolean isPlayer = state instanceof PlayerRenderState;
        boolean isSelf = isPlayer && mc.player != null
                && ((PlayerRenderState) state).id == mc.player.getId();
        if (!MusicSpin.headShakeActiveFor(isSelf, isPlayer)) {
            return;
        }
        if (!(model instanceof HeadedModel headed)) {
            // 没头的生物（少数模型）不参与头部动作。
            return;
        }
        float[] head = MusicSpin.headDeltas();
        if (head[0] == 0f && head[1] == 0f) {
            return;
        }
        ModelPart headPart = headed.getHead();
        headPart.yRot += head[0] * ((float) Math.PI / 180F);
        headPart.xRot += head[1] * ((float) Math.PI / 180F);
    }
}
