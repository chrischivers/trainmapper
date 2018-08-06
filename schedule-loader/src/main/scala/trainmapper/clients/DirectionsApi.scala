package trainmapper.clients
import java.time._
import java.time.temporal.{ChronoUnit, TemporalAdjusters}

import cats.effect.IO
import com.typesafe.scalalogging.StrictLogging
import org.http4s.EntityDecoder
import org.http4s.circe.jsonOf
import org.http4s.client.Client
import trainmapper.DirectionsApiConfig
import trainmapper.Shared.{DaysRun, LatLng, Polyline}

trait DirectionsApi {
  def trainPolylineFor(from: LatLng,
                       to: LatLng,
                       departureTime: LocalTime,
                       daysRun: DaysRun,
                       scheduleStart: LocalDate,
                       scheduleEnd: LocalDate): IO[Option[Polyline]]
}

object DirectionsApi extends StrictLogging {

  def apply(config: DirectionsApiConfig, client: Client[IO]) = new DirectionsApi {

    val NumberOfAttempts = 4

    def generateReasonableDepartureTime(time: LocalTime,
                                        daysRun: DaysRun,
                                        scheduleStart: LocalDate,
                                        scheduleEnd: LocalDate) = {
      val now = LocalDate.now().plusDays(1)
      val departureDate: LocalDate = {
        val isNotPast = if (scheduleStart.isBefore(now)) now else scheduleStart
        val isNotOnDaysRun =
          if (!daysRun.toDaysOfWeek.contains(isNotPast.getDayOfWeek))
            isNotPast.`with`(TemporalAdjusters.next(daysRun.toDaysOfWeek.head))
          else isNotPast
        val isNotAfterEndDate =
          if (scheduleEnd.isBefore(isNotOnDaysRun))
            now.`with`(TemporalAdjusters.next(daysRun.toDaysOfWeek.head))
          else isNotOnDaysRun
        isNotAfterEndDate
      }
      val departureTime = time.minusMinutes(5)
      LocalDateTime.of(departureDate, departureTime).atZone(ZoneId.of("Europe/London")).toEpochSecond
    }

    override def trainPolylineFor(from: LatLng,
                                  to: LatLng,
                                  departureTime: LocalTime,
                                  daysRun: DaysRun,
                                  scheduleStart: LocalDate,
                                  scheduleEnd: LocalDate): IO[Option[Polyline]] = {

      import _root_.io.circe.generic.auto._

      case class Polyline0(points: String)
      case class Step(travel_mode: String, polyline: Polyline0)
      case class Leg(steps: List[Step])
      case class Route(legs: List[Leg])
      case class DirectionsApiResponse(routes: List[Route])

      implicit val entityDecoder: EntityDecoder[IO, DirectionsApiResponse] = jsonOf[IO, DirectionsApiResponse]

      val uri = config.baseUri / "json" +?
        ("key", config.apiKey) +?
        ("origin", from.asQueryParameterString) +?
        ("destination", to.asQueryParameterString) +?
        ("departure_time", generateReasonableDepartureTime(departureTime, daysRun, scheduleStart, scheduleEnd).toString) +?
        ("mode", "transit") +?
        ("transit_mode", "train") +?
        ("transit_routing_preference", "fewer_transfers")

      logger.info(s"Attempting to retrieve polyline from $from to $to using url $uri")

      client.expect[DirectionsApiResponse](uri).map { response =>
        for {
          route <- response.routes.headOption
          leg   <- route.legs.headOption
          step  <- leg.steps.find(_.travel_mode == "TRANSIT")
        } yield Polyline(step.polyline.points)
      }
    }
  }
}
