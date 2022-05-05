import org.knowm.xchart.VectorGraphicsEncoder.VectorGraphicsFormat
import org.knowm.xchart.XYSeries.XYSeriesRenderStyle
import org.knowm.xchart.style.Styler.{ChartTheme, LegendLayout, LegendPosition}
import org.knowm.xchart.style.colors.XChartSeriesColors
import org.knowm.xchart.style.lines.SeriesLines
import org.knowm.xchart.style.markers.SeriesMarkers
import org.knowm.xchart.{VectorGraphicsEncoder, XYChartBuilder}

import java.awt.{BasicStroke, Color}
import java.io.ByteArrayOutputStream

object Line {
  def apply(username: String, xData: Array[Int], yData: Array[Int]): Line = {
    new Line(username, xData, yData)
  }
}

class Line(val username: String, val xData: Array[Int], val yData: Array[Int]) {
  def getBytes: Array[Byte] = {
    val height = 1024
    val width = 600
    val chart = new XYChartBuilder()
      .title(s"$username's Github Contribution Line")
      .width(height)
      .height(width)
      .theme(ChartTheme.Matlab)
      .xAxisTitle("Years")
      .yAxisTitle("Commits")
      .build()

    val series = chart.addSeries("Commits", xData, yData)
    series.setMarkerColor(Color.orange)
    series.setLineColor(XChartSeriesColors.GREEN)
    series.setXYSeriesRenderStyle(XYSeriesRenderStyle.Line)
    series.setMarker(SeriesMarkers.CIRCLE)
    series.setLineStyle(SeriesLines.SOLID)

    val style = chart.getStyler

    style.setDefaultSeriesRenderStyle(XYSeriesRenderStyle.Line)
    style.setPlotMargin(0)

    style.setSeriesLines(Array(
      new BasicStroke(3f),
      new BasicStroke(3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1.0f, Array(2f, 2f), 2.0f)
    ))

    style.setChartTitleBoxBackgroundColor(Color.WHITE)
    style.setChartTitleBoxBorderColor(Color.WHITE)
    style.setChartTitleBoxVisible(false)
    style.setChartTitlePadding(3)

    // Legend: OutsideE, InsideNW, InsideNE, InsideSE, InsideSW, InsideN, InsideS, OutsideS
    style.setLegendBackgroundColor(Color.WHITE)
    style.setLegendBorderColor(Color.WHITE)
    style.setLegendPosition(LegendPosition.InsideNE)
    style.setLegendLayout(LegendLayout.Vertical)
    style.setLegendPadding(10)

    // Grid and background colors
    style.setPlotBackgroundColor(Color.WHITE)
    style.setChartBackgroundColor(Color.WHITE)
    style.setPlotGridLinesColor(Color.lightGray)

    //Border
    style.setPlotBorderVisible(true)
    style.setPlotBorderColor(Color.BLACK)

    style.setChartBackgroundColor(Color.WHITE)
    style.setDefaultSeriesRenderStyle(XYSeriesRenderStyle.Line)
    style.setDecimalPattern("####")
    style.setLegendVisible(false)

    // Axis
    style.setAxisTickLabelsColor(Color.BLACK)
    //style.setYAxisLabelAlignment(Styler.TextAlignment.Right)
    val divider = if (xData.length * 0.5 < 1) 1 else (xData.length * 0.5).toInt
    style.setXAxisTickMarkSpacingHint(width / divider)
    style.setYAxisTickMarkSpacingHint(50)

    style.setYAxisMin(yData.minBy(x => x).toDouble)
    style.setXAxisMin(xData.minBy(x => x).toDouble)
    style.setAntiAlias(true)

    val os = new ByteArrayOutputStream()
    VectorGraphicsEncoder.saveVectorGraphic(chart, os, VectorGraphicsFormat.SVG)
    os.toByteArray
  }
}
