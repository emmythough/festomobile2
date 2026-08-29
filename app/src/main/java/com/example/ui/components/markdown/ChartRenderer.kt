package com.example.ui.components.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.FestoTheme
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import org.json.JSONObject

data class ChartSpec(
    val type: String, // "line", "bar", "column"
    val title: String?,
    val series: List<ChartSeries>,
    val xLabels: List<String>
)

data class ChartSeries(
    val label: String,
    val data: List<Double>
)

fun parseChartSpec(rawJson: String): ChartSpec? {
    return try {
        val obj = JSONObject(rawJson.trim())
        val type = obj.optString("type", "line").lowercase()
        val title = if (obj.has("title")) obj.getString("title") else null

        val seriesList = mutableListOf<ChartSeries>()
        if (obj.has("series")) {
            val seriesArr = obj.getJSONArray("series")
            for (i in 0 until seriesArr.length()) {
                val sObj = seriesArr.getJSONObject(i)
                val label = sObj.optString("label", "Series ${i + 1}")
                val dataArr = sObj.getJSONArray("data")
                val dataList = mutableListOf<Double>()
                for (j in 0 until dataArr.length()) {
                    dataList.add(dataArr.getDouble(j))
                }
                seriesList.add(ChartSeries(label, dataList))
            }
        }

        val xLabelsList = mutableListOf<String>()
        if (obj.has("x_labels")) {
            val labelsArr = obj.getJSONArray("x_labels")
            for (i in 0 until labelsArr.length()) {
                xLabelsList.add(labelsArr.getString(i))
            }
        }

        if (seriesList.isEmpty() || seriesList.all { it.data.isEmpty() }) {
            null
        } else {
            ChartSpec(type, title, seriesList, xLabelsList)
        }
    } catch (e: Exception) {
        null
    }
}

@Composable
fun MessageChartBlock(
    spec: ChartSpec,
    modifier: Modifier = Modifier
) {
    val extendedColors = FestoTheme.colors
    val modelProducer = remember { CartesianChartModelProducer() }
    val isColumn = spec.type == "bar" || spec.type == "column"

    LaunchedEffect(spec) {
        modelProducer.runTransaction {
            if (isColumn) {
                columnSeries {
                    spec.series.forEach { s ->
                        series(s.data)
                    }
                }
            } else {
                lineSeries {
                    spec.series.forEach { s ->
                        series(s.data)
                    }
                }
            }
        }
    }

    val bottomAxisFormatter = remember(spec.xLabels) {
        CartesianValueFormatter { _, x, _ ->
            val index = x.toInt()
            if (index in spec.xLabels.indices) {
                spec.xLabels[index]
            } else {
                "${index + 1}"
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(extendedColors.surfaceDialog)
            .border(1.dp, extendedColors.borderHairline, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Column {
            // Header Row: Icon + Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = if (isColumn) Icons.Rounded.BarChart else Icons.Rounded.ShowChart,
                    contentDescription = null,
                    tint = extendedColors.brandNova,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = spec.title ?: "Chart Visualization",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Chart Render
            CartesianChartHost(
                chart = rememberCartesianChart(
                    if (isColumn) rememberColumnCartesianLayer() else rememberLineCartesianLayer(),
                    startAxis = VerticalAxis.rememberStart(),
                    bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = bottomAxisFormatter),
                ),
                modelProducer = modelProducer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )

            // Series Legend
            if (spec.series.size > 1 || (spec.series.isNotEmpty() && spec.series[0].label.isNotBlank())) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    spec.series.forEach { s ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(extendedColors.brandNova)
                            )
                            Text(
                                text = s.label,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = extendedColors.inkTertiary
                            )
                        }
                    }
                }
            }
        }
    }
}
