package trainmapper

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto._

object Shared {

  case class TrainId(value: String)

  object TrainId {
    implicit val decoder: Decoder[TrainId] = Decoder.decodeString.map(TrainId(_))
    implicit val encoder: Encoder[TrainId] = Encoder[TrainId](a => Json.fromString(a.value))
  }

  case class ServiceCode(value: String)

  object ServiceCode {
    implicit val decoder: Decoder[ServiceCode] = Decoder.decodeString.map(ServiceCode(_))
    implicit val encoder: Encoder[ServiceCode] = Encoder[ServiceCode](a => Json.fromString(a.value))
  }

  case class ScheduleTrainId(value: String)
  object ScheduleTrainId {
    implicit val decoder: Decoder[ScheduleTrainId] = Decoder.decodeString.map(ScheduleTrainId(_))
    implicit val encoder: Encoder[ScheduleTrainId] = Encoder[ScheduleTrainId](a => Json.fromString(a.value))
  }

  sealed trait EventType {
    val string: String
  }

  object EventType {

    case object Departure extends EventType {
      override val string: String = "DEPARTURE"
    }

    case object Arrival extends EventType {
      override val string: String = "ARRIVAL"
    }

    def fromString(str: String): EventType =
      str match {
        case Departure.string => Departure
        case Arrival.string   => Arrival
      }

    implicit val encoder: Encoder[EventType] = (a: EventType) => Json.fromString(a.string)
    implicit val decoder: Decoder[EventType] = Decoder.decodeString.map(fromString)
  }

  case class TOC(value: String)

  object TOC {
    implicit val decoder: Decoder[TOC] = Decoder.decodeString.map(TOC(_))
    implicit val encoder: Encoder[TOC] = Encoder[TOC](a => Json.fromString(a.value))
  }

  case class StanoxCode(value: String)

  object StanoxCode {
    implicit val decoder: Decoder[StanoxCode] = Decoder.decodeString.map(StanoxCode(_))
    implicit val encoder: Encoder[StanoxCode] = Encoder[StanoxCode](a => Json.fromString(a.value))
  }

  case class TipLocCode(value: String)

  object TipLocCode {

    implicit val decoder: Decoder[TipLocCode] = Decoder.decodeString.map(TipLocCode(_))
    implicit val encoder: Encoder[TipLocCode] = Encoder[TipLocCode](a => Json.fromString(a.value))
  }

  sealed trait VariationStatus {
    val string: String
    val notifiable: Boolean
  }

  object VariationStatus {

    case object OnTime extends VariationStatus {
      override val string: String      = "ON TIME"
      override val notifiable: Boolean = false
    }

    case object Early extends VariationStatus {
      override val string: String      = "EARLY"
      override val notifiable: Boolean = false
    }

    case object Late extends VariationStatus {
      override val string: String      = "LATE"
      override val notifiable: Boolean = true
    }

    case object OffRoute extends VariationStatus {
      override val string: String      = "OFF ROUTE"
      override val notifiable: Boolean = true
    }

    def fromString(str: String): VariationStatus =
      str match {
        case OnTime.string   => OnTime
        case Early.string    => Early
        case Late.string     => Late
        case OffRoute.string => OffRoute
      }

    implicit val decoder: Decoder[VariationStatus] = Decoder.decodeString.map(fromString)
  }

  case class LatLng(lat: Double, lng: Double)

  object LatLng {
    implicit val encoder: Encoder[LatLng] = deriveEncoder[LatLng]
    implicit val decoder: Decoder[LatLng] = deriveDecoder[LatLng]
  }

//  case class JourneyDetails(originStationName: String)
//
//  object JourneyDetails {
//    implicit val encoder: Encoder[JourneyDetails] = deriveEncoder[JourneyDetails]
//    implicit val decoder: Decoder[JourneyDetails] = deriveDecoder[JourneyDetails]
//  }

  case class MovementPacket(trainId: TrainId, serviceCode: ServiceCode, latLng: LatLng, actualTimeStamp: Long)

  object MovementPacket {
    implicit val encoder: Encoder[MovementPacket] = deriveEncoder[MovementPacket]
    implicit val decoder: Decoder[MovementPacket] = deriveDecoder[MovementPacket]
  }

}
