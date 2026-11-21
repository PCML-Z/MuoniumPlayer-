package com.muoniumplayer.mixin;

import com.muoniumplayer.compat.mc.WorldLyricsBridge;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 3D 世界歌词的绘制时机修正 —— <b>Fabric 1.21.8 侧</b>（NeoForge 侧走
 * {@code RenderLevelStageEvent.AfterLevel}，本身就在帧图之后，不需要本 mixin）。
 *
 * <h2>为什么换钩子（云盖住歌词的根因）</h2>
 * 之前挂在 fabric-api 的 {@code WorldRenderEvents} —— 那些钩子注入在 main pass
 * 执行体末尾 —— 实体/地形/半透明画完，但云（clouds pass）、天气（weather pass）、
 * 透明后期链（transparencyChain）都是 main pass <b>之后</b>的独立 pass。歌词画得太早，
 * 云直接混叠盖字。
 *
 * <p>1.21.8 的 fabric-api 世界事件里没有「云之后」的挂点，所以本树自己加 mixin：
 * 注入 {@code LevelRenderer#renderLevel} 的 TAIL —— 帧图已执行完（云、天气、
 * 后期链全部落屏），modelViewStack 还在栈上（带视图旋转），与 END_MAIN 事件时
 * 相同的矩阵环境。{@code WorldLyricsBridge.worldRenderEnd()} 自己
 * push 单位矩阵 + 自算相机旋转，不依赖栈上状态，两处都能安全调用。</p>
 *
 * <p>只注册在 fabric 加载器（本文件只进 fabric 的 mixins.json）。</p>
 */
@Mixin(LevelRenderer.class)
public class WorldLyricsRenderTailMixin {

    // 1.21.8 的 renderLevel 描述符：(GraphicsResourceAllocator, DeltaTracker, boolean,
    // Camera, Matrix4f, Matrix4f, GpuBufferSlice, Vector4f, boolean) —— 两个 Matrix4f
    //（1.21.10 起是三个，多了投影前的第三个矩阵，描述符不同，不能照抄）。
    @Inject(method = "renderLevel(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;"
            + "Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;"
            + "Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"
            + "Lorg/joml/Vector4f;Z)V",
            at = @At("TAIL"))
    private void muonium$renderWorldLyricsAfterFrameGraph(CallbackInfo ci) {
        WorldLyricsBridge.worldRenderEnd();
        // 场景声学调参:射线可视化(debug 文件 showRays=true 时画;内部第一行
        // 就判开关,关闭时一次布尔读)。
        com.muoniumplayer.compat.mc.AcousticsRayDebugRenderer.render();
        // 世界声源线框(有音箱才画;内部第一行判空)
        com.muoniumplayer.compat.mc.SpeakerRenderBridge.render();
    }
}
