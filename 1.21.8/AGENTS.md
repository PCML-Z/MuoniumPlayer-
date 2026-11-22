# 1.21.8 车道补充规范

> 继承仓库根 `AGENTS.md`；共享源码另遵守 `muonium-common/AGENTS.md`。核对：2026-11-21。
> 此树是独立车道，不是 1.21.1 或 1.21.10 的目录别名。**当前仅 Fabric 外壳**
>（用户范围为 1.21.8 Fabric；NeoForge 外壳待后续按需补，`ProjectPaths.secondLoaderDir()`
> 对单加载器树返回空，共享测试里第二加载器断言自动跳过）。

## API 使用与移植约束（强制）

- 本树为 **Minecraft 1.21.8 × Fabric** 单一核实组合；以当前实际解析的 loader/API 依赖、映射和访问配置为准，不能由 1.21.1 或 1.21.10 推断本版 API。
- **1.21.8 是过渡代际、混合体**，两侧邻树都不能整套照抄：
  - **GPU/GUI/纹理栈是新形态**（与 1.21.10 同侧）：`GlStateManager` 在 `com.mojang.blaze3d.opengl`（不在 `blaze3d.platform`）；`GuiRenderState`/`GuiRenderer` 延迟批处理（`GuiRenderState` 在 `net.minecraft.client.gui.render.state` 包）；`GuiGraphics.drawString` 返回 `void`、有 `submitBlit`/`submitColoredRectangle`；`DynamicTexture` 构造走 `GpuDevice.createTexture`（`(String,int,int,boolean)` 四参形态）；`NativeImage` **没有** `upload()`/`setPixelRGBA`，只有 `setPixelABGR`，子矩形上传走 `GlCommandEncoder.writeToTexture(GpuTexture,NativeImage,int×8)`；`BufferUploader` 类已删除；`GameRenderer.getFov` 返回 `float`；`Screenshot.takeScreenshot` 两重载都是 Consumer 异步形；`RenderPipeline`/`RenderPipelines.BEACON_BEAM_SNIPPET`/`BlendFunction.LIGHTNING`/`GlTexture.glId()` 全在；`LevelRenderer.renderLevel` 描述符是 **两个** `Matrix4f` + `GpuBufferSlice` + `Vector4f`（1.21.10 起是三个 Matrix4f）；`RenderTarget.blitToScreen()` 在（`FramebufferMixin` 延迟合成的挂点）；过滤仍挂 `AbstractTexture.setFilter/setClamp`（无 `GpuSampler`/`SamplerCache`）。
  - **输入/窗口/实体渲染是旧形态**（与 1.21.1 同侧）：无 `KeyEvent`/`CharacterEvent`/`MouseButtonEvent`/`InputWithModifiers` 记录（Screen 回调是 `(int,int,int)`/`(char,int)`/`(double,double,int)` 散装形参）；无 `KeyMapping.Category`（分类是字符串翻译键 `key.categories.muonium`）；`Window.getWindow()` 返回 long（`handle()` 是 1.21.9 改名）；`InputConstants.isKeyDown(long,int)`；`KeyboardHandler.keyPress(JIIII)V`、`MouseHandler.onPress(JIII)V`；`EntityRenderDispatcher.render(Entity,....)` 单段式仍在、无 `SubmitNodeCollector`/`extractEntity`，但 dispatcher render 已按调用形拆成四个重载（Entity/EntityRenderState × 8/9 参），裸名 `render` 选择器会被 remapper 钉到转发形（零注入点，Scanned 0 target(s) 崩，实测）；名牌/牵引绳下放进 renderer 内部的 `super.render`（基类 `EntityRenderer.render` 只画绳+名牌），火焰/阴影/碰撞箱仍在 dispatcher 侧读渲染态字段——透明/陀螺的 pose 括号挂 `LivingEntityRenderer.render(LivingEntityRenderState,…)` 的 HEAD…super.render（选择器全描述符钉死，实体身份走 `PlayerRenderState.id`），不能照抄 1.21.1 的 dispatcher translate/fire 锚点；`GameRenderer.getFov` 描述符尾是 `F`（float）但注入描述符要与本树实际一致；`Camera.getPosition()`/`getLookVector()`(Vector3f)/`getXRot()` 在（`position()` 也在，但本树统一取 get* 形态）；`Direction.getApproximateNearest(double,double,double)`；`GuiButton.onPress()` 无参。
- 禁止编造 API、猜签名或凭其他版本套用。新增/替换调用先核实本组合依赖源码或字节码（`dumpCompileClasspath` + javap）；同树现有调用、相邻版本、通用示例及本文快照都不能单独证明兼容。
- Mixin 描述符逐触点按本树反汇编确认；`WorldLyricsRenderTailMixin` 注入 `renderLevel` 用 1.21.8 的九参描述符，不能照抄 1.21.10 的十参。
- 查不到准确依据时明确“未确认”，不得猜替代 API、用空实现假装支持或通过升级依赖掩盖问题。

## 配置快照

| 项目 | 当前值/来源 |
| --- | --- |
| MC / 外壳 | `1.21.8`；`fabric/`（NeoForge 暂缺，单加载器树） |
| Java 目标/运行 / daemon | 21 / 21；`fabric/build.gradle` 与 `gradle.properties` |
| Fabric Loader 开发 / 下限 | `0.19.3` / `0.17.0`（fabric-api 0.136.x 要求 >=0.17.0） |
| Fabric API | `0.136.1+1.21.8` |
| FLK | `1.13.12+kotlin.2.4.0` |
| Kotlin 编译器 / 语言与 API / stdlib | `2.4.21` / `2.2` / `2.2.20`；仓库根 `gradle/kotlin-support.gradle` |
| Fabric 插件 | `fabric-loom 1.17.20` |
| ModernUI（可选） | 上游 `icyllis.modernui:ModernUI-Fabric:1.21.8-3.13.0.3`（maven.izzel.io）+ `modernui-core`/`Markflow` 3.13.0；3.13 API，面板源码在 `external/modernui/`（与 1.21.1 同代） |
| JDK | macOS `/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home` |

## 过渡车道的适配规则

- 使用 Mojang 官方名。Fabric 仍是 `loom.officialMojangMappings()` + 重映射。
- 树内混合：输入 mixin 走 1.21.1 形态；实体渲染 mixin 是混合形态（`SpinHeadMixin`/`TransparentEntityMixin`/`SpinEntityMixin` 挂 `LivingEntityRenderer.render` 的 state 形全描述符，括号 HEAD…super.render，身份走 `PlayerRenderState.id`）；GUI/GPU compat 走 1.21.10 形态（`FramebufferMixin`、`RenderTypeCreateAccessor`、`SkijaHost.compositeFrame`/`deferPostCompositeOverlay` 延迟合成、`LyricGlyphAtlasBridge` 的 setFilter+writeToTexture 过渡形状）。
- Java 目标 21；Kotlin 本树语言/API 2.2，共享 Kotlin 仍按 1.8。
- 可选 ModernUI 留 `external/modernui/`（上游 3.13 API，不是 mVUS 3.12）；拉不到构件用 `-PskipModernUi=true`。
- 此树必须参与共享改动的验收；单加载器是本车道的明确形态，不等于可跳过 `:fabric:test`。

## 验证

在本树目录（macOS）：

```bash
./gradlew :fabric:test
./gradlew :fabric:build
```

保留 `Port1218StaticTest`（钉住本树混合代际形状）、按键与渲染层守卫。GUI/输入变化实测字符输入、组合键、鼠标按住/释放、滚轮和 resize；世界渲染变化检查正确绘制阶段及光影兼容。游戏实测在真机（Windows 启动器 + 本机 runClient 均可）进行，构建通过 ≠ Mixin 注入生效。
