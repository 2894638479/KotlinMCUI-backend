package io.github.u2894638479.kotlinmcuibackend

import com.mojang.blaze3d.vertex.VertexConsumer
import io.github.u2894638479.kotlinmcui.backend.DslBackendRenderer
import io.github.u2894638479.kotlinmcui.context.DslScaleContext
import io.github.u2894638479.kotlinmcui.context.scaled
import io.github.u2894638479.kotlinmcui.image.ImageHolder
import io.github.u2894638479.kotlinmcui.image.ImageStrategy
import io.github.u2894638479.kotlinmcui.math.Color
import io.github.u2894638479.kotlinmcui.math.Measure
import io.github.u2894638479.kotlinmcui.math.px
import io.github.u2894638479.kotlinmcui.math.rect.*
import io.github.u2894638479.kotlinmcui.math.transform.Transform
import io.github.u2894638479.kotlinmcui.text.DslFont
import io.github.u2894638479.kotlinmcui.text.DslGlyph
import io.github.u2894638479.kotlinmcui.text.DslRenderableChar
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.font.glyphs.BakedGlyph
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.state.gui.BlitRenderState
import net.minecraft.client.renderer.state.gui.GlyphRenderState
import net.minecraft.client.renderer.state.gui.GuiElementRenderState
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FontDescription
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.resources.Identifier
import net.minecraft.util.RandomSource
import org.joml.Matrix3x2f
import kotlin.math.roundToInt

internal object Renderer: DslBackendRenderer<GuiGraphics> {
    override val guiScale get() = Minecraft.getInstance().window.guiScale.toDouble()
    context(renderParam: GuiGraphics)
    override fun flush() = renderParam.nextStratum()
    context(renderParam:GuiGraphics, ctx: DslScaleContext)
    override fun renderButton(rect: Rect, highlighted: Boolean, active: Boolean, color: Color) {
        if(rect.isEmpty) return
        val imageId = "minecraft:textures/gui/sprites/widget/" +
                (if(active) "button" else "slider") +
                if(highlighted) "_highlighted.png" else ".png"
        val image = ImageHolder(imageId,200.px,20.px)
        val uvOuter = Rect(0.px,0.px,200.px,20.px)
        ImageStrategy.nineSlice(uvOuter,uvOuter.expand(-3.px),ctx.scale).render(rect,image,color)
    }

    private fun VertexConsumer.color(color: Color) = setColor(color.rInt,color.gInt,color.bInt,color.aInt)

    context(renderParam:GuiGraphics)
    override fun fillRect(rect: Rect, color: Color) = fillRectGradient(rect,color,color,color,color)

    context(renderParam: GuiGraphics)
    override fun fillRectGradient(rect: Rect, lt: Color, rt: Color, lb: Color, rb: Color) {
        renderParam.guiRenderState.addGuiElement(object : GuiElementRenderState {
            val pose = Matrix3x2f(renderParam.pose())
            val rect = rect.toFloat()
            val scissor = renderParam.scissorStack.peek()
            override fun buildVertices(vertexConsumer: VertexConsumer) {
                val rect = this.rect
                vertexConsumer.addVertexWith2DPose(pose, rect.left,rect.top).color(lt)
                vertexConsumer.addVertexWith2DPose(pose, rect.left,rect.bottom).color(lb)
                vertexConsumer.addVertexWith2DPose(pose, rect.right,rect.bottom).color(rb)
                vertexConsumer.addVertexWith2DPose(pose, rect.right,rect.top).color(rt)
            }
            override fun pipeline() = RenderPipelines.GUI
            override fun textureSetup() = TextureSetup.noTexture()
            override fun scissorArea() = scissor
            override fun bounds(): ScreenRectangle? {
                val rect = ScreenRectangle(this.rect.left.toInt(),this.rect.top.toInt(),this.rect.width.toInt(),this.rect.height.toInt()).transformMaxBounds(pose)
                return rect.intersection(scissor ?: return rect)
            }
        })
    }

    context(renderParam: GuiGraphics, ctx: DslScaleContext)
    override fun renderContainer(rect: Rect) {
        val image = ImageHolder("minecraft:textures/gui/container/blast_furnace.png",256.px,256.px)
        ImageStrategy.nineSlice(Rect(0.px,0.px,176.px,166.px),Rect(3.px,3.px,173.px,163.px),ctx.scale).render(rect,image,Color.WHITE)
        ImageStrategy.repeatUV(Rect(4.px,4.px,52.px,78.px),ctx.scale).render(rect.expand(-4.scaled),image,Color.WHITE)
    }

    context(renderParam: GuiGraphics, ctx: DslScaleContext)
    override fun renderSlot(rect: Rect) = ImageStrategy.nineSlice(
        Rect(7.px,141.px,25.px,159.px),Rect(8.px,142.px,24.px,158.px),ctx.scale
    ).render(rect,ImageHolder("minecraft:textures/gui/container/inventory.png",256.px,256.px),Color.WHITE)

    context(renderParam: GuiGraphics, ctx: DslScaleContext)
    override fun renderTooltip(rect: Rect) {
        stack {
            renderParam.pose().scale(ctx.scale.toFloat(),ctx.scale.toFloat())
            val rect = rect.div(ctx.scale).toInt().ifEmpty { return }
            TooltipRenderUtil.extractTooltipBackground(renderParam,rect.left,rect.top,rect.width,rect.height,null)
        }
    }

    context(renderParam: GuiGraphics, ctx: DslScaleContext)
    override fun renderItem(rect: Rect, item: String, count:Int, damage:Double?, enchanted: Boolean) {
        val item = BuiltInRegistries.ITEM.getOptional(Identifier.tryParse(item))
        if(!item.isPresent) renderImage(ImageHolder("missing", 16.px, 16.px),rect,Rect(0.px,0.px,16.px,16.px),Color.WHITE)
        else stack {
            val itemStack = try {
                item.get().defaultInstance
            } catch (_: Exception) { return }
            itemStack.count = count
            if(damage != null) itemStack.damageValue = (damage * itemStack.maxDamage).roundToInt()
            if(enchanted) itemStack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)

            val rect = rect.toFloat().ifEmpty { return@stack }
            renderParam.pose().translate(rect.left,rect.top)
            renderParam.pose().scale(rect.width / 16f,rect.height / 16f)
            renderParam.item(itemStack, 0, 0)
            renderParam.itemDecorations(Minecraft.getInstance().font,itemStack,0,0)
        }
    }

    context(renderParam: GuiGraphics)
    override fun withScissor(rect: Rect, block: () -> Unit) {
        flush()
        rect.toInt().run {
            renderParam.enableScissor(left,top,right,bottom)
        }
        try {
            if(renderParam.scissorStack.peek()?.run { width <= 0 || height <= 0 } == true) return
            block()
            flush()
        } finally {
            renderParam.disableScissor()
        }
    }

    context(renderParam: GuiGraphics)
    override fun withTransform(transform: Transform, block: () -> Unit) {
        renderParam.pose().pushMatrix()
        renderParam.pose().mul(
            transform.run {
                Matrix3x2f(m00,m10, m01,m11, m02,m12)
            }
        )
        try {
            block()
        } finally {
            renderParam.pose().popMatrix()
        }
    }

    context(renderParam: GuiGraphics)
    private inline fun stack(block:()->Unit) {
        renderParam.pose().pushMatrix()
        try {
            block()
        } finally {
            renderParam.pose().popMatrix()
        }
    }

    context(renderParam: GuiGraphics)
    override fun renderImage(image: ImageHolder, rect: Rect, uv: Rect, color: Color) {
        if(image.isEmpty) return
        val rectI = rect.toInt().ifEmpty { return }
        val location = Identifier.tryParse(image.id) ?: return
        val texture = Minecraft.getInstance().textureManager.getTexture(location)
        val pose = Matrix3x2f(renderParam.pose())
        renderParam.guiRenderState.addGuiElement(
            BlitRenderState(
                RenderPipelines.GUI_TEXTURED,
                TextureSetup.singleTexture(texture.textureView, texture.sampler),
                pose,
                rectI.left,rectI.top,rectI.right,rectI.bottom,
                uv.left.div(image.width).toFloat(),
                uv.right.div(image.width).toFloat(),
                uv.top.div(image.height).toFloat(),
                uv.bottom.div(image.height).toFloat(),
                color.argbInt,
                renderParam.scissorStack.peek()
            )
        )
    }

    val sc = object: Screen(Component.literal("")){
        init { init(0,0) }
    }
    context(ctx: DslScaleContext,renderParam: GuiGraphics)
    override fun renderDefaultBackground(rect: Rect) {
        sc.width = rect.width.pixelsOrElse { return }
        sc.height = rect.height.pixelsOrElse { return }
        try {
            sc.extractBackground(renderParam,0,0,
                ((System.nanoTime() % 50_000_000) / 50_000_000.0).toFloat())
        } catch (e: IllegalStateException) {
            if(e.message != "Can only blur once per frame") throw e
        }
    }

    override fun getFont(name: String?) = defaultFont

    val defaultFont = object : DslFont<GuiGraphics> {
        val font get() = Minecraft.getInstance().font
        val fontSet get() = font.getGlyphSource(FontDescription.DEFAULT)
        override val lineHeight get() = font.lineHeight.px
        override fun glyph(code: Int) = object : DslGlyph {
            val bakedGlyph = fontSet.getGlyph(code)
            val glyph get()= bakedGlyph.info()
            override val normalAdvance get() = glyph.advance.px
            override val boldOffset get() = glyph.boldOffset.px
            override val shadowOffset get() = glyph.shadowOffset.px
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
        private fun renderCharOnly(char: DslRenderableChar, glyph: BakedGlyph, x: Measure, y: Measure, color: Color, boldOffset: Measure) {
            if (char.code == ' '.code) return
            val shadowColor = if(char.style.isShadowed) Color.BLACK else Color.TRANSPARENT_WHITE
            val style = Style(
                TextColor.fromRgb(color.argbInt),
                shadowColor.argbInt,
                char.style.isBold,
                char.style.isItalic,
                char.style.isUnderlined,
                char.style.isStrikeThrough,
                char.style.isObfuscated,
                null,null,null, FontDescription.DEFAULT
            )
            val renderableGlyph = glyph.createGlyph(
                0f,0f,
                color.argbInt,
                shadowColor.argbInt,
                style,
                glyph.info().boldOffset,
                glyph.info().shadowOffset
            ) ?: return
            renderParam.guiRenderState.addGlyphToCurrentLayer(
                GlyphRenderState(
                    Matrix3x2f(renderParam.pose()),
                    renderableGlyph,
                    renderParam.scissorStack.peek()
                )
            )
        }

        private val random = RandomSource.create(293487214L)
        context(renderParam: GuiGraphics)
        override fun renderChar(char: DslRenderableChar, x: Measure, y: Measure, effectLeft: Measure, effectRight: Measure) {
            stack {
                renderParam.pose()
                    .translate(x.pixelsOrElse { 0f },y.pixelsOrElse { 0f })
                    .scale((char.size / lineHeight).toFloat())
                val dslGlyph = glyph(char.code)
                renderCharOnly(char,
                    if(char.style.isObfuscated) fontSet.getRandomGlyph(random,dslGlyph.normalAdvance.pixelsOrElse { 0 }) else dslGlyph.bakedGlyph
                    ,0.px,0.px,char.color,if(char.style.isBold) dslGlyph.boldOffset else 0.px)
                if(char.style.isShadowed) {
                    renderStrike(char,dslGlyph, 1.px,1.px, Color.BLACK)
                    renderUnderline(char,dslGlyph, 1.px,1.px, Color.BLACK)
                }
                renderStrike(char,dslGlyph, 0.px,0.px, char.color)
                renderUnderline(char,dslGlyph, 0.px,0.px,char.color)
            }
        }
    }
}