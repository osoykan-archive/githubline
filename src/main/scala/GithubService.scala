import DataRepresentation.ContributionResponse
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.http.scaladsl.model.{ContentType, ContentTypes, HttpEntity, HttpHeader, HttpMethods, HttpRequest, HttpResponse, MediaTypes}
import akka.http.scaladsl.server.directives.CachingDirectives.{alwaysCache, routeCache}
import akka.http.scaladsl.server.{Directives, ExceptionHandler, RequestContext, Route, StandardRoute}
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl.Flow
import com.github.blemale.scaffeine.{Cache, Scaffeine}
import com.github.nscala_time.time.Imports.DateTime

import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{Await, ExecutionContext, Promise}
import scala.util.Try

class GithubService(val token: String, exceptionHandler: ExceptionHandler)
                   (implicit system: ActorSystem, executionContext: ExecutionContext) extends Directives with JsonSupport with Utilities {
  require(token != "")

  val cache: Cache[String, Array[Byte]] =
    Scaffeine()
      .recordStats()
      .expireAfterWrite(24.hour)
      .maximumSize(500)
      .build[String, Array[Byte]]()

  val httpPool: Flow[(HttpRequest, Promise[HttpResponse]), (Try[HttpResponse], Promise[HttpResponse]), Http.HostConnectionPool] =
    Http().cachedHostConnectionPoolHttps[Promise[HttpResponse]]("api.github.com", settings = ConnectionPoolSettings(system))

  val queue = new RequestQueue(httpPool)

  def routes: Route = handleExceptions(exceptionHandler) {
    path("")(get {
      complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Hello! Up and running...</h1>"))
    }) ~
      pathPrefix("api")(path("contributions") {
        alwaysCache(routeCache, keyer = {
          case r: RequestContext => r.request.uri.rawQueryString
        })(handleGetSvg)
      })
  }

  private def handleGetSvg = get(extractRequestContext { ctx =>
    parameters(Symbol("username").as[String], Symbol("years").as[Array[Int]]) {
      (username, years) => handleSvgRequest(ctx.request.uri.toString(), username, years)
    }
  })

  private def handleSvgRequest(key: String, username: String, years: Array[Int]): StandardRoute = {
    require(username.length > 0, "Username can not be empty")
    require(!(years.min < 2008), "The provided years should be in between 2008 and this year")
    require(!(years.max > DateTime.now().getYear), "The provided years should be in between 2008 and this year")

    val svgByteArray = cache.getIfPresent(key) match {
      case Some(value) => value
      case None => getContributionsAndUpdateCache(queue)(username, years, key)
    }

    complete(
      HttpEntity(ContentType(MediaTypes.`image/svg+xml`), svgByteArray)
    )
  }

  private def getContributionsAndUpdateCache(queue: RequestQueue)(username: String, years: Array[Int], key: String) = {
    val contributions = getContributions(queue)(username, years)
    val bytes = timed("SVG Image Creation") {
      Line(username, contributions._1.toArray, contributions._2.toArray).getBytes
    }
    cache.put(key, bytes)
    bytes
  }

  private def getContributions(queue: RequestQueue)(userName: String, years: Array[Int]): (List[Int], List[Int]) = {

    def graphqlQuery(name: String, year: Int): String = {
      s"""{
         |  "query": "query {  user(login: \\"$name\\") { name  contributionsCollection(from: \\"$year-01-01T00:00:00\\" to:\\"$year-12-31T23:59:59\\" ) {startedAt contributionYears  contributionCalendar { totalContributions weeks { contributionDays {contributionCount date} } } } } }"
         |}""".stripMargin
    }

    val header = HttpHeader.parse("Authorization", s"Bearer $token") match {
      case ParsingResult.Ok(header, _) => header
    }

    val request = HttpRequest(HttpMethods.POST, "/graphql")
      .addHeader(header)

    val contributions = timed("Api Calls") {
      years
        .map(year => {
          for {
            response <- queue.enqueue(request.withEntity(ContentTypes.`application/json`, graphqlQuery(userName, year)))
            entity <- Unmarshal(response.entity).to[ContributionResponse]
          } yield entity
        }).map(Await.result(_, Duration.Inf))
    }

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
}







