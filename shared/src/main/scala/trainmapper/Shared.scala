package trainmapper

import java.time.LocalTime
import java.time.format.DateTimeFormatter

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto._

object Shared {

  import io.circe.java8.time.decodeLocalTime
  import io.circe.java8.time.encodeLocalTime

  val timeFormatter = DateTimeFormatter.ofPattern("HHmm")

  implicit final val localTimeDecoder: Decoder[LocalTime] =
    decodeLocalTime(timeFormatter)

  implicit final val localTimeEncoder: Encoder[LocalTime] =
    encodeLocalTime(timeFormatter)

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

  case class DaysRun(value: String)

  object DaysRun {

    implicit val decoder: Decoder[DaysRun] = Decoder.decodeString.map(DaysRun(_))
    implicit val encoder: Encoder[DaysRun] = Encoder[DaysRun](a => Json.fromString(a.value))
  }

  sealed trait VariationStatus {
    val string: String
  }

  object VariationStatus {

    case object OnTime extends VariationStatus {
      override val string: String = "ON TIME"
    }

    case object Early extends VariationStatus {
      override val string: String = "EARLY"
    }

    case object Late extends VariationStatus {
      override val string: String = "LATE"
    }

    case object OffRoute extends VariationStatus {
      override val string: String = "OFF ROUTE"
    }

    def fromString(str: String): VariationStatus =
      str match {
        case OnTime.string   => OnTime
        case Early.string    => Early
        case Late.string     => Late
        case OffRoute.string => OffRoute
      }

    implicit val decoder: Decoder[VariationStatus] = Decoder.decodeString.map(fromString)
    implicit val encoder: Encoder[VariationStatus] = (a: VariationStatus) => Json.fromString(a.string)
  }

  sealed trait LocationType {
    val string: String
  }

  object LocationType {

    case object OriginatingLocation extends LocationType {
      override val string: String = "LO"
    }
    case object TerminatingLocation extends LocationType {
      override val string: String = "LT"
    }
    case object IntermediateLocation extends LocationType {
      override val string: String = "LI"
    }

    def fromString(str: String): LocationType =
      str match {
        case OriginatingLocation.string  => OriginatingLocation
        case TerminatingLocation.string  => TerminatingLocation
        case IntermediateLocation.string => IntermediateLocation
      }
    implicit val decoder: Decoder[LocationType] = Decoder.decodeString.map(fromString)
    implicit val encoder: Encoder[LocationType] = (a: LocationType) => Json.fromString(a.string)
  }

  case class LatLng(lat: Double, lng: Double)

  object LatLng {
    implicit val encoder: Encoder[LatLng] = deriveEncoder[LatLng]
    implicit val decoder: Decoder[LatLng] = deriveDecoder[LatLng]
  }

  case class ScheduleDetailRecord(sequence: Int,
                                  tipLocCode: TipLocCode,
                                  stanoxCode: Option[StanoxCode],
                                  locationType: LocationType,
                                  scheduledArrivalTime: Option[LocalTime],
                                  scheduledDepartureTime: Option[LocalTime],
                                  daysRun: DaysRun)

  object ScheduleDetailRecord {
    implicit val encoder: Encoder[ScheduleDetailRecord] = deriveEncoder[ScheduleDetailRecord]
    implicit val decoder: Decoder[ScheduleDetailRecord] = deriveDecoder[ScheduleDetailRecord]
  }

  case class MovementPacket(trainId: TrainId,
                            scheduleTrainId: ScheduleTrainId,
                            serviceCode: ServiceCode,
                            toc: TOC,
                            stanoxCode: Option[StanoxCode],
                            eventType: EventType,
                            latLng: LatLng,
                            actualTimeStamp: Long,
                            plannedTimestamp: Option[Long],
                            plannedPassengerTimestamp: Option[Long],
                            variationStatus: Option[VariationStatus],
                            scheduleDetails: List[ScheduleDetailRecord])

  object MovementPacket {
    implicit val encoder: Encoder[MovementPacket] = deriveEncoder[MovementPacket]
    implicit val decoder: Decoder[MovementPacket] = deriveDecoder[MovementPacket]
  }

}
