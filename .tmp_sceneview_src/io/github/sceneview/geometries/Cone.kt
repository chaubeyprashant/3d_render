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
import io.github.sceneview.math.Size
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class Cone private constructor(
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
    sideCount: Int
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
        var sideCount: Int = DEFAULT_SIDE_COUNT
            private set

        fun radius(radius: Float) = apply { this.radius = radius }
        fun height(height: Float) = apply { this.height = height }
        fun center(center: Position) = apply { this.center = center }
        fun sideCount(sideCount: Int) = apply { this.sideCount = sideCount }

        override fun build(engine: Engine): Cone {
            vertices(getVertices(radius, height, center, sideCount))
            primitivesIndices(getIndices(sideCount))
            return build(engine) { vertexBuffer, indexBuffer, offsets, boundingBox ->
                Cone(
                    primitiveType, vertices, vertexBuffer, indices, indexBuffer, offsets,
                    boundingBox, radius, height, center, sideCount
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
    var sideCount: Int = sideCount
        private set

    fun update(
        engine: Engine,
        radius: Float = this.radius,
        height: Float = this.height,
        center: Position = this.center,
        sideCount: Int = this.sideCount
    ) = apply {
        update(
            engine = engine,
            vertices = getVertices(radius, height, center, sideCount),
            primitivesIndices = if (sideCount != this.sideCount) {
                getIndices(sideCount)
            } else {
                primitivesIndices
            }
        )
        this.radius = radius
        this.height = height
        this.center = center
        this.sideCount = sideCount
    }

    companion object {
        val DEFAULT_RADIUS = 1.0f
        val DEFAULT_HEIGHT = 2.0f
        val DEFAULT_CENTER = Position(0.0f)
        val DEFAULT_SIDE_COUNT = 24

        fun getVertices(radius: Float, height: Float, center: Position, sideCount: Int) =
            buildList {
                val halfHeight = height / 2
                val thetaIncrement = TWO_PI / sideCount
                // Slope angle for smooth normals along the cone surface
                val slopeAngle = atan2(radius, height)
                val ny = sin(slopeAngle)
                val nScale = cos(slopeAngle)

                // Tip vertex (repeated per side for correct normals)
                val tipPosition = center + Size(y = halfHeight)

                // Side vertices
                for (side in 0..sideCount) {
                    val theta = thetaIncrement * side
                    val cosTheta = cos(theta)
                    val sinTheta = sin(theta)

                    // Base edge vertex
                    val basePosition = Position(
                        x = radius * cosTheta,
                        y = -halfHeight,
                        z = radius * sinTheta
                    ) + center

                    val sideNormal = normalize(
                        Direction(
                            x = nScale * cosTheta,
                            y = ny,
                            z = nScale * sinTheta
                        )
                    )

                    // Base vertex (side)
                    add(
                        Vertex(
                            position = basePosition,
                            normal = sideNormal,
                            uvCoordinate = UvCoordinate(
                                x = side.toFloat() / sideCount,
                                y = 0.0f
                            )
                        )
                    )

                    // Tip vertex (side)
                    add(
                        Vertex(
                            position = tipPosition,
                            normal = sideNormal,
                            uvCoordinate = UvCoordinate(
                                x = (side + 0.5f) / sideCount,
                                y = 1.0f
                            )
                        )
                    )
                }

                // Base cap center
                add(
                    Vertex(
                        position = center + Size(y = -halfHeight),
                        normal = Direction(y = -1.0f),
                        uvCoordinate = UvCoordinate(x = 0.5f, y = 0.5f)
                    )
                )

                // Base cap ring vertices
                for (side in 0..sideCount) {
                    val theta = thetaIncrement * side
                    add(
                        Vertex(
                            position = Position(
                                x = radius * cos(theta),
                                y = -halfHeight,
                                z = radius * sin(theta)
                            ) + center,
                            normal = Direction(y = -1.0f),
                            uvCoordinate = UvCoordinate(
                                x = (cos(theta) + 1.0f) / 2.0f,
                                y = (sin(theta) + 1.0f) / 2.0f
                            )
                        )
                    )
                }
            }

        fun getIndices(sideCount: Int) = buildList {
            val triangleIndices = mutableListOf<Int>()
            // Side triangles: each side has 2 vertices (base, tip)
            for (side in 0 until sideCount) {
                val base = side * 2
                val tip = base + 1
                val nextBase = base + 2
                triangleIndices.addAll(listOf(base, tip, nextBase))
            }
            // Base cap triangles
            val capCenter = (sideCount + 1) * 2
            for (side in 0 until sideCount) {
                triangleIndices.addAll(
                    listOf(capCenter, capCenter + side + 1, capCenter + side + 2)
                )
            }
            add(triangleIndices)
        }
    }
}
