package trainmapper.networkrail

import com.github.tototoshi.csv.CSVReader
import com.typesafe.scalalogging.StrictLogging
import io.circe.Decoder
import io.circe.parser._
import trainmapper.Shared.{LatLng, StanoxCode, TipLocCode}
import uk.me.jstott.jcoord.OSRef

import scala.io.Source
import scala.util.Try

object Reference extends StrictLogging {

  case class StanoxTipLocMapping(stanox: StanoxCode, tiploc: TipLocCode)

  object StanoxTipLocMapping {
    implicit val decoder = Decoder.instance[StanoxTipLocMapping](f =>
      for {
        tipLoc <- f.downField("TIPLOC").as[String].map(_.trim).map(TipLocCode(_))
        stanox <- f.downField("STANOX").as[String].map(_.trim).map(StanoxCode(_))
      } yield StanoxTipLocMapping(stanox, tipLoc))
  }

  lazy val corpusData = {
    parse(Source.fromResource("CORPUSExtract.json").getLines().mkString)
      .flatMap(
        _.hcursor
          .downField("TIPLOCDATA")
          .as[List[StanoxTipLocMapping]])
      .map(_.filter(mapping => mapping.stanox.value != "" && mapping.tiploc.value != ""))
  }

  lazy val railReferences = {
    val csvReader = CSVReader.open(Source.fromFile(getClass.getResource("/RailReferences.csv").getFile))
    csvReader.allWithHeaders()
  }

  def latLngFor(stanox: StanoxCode): Option[LatLng] =
    for {
      data <- corpusData.toOption
//      _ = println("data: "  + data)
      tipLocCode <- data.find(_.stanox == stanox).map(_.tiploc)
//      _ = println("tiploccode: " + tipLocCode)
      recordMap <- railReferences.find(_.get("TiplocCode").contains(tipLocCode.value))
//      _ = println("record map: " + recordMap)
      easting  <- recordMap.get("Easting").flatMap(safeToDouble)
      northing <- recordMap.get("Northing").flatMap(safeToDouble)
//      _ = println("easting and northing: " + easting + ", " + northing)
      latLng <- latLngFromEastingNorthing(easting, northing)
//      _ = println("latlng: " + latLng)
    } yield latLng

  private def safeToDouble(str: String) = Try(str.toDouble).toOption

  private def latLngFromEastingNorthing(easting: Double, northing: Double): Option[LatLng] =
    Try {
      val osRef  = new OSRef(easting, northing)
      val latLng = osRef.toLatLng
      latLng.toWGS84()
      LatLng(latLng.getLat, latLng.getLng)
    }.toOption

}