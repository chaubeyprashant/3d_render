package io.github.sceneview.geometries

import com.google.android.filament.Box
import com.google.android.filament.Engine
import com.google.android.filament.IndexBuffer
import com.google.android.filament.RenderableManager.PrimitiveType
import com.google.android.filament.VertexBuffer
import dev.romainguy.kotlin.math.TWO_PI
import dev.romainguy.kotlin.math.normalize
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Position
import kotlin.math.cos
import kotlin.math.sin

class Torus private constructor(
    primitiveType: PrimitiveType,
    vertices: List<Vertex>,
    vertexBuffer: VertexBuffer,
    indices: List<List<Int>>,
    indexBuffer: IndexBuffer,
    primitivesOffsets: List<IntRange>,
    boundingBox: Box,
    majorRadius: Float,
    minorRadius: Float,
    center: Position,
    majorSegments: Int,
    minorSegments: Int
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
        var majorRadius: Float = DEFAULT_MAJOR_RADIUS
            private set
        var minorRadius: Float = DEFAULT_MINOR_RADIUS
            private set
        var center: Position = DEFAULT_CENTER
            private set
        var majorSegments: Int = DEFAULT_MAJOR_SEGMENTS
            private set
        var minorSegments: Int = DEFAULT_MINOR_SEGMENTS
            private set

        fun majorRadius(majorRadius: Float) = apply { this.majorRadius = majorRadius }
        fun minorRadius(minorRadius: Float) = apply { this.minorRadius = minorRadius }
        fun center(center: Position) = apply { this.center = center }
        fun majorSegments(majorSegments: Int) = apply { this.majorSegments = majorSegments }
        fun minorSegments(minorSegments: Int) = apply { this.minorSegments = minorSegments }

        override fun build(engine: Engine): Torus {
            vertices(getVertices(majorRadius, minorRadius, center, majorSegments, minorSegments))
            primitivesIndices(getIndices(majorSegments, minorSegments))
            return build(engine) { vertexBuffer, indexBuffer, offsets, boundingBox ->
                Torus(
                    primitiveType, vertices, vertexBuffer, indices, indexBuffer, offsets,
                    boundingBox, majorRadius, minorRadius, center, majorSegments, minorSegments
                )
            }
        }
    }

    var majorRadius: Float = majorRadius
        private set
    var minorRadius: Float = minorRadius
        private set
    var center: Position = center
        private set
    var majorSegments: Int = majorSegments
        private set
    var minorSegments: Int = minorSegments
        private set

    fun update(
        engine: Engine,
        majorRadius: Float = this.majorRadius,
        minorRadius: Float = this.minorRadius,
        center: Position = this.center,
        majorSegments: Int = this.majorSegments,
        minorSegments: Int = this.minorSegments
    ) = apply {
        update(
            engine = engine,
            vertices = getVertices(majorRadius, minorRadius, center, majorSegments, minorSegments),
            primitivesIndices = if (majorSegments != this.majorSegments || minorSegments != this.minorSegments) {
                getIndices(majorSegments, minorSegments)
            } else {
                primitivesIndices
            }
        )
        this.majorRadius = majorRadius
        this.minorRadius = minorRadius
        this.center = center
        this.majorSegments = majorSegments
        this.minorSegments = minorSegments
    }

    companion object {
        val DEFAULT_MAJOR_RADIUS = 1.0f
        val DEFAULT_MINOR_RADIUS = 0.3f
        val DEFAULT_CENTER = Position(0.0f)
        val DEFAULT_MAJOR_SEGMENTS = 32
        val DEFAULT_MINOR_SEGMENTS = 16

        fun getVertices(
            majorRadius: Float, minorRadius: Float, center: Position,
            majorSegments: Int, minorSegments: Int
        ) = buildList {
            for (i in 0..majorSegments) {
                val u = i.toFloat() / majorSegments
                val theta = u * TWO_PI
                val cosTheta = cos(theta)
                val sinTheta = sin(theta)

                for (j in 0..minorSegments) {
                    val v = j.toFloat() / minorSegments
                    val phi = v * TWO_PI
                    val cosPhi = cos(phi)
                    val sinPhi = sin(phi)

                    val x = (majorRadius + minorRadius * cosPhi) * cosTheta
                    val y = minorRadius * sinPhi
                    val z = (majorRadius + minorRadius * cosPhi) * sinTheta

                    val normal = normalize(
                        Direction(
                            x = cosPhi * cosTheta,
                            y = sinPhi,
                            z = cosPhi * sinTheta
                        )
                    )

                    add(
                        Vertex(
                            position = Position(x, y, z) + center,
                            normal = normal,
                            uvCoordinate = UvCoordinate(x = u, y = v)
                        )
                    )
                }
            }
        }

        fun getIndices(majorSegments: Int, minorSegments: Int) = buildList {
            val stride = minorSegments + 1
            val triangleIndices = mutableListOf<Int>()
            for (i in 0 until majorSegments) {
                for (j in 0 until minorSegments) {
                    val a = i * stride + j
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
