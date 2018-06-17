import io.circe.{Decoder, Encoder, Json}
import uk.me.jstott.jcoord.OSRef

import scala.util.Try

package object trainmapper {

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

  case class LatLng(lat: Double, lng: Double) {

//    def distanceTo(latLng: LatLng, decimalPrecision: Int = 4) = {
//      LatLng.calculateDistanceBetween(this, latLng, decimalPrecision)
//    }
//
//    def angleTo(latLng: LatLng) = {
//      LatLng.getAngleBetween(this, latLng)
//    }
//
//    def truncate(decimalPrecision: Int = 6): LatLng = {
//      LatLng.truncateLatLng(this, decimalPrecision)
//    }
  }
//
//  case class LatLngBounds(southwest: LatLng, northeast: LatLng) {
//    def isWithinBounds(point: LatLng) = {
//      LatLng.pointIsWithinBounds(point, this)
//    }
//
//  }

  object LatLng {

    import io.circe.generic.semiauto._
    implicit val encoder: Encoder[LatLng] = deriveEncoder[LatLng]

    def fromEastingNorthing(easting: Double, northing: Double): Option[LatLng] =
      Try {
        val osRef  = new OSRef(easting, northing)
        val latLng = osRef.toLatLng
        latLng.toWGS84()
        LatLng(latLng.getLat, latLng.getLng)
      }.toOption

//    private val R = 6378137 // Earth’s mean radius in meters
//    private def rad(x: Double) = x * Math.PI / 180
//
//    private def truncateAt(n: Double, p: Int): Double = {
//      val s = math pow(10, p)
//      (math floor n * s) / s
//    }
//
//    def truncateLatLng(latLng: LatLng, decimalPrecision: Int): LatLng = {
//      val truncatedLat = truncateAt(latLng.lat, decimalPrecision)
//      val truncatedLng = truncateAt(latLng.lng, decimalPrecision)
//      LatLng(truncatedLat, truncatedLng)
//    }
//
//    // This function was adapted from code at : http://stackoverflow.com/questions/1502590/calculate-distance-between-two-points-in-google-maps-v3
//    def calculateDistanceBetween(fromLatLng: LatLng, toLatLng: LatLng, decimalPrecision: Int): Double = {
//      val dLat = rad(toLatLng.lat - fromLatLng.lat)
//      val dLong = rad(toLatLng.lng - fromLatLng.lng)
//      val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
//        Math.cos(rad(fromLatLng.lat)) * Math.cos(rad(toLatLng.lat)) *
//          Math.sin(dLong / 2) * Math.sin(dLong / 2)
//      val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
//      val d = R * c
//      truncateAt(d, decimalPrecision)
//    }
//
//    def getAngleBetween(fromLatLng: LatLng, toLatLng: LatLng): Int = {
//      val lat1x = fromLatLng.lat * Math.PI / 180
//      val lat2x = toLatLng.lat * Math.PI / 180
//      val dLon = (toLatLng.lng - fromLatLng.lng) * Math.PI / 180
//
//      val y = Math.sin(dLon) * Math.cos(lat2x)
//      val x = Math.cos(lat1x) * Math.sin(lat2x) -
//        Math.sin(lat1x) * Math.cos(lat2x) * Math.cos(dLon)
//
//      val brng = Math.atan2(y, x)
//      val result = ((brng * 180 / Math.PI) + 180) % 360
//      result.toInt
//    }
//
//    def pointIsWithinBounds(point: LatLng, bounds: LatLngBounds): Boolean = {
//      bounds.southwest.lat <= point.lat &&
//        bounds.northeast.lat >= point.lat &&
//        bounds.southwest.lng <= point.lng &&
//        bounds.northeast.lng >= point.lng
//    }
  }

}
