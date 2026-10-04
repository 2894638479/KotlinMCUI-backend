package io.github.u2894638479.kotlinmcuibackend

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.blaze3d.vertex.VertexFormat
import io.github.u2894638479.kotlinmcui.backend.DslBackendRenderer
import io.github.u2894638479.kotlinmcui.backend.DslBackendTriangles
import io.github.u2894638479.kotlinmcui.backend.fillRect
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
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.RenderType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.enchantment.Enchantments
import org.joml.Matrix4f
import kotlin.math.roundToInt

internal object Renderer: DslBackendRenderer<GuiGraphics>, DslBackendTriangles<GuiGraphics> {
    override val guiScale get() = Minecraft.getInstance().window.guiScale

    context(renderParam: GuiGraphics)
    override fun flush() = renderParam.flush()

    context(renderParam:GuiGraphics, ctx: DslScaleContext)
    override fun renderButton(rect: Rect, highlighted: Boolean, active: Boolean, color: Color) {
        if(rect.isEmpty) return
        var textureY = 0
        if(highlighted) textureY += 20
        if(active) textureY += 40
        val uvOuter = Rect(0.px,textureY.px,200.px,textureY.px + 20.px)
        val image = ImageHolder("minecraft:textures/gui/slider.png",256.px,256.px)
        ImageStrategy.nineSlice(uvOuter,uvOuter.expand(-3.px),ctx.scale).render(rect,image,color)
    }

    private fun VertexConsumer.color(color: Color) = color(color.rInt,color.gInt,color.bInt,color.aInt)

    /**
     * Draw one quad.
     *
     * The batched GUI buffer on this version only speaks quads, so an untextured draw becomes
     * one quad and a textured draw whose corners share a colour takes the vanilla blit path.
     * The remaining case — per-corner colours over a texture — goes through the immediate
     * triangle path, which is also what [renderTriangles] uses.
     *
     * Submitting one quad instead of two triangles lets the platform choose the diagonal; the
     * framework only ever passes colours that are affine along the axes, so that choice cannot
     * show. A caller that passes four arbitrary corner colours would see the difference.
     */
    context(renderParam: GuiGraphics)
    override fun renderQuad(
        rect: Rect, lt: Color, rt: Color, lb: Color, rb: Color, image: ImageHolder?, uv: Rect?
    ) {
        val l = rect.left.pixelsOrElse<Float> { return }
        val t = rect.top.pixelsOrElse<Float> { return }
        val r = rect.right.pixelsOrElse<Float> { return }
        val b = rect.bottom.pixelsOrElse<Float> { return }
        if(image == null || image.isEmpty || uv == null) {
            val vc = renderParam.bufferSource.getBuffer(RenderType.gui())
            val matrix = renderParam.pose().last().pose()
            vc.vertex(matrix,l,t,0f).color(lt).endVertex()
            vc.vertex(matrix,l,b,0f).color(lb).endVertex()
            vc.vertex(matrix,r,b,0f).color(rb).endVertex()
            vc.vertex(matrix,r,t,0f).color(rt).endVertex()
            return
        }
        if(lt == rt && rt == lb && lb == rb) {
            blitTexture(image,rect,uv,lt)
            return
        }
        val u0 = uv.left.pixelsOrElse<Float> { return } / image.width.pixelsOrElse<Float> { return }
        val u1 = uv.right.pixelsOrElse<Float> { return } / image.width.pixelsOrElse<Float> { return }
        val v0 = uv.top.pixelsOrElse<Float> { return } / image.height.pixelsOrElse<Float> { return }
        val v1 = uv.bottom.pixelsOrElse<Float> { return } / image.height.pixelsOrElse<Float> { return }
        immediate(image) { vc, matrix ->
            vc.vertex(matrix,l,t,0f).uv(u0,v0).color(lt).endVertex()
            vc.vertex(matrix,r,t,0f).uv(u1,v0).color(rt).endVertex()
            vc.vertex(matrix,l,b,0f).uv(u0,v1).color(lb).endVertex()
            vc.vertex(matrix,r,t,0f).uv(u1,v0).color(rt).endVertex()
            vc.vertex(matrix,r,b,0f).uv(u1,v1).color(rb).endVertex()
            vc.vertex(matrix,l,b,0f).uv(u0,v1).color(lb).endVertex()
        }
    }

    context(renderParam: GuiGraphics)
    override fun renderTriangles(vertices: FloatArray, colors: IntArray, image: ImageHolder?) {
        val img = image?.takeIf { !it.isEmpty }
        val stride = if(img == null) 2 else 4
        // A malformed list is a caller bug; drawing nothing beats drawing garbage.
        if(colors.size * stride != vertices.size || colors.size % 3 != 0) return
        val imageW = if(img == null) 0f else img.width.pixelsOrElse<Float> { return }
        val imageH = if(img == null) 0f else img.height.pixelsOrElse<Float> { return }
        immediate(img) { vc, matrix ->
            var i = 0
            var c = 0
            while(i < vertices.size) {
                vc.vertex(matrix,vertices[i],vertices[i + 1],0f)
                if(img != null) vc.uv(vertices[i + 2] / imageW,vertices[i + 3] / imageH)
                vc.color(Color(colors[c])).endVertex()
                i += stride
                c++
            }
        }
    }

    /** The vanilla textured-quad path: batched, one tint for the whole quad. */
    context(renderParam: GuiGraphics)
    private fun blitTexture(image: ImageHolder, rect: Rect, uv: Rect, color: Color) {
        val rect = rect.toInt().ifEmpty { return }
        renderParam.innerBlit(
            ResourceLocation.tryParse(image.id) ?: return,
            rect.left,rect.right,rect.top,rect.bottom,0,
            (uv.left / image.width).toFloat(),(uv.right / image.width).toFloat(),
            (uv.top / image.height).toFloat(),(uv.bottom / image.height).toFloat(),
            color.rFloat,color.gFloat,color.bFloat,color.aFloat
        )
    }

    /**
     * The immediate triangle path. It flushes per call, so it is reserved for what the batched
     * quad buffer cannot express: a triangle list, or per-vertex colours and UVs.
     */
    context(renderParam: GuiGraphics)
    private inline fun immediate(image: ImageHolder?, block: (VertexConsumer, Matrix4f) -> Unit) {
        val matrix = renderParam.pose().last().pose()
        val builder = Tesselator.getInstance().builder
        builder.begin(
            VertexFormat.Mode.TRIANGLES,
            if(image == null) DefaultVertexFormat.POSITION_COLOR else DefaultVertexFormat.POSITION_COLOR_TEX
        )
        if(image == null) {
            RenderSystem.setShader(GameRenderer::getPositionColorShader)
        } else {
            val location = ResourceLocation.tryParse(image.id) ?: return
            RenderSystem.setShader(GameRenderer::getPositionColorTexShader)
            RenderSystem.setShaderTexture(0,location)
        }
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        // This path is not a RenderType, so it cannot rely on the cull and depth states being
        // set up for it; the next RenderType-backed draw applies its own anyway.
        RenderSystem.disableCull()
        RenderSystem.disableDepthTest()
        block(builder,matrix)
        BufferUploader.drawWithShader(builder.end())
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
        if(!item.isPresent) blitTexture(ImageHolder("missing", 16.px, 16.px),rect,Rect(0.px,0.px,16.px,16.px),Color.WHITE)
        else stack {
            RenderSystem.disableDepthTest()
            // A copy: the registry's default instance is shared, and writing a count or a damage
            // value into it would show up wherever else the game renders that item.
            val itemStack = item.get().defaultInstance.copy().also {
                it.count = count
                if(damage != null) it.damageValue = (damage * it.maxDamage).roundToInt()
                if(enchanted) it.enchant(Enchantments.SHARPNESS,1)
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
        renderParam.pose().mulPoseMatrix(
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
