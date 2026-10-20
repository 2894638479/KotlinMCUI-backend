package io.github.u2894638479.kotlinmcuibackend

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.blaze3d.vertex.VertexFormat
import io.github.u2894638479.kotlinmcui.backend.DslRootRenderer
import io.github.u2894638479.kotlinmcui.backend.fillRect
import io.github.u2894638479.kotlinmcui.context.DslScaleContext
import io.github.u2894638479.kotlinmcui.image.ImageHolder
import io.github.u2894638479.kotlinmcui.image.ImageSource
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

/** The drawing kinds a batch boundary has to distinguish: mc gives each its own render type. */
private const val KIND_FILL = 0
private const val KIND_TEXTURE = 1
private const val KIND_TEXT = 2

internal object Renderer: DslRootRenderer<GuiGraphics> {
    override val guiScale get() = Minecraft.getInstance().window.guiScale

    /**
     * Measurement hooks, off unless a system property asks for them.
     *
     * `-Dkotlinmcui.flush.stats=true` counts and times every batch submit and prints one line per frame;
     * `-Dkotlinmcui.flush.none=true` skips the submit entirely - wrong to look at, but it gives the upper
     * bound of what the ordering boundary costs on the same page. `-Dkotlinmcui.flush.barrier=true` turns
     * `flush()` into a deferred barrier: the batch is only submitted when the next draw is of another kind,
     * which is the cheapest a boundary can be without reordering anything.
     */
    private val flushStats = System.getProperty("kotlinmcui.flush.stats") != null
    private var flushCount = 0L
    private var flushNanos = 0L
    private var lastFrameCount = 0L
    private var lastFrameNanos = 0L
    private var lastKind = -1
    private var alternations = 0L
    private var lastFrameAlternations = 0L

    /** Scissor boundaries, the one kind of submit reordering cannot merge away. */
    private var scissorSubmits = 0L
    private var lastFrameScissorSubmits = 0L

    internal fun takeFrameScissorSubmits(): Long {
        val delta = scissorSubmits - lastFrameScissorSubmits
        lastFrameScissorSubmits = scissorSubmits
        return delta
    }

    /**
     * Order analysis, off unless `-Dkotlinmcui.order.stats=true`.
     *
     * Every draw is reduced to the one thing a sorter can use: its kind and its rectangle. Consecutive
     * draws of one kind become one run, because that is the batch a reordering backend would emit.
     */
    private val orderStats = System.getProperty("kotlinmcui.order.stats") != null
    private var runCount = 0
    private var runKind = IntArray(256)
    private var runLeft = FloatArray(256)
    private var runTop = FloatArray(256)
    private var runRight = FloatArray(256)
    private var runBottom = FloatArray(256)

    /** Start a frame's draw record; the sorter works on one frame at a time. */
    internal fun beginFrameTrace() {
        runCount = 0
        // A frame that threw while drawing must not leave its half-recorded list to the next one.
        drawCount = 0
        vertexCount = 0
        typeCount = 0
        lastRunType = -1
    }

    /** How many runs the frame's declared order produced. */
    internal val tracedRuns get() = runCount

    private fun recordOrder(kind: Int, left: Float, top: Float, right: Float, bottom: Float) {
        val last = runCount - 1
        if(last >= 0 && runKind[last] == kind) {
            if(left < runLeft[last]) runLeft[last] = left
            if(top < runTop[last]) runTop[last] = top
            if(right > runRight[last]) runRight[last] = right
            if(bottom > runBottom[last]) runBottom[last] = bottom
            return
        }
        if(runCount == runKind.size) {
            val capacity = runCount * 2
            runKind = runKind.copyOf(capacity)
            runLeft = runLeft.copyOf(capacity)
            runTop = runTop.copyOf(capacity)
            runRight = runRight.copyOf(capacity)
            runBottom = runBottom.copyOf(capacity)
        }
        runKind[runCount] = kind
        runLeft[runCount] = left
        runTop[runCount] = top
        runRight[runCount] = right
        runBottom[runCount] = bottom
        runCount++
    }

    private fun recordOrder(kind: Int, rect: Rect) {
        val left = rect.left.pixelsOrElse<Float> { return }
        val top = rect.top.pixelsOrElse<Float> { return }
        val right = rect.right.pixelsOrElse<Float> { return }
        val bottom = rect.bottom.pixelsOrElse<Float> { return }
        recordOrder(kind, left, top, right, bottom)
    }

    private fun overlaps(a: Int, b: Int) =
        runLeft[a] < runRight[b] && runLeft[b] < runRight[a] &&
            runTop[a] < runBottom[b] && runTop[b] < runBottom[a]

    /** Whether every earlier run that overlaps [j] has already been drawn. */
    private fun available(j: Int, done: BooleanArray): Boolean {
        for(i in 0 until j) {
            if(done[i] || !overlaps(i, j)) continue
            return false
        }
        return true
    }

    /**
     * The fewest kind changes a reordering backend would need, measured by a greedy over the declared order.
     *
     * Two draws that do not overlap can swap without changing a pixel, so only the overlapping pairs pin the
     * order; any order that keeps those pairs is the same picture. This walks such an order, sticking to the
     * current kind whenever one is available and switching only when the overlap constraints force it - the
     * number it returns is an upper bound on the optimum, so it is enough to decide whether reordering is
     * worth building.
     */
    internal fun takeFrameOrderSwitches(): Int {
        if(!orderStats || runCount == 0) return 0
        val n = runCount
        val done = BooleanArray(n)
        var current = -1
        var switches = 0
        repeat(n) {
            var pick = -1
            for(j in 0 until n) {
                if(!done[j] && runKind[j] == current && available(j, done)) { pick = j; break }
            }
            if(pick < 0) {
                for(j in 0 until n) if(!done[j] && available(j, done)) { pick = j; break }
                if(pick < 0) return switches
                if(runKind[pick] != current) switches++
            }
            done[pick] = true
            current = runKind[pick]
        }
        return switches
    }

    /** The type switches since the previous call: the floor on how many submits a barrier costs. */
    internal fun takeFrameAlternations(): Long {
        val delta = alternations - lastFrameAlternations
        lastFrameAlternations = alternations
        return delta
    }

    /** The batch submits since the previous call: one number per frame. */
    internal fun takeFrameFlushes(): Long {
        val delta = flushCount - lastFrameCount
        lastFrameCount = flushCount
        return delta
    }

    /** The microseconds spent submitting since the previous call. */
    internal fun takeFrameFlushNanos(): Long {
        val delta = flushNanos - lastFrameNanos
        lastFrameNanos = flushNanos
        return delta
    }

    /** Whether the frame trace should be printed. */
    internal val tracingFlushes get() = flushStats

    /**
     * Draw-order recording, off unless `-Dkotlinmcui.order.reorder=true`.
     *
     * With it on a draw is not submitted where it is declared: it appends its screen-space vertices
     * to a display list, and the list is replayed in an order that keeps every overlapping pair in
     * declared order and otherwise groups equal render types, because a group is one batch. The
     * vertices are the ones the platform would have written, so the replay is the same picture with
     * fewer boundaries.
     */
    private val reorder = System.getProperty("kotlinmcui.order.reorder") != "false"

    /**
     * The safety net under the sorter, on whenever [reorder] is on.
     *
     * After sorting, every pair that overlaps must still be in declared order; a violation throws
     * rather than draw a different picture. `-Dkotlinmcui.order.assert=false` turns it off, which is
     * only useful to prove that it fires.
     */
    private val assertOrder = reorder && System.getProperty("kotlinmcui.order.assert") != "false"

    /** One recorded draw: its kind, the batch it belongs to, the vertices it owns, and their box. */
    private var drawCount = 0
    private var drawKind = IntArray(64)
    private var drawType = IntArray(64)
    private var drawFirst = IntArray(64)
    private var drawVerts = IntArray(64)
    private var drawTexture = arrayOfNulls<ResourceLocation>(64)
    private var drawText = arrayOfNulls<RenderType>(64)
    private var drawLeft = FloatArray(64)
    private var drawTop = FloatArray(64)
    private var drawRight = FloatArray(64)
    private var drawBottom = FloatArray(64)

    /**
     * The frame's batch types, one row per distinct key, so a key stays the same across the barriers
     * that split a frame into several display lists.
     */
    private var typeCount = 0
    private var typeKind = IntArray(32)
    private var typeTexture = arrayOfNulls<ResourceLocation>(32)
    private var typeText = arrayOfNulls<RenderType>(32)

    /** The type of the last batch that went out this frame, for the alternation count. */
    private var lastRunType = -1

    /** The vertex pool: parallel arrays, so appending a vertex is a handful of stores and no object. */
    private var vertexCount = 0
    private var vx = FloatArray(512)
    private var vy = FloatArray(512)
    private var vz = FloatArray(512)
    private var vColor = IntArray(512)
    private var vu = FloatArray(512)
    private var vv = FloatArray(512)
    private var vLightU = IntArray(512)
    private var vLightV = IntArray(512)

    /** The sort's output and whatever it needs to get there; all reused, so a frame allocates none. */
    private var orderIdx = IntArray(64)
    private var predCount = IntArray(64)
    private var doneFlag = BooleanArray(64)
    private var positionAt = IntArray(64)

    /** The draw being recorded, if any. Draws do not nest, so one slot describes the whole list. */
    private var openKind = KIND_FILL
    private var openTexture: ResourceLocation? = null
    private var openText: RenderType? = null
    private var openFirst = 0
    private var openLeft = 0f
    private var openTop = 0f
    private var openRight = 0f
    private var openBottom = 0f

    /** A quad's four corners in `(lt, rt, lb, rb)` order, and one projected point. Both reused. */
    private val cornerX = FloatArray(4)
    private val cornerY = FloatArray(4)
    private val projectedPoint = FloatArray(2)

    /** The frame is over: whatever the decorators left in the list is replayed now. */
    internal fun endFrame(renderParam: GuiGraphics) {
        commit(renderParam)
    }

    private fun appendVertex(x: Float, y: Float, z: Float, color: Int, u: Float, v: Float, lightU: Int, lightV: Int) {
        if(vertexCount == vx.size) {
            val capacity = vx.size * 2
            vx = vx.copyOf(capacity)
            vy = vy.copyOf(capacity)
            vz = vz.copyOf(capacity)
            vColor = vColor.copyOf(capacity)
            vu = vu.copyOf(capacity)
            vv = vv.copyOf(capacity)
            vLightU = vLightU.copyOf(capacity)
            vLightV = vLightV.copyOf(capacity)
        }
        val i = vertexCount++
        vx[i] = x
        vy[i] = y
        vz[i] = z
        vColor[i] = color
        vu[i] = u
        vv[i] = v
        vLightU[i] = lightU
        vLightV[i] = lightV
        if(x < openLeft) openLeft = x
        if(x > openRight) openRight = x
        if(y < openTop) openTop = y
        if(y > openBottom) openBottom = y
    }

    /**
     * The [VertexConsumer] that records instead of drawing.
     *
     * It is layout-agnostic on purpose - each call sets one field and [endVertex] appends the vertex -
     * because callers disagree about the order of `uv` and `color`. The transformation is the
     * platform's own: it is inherited from `VertexConsumer.vertex(Matrix4f,...)`, so the recorded
     * coordinates are the final ones and no matrix is kept with the draw.
     */
    private class Recorder(private val list: Renderer): VertexConsumer {
        private var px = 0f
        private var py = 0f
        private var pz = 0f
        private var color = 0
        private var u = 0f
        private var v = 0f
        private var lightU = 0
        private var lightV = 0

        override fun vertex(d: Double, e: Double, f: Double): VertexConsumer {
            px = d.toFloat()
            py = e.toFloat()
            pz = f.toFloat()
            return this
        }

        /** Packed back-to-front, so the four bytes survive the round trip through [vColor]. */
        override fun color(i: Int, j: Int, k: Int, l: Int): VertexConsumer {
            color = ((l and 255) shl 24) or ((i and 255) shl 16) or ((j and 255) shl 8) or (k and 255)
            return this
        }

        override fun uv(f: Float, g: Float): VertexConsumer {
            u = f
            v = g
            return this
        }

        override fun overlayCoords(i: Int, j: Int): VertexConsumer = this

        override fun uv2(i: Int, j: Int): VertexConsumer {
            lightU = i
            lightV = j
            return this
        }

        override fun normal(f: Float, g: Float, h: Float): VertexConsumer = this

        override fun defaultColor(i: Int, j: Int, k: Int, l: Int) {}

        override fun unsetDefaultColor() {}

        override fun endVertex() {
            list.appendVertex(px,py,pz,color,u,v,lightU,lightV)
        }
    }

    private val recorder = Recorder(this)

    private fun beginDraw(kind: Int, texture: ResourceLocation?, text: RenderType?) {
        openKind = kind
        openTexture = texture
        openText = text
        openFirst = vertexCount
        openLeft = Float.MAX_VALUE
        openTop = Float.MAX_VALUE
        openRight = -Float.MAX_VALUE
        openBottom = -Float.MAX_VALUE
    }

    /**
     * Closes the open draw, giving it a type key.
     *
     * The key is what a batch is: equal keys may share one submit, different keys may not - a key
     * change is a draw call. A draw that emitted no vertices leaves nothing behind.
     */
    private fun endDraw() {
        if(vertexCount == openFirst) return
        if(drawCount == drawKind.size) {
            val capacity = drawKind.size * 2
            drawKind = drawKind.copyOf(capacity)
            drawType = drawType.copyOf(capacity)
            drawFirst = drawFirst.copyOf(capacity)
            drawVerts = drawVerts.copyOf(capacity)
            drawTexture = drawTexture.copyOf(capacity)
            drawText = drawText.copyOf(capacity)
            drawLeft = drawLeft.copyOf(capacity)
            drawTop = drawTop.copyOf(capacity)
            drawRight = drawRight.copyOf(capacity)
            drawBottom = drawBottom.copyOf(capacity)
            orderIdx = orderIdx.copyOf(capacity)
            predCount = predCount.copyOf(capacity)
            doneFlag = doneFlag.copyOf(capacity)
            positionAt = positionAt.copyOf(capacity)
        }
        val key = typeKeyOf(openKind,openTexture,openText)
        val i = drawCount++
        drawKind[i] = openKind
        drawType[i] = key
        drawFirst[i] = openFirst
        drawVerts[i] = vertexCount - openFirst
        drawTexture[i] = openTexture
        drawText[i] = openText
        drawLeft[i] = openLeft
        drawTop[i] = openTop
        drawRight[i] = openRight
        drawBottom[i] = openBottom
    }

    /**
     * The key of a batch type: equal keys may share one submit, different keys may not.
     *
     * It is assigned by first appearance in the frame, which costs a scan over the handful of types a
     * frame uses and no map. The table survives a barrier, so a type that appears on both sides of one
     * keeps its key and the alternation count can be taken across the whole frame.
     */
    private fun typeKeyOf(kind: Int, texture: ResourceLocation?, text: RenderType?): Int {
        for(i in 0 until typeCount) {
            if(typeKind[i] != kind) continue
            if(kind == KIND_TEXTURE && typeTexture[i] != texture) continue
            if(kind == KIND_TEXT && typeText[i] !== text) continue
            return i
        }
        if(typeCount == typeKind.size) {
            val capacity = typeKind.size * 2
            typeKind = typeKind.copyOf(capacity)
            typeTexture = typeTexture.copyOf(capacity)
            typeText = typeText.copyOf(capacity)
        }
        val key = typeCount++
        typeKind[key] = kind
        typeTexture[key] = texture
        typeText[key] = text
        return key
    }

    /** Opens a draw, lets [block] record into it, and closes it; a draw that emitted nothing is dropped. */
    private inline fun record(kind: Int, texture: ResourceLocation? = null, text: RenderType? = null, block: (VertexConsumer) -> Unit) {
        beginDraw(kind,texture,text)
        block(recorder)
        endDraw()
    }

    /** Whether two recorded draws cover a common pixel, which is the one thing that pins their order. */
    private fun boundsOverlap(a: Int, b: Int) =
        drawLeft[a] < drawRight[b] && drawLeft[b] < drawRight[a] &&
            drawTop[a] < drawBottom[b] && drawTop[b] < drawBottom[a]

    /**
     * The assertion's own overlap: recomputed from the recorded vertices instead of the cached box.
     *
     * It means the same thing as [boundsOverlap] - two draws that share a pixel pin each other's order -
     * but it is a second implementation on purpose. A cached box that was never updated, or updated with
     * the wrong draw, blinds the sorter and is caught here; a slack pixel would instead have made the
     * assertion reject orders the sorter is allowed to produce.
     */
    private fun vertexOverlap(a: Int, b: Int): Boolean {
        var aLeft = Float.MAX_VALUE
        var aTop = Float.MAX_VALUE
        var aRight = -Float.MAX_VALUE
        var aBottom = -Float.MAX_VALUE
        var bLeft = Float.MAX_VALUE
        var bTop = Float.MAX_VALUE
        var bRight = -Float.MAX_VALUE
        var bBottom = -Float.MAX_VALUE
        for(i in drawFirst[a] until drawFirst[a] + drawVerts[a]) {
            val x = vx[i]
            val y = vy[i]
            if(x < aLeft) aLeft = x
            if(x > aRight) aRight = x
            if(y < aTop) aTop = y
            if(y > aBottom) aBottom = y
        }
        for(i in drawFirst[b] until drawFirst[b] + drawVerts[b]) {
            val x = vx[i]
            val y = vy[i]
            if(x < bLeft) bLeft = x
            if(x > bRight) bRight = x
            if(y < bTop) bTop = y
            if(y > bBottom) bBottom = y
        }
        return aLeft < bRight && bLeft < aRight && aTop < bBottom && bTop < aBottom
    }

    /**
     * Puts the recorded draws in replay order.
     *
     * The declared order is a hard constraint only where two draws overlap: a pixel both can touch
     * must be painted in that order. Elsewhere the order is free, so the sorter takes any draw of the
     * current type whose overlapping predecessors are all out, and only switches type when the
     * constraints leave nothing else - a switch is a batch boundary, not a mistake.
     */
    private fun sortDraws() {
        val n = drawCount
        for(j in 0 until n) {
            var count = 0
            for(i in 0 until j) if(boundsOverlap(i,j)) count++
            predCount[j] = count
            doneFlag[j] = false
        }
        var current = -1
        for(slot in 0 until n) {
            var pick = -1
            for(j in 0 until n) {
                if(doneFlag[j] || predCount[j] != 0 || drawType[j] != current) continue
                pick = j
                break
            }
            if(pick < 0) {
                for(j in 0 until n) {
                    if(doneFlag[j] || predCount[j] != 0) continue
                    pick = j
                    break
                }
                if(pick < 0) error("kotlinmcui reorder: the declared order left no draw available")
            }
            orderIdx[slot] = pick
            doneFlag[pick] = true
            current = drawType[pick]
            for(j in pick + 1 until n) if(boundsOverlap(pick,j)) predCount[j]--
        }
        // The pass above cannot invert an overlapping pair, but it is the one thing the picture
        // depends on, so it is checked against the declaration instead of being trusted.
        //
        // The check uses its own, stricter notion of overlap (every box grown by a pixel) rather than
        // calling [boundsOverlap]: a mistake in the sorter's predicate would otherwise blind the very
        // assertion meant to catch it. A pixel of slack also covers what geometry does outside its own
        // box - antialiasing at an edge - at the cost of rejecting a few merges that were legal.
        if(assertOrder) {
            for(slot in 0 until n) positionAt[orderIdx[slot]] = slot
            for(a in 0 until n) for(b in a + 1 until n) {
                if(!vertexOverlap(a,b) || positionAt[a] < positionAt[b]) continue
                throw IllegalStateException(
                    "kotlinmcui reorder inverted an overlapping pair: ${describe(a)} declared before " +
                        "${describe(b)}, replayed at ${positionAt[a]} and ${positionAt[b]}"
                )
            }
        }
    }

    private fun describe(index: Int) = "${when(drawKind[index]) {
        KIND_FILL -> "fill"
        KIND_TEXT -> "text"
        else -> "texture"
    }} ${drawTexture[index] ?: drawText[index]} rect=[" +
        "${drawLeft[index]},${drawTop[index]},${drawRight[index]},${drawBottom[index]}]"

    /**
     * Replays the list in the sorted order, one submit per run of equal type keys.
     *
     * A run is one batch: [GuiGraphics.getBuffer] submits the previous batch when the type changes,
     * and the immediate paths submit themselves, so this only has to start each draw call. Counted
     * per run, so `count` keeps meaning "batches that went out" in both modes.
     */
    private fun commit(renderParam: GuiGraphics) {
        if(!reorder || drawCount == 0) return
        sortDraws()
        var i = 0
        while(i < drawCount) {
            val key = drawType[orderIdx[i]]
            // Counted here rather than over the declared draws: this is the sequence that is submitted.
            if(lastRunType >= 0 && key != lastRunType) alternations++
            lastRunType = key
            var j = i + 1
            while(j < drawCount && drawType[orderIdx[j]] == key) j++
            val start = System.nanoTime()
            // A text run leaves its batch in the buffer source, so the next run has to flush it.
            renderParam.flush()
            emitRun(renderParam,i,j)
            flushNanos += System.nanoTime() - start
            flushCount++
            i = j
        }
        renderParam.flush()
        drawCount = 0
        vertexCount = 0
    }

    /** One batch: [from],[to) is a maximal run of one type key in [orderIdx]. */
    private fun emitRun(renderParam: GuiGraphics, from: Int, to: Int) {
        val first = orderIdx[from]
        when(drawKind[first]) {
            KIND_TEXT -> emitText(renderParam,first,from,to)
            KIND_TEXTURE -> emitImmediate(first,from,to,drawTexture[first])
            else -> emitImmediate(first,from,to,null)
        }
    }

    /**
     * Writes a run into the glyph buffer the framework already uses for text.
     *
     * Text cannot go through the immediate path: its shader samples the lightmap, and only the text
     * render type sets that up. The recorded vertices are already transformed, so the identity matrix
     * keeps the platform from applying the pose a second time.
     */
    private fun emitText(renderParam: GuiGraphics, first: Int, from: Int, to: Int) {
        val type = drawText[first] ?: return
        val buffer = renderParam.bufferSource.getBuffer(type)
        for(k in from until to) {
            val draw = orderIdx[k]
            val end = drawFirst[draw] + drawVerts[draw]
            for(v in drawFirst[draw] until end) {
                buffer.vertex(vx[v].toDouble(),vy[v].toDouble(),vz[v].toDouble())
                    .color(vColor[v])
                    .uv(vu[v],vv[v])
                    .uv2(vLightU[v],vLightV[v])
                    .endVertex()
            }
        }
    }

    /**
     * Writes a run through the immediate triangle path.
     *
     * It is the same call the unbatched backend makes per draw, so the vertex bytes and the render
     * state are the ones the drawing code above already chose: colour-only and textured quads both
     * disable culling and the depth test, and both blend.
     */
    private fun emitImmediate(first: Int, from: Int, to: Int, texture: ResourceLocation?) {
        val builder = Tesselator.getInstance().builder
        builder.begin(
            VertexFormat.Mode.TRIANGLES,
            if(texture == null) DefaultVertexFormat.POSITION_COLOR else DefaultVertexFormat.POSITION_COLOR_TEX
        )
        if(texture == null) {
            RenderSystem.setShader(GameRenderer::getPositionColorShader)
        } else {
            RenderSystem.setShader(GameRenderer::getPositionColorTexShader)
            RenderSystem.setShaderTexture(0,texture)
        }
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.disableCull()
        RenderSystem.disableDepthTest()
        for(k in from until to) {
            val draw = orderIdx[k]
            val end = drawFirst[draw] + drawVerts[draw]
            for(v in drawFirst[draw] until end) {
                builder.vertex(vx[v].toDouble(),vy[v].toDouble(),vz[v].toDouble())
                builder.color(vColor[v])
                if(texture != null) builder.uv(vu[v],vv[v])
                builder.endVertex()
            }
        }
        BufferUploader.drawWithShader(builder.end())
    }

    /** The two triangles the quad contract fixes, corners in `(lt, rt, lb, rb)` order. */
    private fun emitQuad(vc: VertexConsumer, matrix: Matrix4f, xs: FloatArray, ys: FloatArray, lt: Color, rt: Color, lb: Color, rb: Color) {
        vc.vertex(matrix,xs[0],ys[0],0f).color(lt).endVertex()
        vc.vertex(matrix,xs[1],ys[1],0f).color(rt).endVertex()
        vc.vertex(matrix,xs[2],ys[2],0f).color(lb).endVertex()
        vc.vertex(matrix,xs[1],ys[1],0f).color(rt).endVertex()
        vc.vertex(matrix,xs[3],ys[3],0f).color(rb).endVertex()
        vc.vertex(matrix,xs[2],ys[2],0f).color(lb).endVertex()
    }

    /** The same split, carrying one uv per corner. */
    private fun emitTexturedQuad(
        vc: VertexConsumer, matrix: Matrix4f, xs: FloatArray, ys: FloatArray,
        u0: Float, u1: Float, v0: Float, v1: Float, lt: Color, rt: Color, lb: Color, rb: Color
    ) {
        vc.vertex(matrix,xs[0],ys[0],0f).uv(u0,v0).color(lt).endVertex()
        vc.vertex(matrix,xs[1],ys[1],0f).uv(u1,v0).color(rt).endVertex()
        vc.vertex(matrix,xs[2],ys[2],0f).uv(u0,v1).color(lb).endVertex()
        vc.vertex(matrix,xs[1],ys[1],0f).uv(u1,v0).color(rt).endVertex()
        vc.vertex(matrix,xs[3],ys[3],0f).uv(u1,v1).color(rb).endVertex()
        vc.vertex(matrix,xs[2],ys[2],0f).uv(u0,v1).color(lb).endVertex()
    }

    /** The corners of an axis-aligned rect in the order the quad contract emits them. */
    private fun corners(left: Float, top: Float, right: Float, bottom: Float) {
        cornerX[0] = left; cornerX[1] = right; cornerX[2] = left; cornerX[3] = right
        cornerY[0] = top; cornerY[1] = top; cornerY[2] = bottom; cornerY[3] = bottom
    }

    /**
     * Projects the four corners through [projected] and then the pose's affine part.
     *
     * `false` means the projection has no screen position - a corner at or behind the eye - and the
     * draw cannot be turned into vertices a sorter could place, so its caller makes it a barrier.
     */
    private fun projectCorners(projected: Perspective, matrix: Matrix4f, left: Float, top: Float, right: Float, bottom: Float): Boolean {
        val xs = cornerX
        val ys = cornerY
        for(i in 0 until 4) {
            val x = if(i == 0 || i == 2) left else right
            val y = if(i < 2) top else bottom
            if(!projected.project(x,y,projectedPoint)) return false
            val px = projectedPoint[0]
            val py = projectedPoint[1]
            xs[i] = matrix.m00() * px + matrix.m10() * py + matrix.m30()
            ys[i] = matrix.m01() * px + matrix.m11() * py + matrix.m31()
        }
        return true
    }

    /** A draw the display list cannot place: the list is replayed first, then the draw happens. */
    private fun barrier(renderParam: GuiGraphics) {
        commit(renderParam)
    }

    init {
        if(flushStats) Runtime.getRuntime().addShutdownHook(Thread {
            val average = if(flushCount == 0L) 0L else flushNanos / 1000 / flushCount
            println("FLUSHSTATS count=$flushCount total_ms=${flushNanos / 1_000_000} avg_us=$average")
        })
    }


    /** The real submit; every counter goes here, so "flush was called" and "a batch went out" stay apart. */
    private fun submit(renderParam: GuiGraphics) {
        if(flushStats) {
            val start = System.nanoTime()
            renderParam.flush()
            flushNanos += System.nanoTime() - start
        } else renderParam.flush()
        flushCount++
    }

    /** A frame end or a scope change cannot wait for the next draw: get the queue out now. */
    private fun forceSubmit(renderParam: GuiGraphics) {
        scissorSubmits++
        // The scissors are raster state: what is inside them may be reordered, what is outside may not.
        if(reorder) commit(renderParam) else submit(renderParam)
    }

    /**
     * Record the kind of the draw that is about to be queued.
     *
     * In barrier mode a deferred barrier only has to become a submit when the kind changes: draws of one
     * kind sit in one batch and keep their order, so the boundary is free.
     */
    private fun note(renderParam: GuiGraphics, kind: Int) {
        val switches = kind != lastKind
        // Under reorder the alternation that reaches the screen is the replayed one, counted in commit.
        if(switches && !reorder) alternations++
        lastKind = kind
    }

    /**
     * The same, carrying the rectangle the draw covers.
     *
     * A sorter needs nothing else from a draw: it is a kind plus the pixels it can touch. The two-argument
     * form stays for draws that cannot state a box (a triangle list), and they simply do not take part in the
     * ordering analysis.
     */
    private fun note(renderParam: GuiGraphics, kind: Int, rect: Rect) {
        note(renderParam, kind)
        if(!orderStats) return
        val left = rect.left.pixelsOrElse<Float> { return }
        val top = rect.top.pixelsOrElse<Float> { return }
        val right = rect.right.pixelsOrElse<Float> { return }
        val bottom = rect.bottom.pixelsOrElse<Float> { return }
        recordOrder(kind, left, top, right, bottom)
    }

    context(renderParam:GuiGraphics, ctx: DslScaleContext)
    override fun renderButton(rect: Rect, highlighted: Boolean, active: Boolean, color: Color) {
        if(rect.isEmpty) return
        note(renderParam, KIND_TEXTURE, rect)
        var textureY = 0
        if(highlighted) textureY += 20
        if(active) textureY += 40
        val uvOuter = Rect(0.px,textureY.px,200.px,textureY.px + 20.px)
        val image = ImageHolder(ImageSource.Resource("minecraft:textures/gui/slider.png"),256.px,256.px)
        ImageStrategy.nineSlice(uvOuter,uvOuter.expand(-3.px),ctx.scale).render(rect,image,color)
    }

    private fun VertexConsumer.color(color: Color) = color(color.rInt,color.gInt,color.bInt,color.aInt)

    /**
     * Draw one quad.
     *
     * The contract fixes the split: this primitive is the two triangles `(lt, rt, lb)` and
     * `(rt, rb, lb)`. The batched GUI buffer splits a quad on its first and third vertices
     * instead, so handing it one quad would leave the diagonal to the vertex order — and the
     * framework does pass four arbitrary corner colours: `Colors.ColorSquare` paints white, one
     * hue and two blacks, which is affine along neither axis. Every path that can show the split
     * therefore emits the pair above itself rather than submitting one quad.
     *
     * A textured draw whose corners share a colour takes the vanilla blit path, where the split
     * cannot be observed. The remaining cases — untextured, and per-corner colours over a texture
     * — go through the immediate triangle path, which is also what [renderTriangles] uses.
     */
    context(renderParam: GuiGraphics)
    override fun renderQuad(
        rect: Rect, lt: Color, rt: Color, lb: Color, rb: Color, image: ImageHolder?, uv: Rect?
    ) {
        val l = rect.left.pixelsOrElse<Float> { return }
        val t = rect.top.pixelsOrElse<Float> { return }
        val r = rect.right.pixelsOrElse<Float> { return }
        val b = rect.bottom.pixelsOrElse<Float> { return }
        val textured = image != null && image.source is ImageSource.Resource && uv != null
        note(renderParam, if(textured) KIND_TEXTURE else KIND_FILL, rect)
        if(!textured) {
            val matrix = renderParam.pose().last().pose()
            val projected = perspective
            if (projected != null) {
                // The pose below has no perspective in it (withTransform keeps that out), so the
                // corners only need the affine part after the divide, and the identity matrix then
                // stops the platform from applying anything a second time.
                if(!projectCorners(projected,matrix,l,t,r,b)) {
                    // A corner at or behind the eye has no screen position, so this draw cannot be
                    // turned into vertices a sorter could place: it is a barrier, not a draw.
                    barrier(renderParam)
                    corners(l,t,r,b)
                    immediate(null) { vc, _ -> emitQuad(vc,identity,cornerX,cornerY,lt,rt,lb,rb) }
                    return
                }
                if(reorder) record(KIND_FILL) { vc -> emitQuad(vc,identity,cornerX,cornerY,lt,rt,lb,rb) }
                else immediate(null) { vc, _ -> emitQuad(vc,identity,cornerX,cornerY,lt,rt,lb,rb) }
                return
            }
            corners(l,t,r,b)
            if(reorder) record(KIND_FILL) { vc -> emitQuad(vc,matrix,cornerX,cornerY,lt,rt,lb,rb) }
            else immediate(null) { vc, m -> emitQuad(vc,m,cornerX,cornerY,lt,rt,lb,rb) }
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
        corners(l,t,r,b)
        if(reorder) {
            val location = image.texture() ?: return
            val matrix = renderParam.pose().last().pose()
            record(KIND_TEXTURE, location) { vc ->
                emitTexturedQuad(vc,matrix,cornerX,cornerY,u0,u1,v0,v1,lt,rt,lb,rb)
            }
        } else immediate(image) { vc, matrix ->
            emitTexturedQuad(vc,matrix,cornerX,cornerY,u0,u1,v0,v1,lt,rt,lb,rb)
        }
    }

    context(renderParam: GuiGraphics)
    override fun renderTriangles(vertices: FloatArray, colors: IntArray, image: ImageHolder?) {
        // A holder that is not a registered resource has no texture this backend can bind, so it
        // draws as colour only, exactly like an empty holder.
        val img = image?.takeIf { it.source is ImageSource.Resource }
        val kind = if(img == null) KIND_FILL else KIND_TEXTURE
        note(renderParam, kind)
        if(orderStats) {
            // A triangle list has no rectangle, so the sorter gets the closest one: the box it can touch.
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE
            var maxY = -Float.MAX_VALUE
            var i = 0
            while(i + 1 < vertices.size) {
                val x = vertices[i]
                val y = vertices[i + 1]
                if(x < minX) minX = x
                if(x > maxX) maxX = x
                if(y < minY) minY = y
                if(y > maxY) maxY = y
                i += 2
            }
            if(minX <= maxX) recordOrder(kind, minX, minY, maxX, maxY)
        }
        val stride = if(img == null) 2 else 4
        // A malformed list is a caller bug; drawing nothing beats drawing garbage.
        if(colors.size * stride != vertices.size || colors.size % 3 != 0) return
        val imageW = if(img == null) 0f else img.width.pixelsOrElse<Float> { return }
        val imageH = if(img == null) 0f else img.height.pixelsOrElse<Float> { return }
        if(reorder) {
            val location = if(img == null) null else img.texture() ?: return
            val matrix = renderParam.pose().last().pose()
            record(if(img == null) KIND_FILL else KIND_TEXTURE, location) { vc ->
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
            return
        }
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

    /**
     * The game texture this holder names, or `null` when there is none this backend can bind.
     *
     * Only [ImageSource.Resource] maps onto a game texture: a [ImageSource.LocalFile] is decoded and
     * registered as one before it is handed out (see [Utils.localImage]), and this backend has no
     * remote loader, so [ImageSource.Remote] never becomes drawable here.
     */
    private fun ImageHolder.texture(): ResourceLocation? =
        (source as? ImageSource.Resource)?.let { ResourceLocation.tryParse(it.location) }

    /** The vanilla textured-quad path: batched, one tint for the whole quad. */
    context(renderParam: GuiGraphics)
    private fun blitTexture(image: ImageHolder, rect: Rect, uv: Rect, color: Color) {
        val location = image.texture() ?: return
        val rect = rect.toInt().ifEmpty { return }
        val u0 = (uv.left / image.width).toFloat()
        val u1 = (uv.right / image.width).toFloat()
        val v0 = (uv.top / image.height).toFloat()
        val v1 = (uv.bottom / image.height).toFloat()
        if(reorder) {
            // The vanilla path writes four corners of one quad, in this order; the replay has to keep
            // the same split, which is the one mc's own quad indices use.
            val matrix = renderParam.pose().last().pose()
            val red = (color.rFloat * 255f).toInt()
            val green = (color.gFloat * 255f).toInt()
            val blue = (color.bFloat * 255f).toInt()
            val alpha = (color.aFloat * 255f).toInt()
            val left = rect.left.toFloat()
            val top = rect.top.toFloat()
            val right = rect.right.toFloat()
            val bottom = rect.bottom.toFloat()
            record(KIND_TEXTURE, location) { vc ->
                vc.vertex(matrix,left,top,0f).color(red,green,blue,alpha).uv(u0,v0).endVertex()
                vc.vertex(matrix,left,bottom,0f).color(red,green,blue,alpha).uv(u0,v1).endVertex()
                vc.vertex(matrix,right,bottom,0f).color(red,green,blue,alpha).uv(u1,v1).endVertex()
                vc.vertex(matrix,right,bottom,0f).color(red,green,blue,alpha).uv(u1,v1).endVertex()
                vc.vertex(matrix,right,top,0f).color(red,green,blue,alpha).uv(u1,v0).endVertex()
                vc.vertex(matrix,left,top,0f).color(red,green,blue,alpha).uv(u0,v0).endVertex()
            }
            return
        }
        renderParam.innerBlit(
            location,
            rect.left,rect.right,rect.top,rect.bottom,0,
            u0,u1,v0,v1,
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
            val location = image.texture() ?: return
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
    override fun renderContainer(rect: Rect) {
        note(renderParam, KIND_TEXTURE, rect)
        ImageStrategy.nineSlice(
            Rect(0.px,0.px,248.px,166.px),Rect(3.px,3.px,245.px,163.px),ctx.scale
        ).render(rect,ImageHolder(ImageSource.Resource("minecraft:textures/gui/demo_background.png"),256.px,256.px),Color.WHITE)
    }

    context(renderParam: GuiGraphics, ctx: DslScaleContext)
    override fun renderSlot(rect: Rect) {
        note(renderParam, KIND_TEXTURE, rect)
        ImageStrategy.nineSlice(
            Rect(7.px,141.px,25.px,159.px),Rect(8.px,142.px,24.px,158.px),ctx.scale
        ).render(rect,ImageHolder(ImageSource.Resource("minecraft:textures/gui/container/inventory.png"),256.px,256.px),Color.WHITE)
    }

    context(renderParam: GuiGraphics, ctx: DslScaleContext)
    override fun renderTooltip(rect: Rect) {
        // A tooltip is the platform's own drawing, not vertices this backend could replay out of
        // order: everything declared before it goes out first.
        barrier(renderParam)
        note(renderParam, KIND_TEXTURE, rect)
        stack {
            renderParam.pose().scale(ctx.scale.toFloat(),ctx.scale.toFloat(),1f)
            val rect = rect.div(ctx.scale).toInt().ifEmpty { return }
            TooltipRenderUtil.renderTooltipBackground(renderParam,rect.left,rect.top,rect.width,rect.height,0)
        }
    }

    context(renderParam: GuiGraphics, ctx: DslScaleContext)
    override fun renderItem(rect: Rect, item: String, count:Int, damage:Double?, enchanted: Boolean) {
        // Item rendering goes through the item renderer and its own depth state, so it is a barrier:
        // the list is replayed, then the item is drawn where it was declared.
        barrier(renderParam)
        note(renderParam, KIND_TEXTURE, rect)
        val item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.tryParse(item))
        if(!item.isPresent) blitTexture(ImageHolder(ImageSource.Resource("missing"), 16.px, 16.px),rect,Rect(0.px,0.px,16.px,16.px),Color.WHITE)
        else stack {
            RenderSystem.disableDepthTest()
            val itemStack = item.get().defaultInstance.also {
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
        // A scope change cannot be deferred: queued draws must be out before the scissors move.
        forceSubmit(renderParam)
        (rect / guiScale).toInt().run {
            renderParam.enableScissor(left,top,right,bottom)
        }
        try {
            block()
        } finally {
            forceSubmit(renderParam)
            renderParam.disableScissor()
        }
    }

    /**
     * The perspective transform that is currently in effect, or null when there is none.
     *
     * The platform's GUI vertex path applies a pose matrix affinely: it computes
     * `x' = m00*x + m10*y + m20*z + m30` and never divides by `w'`, so the third row of the matrix
     * is dropped and a perspective would look exactly like its affine part. While a perspective is
     * active this backend therefore keeps the transform here, projects the quad's corners itself
     * and draws them with the identity matrix.
     */
    private var perspective: Perspective? = null

    private val identity = Matrix4f()

    /** The homogeneous part of a [Transform], enough to project a point and to nest transforms. */
    private class Perspective(
        private val m00: Float, private val m01: Float, private val m02: Float,
        private val m10: Float, private val m11: Float, private val m12: Float,
        private val m20: Float, private val m21: Float, private val m22: Float
    ) {
        val isAffine get() = m20 == 0f && m21 == 0f && m22 == 1f

        /** [x],[y] after the divide by `w`, in the space this transform maps into. */
        fun project(x: Float, y: Float): Pair<Float, Float> {
            val w = m20 * x + m21 * y + m22
            if (w == 0f) return x to y
            return (m00 * x + m01 * y + m02) / w to (m10 * x + m11 * y + m12) / w
        }

        /**
         * The same divide, into [out], and `false` when there is no screen position to record.
         *
         * A corner at or behind the eye (`w <= 0`) has no finite image in front of the camera, so a
         * draw that lands there cannot be given vertices a sorter could place; the caller has to keep
         * it where it was declared instead of moving it.
         */
        fun project(x: Float, y: Float, out: FloatArray): Boolean {
            val w = m20 * x + m21 * y + m22
            if (w <= 0f || !w.isFinite()) return false
            val px = (m00 * x + m01 * y + m02) / w
            val py = (m10 * x + m11 * y + m12) / w
            if (!px.isFinite() || !py.isFinite()) return false
            out[0] = px
            out[1] = py
            return true
        }

        /** The matrix that applies [other] first and then this one. */
        fun concat(other: Perspective) = Perspective(
            m00 * other.m00 + m01 * other.m10 + m02 * other.m20,
            m00 * other.m01 + m01 * other.m11 + m02 * other.m21,
            m00 * other.m02 + m01 * other.m12 + m02 * other.m22,
            m10 * other.m00 + m11 * other.m10 + m12 * other.m20,
            m10 * other.m01 + m11 * other.m11 + m12 * other.m21,
            m10 * other.m02 + m11 * other.m12 + m12 * other.m22,
            m20 * other.m00 + m21 * other.m10 + m22 * other.m20,
            m20 * other.m01 + m21 * other.m11 + m22 * other.m21,
            m20 * other.m02 + m21 * other.m12 + m22 * other.m22
        )
    }

    context(renderParam: GuiGraphics)
    override fun withTransform(transform: Transform, block: () -> Unit) {
        val next = transform.run { Perspective(m00,m01,m02,m10,m11,m12,m20,m21,m22) }
        if (!next.isAffine) {
            val previous = perspective
            perspective = previous?.concat(next) ?: next
            try {
                block()
            } finally {
                perspective = previous
            }
            return
        }
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
        note(renderParam, KIND_TEXTURE, rect)
        ImageStrategy.repeat(scale = ctx.scale).render(
            rect,
            ImageHolder(ImageSource.Resource(Screen.BACKGROUND_LOCATION.toString()), 32.px, 32.px),
            Color(0.25,0.25,0.25)
        )
    }

    override fun getFont(name: String?) = defaultFont

    private val defaultFont = object : DslFont<GuiGraphics> {
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
            val left = x.pixelsOrWarn<Float> { return }
            val top = y.pixelsOrWarn<Float> { return }
            val type = glyph.renderType(Font.DisplayMode.NORMAL)
            val matrix = renderParam.pose().last().pose()
            if(reorder) {
                // The glyph's own vertex order is kept, but the consumer records instead of drawing:
                // the matrix is applied on the way in, so the entry holds the final screen vertices.
                record(KIND_TEXT, text = type) { vc ->
                    renderGlyph(char,glyph,left,top,matrix,vc,color,LightTexture.FULL_BRIGHT)
                }
            } else {
                renderGlyph(char,glyph,left,top,matrix,renderParam.bufferSource.getBuffer(type),color,LightTexture.FULL_BRIGHT)
            }
            if(boldOffset != 0.px) renderCharOnly(char,glyph,x + boldOffset,y,color,0.px)
        }

        private fun renderGlyph(
            char: DslRenderableChar, glyph: BakedGlyph, x: Float, y: Float, matrix: Matrix4f,
            consumer: VertexConsumer, color: Color, light: Int
        ) {
            glyph.render(
                char.style.isItalic,x,y,matrix,consumer,
                color.rFloat,color.gFloat,color.bFloat,color.aFloat,light
            )
        }

        context(renderParam: GuiGraphics)
        private fun renderStrike(char: DslRenderableChar, dslGlyph: DslGlyph, x: Measure, y: Measure, color: Color, scale: Double) {
            if(!char.style.isStrikeThrough) return
            fillRect(
                Rect(
                    left = x - 1.px * scale,
                    right = x + dslGlyph.advance(char.style) * scale,
                    top = y + 3.5.px * scale,
                    bottom = y + 4.5.px * scale
                ),color
            )
        }

        context(renderParam: GuiGraphics)
        private fun renderUnderline(char: DslRenderableChar, dslGlyph: DslGlyph, x: Measure, y: Measure, color: Color, scale: Double) {
            if(!char.style.isUnderlined) return
            fillRect(
                Rect(
                    left = x - 1.px * scale,
                    right = x + dslGlyph.advance(char.style) * scale,
                    top = y + 8.px * scale,
                    bottom = y + 9.px * scale
                ),color
            )
        }

        context(renderParam: GuiGraphics)
        override fun renderChar(char: DslRenderableChar, x: Measure, y: Measure, effectLeft: Measure, effectRight: Measure) {
            note(renderParam, KIND_TEXT)
            if(orderStats) {
                // One glyph is one quad; its box is the advance the layout already knows about.
                val left = x.pixelsOrElse<Float> { 0f }
                val top = y.pixelsOrElse<Float> { 0f }
                val height = char.size.pixelsOrElse<Float> { 0f }
                recordOrder(KIND_TEXT, left, top, left + height, top + height)
            }
            val dslGlyph = glyph(char.code)
            val glyph = if(char.style.isObfuscated) fontSet.getRandomGlyph(dslGlyph.glyph) else fontSet.getGlyph(char.code)
            val scale = char.size / lineHeight
            val right = glyph.right
            val down = glyph.down
            glyph.right = glyph.left + scale.toFloat() * (glyph.right - glyph.left)
            glyph.down = glyph.up + scale.toFloat() * (glyph.down - glyph.up)
            if(char.style.isShadowed) {
                val xShadow = x + dslGlyph.shadowOffset * scale
                val yShadow = y + dslGlyph.shadowOffset * scale
                val colorShadow = char.color.change(
                    r = char.color.rInt / 4,
                    g = char.color.gInt / 4,
                    b = char.color.bInt / 4
                )
                renderCharOnly(char,glyph,xShadow,yShadow,colorShadow,if(char.style.isBold) dslGlyph.boldOffset else 0.px)
                renderCharOnly(char,glyph,x,y,char.color,if(char.style.isBold) dslGlyph.boldOffset else 0.px)
                renderUnderline(char,dslGlyph,xShadow,yShadow,colorShadow,scale)
                renderUnderline(char,dslGlyph,x,y,char.color,scale)
                renderStrike(char,dslGlyph,xShadow,yShadow,colorShadow,scale)
                renderStrike(char,dslGlyph,x,y,char.color,scale)
            } else {
                renderCharOnly(char,glyph,x,y,char.color,if(char.style.isBold) dslGlyph.boldOffset else 0.px)
                renderUnderline(char,dslGlyph,x,y,char.color,scale)
                renderStrike(char,dslGlyph,x,y,char.color,scale)
            }
            glyph.right = right
            glyph.down = down
        }
    }
}
