package io.github.u2894638479.kotlinmcuibackend

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.VertexConsumer
import io.github.u2894638479.kotlinmcui.backend.DslBackendRenderer
import io.github.u2894638479.kotlinmcui.context.DslScaleContext
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
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.font.glyphs.BakedGlyph
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.RenderType
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import org.joml.Matrix4f
import kotlin.math.roundToInt

internal object Renderer: DslBackendRenderer<GuiGraphics> {
    override val guiScale get() = Minecraft.getInstance().window.guiScale
    context(renderParam: GuiGraphics)
    override fun flush() = renderParam.flush()
    context(renderParam:GuiGraphics, ctx: DslScaleContext)
    override fun renderButton(rect: Rect, highlighted: Boolean, active: Boolean, color: Color) = withColor(color){
        if(rect.isEmpty) return@withColor
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
        val vc = renderParam.bufferSource.getBuffer(RenderType.gui())
        val matrix = renderParam.pose().last().pose()
        val rect = rect.toFloat().ifEmpty { return }
        vc.addVertex(matrix,rect.left,rect.top,0f).color(lt)
        vc.addVertex(matrix,rect.left,rect.bottom,0f).color(lb)
        vc.addVertex(matrix,rect.right,rect.bottom,0f).color(rb)
        vc.addVertex(matrix,rect.right,rect.top,0f).color(rt)
    }

    context(renderParam: GuiGraphics, ctx: DslScaleContext)
    override fun renderContainer(rect: Rect) = ImageStrategy.nineSlice(
        Rect(0.px,0.px,248.px,166.px),Rect(3.px,3.px,245.px,163.px),ctx.scale
    ).render(rect,ImageHolder("minecraft:textures/gui/demo_background.png",256.px,256.px),Color.WHITE)

    context(renderParam: GuiGraphics, ctx: DslScaleContext)
    override fun renderSlot(rect: Rect) = ImageStrategy.nineSlice(
        Rect(7.px,141.px,25.px,159.px),Rect(8.px,142.px,24.px,158.px),ctx.scale
    ).render(rect,ImageHolder("minecraft:textures/gui/container/inventory.png",256.px,256.px),Color.WHITE)

    context(renderParam: GuiGraphics, ctx: DslScaleContext)
    override fun renderTooltip(rect: Rect) {
        stack {
            renderParam.pose().scale(ctx.scale.toFloat(),ctx.scale.toFloat(),1f)
            val rect = rect.div(ctx.scale).toInt().ifEmpty { return }
            TooltipRenderUtil.renderTooltipBackground(renderParam,rect.left,rect.top,rect.width,rect.height,0)
        }
    }

    context(renderParam: GuiGraphics, ctx: DslScaleContext)
    override fun renderItem(rect: Rect, item: String, count:Int, damage:Double?, enchanted: Boolean) {
        val item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.tryParse(item))
        if(!item.isPresent) renderImage(ImageHolder("missing", 16.px, 16.px),rect,Rect(0.px,0.px,16.px,16.px),Color.WHITE)
        else stack {
            RenderSystem.disableDepthTest()
            val itemStack = item.get().defaultInstance.also {
                it.count = count
                if(damage != null) it.damageValue = (damage * it.maxDamage).roundToInt()
                if(enchanted) it.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
            }
            val rect = rect.toDouble().ifEmpty { return@stack }
            renderParam.pose().translate(rect.left,rect.top,0.0)
            renderParam.pose().scale((rect.width / 16.0).toFloat(),(rect.height / 16.0).toFloat(),1f)
            renderParam.renderItem(itemStack, 0, 0)
            renderParam.renderItemDecorations(Minecraft.getInstance().font,itemStack,0,0)
            RenderSystem.enableDepthTest()
        }
    }

    context(renderParam: GuiGraphics)
    override fun withScissor(rect: Rect, block: () -> Unit) {
        flush()
        (rect / guiScale).toInt().run {
            renderParam.enableScissor(left,top,right,bottom)
        }
        try {
            block()
        } finally {
            flush()
            renderParam.disableScissor()
        }
    }

    context(renderParam: GuiGraphics)
    override fun withTransform(transform: Transform, block: () -> Unit) {
        renderParam.pose().pushPose()
        renderParam.pose().mulPose(
            transform.run {
                Matrix4f(
                    m00,m10,0f,m20,
                    m01,m11,0f,m21,
                    0f,0f,1f,0f,
                    m02,m12,0f,m22
                )
            }
        )
        try {
            block()
        } finally {
            renderParam.pose().popPose()
        }
    }

    context(renderParam: GuiGraphics)
    private inline fun stack(block:()->Unit) {
        renderParam.pose().pushPose()
        try {
            block()
        } finally {
            renderParam.pose().popPose()
        }
    }

    context(renderParam: GuiGraphics)
    private var color: Color
        get() = RenderSystem.getShaderColor().let { Color(it[0], it[1], it[2], it[3]) }
        set(value) {
            flush()
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
        val rect = rect.toInt().ifEmpty { return }
        renderParam.innerBlit(
            ResourceLocation.tryParse(image.id) ?: return,
            rect.left,rect.right,rect.top,rect.bottom,0,
            (uv.left / image.width).toFloat(),(uv.right / image.width).toFloat(),
            (uv.top / image.height).toFloat(),(uv.bottom / image.height).toFloat(),
            color.rFloat,color.gFloat,color.bFloat,color.aFloat
        )
    }

    val sc = object: Screen(Component.literal("")){
        init { init(Minecraft.getInstance(),0,0) }
    }
    context(ctx: DslScaleContext,renderParam: GuiGraphics)
    override fun renderDefaultBackground(rect: Rect) {
        sc.width = rect.width.pixelsOrElse { return }
        sc.height = rect.height.pixelsOrElse { return }
        sc.renderBackground(renderParam,0,0,
            ((System.nanoTime() % 50_000_000) / 50_000_000.0).toFloat())
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
            val dslGlyph = glyph(char.code)
            val glyph = if(char.style.isObfuscated) fontSet.getRandomGlyph(dslGlyph.glyph) else fontSet.getGlyph(char.code)
            val scale = (char.size / lineHeight).toFloat()
            val right = glyph.right
            val down = glyph.down
            glyph.right = glyph.left + scale * (glyph.right - glyph.left)
            glyph.down = glyph.up + scale * (glyph.down - glyph.up)
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
            glyph.right = right
            glyph.down = down
        }
    }
}