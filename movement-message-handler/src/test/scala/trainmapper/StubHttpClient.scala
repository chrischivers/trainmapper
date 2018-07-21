package trainmapper

import cats.effect.IO
import com.typesafe.scalalogging.StrictLogging
import io.circe.syntax._
import org.http4s.HttpService
import org.http4s.circe._
import org.http4s.dsl.io._
import trainmapper.Shared.{ScheduleTrainId, ServiceCode, StanoxCode, TrainId}

object StubHttpClient extends StrictLogging {

  import io.circe.generic.auto._
  case class TrainActivationMessage(scheduleTrainId: ScheduleTrainId,
                                    trainServiceCode: ServiceCode,
                                    trainId: TrainId,
                                    originStanox: StanoxCode,
                                    originDepartureTimestamp: Long)

  def apply(respondWith: Option[TrainActivationMessage] = None) = HttpService[IO] {
    case GET -> Root / "activation" / id =>
      respondWith.fold(NotFound())(r => Ok(r.asJson))
    case x =>
      logger.error(s"No path for request $x in http stub")
      NotFound()
  }
}
