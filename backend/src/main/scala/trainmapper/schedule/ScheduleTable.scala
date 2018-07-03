package trainmapper.schedule

import java.time.{LocalDate, LocalTime}
import java.time.format.DateTimeFormatter

import cats.effect.IO
import cats.syntax.functor._
import doobie._
import doobie.implicits._
import doobie.util.meta.Meta
import io.circe.Decoder
import trainmapper.Shared._
import trainmapper.db.Table
import trainmapper.schedule.ScheduleTable.ScheduleRecord

trait ScheduleTable extends Table[ScheduleRecord] {
  def scheduleFor(scheduleTrainId: ScheduleTrainId): IO[List[ScheduleRecord]]
  def allRecords: IO[List[ScheduleRecord]]
}

object ScheduleTable {

  val timeFormatter = DateTimeFormatter.ofPattern("HHmm")

  case class ScheduleRecord(id: Option[Int],
                            scheduleTrainId: ScheduleTrainId,
                            sequence: Int,
                            serviceCode: ServiceCode,
                            tipLocCode: TipLocCode,
                            stanoxCode: Option[StanoxCode],
                            locationType: LocationType,
                            scheduledArrivalTime: Option[LocalTime],
                            scheduledDepartureTime: Option[LocalTime],
                            scheduleStart: LocalDate,
                            scheduleEnd: LocalDate)

  object ScheduleRecord {

    import io.circe.generic.semiauto._
    import io.circe.java8.time.decodeLocalTime
    import io.circe.java8.time.decodeLocalDateDefault

    implicit val scheduleTrainIdMeta: Meta[ScheduleTrainId] = Meta[String].xmap(ScheduleTrainId(_), _.value)
    implicit val serviceCodeMeta: Meta[ServiceCode]         = Meta[String].xmap(ServiceCode(_), _.value)
    implicit val tipLocCodeMeta: Meta[TipLocCode]           = Meta[String].xmap(TipLocCode(_), _.value)
    implicit val stanoxCodeMeta: Meta[StanoxCode]           = Meta[String].xmap(StanoxCode(_), _.value)
    implicit val locationTypeMeta: Meta[LocationType]       = Meta[String].xmap(LocationType.fromString, _.string)
    implicit val localTimeMeta: doobie.Meta[LocalTime] = Meta[java.sql.Time]
      .xmap(t => LocalTime.of(t.toLocalTime.getHour, t.toLocalTime.getMinute), lt => java.sql.Time.valueOf(lt))

    implicit val decoder: Decoder[ScheduleRecord] = deriveDecoder
  }

  def apply(db: Transactor[IO]) = new ScheduleTable {
    import ScheduleRecord._

    override protected def insertRecord(record: ScheduleRecord): IO[Unit] =
      sql"""
      INSERT INTO schedule
      (schedule_train_id, sequence, service_code, tiploc_code, stanox_code, location_type, scheduled_arrival_time, scheduled_departure_time, schedule_start, schedule_end)
      VALUES(${record.scheduleTrainId}, ${record.sequence}, ${record.serviceCode}, ${record.tipLocCode}, ${record.stanoxCode}, ${record.locationType}, ${record.scheduledArrivalTime}, ${record.scheduledDepartureTime}, ${record.scheduleStart}, ${record.scheduleEnd})
     """.update.run
        .transact(db)
        .void

    override def scheduleFor(scheduleTrainId: ScheduleTrainId): IO[List[ScheduleRecord]] =
      sql"""
      SELECT id, schedule_train_id, sequence, service_code, tiploc_code, stanox_code, location_type, scheduled_arrival_time, scheduled_departure_time, schedule_start, schedule_end
      FROM schedule
      """
        .query[ScheduleRecord]
        .to[List]
        .transact(db)

    override def allRecords: IO[List[ScheduleRecord]] =
      sql"""
      SELECT id, schedule_train_id, sequence, service_code, tiploc_code, stanox_code, location_type, scheduled_arrival_time, scheduled_departure_time, schedule_start, schedule_end
      FROM schedule
      """
        .query[ScheduleRecord]
        .to[List]
        .transact(db)

  }
}
