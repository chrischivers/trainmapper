package trainmapper
import cats.effect.IO
import com.typesafe.scalalogging.StrictLogging
import org.http4s.{HttpService, StaticFile}
import trainmapper.Shared.TrainId
import trainmapper.StubActivationLookupClient.logger
import org.http4s.dsl.io._

object StubScheduleDownloadClient extends StrictLogging {
  def apply() = HttpService[IO] {
    case request => {
      logger.info("Received request to download schedule")
      StaticFile.fromResource("/schedule-data-downloaded.gz", Some(request)).getOrElseF(NotFound())
    }
  }
}
