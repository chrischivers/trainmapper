package trainmapper.clients

import cats.effect.IO
import io.circe.generic.semiauto._
import org.http4s.Uri
import org.http4s.circe.jsonOf
import org.http4s.client.Client
import trainmapper.ActivationLookupConfig.ActivationLookupConfig
import trainmapper.Shared.{ScheduleTrainId, StanoxCode, TrainId}
import trainmapper.clients.ActivationLookupClient.ActivationResponse

trait ActivationLookupClient {
  def fetch(trainId: TrainId): IO[Option[ActivationResponse]]
}

object ActivationLookupClient {

  case class ActivationResponse(scheduleTrainId: ScheduleTrainId,
                                originStanox: StanoxCode,
                                originDepartureTimestamp: Long)

  object ActivationResponse {
    implicit val decoder       = deriveDecoder[ActivationResponse]
    implicit val entityDecoder = jsonOf[IO, ActivationResponse]

  }

  def apply(baseUri: Uri, client: Client[IO]) = new ActivationLookupClient {
    override def fetch(trainId: TrainId): IO[Option[ActivationResponse]] =
      client.get[Option[ActivationResponse]](baseUri / "activation" / trainId.value) { response =>
        if (response.status.code == 404) IO.pure(None)
        else response.as[ActivationResponse].map(Some(_))
      }
  }
}
