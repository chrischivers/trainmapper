package trainmapper.reference

import trainmapper.Shared.{CRS, StanoxCode, StopReferenceDetails, TipLocCode}
import trainmapper.clients.RailwaysCodesClient
import io.circe.generic.semiauto._

trait StopReference {
  val allRailwayCodes: List[StopReferenceDetails]
  def referenceFor(stanoxCode: StanoxCode): Option[StopReferenceDetails]
}

object StopReference {

  def apply(railwaysCodesClient: RailwaysCodesClient) = new StopReference {
    override lazy val allRailwayCodes: List[StopReferenceDetails] = {
      railwaysCodesClient.parseAllCodes().unsafeRunSync()
    }

    override def referenceFor(stanoxCode: StanoxCode): Option[StopReferenceDetails] =
      allRailwayCodes.find(_.stanox.contains(stanoxCode))
  }
}
