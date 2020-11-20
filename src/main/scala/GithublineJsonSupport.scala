import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.unmarshalling.Unmarshaller
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

object DataRepresentation {

  final case class ContributionDays(contributionCount: Double, date: String)

  final case class Weeks(contributionDays: List[ContributionDays])

  final case class ContributionCalendar(totalContributions: Double, weeks: List[Weeks])

  final case class ContributionsCollection(startedAt: String, contributionYears: List[Double], contributionCalendar: ContributionCalendar)

  final case class User(name: Option[String], contributionsCollection: ContributionsCollection)

  final case class Data(user: User)

  final case class ContributionResponse(data: Data)

  final case class Contributions(contributions: List[ContributionResponse])

}

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {

  import DataRepresentation._

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
