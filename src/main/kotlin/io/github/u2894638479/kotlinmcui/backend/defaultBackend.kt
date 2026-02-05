package io.github.u2894638479.kotlinmcui.backend

import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.VertexConsumer
import io.github.u2894638479.kotlinmcui.DslDataStore
import io.github.u2894638479.kotlinmcui.InternalBackend
import io.github.u2894638479.kotlinmcui.context.DslScaleContext
import io.github.u2894638479.kotlinmcui.context.scaled
import io.github.u2894638479.kotlinmcui.dslLogger
import io.github.u2894638479.kotlinmcui.functions.DslFunction
import io.github.u2894638479.kotlinmcui.glfw.EventModifier
import io.github.u2894638479.kotlinmcui.glfw.MouseButton
import io.github.u2894638479.kotlinmcui.image.ImageHolder
import io.github.u2894638479.kotlinmcui.image.ImageStrategy
import io.github.u2894638479.kotlinmcui.math.*
import io.github.u2894638479.kotlinmcui.text.DslFont
import io.github.u2894638479.kotlinmcui.text.DslGlyph
import io.github.u2894638479.kotlinmcui.text.DslRenderableChar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.font.glyphs.BakedGlyph
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.locale.Language
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvents
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO

@InternalBackend
val defaultBackend = object : DslBackend<GuiGraphics, Screen> {
    private val vanillaButton = object : Button(0,0,0,0,Component.empty(),{}, DEFAULT_NARRATION) {
        fun setStatus(rect: Rect, highlighted: Boolean, active: Boolean){
            height = rect.height.div(guiScale).pixelsOrElse { 0 }
            width = rect.width.div(guiScale).pixelsOrElse { 0 }
            x = rect.left.div(guiScale).pixelsOrElse { 0 }
            y = rect.top.div(guiScale).pixelsOrElse { 0 }
            isHovered = false
            isFocused = highlighted
            this.active = active
            Screen.BACKGROUND_LOCATION
        }
        override fun renderString(guiGraphics: GuiGraphics?, font: Font?, i: Int) {}
        override fun renderScrollingString(guiGraphics: GuiGraphics?, font: Font?, i: Int, j: Int) {}
        context(renderParam: GuiGraphics)
        fun render() = stack {
            if(width <= 0 || height <= 0) return@stack
            renderParam.pose().scale(guiScale.toFloat(),guiScale.toFloat(),1f)
            try { renderWidget(renderParam,0,0,0f) } catch (_: Exception) {}
        }
    }
    private val vanillaEditBox = object : EditBox(Minecraft.getInstance().font,0,0,0,0,Component.empty()) {
        override fun renderScrollingString(guiGraphics: GuiGraphics?, font: Font?, i: Int, j: Int) {}
        fun setStatus(rect: Rect, highlighted: Boolean) {
            height = rect.height.div(guiScale).pixelsOrElse { 0 }
            width = rect.width.div(guiScale).pixelsOrElse { 0 }
            x = rect.left.div(guiScale).pixelsOrElse { 0 }
            y = rect.top.div(guiScale).pixelsOrElse { 0 }
            isHovered = false
            isFocused = highlighted
            repeat(7) { tick() }
        }
        context(renderParam: GuiGraphics)
        fun render() = stack {
            if(width <= 0 || height <= 0) return@stack
            renderParam.pose().scale(guiScale.toFloat(),guiScale.toFloat(),1f)
            try { renderWidget(renderParam,0,0,0f) } catch (_: Exception) {}
        }
    }

    context(renderParam:GuiGraphics)
    override fun renderButton(rect: Rect, highlighted: Boolean, active: Boolean, color: Color) = withColor(color){
        vanillaButton.setStatus(rect,highlighted,active)
        vanillaButton.render()
    }

    context(renderParam: GuiGraphics)
    override fun renderEditBox(rect: Rect, highlighted: Boolean, color: Color) = withColor(color) {
        vanillaEditBox.setStatus(rect,highlighted)
        vanillaEditBox.render()
    }

    private fun VertexConsumer.color(color: Color) = color(color.rInt,color.gInt,color.bInt,color.aInt)
    context(renderParam:GuiGraphics)
    override fun fillRect(rect: Rect, color: Color) = 
        fillRectGradient(rect,color,color,color,color)

    context(renderParam: GuiGraphics)
    override fun fillRectGradient(rect: Rect, lt: Color, rt: Color, lb: Color, rb: Color) = rect.run {
        val vc = renderParam.bufferSource.getBuffer(RenderType.gui())
        val matrix = renderParam.pose().last().pose()
        val l = left.pixelsOrWarn<Float> { return@run }
        val t = top.pixelsOrWarn<Float> { return@run }
        val r = right.pixelsOrWarn<Float> { return@run }
        val b = bottom.pixelsOrWarn<Float> { return@run }
        vc.vertex(matrix,l,t,0f).color(lt).endVertex()
        vc.vertex(matrix,l,b,0f).color(lb).endVertex()
        vc.vertex(matrix,r,b,0f).color(rb).endVertex()
        vc.vertex(matrix,r,t,0f).color(rt).endVertex()
        renderParam.flush()
    }

    context(renderParam: GuiGraphics)
    override fun withScissor(rect: Rect, block: () -> Unit) {
        renderParam.flush()
        rect.run {
            renderParam.enableScissor(
                (left/guiScale).pixelsOrWarn<Int> { return },
                (top/guiScale).pixelsOrWarn<Int> { return },
                (right/guiScale).pixelsOrWarn<Int> { return },
                (bottom/guiScale).pixelsOrWarn<Int> { return },
            )
        }
        block()
        renderParam.flush()
        renderParam.disableScissor()
    }

    override fun translate(key: String,vararg args: Any): String? {
        return Language.getInstance().getOrDefault(key,null)?.let {
            if(args.isEmpty()) it else try {
                return String.format(it,*Array(args.size) { args[it].toString() })
            } catch (_: Exception) { it }
        }
    }

    override var clipBoard by Minecraft.getInstance().keyboardHandler::clipboard

    context(renderParam: GuiGraphics)
    private inline fun stack(block:()->Unit) {
        renderParam.pose().pushPose()
        try {
            block()
        } finally {
            renderParam.pose().popPose()
        }
    }

    val imageMap = mutableMapOf<File, ImageHolder>()
    private suspend fun loadImageFile(file: File): DynamicTexture? {
        val image = try {
            withContext(Dispatchers.IO) {
                ImageIO.read(file)
            }
        } catch (e: IOException){
            dslLogger.warn("load texture failed : $file")
            dslLogger.warn(e.toString())
            return null
        }
        val width = image.width
        val height = image.height
        val pixels = IntArray(width * height)
        image.getRGB(0, 0, width, height, pixels, 0, width)

        val native = NativeImage(width, height, false)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val argb = pixels[y * width + x]
                val abgr = Color.ofARGB(argb).abgrInt
                native.setPixelRGBA(x, y, abgr)
            }
        }
        return DynamicTexture(native)
    }
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun loadLocalImage(file: File): ImageHolder {
        imageMap[file]?.let { return it }
        if(!imageMap.containsKey(file)) {
            imageMap[file] = ImageHolder.empty
            scope.launch {
                val dynamic = loadImageFile(file) ?: return@launch
                val native = dynamic.pixels ?: return@launch
                Minecraft.getInstance().execute {
                    val location = Minecraft.getInstance().textureManager.register("dslimageid", dynamic)
                    imageMap[file] = ImageHolder(location.toString(), native.width.px, native.height.px)
                }
            }
        }
        return ImageHolder.empty
    }

    override fun forceLoadLocalImage(file: File): ImageHolder {
        imageMap.remove(file)
        return loadLocalImage(file)
    }

    context(renderParam: GuiGraphics)
    private var color: Color
        get() = RenderSystem.getShaderColor().let { Color(it[0], it[1], it[2], it[3]) }
        set(value) {
            renderParam.flush()
            RenderSystem.setShaderColor(value.rFloat, value.gFloat, value.bFloat,value.aFloat)
        }

    context(renderParam: GuiGraphics)
    private inline fun withColor(color: Color, block:()->Unit) {
        if(color == Color.WHITE) return block()
        try {
            this.color = color
            block()
        } finally {
            this.color = Color.WHITE
        }
    }

    context(renderParam: GuiGraphics)
    override fun renderImage(image: ImageHolder, rect: Rect, uv: Rect, color: Color) {
        if(image.isEmpty) return
        fun Measure.int() = pixelsOrElse { 0 }
//        fun Measure.float() = pixelsOrElse { 0f }
//        renderParam.blit(ResourceLocation(image.id),rect.left.int(),rect.top.int(),rect.width.int(),rect.height.int(),
//            uv.left.float(),uv.top.float(),uv.width.int(),uv.height.int(),image.width.int(),image.height.int())
        renderParam.innerBlit(
            ResourceLocation(image.id),
            rect.left.int(),rect.right.int(),rect.top.int(),rect.bottom.int(),0,
            (uv.left / image.width).toFloat(),(uv.right / image.width).toFloat(),
            (uv.top / image.height).toFloat(),(uv.bottom / image.height).toFloat(),
            color.rFloat,color.gFloat,color.bFloat,color.aFloat
        )
    }

    override fun playButtonSound() {
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f))
    }

    override fun narrate(string: String) {
        Minecraft.getInstance().narrator.sayNow(string.ifEmpty { return })
    }

    context(ctx: DslScaleContext,renderParam: GuiGraphics)
    override fun renderDefaultBackground(rect: Rect) {
        ImageStrategy.repeat(scale = ctx.scale).render(
            rect,
            ImageHolder(Screen.BACKGROUND_LOCATION.toString(), 32.px, 32.px),
            Color(0.25,0.25,0.25)
        )
    }

    override fun getFont(name: String?) = defaultFont

    val defaultFont = object : DslFont<GuiGraphics> {
        val font get() = Minecraft.getInstance().font
        val fontSet get() = font.getFontSet(Minecraft.DEFAULT_FONT)
        override val lineHeight get() = font.lineHeight.px
        override fun glyph(code: Int) = object : DslGlyph {
            val glyph = fontSet.getGlyphInfo(code,false)
            override val normalAdvance get() = glyph.advance.px
            override val boldOffset get() = glyph.boldOffset.px
            override val shadowOffset get() = glyph.shadowOffset.px
        }

        context(renderParam: GuiGraphics)
        private fun renderCharOnly(char: DslRenderableChar, glyph: BakedGlyph, x: Measure, y: Measure, color: Color, boldOffset: Measure) {
            if (char.code == ' '.code) return
            glyph.render(
                char.style.isItalic,
                x.pixelsOrWarn { return },
                y.pixelsOrWarn { return },
                renderParam.pose().last().pose(),
                renderParam.bufferSource.getBuffer(glyph.renderType(Font.DisplayMode.NORMAL)),
                color.rFloat,
                color.gFloat,
                color.bFloat,
                color.aFloat,
                LightTexture.FULL_BRIGHT
            )
            if(boldOffset != 0.px) renderCharOnly(char,glyph,x + boldOffset,y,color,0.px)
        }

        context(renderParam: GuiGraphics)
        private fun renderStrike(char: DslRenderableChar, dslGlyph: DslGlyph, x: Measure, y: Measure, color: Color) {
            if(!char.style.isStrikeThrough) return
            fillRect(
                Rect(
                    left = x - 1.px,
                    right = x + dslGlyph.advance(char.style),
                    top = y + 3.5.px,
                    bottom = y + 4.5.px
                ),color
            )
        }

        context(renderParam: GuiGraphics)
        private fun renderUnderline(char: DslRenderableChar, dslGlyph: DslGlyph, x: Measure, y: Measure, color: Color) {
            if(!char.style.isUnderlined) return
            fillRect(
                Rect(
                    left = x - 1.px,
                    right = x + dslGlyph.advance(char.style),
                    top = y + 8.px,
                    bottom = y + 9.px
                ),color
            )
        }

        context(renderParam: GuiGraphics)
        override fun renderChar(char: DslRenderableChar, x: Measure, y: Measure, effectLeft: Measure, effectRight: Measure) {
            val dslGlyph by lazy { glyph(char.code) }
            val glyph = if(char.style.isObfuscated) fontSet.getRandomGlyph(dslGlyph.glyph) else fontSet.getGlyph(char.code)
            val scale = (char.size / lineHeight).toFloat()
            stack {
                renderParam.pose().scale(scale,scale,1f)
                val x = x / scale
                val y = y / scale
                if(char.style.isShadowed) {
                    val xShadow = x + dslGlyph.shadowOffset
                    val yShadow = y + dslGlyph.shadowOffset
                    val colorShadow = char.color.change(
                        r = char.color.rInt / 4,
                        g = char.color.gInt / 4,
                        b = char.color.bInt / 4
                    )
                    renderCharOnly(char,glyph,xShadow,yShadow,colorShadow,if(char.style.isBold) dslGlyph.boldOffset else 0.px)
                    renderCharOnly(char,glyph,x,y,char.color,if(char.style.isBold) dslGlyph.boldOffset else 0.px)
                    renderUnderline(char,dslGlyph,xShadow,yShadow,colorShadow)
                    renderUnderline(char,dslGlyph,x,y,char.color)
                    renderStrike(char,dslGlyph,xShadow,yShadow,colorShadow)
                    renderStrike(char,dslGlyph,x,y,char.color)
                } else {
                    renderCharOnly(char,glyph,x,y,char.color,if(char.style.isBold) dslGlyph.boldOffset else 0.px)
                    renderUnderline(char,dslGlyph,x,y,char.color)
                    renderStrike(char,dslGlyph,x,y,char.color)
                }
            }
//            renderParam.flushIfUnmanaged()
        }
    }

    override val guiScale get() = Minecraft.getInstance().window.guiScale
    override val isInWorld get() = Minecraft.getInstance().level != null
    override fun create(dslFunction: DslFunction): DslBackendScreenHolder<Screen> = object: DslBackendScreenHolder<Screen> {
        override fun show(){
            Minecraft.getInstance().execute {
                Minecraft.getInstance().setScreen(screen)
            }
        }
        override val screen = object : Screen(Component.literal("DSL Screen")), DslScaleContext {
            override val scale get() = guiScale
            val parent = Minecraft.getInstance().screen
            fun DslBackend<*,*>.createDataStore() = DslDataStore(this, {
                Minecraft.getInstance().execute { Minecraft.getInstance().setScreen(parent) }
            },dslFunction)
            val dslScreen = createDataStore().dslScreen
            override fun onClose() = dslScreen.close()
            override fun keyPressed(i: Int, j: Int, k: Int): Boolean {
                if(dslScreen.run { keyDown(i, j, EventModifier(k)) }) return true
                return super.keyPressed(i, j, k)
            }
            override fun keyReleased(i: Int, j: Int, k: Int): Boolean {
                if(dslScreen.run { keyUp(i, j, EventModifier(k)) }) return true
                return super.keyReleased(i, j, k)
            }
            override fun mouseClicked(d: Double, e: Double, i: Int): Boolean {
                if(dslScreen.run { mouseDown(Position(d.scaled, e.scaled), MouseButton.from(i)) }) return true
                return super.mouseClicked(d, e, i)
            }
            override fun mouseReleased(d: Double, e: Double, i: Int): Boolean {
                if(dslScreen.run { mouseUp(Position(d.scaled, e.scaled), MouseButton.from(i)) }) return true
                return super.mouseReleased(d, e, i)
            }
            override fun mouseMoved(d: Double, e: Double) {
                dslScreen.run { mouseMove(Position(d.scaled, e.scaled)) }
                super.mouseMoved(d, e)
            }
            override fun mouseScrolled(d: Double, e: Double, f: Double): Boolean {
                val remain = dslScreen.run { mouseScroll(Position(d.scaled, e.scaled), f) }
                if(remain == 0.0) return true
                return super.mouseScrolled(d, e, remain)
            }

            override fun charTyped(c: Char, i: Int): Boolean {
                if(dslScreen.run { charTyped(c, EventModifier(i)) }) return true
                return super.charTyped(c, i)
            }
            override fun render(guiGraphics: GuiGraphics, i: Int, j: Int, f: Float) {
                context(guiGraphics) {
                    stack {
                        guiGraphics.pose().scale(1/guiScale.toFloat(),1/guiScale.toFloat(),1f)
                        dslScreen.run { render(Position(i.scaled, j.scaled)) }
                    }
                }
            }

            override fun init() {
                super.init()
                dslScreen.init(Rect().also {
                    it.width = width.px * guiScale
                    it.height = height.px * guiScale
                })
            }
        }
    }
}