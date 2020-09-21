import java.awt.{BasicStroke, Color}
import java.io.ByteArrayOutputStream

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, _}
import akka.http.scaladsl.server.directives.CachingDirectives.{alwaysCache, routeCache}
import akka.http.scaladsl.server.{Directives, ExceptionHandler, RequestContext, Route}
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import akka.stream.scaladsl.{Flow, Sink, Source, SourceQueueWithComplete}
import akka.stream.{OverflowStrategy, QueueOfferResult}
import com.github.blemale.scaffeine.{Cache, Scaffeine}
import com.github.nscala_time.time.Imports.DateTime
import org.knowm.xchart.VectorGraphicsEncoder.VectorGraphicsFormat
import org.knowm.xchart.XYSeries.XYSeriesRenderStyle
import org.knowm.xchart.style.Styler
import org.knowm.xchart.style.Styler.{ChartTheme, LegendLayout, LegendPosition}
import org.knowm.xchart.style.colors.XChartSeriesColors
import org.knowm.xchart.style.lines.SeriesLines
import org.knowm.xchart.style.markers.SeriesMarkers
import org.knowm.xchart.{VectorGraphicsEncoder, XYChartBuilder}
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}


class GithubService(val token: String)(implicit system: ActorSystem, executionContext: ExecutionContext) extends Directives with JsonSupport {
  require(token != "")

  private def timed[T](f: => T, name: String): T = {
    val start = System.nanoTime()
    val ret = f
    val end = System.nanoTime()
    println(s"[$name] - Time taken: ${(end - start) / 1000 / 1000} ms")
    ret
  }

  val cache: Cache[String, Array[Byte]] =
    Scaffeine()
      .recordStats()
      .expireAfterWrite(24.hour)
      .maximumSize(500)
      .build[String, Array[Byte]]()

  val httpPool: Flow[(HttpRequest, Promise[HttpResponse]), (Try[HttpResponse], Promise[HttpResponse]), Http.HostConnectionPool] =
    Http().cachedHostConnectionPoolHttps[Promise[HttpResponse]]("api.github.com", settings = ConnectionPoolSettings(system))

  private def getContributions(userName: String, years: Array[Int]): (List[Int], List[Int]) = {

    def graphqlQuery(name: String, year: Int): String = {
      s"""{
         |  "query": "query {  user(login: \\"$name\\") { name  contributionsCollection(from: \\"$year-01-01T00:00:00\\" to:\\"$year-12-31T23:59:59\\" ) {startedAt contributionYears  contributionCalendar { totalContributions weeks { contributionDays {contributionCount date} } } } } }"
         |}""".stripMargin
    }

    val request = HttpRequest(HttpMethods.POST, "/graphql")
      .withHeaders(headers.RawHeader("Authorization", s"Bearer $token"))

    val q = RequestQueue(httpPool)
    val contributions = timed(years
      .map(year => {
        for {
          response <- q.enqueue(request.withEntity(ContentTypes.`application/json`, graphqlQuery(userName, year)))
          entity <- Unmarshal(response.entity).to[ContributionResponse]
        } yield entity
      }).map(Await.result(_, Duration.Inf)), "Api Calls")

    val contributionsByDate = contributions
      .toList
      .flatMap((response: ContributionResponse) => {
        response.data
          .user
          .contributionsCollection
          .contributionCalendar
          .weeks.flatMap(x => x.contributionDays)
          .map(x => (DateTime.parse(x.date), x.contributionCount))
      })

    val sum = contributionsByDate
      .groupBy(_._1.getYear)
      .view.mapValues(_.map(_._2).sum)
      .toList
      .sortBy(x => x._1)

    val xData = sum.map(_._1)
    val yData = sum.map(_._2.toInt)

    (xData, yData)
  }

  implicit def exceptionHandler: ExceptionHandler =
    ExceptionHandler {
      case ex: IllegalArgumentException => ctx => {
        ctx.complete(StatusCodes.BadRequest, ex.getMessage)
      }
      case ex: Throwable => ctx =>
        ex.printStackTrace()
        ctx.complete(StatusCodes.InternalServerError, "There was a problem while processing the request")
    }

  def routes: Route = concat(handleExceptions(exceptionHandler) {
    path("") {
      get {
        complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Hello! Up and running...</h1>"))
      }
    } ~
      pathPrefix("api") {
        path("contributions") {
          alwaysCache(routeCache, keyer = {
            case r: RequestContext => r.request.uri.rawQueryString
          }) {
            get {
              extractRequestContext { ctx =>
                parameters(Symbol("username").as[String], Symbol("years").as[Array[Int]]) { (username, years) =>
                  require(username.length > 0, "Username can not be empty")
                  require(!(years.min < 2008), "The provided years should be in between 2008 and this year")
                  require(!(years.max > DateTime.now().getYear), "The provided years should be in between 2008 and this year")

                  val key = ctx.request.uri.toString()

                  val svgByteArray = cache.getIfPresent(key) match {
                    case Some(value) => value
                    case None =>
                      val contributions = getContributions(username, years)
                      val bytes = timed(Line(username, contributions._1.toArray, contributions._2.toArray).getBytes, "SVG")
                      cache.put(key, bytes)
                      bytes
                  }

                  complete(
                    HttpEntity(ContentType(MediaTypes.`image/svg+xml`), svgByteArray)
                  )
                }

              }
            }
          }

        }
      }
  }

  )
}

final case class Line(username: String, xData: Array[Int], yData: Array[Int]) {

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
    style.setYAxisLabelAlignment(Styler.TextAlignment.Right)
    val divider = if (xData.length * 0.5 < 1) 1 else (xData.length * 0.5).toInt;
    style.setXAxisTickMarkSpacingHint(width / divider)
    style.setYAxisTickMarkSpacingHint(50)

    style.setYAxisMin(yData.minBy(x => x))
    style.setXAxisMin(xData.minBy(x => x))
    style.setAntiAlias(true)

    val os = new ByteArrayOutputStream()
    VectorGraphicsEncoder.saveVectorGraphic(chart, os, VectorGraphicsFormat.SVG)
    os.toByteArray
  }
}

final case class ContributionDays(contributionCount: Double, date: String)

final case class Weeks(contributionDays: List[ContributionDays])

final case class ContributionCalendar(totalContributions: Double, weeks: List[Weeks])

final case class ContributionsCollection(startedAt: String, contributionYears: List[Double], contributionCalendar: ContributionCalendar)

final case class User(name: Option[String], contributionsCollection: ContributionsCollection)

final case class Data(user: User)

final case class ContributionResponse(data: Data)

final case class Contributions(contributions: List[ContributionResponse])

final case class RequestQueue(pool: Flow[(HttpRequest, Promise[HttpResponse]), (Try[HttpResponse], Promise[HttpResponse]), Http.HostConnectionPool])
                             (implicit actorSystem: ActorSystem, executionContext: ExecutionContext) {

  val queue: SourceQueueWithComplete[(HttpRequest, Promise[HttpResponse])] =
    Source.queue[(HttpRequest, Promise[HttpResponse])](4000, OverflowStrategy.dropNew)
      .via(pool)
      .throttle(4096, 5.seconds)
      .to(Sink.foreach({
        case (Success(resp), p) => p.success(resp)
        case (Failure(e), p) => p.failure(e)
      }))
      .run()

  def enqueue(request: HttpRequest): Future[HttpResponse] = {
    val responsePromise = Promise[HttpResponse]()
    queue.offer(request -> responsePromise).flatMap {
      case QueueOfferResult.Enqueued => responsePromise.future
      case QueueOfferResult.Dropped => Future.failed(new RuntimeException("Queue overflowed. Try again later."))
      case QueueOfferResult.Failure(ex) => Future.failed(ex)
      case QueueOfferResult.QueueClosed => Future.failed(new RuntimeException("Queue was closed (pool shut down) while running the request. Try again later."))
    }
  }
}

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val contributionDays: RootJsonFormat[ContributionDays] = jsonFormat2(ContributionDays)
  implicit val weeksFormat: RootJsonFormat[Weeks] = jsonFormat1(Weeks)
  implicit val contributionCalendarFormat: RootJsonFormat[ContributionCalendar] = jsonFormat2(ContributionCalendar)
  implicit val contribCollectionFormat: RootJsonFormat[ContributionsCollection] = jsonFormat3(ContributionsCollection)
  implicit val userFormat: RootJsonFormat[User] = jsonFormat2(User)
  implicit val dataFormat: RootJsonFormat[Data] = jsonFormat1(Data)
  implicit val contributionFormat: RootJsonFormat[ContributionResponse] = jsonFormat1(ContributionResponse)
  implicit val contributionsByYearFormat: RootJsonFormat[Contributions] = jsonFormat1(Contributions)

  implicit val yearsQueryUnmarshaller: Unmarshaller[String, Array[Int]] = Unmarshaller.strict[String, Array[Int]]((str: String) => {
    val array = str.split(",").map(_.toInt)
    array
  })
}

