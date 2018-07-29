package trainmapper
import org.scalatest.FlatSpec
import trainmapper.Shared.{CRS, LatLng, StanoxCode, StopReferenceDetails, StopReferenceDetailsWithLatLng, TipLocCode}
import trainmapper.reference.StopReference
import org.scalatest.Matchers._

class ReferencesTest extends FlatSpec {

  "References" should "read from railway client and join with eastings/northings file to provide reference details" in {
    val stubData =
      StopReferenceDetails("Redhill Station", Some(CRS("RDH")), Some(TipLocCode("REDHILL")), Some(StanoxCode("87722")))
    val stubRailwayCodesClient       = StubRailwayCodesClient(List(stubData))
    val stopReference: StopReference = StopReference(stubRailwayCodesClient)
    stopReference.allReferenceDetails should ===(
      List(
        StopReferenceDetailsWithLatLng(stubData.description,
                                       stubData.crs,
                                       stubData.tiploc,
                                       stubData.stanox,
                                       Some(LatLng(51.240060406414315, -0.1656046471268615)))))
  }

}
