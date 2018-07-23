package trainmapper
import cats.effect.IO
import clients.RailwaysCodesClient
import trainmapper.Shared.{StopReferenceDetails, StopReferenceDetailsWithLatLng}

object StubRailwayCodesClient {

  def apply(stubData: List[StopReferenceDetails]) = new RailwaysCodesClient {
    override def parseAllCodes(): IO[List[Shared.StopReferenceDetails]] = IO(stubData)

    override def parseCodesFor(startingLetter: Char): IO[List[Shared.StopReferenceDetails]] = IO(stubData)
  }
}
