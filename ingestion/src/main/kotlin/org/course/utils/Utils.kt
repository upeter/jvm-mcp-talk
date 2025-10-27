package org.course.utils

import org.apache.commons.math3.linear.*
import org.apache.commons.math3.linear.MatrixUtils
import org.apache.commons.math3.linear.SingularValueDecomposition
import org.apache.commons.math3.ml.clustering.*
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import org.jetbrains.kotlinx.kandy.dsl.plot
import org.jetbrains.kotlinx.kandy.ir.Plot
import org.jetbrains.kotlinx.kandy.letsplot.feature.layout
import org.jetbrains.kotlinx.kandy.letsplot.layers.points
import org.jetbrains.kotlinx.kandy.letsplot.layers.tiles
import org.jetbrains.kotlinx.kandy.letsplot.tooltips.tooltips
import org.jetbrains.kotlinx.kandy.letsplot.x
import org.jetbrains.kotlinx.kandy.letsplot.y
import kotlin.math.min
import kotlin.text.get

data class SimilarityMatrixEntry(val sentence1: String, val sentence2: String, val similarity: Double)

fun List<SimilarityMatrixEntry>.plotMatrix() =
    dataFrameOf(
        "Sentence1" to this.map { it.sentence1 },
        "Sentence2" to this.map { it.sentence2 },
        "Similarity" to this.map { it.similarity }
    ).plot {
        tiles {
            x("Sentence1")
            y("Sentence2")
            fillColor("Similarity")
        }
        layout {
            title = "Sentence Similarity Heatmap (Cosine Similarity)"
            size = 800 to 600
        }
    }



data class EmbeddingPoint(private val coordinates: List<Double>, val originalIndex: Int) : Clusterable {
    override fun getPoint(): DoubleArray = coordinates.toDoubleArray()
}

/**
 * Performs K-means clustering on a list of high-dimensional embeddings using the K-means++ initialization algorithm.
 *
 * This function converts the embedding vectors into clusterable points and applies the K-means++ clustering
 * algorithm to group them into k clusters. The K-means++ initialization helps select initial centroids
 * that are spread out, leading to better clustering results compared to random initialization.
 *
 * @param embeddings A list of embedding vectors, where each vector is represented as a List<Double>
 * @param k The number of clusters to create (must be positive and typically less than the number of embeddings)
 * @return A list of CentroidCluster objects, each containing the points assigned to that cluster and the cluster's centroid
 *
 * @throws IllegalArgumentException if k is less than 1 or greater than the number of embeddings
 *
 * @see EmbeddingPoint
 * @see KMeansPlusPlusClusterer
 * @see CentroidCluster
 */
fun performKMeansClustering(embeddings: List<List<Double>>, k: Int): List<CentroidCluster<EmbeddingPoint>> {
    val points = embeddings.mapIndexed { index, embedding -> EmbeddingPoint(embedding, index) }
    val clusterer = KMeansPlusPlusClusterer<EmbeddingPoint>(k, 100)
    return clusterer.cluster(points)
}


data class ClusterData(val clusterName:String, val values:List<String>)

fun List<CentroidCluster<EmbeddingPoint>>.valuesPerCluster(values: List<String>):List<ClusterData> {
    // Group values by cluster functionally
    val valuesByCluster = clusterAssignments()
        .mapIndexed { index, clusterIndex -> clusterIndex to values[index] }
        .groupBy({ it.first }, { it.second })

    // map values for each cluster
    return valuesByCluster.map { (clusterIndex, valuesInCluster) ->
        ClusterData("=== Cluster $clusterIndex (${valuesInCluster.size} items) ===", valuesInCluster)
    }.sortedBy { it.clusterName }

}


fun List<CentroidCluster<EmbeddingPoint>>.clusterAssignments() = this
    .flatMapIndexed { clusterIndex, cluster ->
        cluster.points.map { point -> point.originalIndex to clusterIndex }
    }
    .sortedBy { it.first }
    .map { it.second }
    .toIntArray()

fun List<CentroidCluster<EmbeddingPoint>>.printValuesPerCluster(titles:List<String>) {
    this.valuesPerCluster(titles).forEach { (cluster, values) ->
        println(cluster)
        values.forEachIndexed { idx, value -> println("  ${idx + 1}. $value") }
        println()
    }
}

fun List<CentroidCluster<EmbeddingPoint>>.plotClusterValues(embeddingVectors:List<List<Double>>, titles:List<String>): Plot {
    val embeddings2D = performPCA(embeddingVectors, 2)
    println("Reduced embeddings to 2D: ${embeddings2D.size} points")

    val plotData = dataFrameOf(
        "x" to embeddings2D.map { it[0] },
        "y" to embeddings2D.map { it[1] },
        "cluster" to this.clusterAssignments().map { "Cluster $it" },
        "title" to titles
    )
    println("Prepared plot data with ${plotData.rowsCount()} points")

    return plotData.plot {
        points {
            x("x")
            y("y")
            color("cluster")
            size = 4.0
            tooltips("title", "cluster")
        }

        layout {
            title = "K-Means Clustering of Conference Session Titles (PCA Projection)"
            xAxisLabel = "First Principal Component"
            yAxisLabel = "Second Principal Component"
            size = 800 to 600
        }

    }

}



data class PCAResult(
    val projected: List<List<Double>>,
    val explainedVariance: DoubleArray,           // per component
    val explainedVarianceRatio: DoubleArray       // per component
)

/**
 * Projects high-dimensional data into a lower-dimensional space using Principal Component Analysis (PCA).
 *
 * This function centers the input data, computes the principal components via Singular Value Decomposition (SVD),
 * and returns the data projected onto the top principal components. PCA is commonly used for visualization,
 * dimensionality reduction, and exploratory data analysis.
 *
 * @param data A list of embedding vectors, where each vector is represented as a List<Double>.
 * @param targetDimensions The number of principal components to project onto (default is 2).
 * @return A list of projected vectors in the lower-dimensional space.
 *
 * @throws IllegalArgumentException if data is empty.
 *
 * @see SingularValueDecomposition
 * @see PCAResult
 *
 * Example usage:
 * ```
 * val projected = performPCA(embeddings, targetDimensions = 2)
 * // projected is a List<List<Double>> of 2D coordinates
 * ```
 */
fun performPCA(
    data: List<List<Double>>,
    targetDimensions: Int = 2
): List<List<Double>> {
    require(data.isNotEmpty()) { "data must not be empty" }
    val n = data.size
    val d = data[0].size
    val k = min(targetDimensions, min(n, d))

    val X = MatrixUtils.createRealMatrix(Array(n) { i -> data[i].toDoubleArray() })

    // center
    val means = DoubleArray(d) { j -> (0 until n).sumOf { X.getEntry(it, j) } / n }
    val Xc = X.copy()
    for (i in 0 until n) for (j in 0 until d) Xc.setEntry(i, j, X.getEntry(i, j) - means[j])

    // SVD: Xc = U S V^T
    val svd = SingularValueDecomposition(Xc)
    val U = svd.u                     // n x r
    val S = svd.s                     // r x r diagonal (as matrix)
    val V = svd.v                     // d x r
    val r = min(min(U.columnDimension, V.columnDimension), min(n, d))
    val kk = min(k, r)

    // Components are columns of V; singular values relate to explained variance
    val singularValues = svd.singularValues
    val explainedVar = DoubleArray(kk) { i ->
        val s = singularValues[i]
        (s * s) / (n - 1)            // eigenvalues of covariance
    }
    val totalVar = singularValues.sumOf { (it * it) / (n - 1) }.coerceAtLeast(1e-12)
    val explainedRatio = DoubleArray(kk) { explainedVar[it] / totalVar }

    // Project: Y = Xc * V_k
    val Vk = V.getSubMatrix(0, d - 1, 0, kk - 1)
    val Y = Xc.multiply(Vk)

    val projected = List(n) { i -> List(kk) { j -> Y.getEntry(i, j) } }
    return PCAResult(projected, explainedVar, explainedRatio).projected
}
