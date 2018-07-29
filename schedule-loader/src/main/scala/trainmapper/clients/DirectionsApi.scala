package trainmapper.clients
import java.time.temporal.TemporalAdjusters
import java.time.{DayOfWeek, LocalDateTime, ZoneOffset}

import cats.effect.IO
import com.typesafe.scalalogging.StrictLogging
import org.http4s.circe.jsonOf
import org.http4s.client.Client
import org.http4s.{EntityDecoder, Uri}
import trainmapper.DirectionsApiConfig
import trainmapper.Shared.{LatLng, Polyline}

trait DirectionsApi {

  def trainPolylineFor(from: LatLng, to: LatLng): IO[Option[Polyline]]

}

object DirectionsApi extends StrictLogging {

  def apply(config: DirectionsApiConfig, client: Client[IO]) = new DirectionsApi {

    override def trainPolylineFor(from: LatLng, to: LatLng): IO[Option[Polyline]] = {

      logger.info(s"Attempting to retrieve polyline from $from to $to")

      import _root_.io.circe.generic.auto._

      case class Polyline0(points: String)
      case class Step(travel_mode: String, polyline: Polyline0)
      case class Leg(steps: List[Step])
      case class Route(legs: List[Leg])
      case class DirectionsApiResponse(routes: List[Route])

      implicit val entityDecoder: EntityDecoder[IO, DirectionsApiResponse] = jsonOf[IO, DirectionsApiResponse]

      val fixedDepartureTime = LocalDateTime
        .now()
        .`with`(TemporalAdjusters.next(DayOfWeek.MONDAY))
        .withHour(9)
        .withMinute(0)
        .withSecond(0)
        .toEpochSecond(ZoneOffset.UTC)

      val uri = config.baseUri / "json" +?
        ("key", config.apiKey) +?
        ("origin", from.asQueryParameterString) +?
        ("destination", to.asQueryParameterString) +?
        ("departure_time", fixedDepartureTime.toString) +?
        ("mode", "transit") +?
        ("transit_mode", "train") +?
        ("transit_routing_preference", "fewer_transfers")

      logger.debug(s"Using uri ${uri.renderString} to look up directions")

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
