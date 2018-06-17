package trainmapper

import io.circe.Encoder
import io.circe.generic.semiauto._
import trainmapper.networkrail.TrainMovementMessage

package object http {

  case class MovementPacket(trainId: TrainId, serviceCode: ServiceCode, latLng: LatLng, actualTimeStamp: Long) {
    def from(trainMovementMessage: TrainMovementMessage) = {}
  }

  object MovementPacket {
    implicit val encoder: Encoder[MovementPacket] = deriveEncoder[MovementPacket]
  }
}
