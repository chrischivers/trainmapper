package trainmapper.schedule

import java.nio.file.{Path, Paths, StandardOpenOption}
import java.time.{LocalDate, LocalTime}

import cats.effect.IO
import com.typesafe.scalalogging.StrictLogging
import fs2.compress._
import fs2.{Pipe, Stream}
import io.circe.Decoder.Result
import io.circe.fs2._
import io.circe.{Decoder, DecodingFailure, HCursor, Json}
import org.http4s.client.Client
import org.http4s.client.middleware.FollowRedirect
import org.http4s.headers.Authorization
import org.http4s.{BasicCredentials, EntityBody, Headers, Request}
import trainmapper.Config.NetworkRailConfig
import trainmapper.Shared.{DaysRun, LocationType, ScheduleTrainId, ServiceCode, TipLocCode}
import trainmapper.networkrail.Reference
import trainmapper.schedule.ScheduleTable.ScheduleRecord
import trainmapper.schedule.ScheduleTablePopulator.DecodedScheduleRecord.ScheduleSegment
import trainmapper.schedule.ScheduleTablePopulator.DecodedScheduleRecord.ScheduleSegment.ScheduleLocation

trait ScheduleTablePopulator {

  def populateTable(): IO[Unit]
}

object ScheduleTablePopulator extends StrictLogging {

  import trainmapper.Shared.localTimeDecoder

  val tmpDownloadLocation = Paths.get("/tmp/schedule-data-downloaded.gz")
  val tmpUnzipLocation    = Paths.get("/tmp/schedule-data-unzipped.dat")

  trait DecodedRecord

  object DecodedRecord {
    implicit val decoder = new Decoder[DecodedRecord] {
      override def apply(c: HCursor): Result[DecodedRecord] =
        c.keys
          .flatMap(_.headOption)
          .fold[Decoder.Result[DecodedRecord]](
            Left(DecodingFailure(s"Unable to get head record ${c.value}", c.history))) {
            case "JsonScheduleV1" => c.downField("JsonScheduleV1").as[DecodedScheduleRecord]
            case other            => Left(DecodingFailure(s"Unhandled record type $other", c.history))
          }
    }
  }

  case class DecodedScheduleRecord(CIF_train_uid: ScheduleTrainId,
                                   schedule_days_runs: DaysRun,
                                   schedule_start_date: LocalDate,
                                   schedule_end_date: LocalDate,
                                   schedule_segment: ScheduleSegment)
      extends DecodedRecord {

    def toScheduleRecords: List[ScheduleRecord] = schedule_segment.schedule_location.zipWithIndex.map {
      case (loc, sequence) =>
        ScheduleRecord(
          None,
          CIF_train_uid,
          sequence,
          schedule_segment.CIF_train_service_code,
          loc.tiploc_code,
          Reference.stanoxFor(loc.tiploc_code),
          loc.location_type,
          loc.public_arrival,
          loc.public_departure,
          schedule_days_runs,
          schedule_start_date,
          schedule_end_date
        )
    }
  }

  object DecodedScheduleRecord {
    import io.circe.generic.semiauto._
    import io.circe.java8.time.decodeLocalDateDefault

    implicit val decoder: Decoder[DecodedScheduleRecord] = deriveDecoder

    case class ScheduleSegment(CIF_train_service_code: ServiceCode, schedule_location: List[ScheduleLocation])
    object ScheduleSegment {
      implicit val decoder: Decoder[ScheduleSegment] = deriveDecoder

      case class ScheduleLocation(location_type: LocationType,
                                  tiploc_code: TipLocCode,
                                  public_departure: Option[LocalTime],
                                  public_arrival: Option[LocalTime])

      object ScheduleLocation {
        implicit val decoder: Decoder[ScheduleLocation] = deriveDecoder
      }
    }

  }

  def apply(client: Client[IO], scheduleTable: ScheduleTable, config: NetworkRailConfig) = new ScheduleTablePopulator {

    val credentials = BasicCredentials(config.username, config.password)

    override def populateTable(): IO[Unit] =
      for {
        _ <- deleteTmpFiles()
        _ <- downloadFromUrl()
        _ <- unpackScheduleData()
        _ <- checkFileExists
        _ <- readData.evalMap(scheduleTable.safeInsertRecord).compile.drain
      } yield ()

    private def deleteTmpFiles() =
      for {
        _ <- IO(logger.info("Deleting tmp files"))
        _ <- IO(tmpDownloadLocation.toFile.delete())
        _ <- IO(tmpUnzipLocation.toFile.delete())
      } yield ()

    private def downloadFromUrl(): IO[Unit] =
      for {
        _ <- IO(logger.info(s"Downloading from URL ${config.scheduleUrl}"))
        request = Request[IO](uri = config.scheduleUrl)
          .withHeaders(Headers(Authorization(credentials)))
        _ <- FollowRedirect(maxRedirects = 10)(client)
          .streaming(request) { resp =>
            println("Response status: " + resp.status)
            if (resp.status.isSuccess) {
              logger.info("Download of schedule response successful. Writing to file...")
              fs2.Stream.eval(writeToFile(tmpDownloadLocation, resp.body))
            } else {
              fs2.Stream.eval(
                IO(logger.error(s"Download of schedule response unsuccessful ${resp.status}. Not downloading")))
            }
          }
          .compile
          .drain
      } yield ()

    private def writeToFile(path: Path, data: EntityBody[IO]): IO[Unit] =
      data
        .to(fs2.io.file.writeAll(path))
        .compile
        .drain
        .flatMap(_ => IO(logger.info("Finished writing to file")))

    private def unpackScheduleData(): IO[Unit] =
      fs2.io.file
        .readAll[IO](tmpDownloadLocation, 4096)
        .drop(10) //drops gzip header
        .through(inflate(nowrap = true))
        .to(
          fs2.io.file.writeAll[IO](tmpUnzipLocation, flags = List(StandardOpenOption.CREATE, StandardOpenOption.SYNC)))
        .compile
        .drain
        .flatMap(_ => IO(logger.info("Finished unpacking schedule data")))

    private def readData: fs2.Stream[IO, ScheduleRecord] =
      fs2.io.file
        .readAll[IO](tmpUnzipLocation, 4096)
        .through(fs2.text.utf8Decode)
        .through(fs2.text.lines)
        .through(stringStreamParser[IO])
        .dropLast //EOF line
        .through(decoderPipe[IO, DecodedRecord])
        .collect { case Right(decodedRecord: DecodedScheduleRecord) => decodedRecord }
        .through(_.map(_.toScheduleRecords))
        .flatMap(scheduleRecords => fs2.Stream.fromIterator[IO, ScheduleRecord](scheduleRecords.toIterator))

  }

  def decoderPipe[F[_], A](implicit decode: Decoder[A]): Pipe[F, Json, Decoder.Result[A]] =
    _.flatMap { json =>
      Stream.emit(decode(json.hcursor))
    }

  private def checkFileExists: IO[Unit] =
    if (tmpDownloadLocation.toFile.exists()) IO.unit
    else IO.raiseError(new RuntimeException(s"Downloaded file cannot be found at $tmpDownloadLocation"))

}
