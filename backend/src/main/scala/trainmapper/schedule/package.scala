package trainmapper

import java.time.LocalTime

import doobie.util.meta.Meta
import io.circe.{Decoder, Encoder, Json}
import io.circe.java8.time.decodeLocalTime
import trainmapper.schedule.ScheduleTable.timeFormatter

package object schedule {

  implicit final val localTimeDecoder: Decoder[LocalTime] =
    decodeLocalTime(timeFormatter)

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

    implicit val meta: Meta[LocationType] =
      Meta[String].xmap(LocationType.fromString, _.string)
  }
}
