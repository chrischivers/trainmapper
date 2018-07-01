package trainmapper.schedule

import java.time.LocalTime
import java.time.format.DateTimeFormatter

import trainmapper.TestFixture
import org.scalatest.FlatSpec
import org.scalatest.Matchers._
import trainmapper.Shared.{ScheduleTrainId, ServiceCode, StanoxCode, TipLocCode}
import trainmapper.schedule.ScheduleTable.ScheduleRecord

class ScheduleTableTest extends FlatSpec with TestFixture {

  val timeFormatter = DateTimeFormatter.ofPattern("HHmm")

  "ScheduleTable" should "insert and retrieve a record" in {
    withDatabase() { db =>
      val scheduleRecord = ScheduleRecord(
        None,
        ScheduleTrainId("11111"),
        1,
        ServiceCode("22222"),
        TipLocCode("33333"),
        StanoxCode("44444"),
        LocationType.OriginatingLocation,
        Some(LocalTime.parse("0649", timeFormatter)),
        Some(LocalTime.parse("0651", timeFormatter))
      )
      val scheduleTable = ScheduleTable(db)
      for {
        _       <- scheduleTable.safeInsertRecord(scheduleRecord)
        records <- scheduleTable.scheduleFor(scheduleRecord.scheduleTrainId)
      } yield {
        records should ===(List(scheduleRecord.copy(id = Some(1))))
      }
    }
  }

}
