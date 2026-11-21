package com.muoniumplayer;

import com.muoniumplayer.testsupport.ProjectPaths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 1.21.8 版本工程的结构性不变量。纯文本扫描，不需要 GL、不需要启动客户端。
 *
 * <p>钉的都是「改回去也照样编译通过、但运行期才出问题」的地方：
 * <ol>
 *   <li>版本声明一致（minecraft / fabric-api / Java 21）—— 只改一半会得到「能编译但装不上」；</li>
 *   <li>共享源码里不许出现任何加载器 API；</li>
 *   <li>映射统一在 Mojang 官方名（mojmap）；</li>
 *   <li>1.21.8 过渡代际的混合形状不许被「顺手统一」成任一邻树的形态；</li>
 *   <li>access widener 保持无条目；</li>
 *   <li>SkijaHost 依赖的渲染入口不许被「顺手降级」掉。</li>
 * </ol>
 *
 * <p><b>1.21.8 是过渡代际，混合体：</b>GPU/GUI 栈已新（{@code GlStateManager} 在
 * {@code blaze3d.opengl}、{@code GuiRenderState}/{@code GuiRenderer} 延迟批处理、
 * {@code submitBlit}/{@code submitColoredRectangle}、{@code DynamicTexture} 走
 * {@code GpuDevice.createTexture}、{@code CommandEncoder.writeToTexture}、
 * {@code GameRenderer.getFov} 返回 float），但输入/窗口/实体渲染仍是旧形
 * （无 {@code KeyEvent}/{@code CharacterEvent}/{@code MouseButtonEvent} 记录、
 * 无 {@code KeyMapping.Category}、{@code Window.getWindow()} 返回 long、
 * {@code InputConstants.isKeyDown(long,int)}、{@code EntityRenderDispatcher.render(...)}
 * 单段式仍在、无 {@code SubmitNodeCollector}/{@code extractEntity}）。
 * 这些「半新半旧」正是 1.21.8 与两个邻树都不能互相照抄的原因，也是本测试要钉住的形状。</p>
 */
class Port1218StaticTest {

    private static final Path ROOT = ProjectPaths.versionRoot();
    private static final Path FABRIC_RESOURCES =
            ProjectPaths.fabric().resolve(Paths.get("src", "main", "resources"));
    private static final Path GUI_SCREEN =
            ProjectPaths.sourceFile(Paths.get("com", "muoniumplayer", "compat", "mc", "GuiScreen.java"));
    private static final Path FFMPEG_NOTICE =
            ProjectPaths.sourceFile(Paths.get("com", "muoniumplayer", "core", "screens", "GuiFfmpegNotice.java"));
    private static final Path SKIJA_HOST =
            ProjectPaths.sourceFile(Paths.get("com", "muoniumplayer", "render", "skija", "SkijaHost.java"));
    private static final Path ATLAS_BRIDGE =
            ProjectPaths.sourceFile(Paths.get("com", "muoniumplayer", "compat", "mc", "LyricGlyphAtlasBridge.java"));
    private static final Path CLIENT =
            ProjectPaths.sourceFile(Paths.get("com", "muoniumplayer", "MuoniumPlayerClient.java"));

    @Test
    @DisplayName("版本声明一致：1.21.8 / fabric-api +1.21.8 / Java 21")
    void 版本声明一致() throws IOException {
        String props = read(ROOT.resolve("gradle.properties"));
        assertTrue(props.contains("minecraft_version=1.21.8"), "gradle.properties 的 minecraft_version 不是 1.21.8");
        assertTrue(props.contains("+1.21.8"), "fabric_version 必须是 <ver>+1.21.8");

        String build = read(ROOT.resolve("fabric").resolve("build.gradle"));
        assertTrue(build.contains("def targetJavaVersion = 21"), "fabric 侧的目标运行时必须是 Java 21");
        assertTrue(read(FABRIC_RESOURCES.resolve("fabric.mod.json")).contains("\">=21\""),
                "fabric.mod.json 的 java 依赖必须是 >=21");
        assertTrue(read(FABRIC_RESOURCES.resolve("muonium.mixins.json")).contains("JAVA_21"),
                "mixin compatibilityLevel 必须是 JAVA_21");
    }

    @Test
    @DisplayName("共享源码里没有任何加载器 API")
    void 共享源码与加载器无关() throws IOException {
        Stream<Path> files = ProjectPaths.allSourceFiles().stream();
        List<Path> offenders = files
                .filter(ProjectPaths::isSourceFile)
                .filter(Port1218StaticTest::mentionsLoaderApi)
                .toList();
        assertTrue(offenders.isEmpty(),
                "共享源码引用了加载器 API，另一个加载器会编不过：" + offenders);
    }

    @Test
    @DisplayName("Fabric 外壳在，modId 一致")
    void Fabric外壳齐全() throws IOException {
        String fabricMeta = read(FABRIC_RESOURCES.resolve("fabric.mod.json"));
        assertTrue(fabricMeta.contains("\"id\": \"muonium\""), "fabric.mod.json 的 modId 必须是 muonium");
        assertTrue(fabricMeta.contains("com.muoniumplayer.MuoniumPlayerMod"),
                "fabric.mod.json 的 client entrypoint 必须指向 Fabric 外壳类");
        assertTrue(Files.exists(ProjectPaths.fabric().resolve(Paths.get(
                        "src", "main", "java", "com", "muoniumplayer", "MuoniumPlayerMod.java"))),
                "Fabric 入口类不存在");
    }

    @Test
    @DisplayName("映射统一在 Mojang 官方名，yarn 不再参与构建")
    void 映射统一mojmap() throws IOException {
        assertTrue(read(ProjectPaths.fabric().resolve("build.gradle")).contains("loom.officialMojangMappings()"),
                "Fabric 侧必须显式用官方名映射");
        assertFalse(read(ROOT.resolve("gradle.properties")).contains("yarn_mappings="),
                "gradle.properties 里不该再有 yarn_mappings（已统一 mojmap）");
        assertFalse(read(ProjectPaths.fabric().resolve("build.gradle")).contains("net.fabricmc:yarn"),
                "fabric/build.gradle 里不该再依赖 yarn");
    }

    @Test
    @DisplayName("输入/窗口/实体渲染保持 1.21.8 的旧形态（记录类型是 1.21.9+ 的）")
    void 输入与实体保持旧形态() throws IOException {
        String guiScreen = stripComments(read(GUI_SCREEN));
        // 1.21.8 没有 KeyEvent / CharacterEvent / MouseButtonEvent 记录（1.21.9 起才有），
        // Screen 回调必须是散装形参；写成记录类型编译期就会断。
        assertFalse(guiScreen.contains("net.minecraft.client.input.KeyEvent"),
                "1.21.8 的 Screen#keyPressed 还是 (int,int,int) 散装形参，没有 KeyEvent 记录");
        assertFalse(guiScreen.contains("net.minecraft.client.input.MouseButtonEvent"),
                "1.21.8 的鼠标回调还是 (double,double,int)，没有 MouseButtonEvent 记录");
        assertFalse(guiScreen.contains("net.minecraft.client.input.CharacterEvent"),
                "1.21.8 的 charTyped 还是 (char,int)，没有 CharacterEvent 记录");
        assertTrue(guiScreen.contains("keyPressed(int keyCode, int scanCode, int modifiers)"),
                "1.21.8 的 Screen#keyPressed 必须是 (int,int,int) 散装形参");
        assertTrue(guiScreen.contains("mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount)"),
                "GuiEventListener#mouseScrolled 仍是水平/垂直两分量的四参");

        String notice = stripComments(read(FFMPEG_NOTICE));
        assertTrue(notice.contains("FfmpegNoticeOverlay"),
                "提示界面必须由 Skija 浮层渲染（FfmpegNoticeOverlay），不要退回原版控件自绘");
        assertFalse(notice.contains("getModelViewStack()"),
                "GUI 文字的变换只能压到 GuiGraphics 的 pose 栈上，模型视图栈对它无效");

        // KeyMapping 分类：1.21.8 仍是字符串翻译键（1.21.9 起换 KeyMapping.Category 记录）。
        String client = stripComments(read(CLIENT));
        assertTrue(client.contains("public static final String KEY_CATEGORY = \"key.categories.muonium\""),
                "1.21.8 的 KEY_CATEGORY 必须是字符串翻译键");
        assertFalse(client.contains("KeyMapping.Category"),
                "1.21.8 没有 KeyMapping.Category 记录（1.21.9 起才有）");
        // lang 在树根 src/main/resources（fabric/build.gradle 的 resources.srcDirs 挂了
        // rootProject.file('src/main/resources')，与 1.21.1/1.21.10 同布局），不在 fabric 外壳下。
        assertTrue(read(ROOT.resolve(Paths.get("src", "main", "resources"))
                        .resolve("assets").resolve("muonium").resolve("lang").resolve("en_us.json"))
                .contains("\"key.categories.muonium\""),
                "lang 的分类键必须是 key.categories.muonium（字符串分类时代）");

        // 窗口句柄：1.21.8 的 Window 公开口是 getWindow()（1.21.9 才改 handle()）。
        String display = stripComments(read(ProjectPaths.sourceFile(Paths.get(
                "com", "muoniumplayer", "compat", "lwjgl2", "Display.java"))));
        assertTrue(display.contains("window.getWindow()"),
                "1.21.8 的 Window#getWindow() 还没改名 handle()");
        assertFalse(display.contains("window.handle()"),
                "handle() 是 1.21.9 的改名，1.21.8 编不过");

        // 实体渲染：1.21.8 仍是单段式 render(...)（extract/submit 两段式与
        // SubmitNodeCollector 都不存在），Spin 头部混入要走 SpinHeadMixin。
        String trans = stripComments(read(ProjectPaths.sourceFile(Paths.get(
                "com", "muoniumplayer", "mixin", "TransparentEntityMixin.java"))));
        assertTrue(trans.contains("method = \"render\""),
                "1.21.8 的 EntityRenderDispatcher 仍是单段式 render(...) 入口");
        assertFalse(trans.contains("SubmitNodeCollector") || trans.contains("extractEntity"),
                "1.21.8 没有 SubmitNodeCollector / extractEntity（1.21.9+ 的两段式）");
        assertTrue(Files.exists(ProjectPaths.sourceFile(Paths.get(
                        "com", "muoniumplayer", "mixin", "SpinHeadMixin.java"))),
                "SpinHeadMixin 是单段式世代的头部混入（extract 代际才并进 SpinEntityMixin）");
        String mixinsJson = read(FABRIC_RESOURCES.resolve("muonium.mixins.json"));
        assertTrue(mixinsJson.contains("SpinHeadMixin"), "mixins.json 必须注册 SpinHeadMixin");
        // 模型动画：1.21.8 的 setupAnim 已是 render-state 单参（六参是 1.21.1 及
        // 更早的形态），SpinHeadMixin 的 @Redirect 目标描述符必须钉单参形态。
        String spinHead = stripComments(read(ProjectPaths.sourceFile(Paths.get(
                "com", "muoniumplayer", "mixin", "SpinHeadMixin.java"))));
        assertTrue(spinHead.contains(
                        "setupAnim(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;)V"),
                "1.21.8 的 setupAnim 是 render-state 单参，@Redirect 目标必须钉该描述符");
        assertFalse(spinHead.contains("setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V"),
                "六参 setupAnim 是 1.21.1 及更早的形态，1.21.8 编不过");
        assertTrue(spinHead.contains(
                        "render(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;"),
                "render 有同名桥接，@Redirect 方法选择器必须带 LivingEntityRenderState 擦除描述符");

        // FOV 脉冲：1.21.8 的 GameRenderer#getFov 已返回 float（float 化早于输入记录）。
        String fov = stripComments(read(ProjectPaths.sourceFile(Paths.get(
                "com", "muoniumplayer", "mixin", "CineFovMixin.java"))));
        assertTrue(fov.contains("getFov(Lnet/minecraft/client/Camera;FZ)F"),
                "1.21.8 的 getFov 返回 float（描述符尾 F）");
        assertTrue(fov.contains("CallbackInfoReturnable<Float>"),
                "1.21.8 的 getFov 返回 float，CIR 泛型必须是 Float");

        // 按键/鼠标回调注入点：1.21.8 是 keyPress(JIIII)V / onPress(JIII)V 旧描述符。
        String key = stripComments(read(ProjectPaths.sourceFile(Paths.get(
                "com", "muoniumplayer", "mixin", "CineKeyboardMixin.java"))));
        assertTrue(key.contains("keyPress(JIIII)V"),
                "1.21.8 的 KeyboardHandler#keyPress 是 (JIIII) 五参旧形");
        String mouse = stripComments(read(ProjectPaths.sourceFile(Paths.get(
                "com", "muoniumplayer", "mixin", "CineMouseMixin.java"))));
        assertTrue(mouse.contains("onPress(JIII)V"),
                "1.21.8 的 MouseHandler#onPress 是 (JIII) 四参旧形");

        // 世界歌词时机 mixin：1.21.8 的 renderLevel 描述符是两个 Matrix4f（1.21.10 起三个）。
        String tail = stripComments(read(ProjectPaths.fabric().resolve(Paths.get(
                "src", "main", "java", "com", "muoniumplayer", "mixin", "WorldLyricsRenderTailMixin.java"))));
        assertTrue(tail.contains("Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"),
                "1.21.8 的 renderLevel 是两个 Matrix4f + GpuBufferSlice，描述符必须精确");
    }

    @Test
    @DisplayName("GUI/纹理/GPU 栈保持 1.21.8 的新形态（延迟批处理 + GpuDevice 纹理）")
    void GpuGui栈保持新形态() throws IOException {
        String guiScreen = stripComments(read(GUI_SCREEN));
        // GuiGraphics.drawString 在 1.21.8 已返回 void（advance 时代结束），FontRenderer 自算宽度。
        String font = stripComments(read(ProjectPaths.sourceFile(Paths.get(
                "com", "muoniumplayer", "compat", "mc", "FontRenderer.java"))));
        assertFalse(font.contains("return currentContext.drawString"),
                "1.21.8 的 GuiGraphics.drawString 返回 void，不许再当 int 用");
        assertTrue(font.contains("return x + getStringWidth(text)"),
                "drawString 返回 void 后，推进量要自己按文字宽度算");

        // 按钮回调：1.21.8 的 onPress() 无参（InputWithModifiers 是 1.21.9+）。
        String button = stripComments(read(ProjectPaths.sourceFile(Paths.get(
                "com", "muoniumplayer", "compat", "mc", "GuiButton.java"))));
        assertTrue(button.contains("public void onPress()"),
                "1.21.8 的 AbstractButton#onPress() 无参");
        assertFalse(button.contains("InputWithModifiers"),
                "InputWithModifiers 是 1.21.9+ 的记录，1.21.8 编不过");
    }

    @Test
    @DisplayName("access widener 保持无条目")
    void accessWidener保持无条目() throws IOException {
        for (String line : read(FABRIC_RESOURCES.resolve("muoniumplayer.accesswidener")).split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("accessWidener")) {
                continue;
            }
            throw new AssertionError("access widener 又有条目了: " + trimmed
                    + "（Skija 渲染层不碰 MC 的纹理对象内部字段；能不加就别加）");
        }
    }

    @Test
    @DisplayName("SkijaHost 依赖的 1.21.8 渲染入口不许被顺手降级")
    void 宿主层依赖项未被降级() throws IOException {
        String host = stripComments(read(SKIJA_HOST));
        // 1.21.8 的 GlStateManager 已在 blaze3d.opengl 包下（1.21.1 才在 platform）。
        assertTrue(host.contains("com.mojang.blaze3d.opengl.GlStateManager"),
                "1.21.8 的 GlStateManager 在 blaze3d.opengl 包下（1.21.1 才在 platform）");
        // BufferUploader 在 1.21.8 已删除（顶点缓冲绑定归 GlCommandEncoder），不许再调 reset()。
        assertFalse(host.contains("BufferUploader.reset()"),
                "BufferUploader 在 1.21.8 已不存在，reset() 编不过");
        // unpack 布局复位仍是字形图集不错位的前提（症状：文字缺大半字符）。
        assertTrue(host.contains("GL_UNPACK_ROW_LENGTH") && host.contains("GL_UNPACK_SKIP_ROWS"),
                "NativeImage 上传改了 unpack 布局且不还原，不复位会让 Skia 字形图集错位");
        // 延迟合成：离屏图由 FramebufferMixin 在 blitToScreen 出口贴上（GuiRenderState 批处理时代）。
        assertTrue(host.contains("compositeFrame"),
                "1.21.8 的 GUI 是延迟批处理，合成走 compositeFrame（帧末 blitToScreen 出口）");
        assertTrue(Files.exists(ProjectPaths.sourceFile(Paths.get(
                        "com", "muoniumplayer", "mixin", "FramebufferMixin.java"))),
                "FramebufferMixin 是延迟合成的挂点（RenderTarget#blitToScreen 出口）");
    }

    @Test
    @DisplayName("字形图集走 1.21.8 的过渡形状：setFilter 过滤 + writeToTexture 上传 + setPixelABGR")
    void 图集桥保持过渡形状() throws IOException {
        String bridge = stripComments(read(ATLAS_BRIDGE));
        // 过滤：1.21.8 还没有 GpuSampler/SamplerCache，AbstractTexture.setFilter 仍在。
        assertFalse(bridge.contains("getSamplerCache"),
                "1.21.8 没有 SamplerCache（26.x 才有）");
        assertTrue(bridge.contains("setFilter"),
                "1.21.8 的过滤仍走 AbstractTexture.setFilter，必须重写拦下 NEAREST");
        // 上传：NativeImage.upload 与 setPixelRGBA 在 1.21.8 已删，走 writeToTexture + setPixelABGR。
        assertFalse(bridge.contains(".upload("),
                "1.21.8 已无 NativeImage.upload（mappings 核对），子矩形上传必须走 writeToTexture");
        assertTrue(bridge.contains("writeToTexture"),
                "子矩形上传必须走 GlCommandEncoder.writeToTexture");
        assertFalse(bridge.contains("setPixelRGBA"),
                "1.21.8 的 NativeImage 只有 setPixelABGR（mappings 核对）");
        assertTrue(bridge.contains("setPixelABGR"),
                "像素写入必须走 setPixelABGR");
    }

    @Test
    @DisplayName("产物名带 Minecraft 版本，且 buildAll 会汇总到 dist/")
    void 产物名带版本且汇总到统一目录() throws IOException {
        String build = read(ROOT.resolve("fabric").resolve("build.gradle"));
        assertTrue(build.contains(
                        "archivesName = \"${project.archives_base_name}-${project.minecraft_version}-fabric\""),
                "fabric 侧的 archivesName 必须是 <base>-<minecraft_version>-fabric");

        String aggregate = read(ROOT.resolve("build.gradle"));
        assertTrue(aggregate.contains("tasks.register('collectDist', Copy)"),
                "聚合工程里必须有 collectDist 任务，负责把产物汇总到仓库根的 dist/");
        assertTrue(aggregate.contains("rootProject.file('../dist')"),
                "dist/ 必须落在仓库根，各版本共用一个目录");
        assertTrue(aggregate.contains("'collectDist'"), "buildAll 必须带上 collectDist");
        assertFalse(stripComments(aggregate).contains("include '*.jar'"),
                "collectDist 不能用 include '*.jar' 全收，会把源码包与旧名产物一起发出去");
    }

    @Test
    @DisplayName("剪贴板走原版通道，不许用 AWT")
    void 剪贴板不走AWT() throws IOException {
        // Minecraft 在 net.minecraft.client.main.Main 的静态初始化里写死了
        // System.setProperty("java.awt.headless", "true")。
        // headless 下 Toolkit#getSystemClipboard() 必抛 HeadlessException，
        // 复制 / 粘贴会静默退化成空操作。正确通道是原版 Minecraft#keyboardHandler。
        Path keyboardUtils = ProjectPaths.sourceFile(Paths.get(
                "com", "muoniumplayer", "core", "utils", "KeyboardUtils.java"));
        String body = stripComments(read(keyboardUtils));
        assertTrue(body.contains("keyboardHandler.setClipboard("),
                "写剪贴板必须走原版的 KeyboardHandler#setClipboard");
        assertTrue(body.contains("keyboardHandler.getClipboard()"),
                "读剪贴板必须走原版的 KeyboardHandler#getClipboard");
        assertTrue(body.contains("isSameThread()"),
                "GLFW 剪贴板函数只能在渲染线程调用，必须先判线程");

        Stream<Path> files = ProjectPaths.allSourceFiles().stream();
        List<Path> offenders = files
                .filter(ProjectPaths::isSourceFile)
                .filter(Port1218StaticTest::usesAwtClipboard)
                .toList();
        assertTrue(offenders.isEmpty(),
                "共享源码里还有地方在用 AWT 剪贴板，headless 下必然是空操作：" + offenders);
    }

    private static boolean usesAwtClipboard(Path file) {
        try {
            String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            String body = file.toString().endsWith(".java") ? stripComments(text) : text;
            return body.contains("getSystemClipboard") || body.contains("StringSelection");
        } catch (IOException e) {
            throw new AssertionError("读不了 " + file, e);
        }
    }

    private static boolean mentionsLoaderApi(Path file) {
        try {
            String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            String body = file.toString().endsWith(".java") ? stripComments(text) : text;
            return body.contains("net.fabricmc") || body.contains("net.neoforged");
        } catch (IOException e) {
            throw new AssertionError("读不了 " + file, e);
        }
    }

    private static String read(Path path) throws IOException {
        assertTrue(Files.exists(path), "文件不存在: " + path);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    /** 注释里刻意保留了旧写法作为移植说明，扫描前先去掉。 */
    private static String stripComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }
}
