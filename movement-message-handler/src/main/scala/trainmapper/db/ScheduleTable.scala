package trainmapper.db

import java.time.{LocalDate, LocalTime}

import cats.effect.IO
import cats.syntax.functor._
import doobie._
import doobie.implicits._
import doobie.util.meta.Meta
import io.circe.Decoder
import trainmapper.Shared._
import trainmapper.db.ScheduleTable.ScheduleRecord

trait ScheduleTable extends Table[ScheduleRecord] {
  def scheduleFor(scheduleTrainId: ScheduleTrainId): IO[List[ScheduleRecord]]
  def allRecords: IO[List[ScheduleRecord]]
  def deleteAllRecords: IO[Unit]
}

object ScheduleTable {

  case class ScheduleRecord(id: Option[Int],
                            scheduleTrainId: ScheduleTrainId,
                            sequence: Int,
                            serviceCode: ServiceCode,
                            tipLocCode: TipLocCode,
                            locationType: LocationType,
                            scheduledArrivalTime: Option[LocalTime],
                            scheduledDepartureTime: Option[LocalTime],
                            daysRun: DaysRun,
                            scheduleStart: LocalDate,
                            scheduleEnd: LocalDate,
                            polylineIdToNext: Option[Int]) {
    def toScheduleDetailsRecord(polyLineToNext: Option[Polyline]) =
      ScheduleDetailRecord(this.sequence,
                           this.tipLocCode,
                           this.locationType,
                           this.scheduledArrivalTime,
                           this.scheduledDepartureTime,
                           this.daysRun,
                           polyLineToNext)
  }

  object ScheduleRecord {

    import io.circe.generic.semiauto._
    import io.circe.java8.time.{decodeLocalDateDefault, decodeLocalTime}

    implicit val scheduleTrainIdMeta: Meta[ScheduleTrainId] = Meta[String].xmap(ScheduleTrainId(_), _.value)
    implicit val serviceCodeMeta: Meta[ServiceCode]         = Meta[String].xmap(ServiceCode(_), _.value)
    implicit val tipLocCodeMeta: Meta[TipLocCode]           = Meta[String].xmap(TipLocCode(_), _.value)
    implicit val locationTypeMeta: Meta[LocationType]       = Meta[String].xmap(LocationType.fromString, _.string)
    implicit val daysRunMeta: Meta[DaysRun]                 = Meta[String].xmap(DaysRun(_), _.value)
    implicit val localTimeMeta: doobie.Meta[LocalTime] = Meta[java.sql.Time]
      .xmap(t => LocalTime.of(t.toLocalTime.getHour, t.toLocalTime.getMinute), lt => java.sql.Time.valueOf(lt))

    implicit val decoder: Decoder[ScheduleRecord] = deriveDecoder

    case class WithoutPolyline(scheduleTrainId: ScheduleTrainId,
                               sequence: Int,
                               serviceCode: ServiceCode,
                               tipLocCode: TipLocCode,
                               locationType: LocationType,
                               scheduledArrivalTime: Option[LocalTime],
                               scheduledDepartureTime: Option[LocalTime],
                               daysRun: DaysRun,
                               scheduleStart: LocalDate,
                               scheduleEnd: LocalDate) {
      def toScheduleRecord(polylineId: Option[Int]) =
        ScheduleRecord(
          None,
          scheduleTrainId,
          sequence,
          serviceCode,
          tipLocCode,
          locationType,
          scheduledArrivalTime,
          scheduledDepartureTime,
          daysRun,
          scheduleStart,
          scheduleEnd,
          polylineId
        )
    }
  }

  def apply(db: Transactor[IO]) = new ScheduleTable {

    import ScheduleRecord._

    override protected def insertRecord(record: ScheduleRecord): IO[Unit] =
      sql"""
      INSERT INTO schedule
      (schedule_train_id, sequence, service_code, tiploc_code, location_type, scheduled_arrival_time, scheduled_departure_time, days_run, schedule_start, schedule_end, polyline_id)
      VALUES(${record.scheduleTrainId}, ${record.sequence}, ${record.serviceCode}, ${record.tipLocCode}, ${record.locationType}, ${record.scheduledArrivalTime}, ${record.scheduledDepartureTime}, ${record.daysRun}, ${record.scheduleStart}, ${record.scheduleEnd}, ${record.polylineIdToNext})
     """.update.run
        .transact(db)
        .void

    override def scheduleFor(scheduleTrainId: ScheduleTrainId): IO[List[ScheduleRecord]] =
      sql"""
      SELECT id, schedule_train_id, sequence, service_code, tiploc_code, location_type, scheduled_arrival_time, scheduled_departure_time, days_run, schedule_start, schedule_end, polyline_id
      FROM schedule
      WHERE schedule_train_id = $scheduleTrainId
      ORDER BY sequence
      """
        .query[ScheduleRecord]
        .to[List]
        .transact(db)

    override def allRecords: IO[List[ScheduleRecord]] =
      sql"""
      SELECT id, schedule_train_id, sequence, service_code, tiploc_code, location_type, scheduled_arrival_time, scheduled_departure_time, days_run, schedule_start, schedule_end, polyline_id
      FROM schedule
      """
        .query[ScheduleRecord]
        .to[List]
        .transact(db)
    override def deleteAllRecords: IO[Unit] = sql"""DELETE FROM schedule""".update.run.transact(db).void
  }
}
