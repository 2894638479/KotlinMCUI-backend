# Draw reordering

**This is the default path.** `-Dkotlinmcui.order.reorder=false` turns it off for diagnostics - the
immediate path keeps drawing one call at a time, but with the framework's `flush()` gone it no longer
matches the order contract, so its picture is not trustworthy. The reorder path records a frame's draws
instead of submitting them, and replays
them in an order that groups equal render types while keeping every overlapping pair in declared
order. `-Dkotlinmcui.order.assert=false` turns off the check described below; it is only useful to
prove that the check fires. Nothing changes when the switch is absent.

## Why the order is not free

`DslScope.render()` draws `children.asReversed()`, so an earlier declaration is painted on top. A
draw may therefore only move across draws it cannot touch. Two draws whose vertex bounds do not
intersect share no pixel, so swapping them cannot change the picture; two that overlap are pinned.

The bounds are the ones the recorded vertices span, which is exact for the triangles this backend
emits (a triangle paints only inside its vertices' box).

## Display list

One draw is one entry: kind (`fill` / `texture` / `text`), a type key, the vertex range it owns, and
its box. Vertices live in parallel `FloatArray`/`IntArray` pools (`vx vy vz vColor vu vv vLightU
vLightV`), so recording a vertex is a handful of stores and no object. Entries, the sort's scratch
arrays and the type table are pooled and only grow when a frame needs more room than any frame before
it; a frame of the same size allocates nothing.

A type key is what a batch is: equal keys may share one submit. Fill is one key, a texture is one key
per `ResourceLocation`, text is one key per glyph `RenderType`. Keys are assigned per frame by first
appearance (`typeKeyOf`) and survive the barriers below, so the alternation count holds across a
whole frame.

Vertices are recorded **in screen space**: the draw's own `VertexConsumer.vertex(Matrix4f,...)` does
the transformation, so the matrix is never stored with a draw and a replay cannot apply it twice.

## Recording

- `renderQuad` / `fillRect` / `renderDefaultBackground` / `renderButton` / `renderContainer` /
  `renderSlot`: the colour path records six vertices (the two triangles `(lt,rt,lb)` and
  `(rt,rb,lb)`), the textured path records the same split with uv.
- The uniform-colour textured path records the four corners `(i,k) (i,l) (j,l) (j,k)` in
  `GuiGraphics.innerBlit`'s order and replays them as `(0,1,2)` and `(2,3,0)`, which is the split
  mc's own quad index buffer uses.
- `renderTriangles` records its list verbatim.
- Text: the glyph consumer is a recording `VertexConsumer`, so glyph quads take part in the sort.

## Replay

A run is a maximal group of one type key. It becomes one submit: the immediate paths write their own
vertices and call `BufferUploader.drawWithShader`, text goes through
`renderParam.bufferSource.getBuffer(glyphType)` and is submitted by the next run's flush (mc's buffer
source submits the previous batch when the type changes). `count` therefore means "batches that went
out" in both modes.

Text cannot use the immediate path: its shader samples the lightmap and only the text render type
sets that up. The immediate path is chosen for fills and textures because it is exactly the state the
unbatched backend already uses there, which is what keeps the pixels identical - a custom
POSITION_COLOR_TEX render type would also have to reproduce the cull and depth shards.

## Barriers

A barrier replays the list, then lets the draw happen where it was declared:

- `renderItem` and `renderTooltip`: the platform's own drawing, not vertices this backend could move.
- Scissor entry and exit: clipping is raster state, so only draws inside one clip may reorder among
  themselves.
- A perspective projection that puts a corner at or behind the eye (`w <= 0`, or a non-finite
  result): that corner has no screen position, so the draw cannot be given vertices a sorter could
  place. It is drawn immediately after the list is replayed.

The framework no longer has `flush()`: the interface states the order contract instead, and a backend
is free to batch, defer or reorder as long as it renders the same pixels. This display list is mc's way
of meeting that contract - it keeps the declared order for every pair whose pixels touch and reorders
only what cannot be seen.

## Transform and perspective

`vertex(Matrix4f,...)` multiplies the four-vector and drops `w`; `withTransform` keeps a
non-affine transform out of the pose and `renderQuad` projects the corners itself, writing them with
the identity matrix. Both cases therefore end in the same place: the recorded vertices are the bytes
the platform would have written. A perspective does not apply to text (`glyph.render` gets the pose's
own matrix), so recorded text matches what is drawn, but text does not follow a perspective - that
predates this switch.

## Assert

With the switch on, the sorter's output is checked after sorting: for every declared pair that
overlaps, the replay position of the first must be smaller. A violation throws
`IllegalStateException` naming both draws' types and rectangles. Logging alone would let a wrong
picture through.

## Measurement

`-Dkotlinmcui.flush.stats=true` prints `FLUSHFRAME count= alts= runs= order= scissors= us=
frame_us=`. Under the switch `count` is the replayed batch count, `alts` the type changes of the
submitted sequence, and `us` the time spent in the replay (flush plus draw) rather than in
`GuiGraphics.flush()` alone.

## Verifying a picture against a baseline

Two runs of the *same* build do not produce identical pixels on every page: enchanted items render a
time-based glint, and the font atlas packs glyphs in an order that varies with what the game drew
before the page (the title screen's splash text). Text therefore gets different atlas coordinates per
launch, which flips antialiased edge texels. Compare the draw stream (type, vertex count, positions,
colours, light) when a text- or glint-heavy page has to be compared, or compare pages whose glyphs
are already baked. `ImageChops.difference(...).getbbox()` ignores an RGBA difference whose alpha is
zero and reports "identical" for pictures that differ; compare raw buffers.
