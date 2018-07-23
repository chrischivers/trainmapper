package trainmapper.reference

import com.github.tototoshi.csv.CSVReader
import com.typesafe.scalalogging.StrictLogging
import trainmapper.Shared.{LatLng, StanoxCode, StopReferenceDetails, StopReferenceDetailsWithLatLng, TipLocCode}
import trainmapper.clients.RailwaysCodesClient
import uk.me.jstott.jcoord.OSRef

import scala.io.Source
import scala.util.Try

trait StopReference {
  val allReferenceDetails: List[StopReferenceDetailsWithLatLng]
  def referenceDetailsFor(stanoxCode: StanoxCode): Option[StopReferenceDetailsWithLatLng]
}

object StopReference extends StrictLogging {

  def apply(railwaysCodesClient: RailwaysCodesClient) = new StopReference {

    lazy val railReferencesCSV: List[Map[String, String]] = {
      logger.info("Loading csv reader for rail references")
      val csvReader = CSVReader.open(Source.fromFile(getClass.getResource("/RailReferences.csv").getFile))
      csvReader.allWithHeaders()
    }

    override lazy val allReferenceDetails: List[StopReferenceDetailsWithLatLng] = {
      railwaysCodesClient
        .parseAllCodes()
        .map(_.map(ref => ref.withLatLng(ref.tiploc.flatMap(latLngFor))))
        .unsafeRunSync()
    }

    override def referenceDetailsFor(stanoxCode: StanoxCode): Option[StopReferenceDetailsWithLatLng] =
      allReferenceDetails.find(_.stanox.contains(stanoxCode))

    private def latLngFor(tipLocCode: TipLocCode): Option[LatLng] =
      for {
        rowMap   <- railReferencesCSV.find(_.get("TiplocCode").contains(tipLocCode.value))
        easting  <- rowMap.get("Easting").flatMap(safeToDouble)
        northing <- rowMap.get("Northing").flatMap(safeToDouble)
        latLng   <- latLngFromEastingNorthing(easting, northing)
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
}
