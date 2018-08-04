package trainmapper.populator

import java.nio.file.{Path, Paths, StandardOpenOption}
import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalTime}

import cats.data.OptionT
import cats.effect.IO
import com.typesafe.scalalogging.StrictLogging
import fs2.Pipe
import fs2.compress._
import io.circe.Decoder.Result
import io.circe.fs2._
import io.circe.{Decoder, DecodingFailure, HCursor, Json}
import org.http4s.client.Client
import org.http4s.client.middleware.FollowRedirect
import org.http4s.headers.Authorization
import org.http4s.{BasicCredentials, EntityBody, Headers, Request}
import trainmapper.NetworkRailConfig
import trainmapper.Shared.{DaysRun, LocationType, ScheduleTrainId, ServiceCode, TipLocCode, TrainCategory}
import trainmapper.clients.DirectionsApi
import trainmapper.db.ScheduleTable.ScheduleRecord
import trainmapper.db.{PolylineTable, ScheduleTable}
import trainmapper.populator.ScheduleTablePopulator.DecodedScheduleRecord.ScheduleSegment
import trainmapper.populator.ScheduleTablePopulator.DecodedScheduleRecord.ScheduleSegment.ScheduleLocation
import trainmapper.reference.StopReference

import scala.collection.immutable.Queue

trait ScheduleTablePopulator {

  def populateTable(): IO[Unit]
}

object ScheduleTablePopulator extends StrictLogging {

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

    def toScheduleRecordsWithoutPolyline: List[ScheduleRecord.WithoutPolyline] =
      schedule_segment.schedule_location.zipWithIndex.map {
        case (loc, sequence) =>
          ScheduleRecord.WithoutPolyline(
            CIF_train_uid,
            sequence,
            schedule_segment.CIF_train_service_code,
            loc.tiploc_code,
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
    import io.circe.java8.time
    import io.circe.java8.time.decodeLocalDateDefault

    val timeFormatter: DateTimeFormatter         = DateTimeFormatter.ofPattern("HHmm")
    implicit val timeDecoder: Decoder[LocalTime] = time.decodeLocalTime(timeFormatter)

    implicit val decoder: Decoder[DecodedScheduleRecord] = deriveDecoder

    case class ScheduleSegment(CIF_train_service_code: ServiceCode,
                               CIF_train_category: TrainCategory,
                               schedule_location: List[ScheduleLocation])
    object ScheduleSegment {
      implicit val decoder: Decoder[ScheduleSegment] = deriveDecoder

      case class ScheduleLocation(location_type: LocationType,
                                  tiploc_code: TipLocCode,
//                                  pass: Option[LocalTime],
                                  public_departure: Option[LocalTime],
                                  public_arrival: Option[LocalTime])

      object ScheduleLocation {
        implicit val decoder: Decoder[ScheduleLocation] = deriveDecoder
      }
    }

  }

  def apply(client: Client[IO],
            scheduleTable: ScheduleTable,
            polylineTable: PolylineTable,
            directionsApi: DirectionsApi,
            stopReference: StopReference,
            config: NetworkRailConfig) = new ScheduleTablePopulator {

    val credentials = BasicCredentials(config.username, config.password)

    override def populateTable(): IO[Unit] =
      for {
        _ <- deleteTmpFiles()
        _ <- downloadFromUrl()
        _ <- unpackScheduleData()
        _ <- checkFileExists
        lastRec <- readData.zipWithIndex
          .evalMap { case (rec, i) => scheduleTable.safeInsertRecord(rec).map(_ => i) }
          .compile
          .last
        _ <- IO(logger.info(s"Inserted ${lastRec.getOrElse("N/A")} records into database"))
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

    def withPolyLine: Pipe[IO, Queue[ScheduleRecord.WithoutPolyline], ScheduleRecord] = _.evalMap { queue =>
      queue.toList match {
        case from :: to :: Nil =>
          for {
            existingPolylineId <- polylineTable.polylineIdFor(from.tipLocCode, to.tipLocCode)
            polylineId <- existingPolylineId.fold(
              getPolylineAndPersist(from.tipLocCode,
                                    to.tipLocCode,
                                    from.scheduledDepartureTime.get,
                                    from.daysRun,
                                    from.scheduleStart,
                                    from.scheduleEnd))(IO.pure)
          } yield from.toScheduleRecord(polylineId)
        case other =>
          IO.raiseError(new RuntimeException(s"Unexpected number of elements in sliding window. [$other]"))
      }
    }

    type PolylineId = Int

    private def getPolylineAndPersist(from: TipLocCode,
                                      to: TipLocCode,
                                      departureTime: LocalTime,
                                      daysRun: DaysRun,
                                      scheduleStart: LocalDate,
                                      scheduleEnd: LocalDate): IO[PolylineId] = {
      logger.info(s"Geting polyline from tiploc ${from.value} to tiploc ${to.value}")
      val result = for {
        fromReferenceDetails <- OptionT.fromOption[IO](stopReference.referenceDetailsFor(from))
        _ = logger.info(s"From reference details $fromReferenceDetails")
        toReferenceDetails <- OptionT.fromOption[IO](stopReference.referenceDetailsFor(to))
        _ = logger.info(s"To reference details $toReferenceDetails")
        fromLatLng <- OptionT.fromOption[IO](fromReferenceDetails.latLng)
        toLatLng   <- OptionT.fromOption[IO](toReferenceDetails.latLng)
        polyLine <- OptionT(
          directionsApi.trainPolylineFor(fromLatLng, toLatLng, departureTime, daysRun, scheduleStart, scheduleEnd))
        _ = logger.info(s"Polyline obtained $polyLine")
        insertedRecord <- OptionT.liftF(polylineTable.insertAndRetrieveInserted(from, to, polyLine))
      } yield insertedRecord.id
      result.value
        .flatMap(
          _.fold(IO.raiseError[PolylineId](new RuntimeException("Error retrieving and writing polyline")))(IO.pure))
    }

    private def isScheduleRecord(json: Json) = json.hcursor.downField("JsonScheduleV1").succeeded

    private def isNotReplacementBus(decodedScheduleRecord: DecodedScheduleRecord) =
      decodedScheduleRecord.schedule_segment.CIF_train_category.value != "BR"

    private def removePassingOnlyStations: Pipe[IO, DecodedScheduleRecord, DecodedScheduleRecord] = _.map { rec =>
      rec.copy(
        schedule_segment =
          rec.schedule_segment.copy(schedule_location = rec.schedule_segment.schedule_location.filter(r =>
            r.public_arrival.isDefined || r.public_departure.isDefined)))
    }

    private def readData: fs2.Stream[IO, ScheduleRecord] =
      fs2.io.file
        .readAll[IO](tmpUnzipLocation, 4096)
        .through(fs2.text.utf8Decode)
        .through(fs2.text.lines)
        .through(stringStreamParser[IO])
        .dropLast //EOF line
        .filter(isScheduleRecord)
        .through(decoderPipe[DecodedRecord])
        .collect { case Right(decodedRecord: DecodedScheduleRecord) => decodedRecord }
        .filter(isNotReplacementBus)
        .through(removePassingOnlyStations)
        .through(_.map(_.toScheduleRecordsWithoutPolyline))
        .flatMap(scheduleRecords =>
          fs2.Stream.fromIterator[IO, ScheduleRecord.WithoutPolyline](scheduleRecords.toIterator).sliding(2))
        .through(withPolyLine)

  }

  def decoderPipe[A](implicit decode: Decoder[A]): Pipe[IO, Json, Decoder.Result[A]] =
    _.evalMap(
      json =>
        decode(json.hcursor).fold[IO[Decoder.Result[A]]](
          err =>
            IO {
              logger.error(s"Error decoding json due to ${err.getMessage()} Json: $json")
              Left(err)
          },
          d => IO.pure(Right(d))))

  private def checkFileExists: IO[Unit] =
    if (tmpDownloadLocation.toFile.exists()) IO.unit
    else IO.raiseError(new RuntimeException(s"Downloaded file cannot be found at $tmpDownloadLocation"))

}
