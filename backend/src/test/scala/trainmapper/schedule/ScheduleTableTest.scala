package trainmapper.schedule

import java.time.{LocalDate, LocalTime}

import trainmapper.{Shared, TestFixture}
import org.scalatest.FlatSpec
import org.scalatest.Matchers._
import trainmapper.Shared._
import trainmapper.schedule.ScheduleTable.ScheduleRecord

class ScheduleTableTest extends FlatSpec with TestFixture {

  "ScheduleTable" should "insert and retrieve a record" in {
    withDatabase() { db =>
      val scheduleRecord = ScheduleRecord(
        None,
        ScheduleTrainId("11111"),
        1,
        ServiceCode("22222"),
        TipLocCode("33333"),
        Some(StanoxCode("44444")),
        LocationType.OriginatingLocation,
        Some(LocalTime.parse("0649", Shared.timeFormatter)),
        Some(LocalTime.parse("0651", Shared.timeFormatter)),
        DaysRun("1111100"),
        LocalDate.now(),
        LocalDate.now().plusDays(5)
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
