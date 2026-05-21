package io.github.sceneview.geometries

import com.google.android.filament.Box
import com.google.android.filament.Engine
import com.google.android.filament.IndexBuffer
import com.google.android.filament.RenderableManager.PrimitiveType
import com.google.android.filament.VertexBuffer
import dev.romainguy.kotlin.math.HALF_PI
import dev.romainguy.kotlin.math.TWO_PI
import dev.romainguy.kotlin.math.normalize
import io.github.sceneview.math.Position
import kotlin.math.cos
import kotlin.math.sin

class Capsule private constructor(
    primitiveType: PrimitiveType,
    vertices: List<Vertex>,
    vertexBuffer: VertexBuffer,
    indices: List<List<Int>>,
    indexBuffer: IndexBuffer,
    primitivesOffsets: List<IntRange>,
    boundingBox: Box,
    radius: Float,
    height: Float,
    center: Position,
    capStacks: Int,
    sideSlices: Int
) : Geometry(
    primitiveType,
    vertices,
    vertexBuffer,
    indices,
    indexBuffer,
    primitivesOffsets,
    boundingBox
) {
    class Builder : Geometry.Builder(PrimitiveType.TRIANGLES) {
        var radius: Float = DEFAULT_RADIUS
            private set
        var height: Float = DEFAULT_HEIGHT
            private set
        var center: Position = DEFAULT_CENTER
            private set
        var capStacks: Int = DEFAULT_CAP_STACKS
            private set
        var sideSlices: Int = DEFAULT_SIDE_SLICES
            private set

        fun radius(radius: Float) = apply { this.radius = radius }
        fun height(height: Float) = apply { this.height = height }
        fun center(center: Position) = apply { this.center = center }
        fun capStacks(capStacks: Int) = apply { this.capStacks = capStacks }
        fun sideSlices(sideSlices: Int) = apply { this.sideSlices = sideSlices }

        override fun build(engine: Engine): Capsule {
            vertices(getVertices(radius, height, center, capStacks, sideSlices))
            primitivesIndices(getIndices(capStacks, sideSlices))
            return build(engine) { vertexBuffer, indexBuffer, offsets, boundingBox ->
                Capsule(
                    primitiveType, vertices, vertexBuffer, indices, indexBuffer, offsets,
                    boundingBox, radius, height, center, capStacks, sideSlices
                )
            }
        }
    }

    var radius: Float = radius
        private set
    var height: Float = height
        private set
    var center: Position = center
        private set
    var capStacks: Int = capStacks
        private set
    var sideSlices: Int = sideSlices
        private set

    fun update(
        engine: Engine,
        radius: Float = this.radius,
        height: Float = this.height,
        center: Position = this.center,
        capStacks: Int = this.capStacks,
        sideSlices: Int = this.sideSlices
    ) = apply {
        update(
            engine = engine,
            vertices = getVertices(radius, height, center, capStacks, sideSlices),
            primitivesIndices = if (capStacks != this.capStacks || sideSlices != this.sideSlices) {
                getIndices(capStacks, sideSlices)
            } else {
                primitivesIndices
            }
        )
        this.radius = radius
        this.height = height
        this.center = center
        this.capStacks = capStacks
        this.sideSlices = sideSlices
    }

    companion object {
        val DEFAULT_RADIUS = 0.5f
        val DEFAULT_HEIGHT = 2.0f
        val DEFAULT_CENTER = Position(0.0f)
        val DEFAULT_CAP_STACKS = 8
        val DEFAULT_SIDE_SLICES = 24

        fun getVertices(
            radius: Float, height: Float, center: Position,
            capStacks: Int, sideSlices: Int
        ) = buildList {
            val halfCylHeight = height / 2

            // Top hemisphere (from pole to equator)
            for (stack in 0..capStacks) {
                val phi = HALF_PI * (1.0f - stack.toFloat() / capStacks) // pi/2 -> 0
                val cosPhi = cos(phi)
                val sinPhi = sin(phi)
                val y = radius * sinPhi + halfCylHeight
                val ringRadius = radius * cosPhi
                val v = 0.5f * stack.toFloat() / capStacks // 0 -> 0.5 mapped to top cap

                for (slice in 0..sideSlices) {
                    val theta = TWO_PI * slice.toFloat() / sideSlices
                    val cosTheta = cos(theta)
                    val sinTheta = sin(theta)

                    add(
                        Vertex(
                            position = Position(ringRadius * cosTheta, y, ringRadius * sinTheta) + center,
                            normal = normalize(Position(cosPhi * cosTheta, sinPhi, cosPhi * sinTheta)),
                            uvCoordinate = UvCoordinate(
                                x = slice.toFloat() / sideSlices,
                                y = v
                            )
                        )
                    )
                }
            }

            // Bottom hemisphere (from equator to pole)
            for (stack in 1..capStacks) {
                val phi = -HALF_PI * stack.toFloat() / capStacks // 0 -> -pi/2
                val cosPhi = cos(phi)
                val sinPhi = sin(phi)
                val y = radius * sinPhi - halfCylHeight
                val ringRadius = radius * cosPhi
                val v = 0.5f + 0.5f * stack.toFloat() / capStacks // 0.5 -> 1.0

                for (slice in 0..sideSlices) {
                    val theta = TWO_PI * slice.toFloat() / sideSlices
                    val cosTheta = cos(theta)
                    val sinTheta = sin(theta)

                    add(
                        Vertex(
                            position = Position(ringRadius * cosTheta, y, ringRadius * sinTheta) + center,
                            normal = normalize(Position(cosPhi * cosTheta, sinPhi, cosPhi * sinTheta)),
                            uvCoordinate = UvCoordinate(
                                x = slice.toFloat() / sideSlices,
                                y = v
                            )
                        )
                    )
                }
            }
        }

        fun getIndices(capStacks: Int, sideSlices: Int) = buildList {
            val totalStacks = capStacks * 2 // top cap + bottom cap
            val stride = sideSlices + 1
            val triangleIndices = mutableListOf<Int>()

            for (stack in 0 until totalStacks) {
                for (slice in 0 until sideSlices) {
                    val a = stack * stride + slice
                    val b = a + stride
                    val c = a + 1
                    val d = b + 1
                    triangleIndices.addAll(listOf(a, b, d, a, d, c))
                }
            }
            add(triangleIndices)
        }
    }
}
