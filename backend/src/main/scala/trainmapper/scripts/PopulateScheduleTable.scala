package trainmapper.scripts

import cats.effect.IO
import org.http4s.client.blaze.Http1Client
import trainmapper.{Config, db}
import trainmapper.schedule.{ScheduleTable, ScheduleTablePopulator}

object PopulateScheduleTable extends App {

  val config = Config()

  db.withTransactor(config.databaseConfig)() { db =>
      val scheduleTable = ScheduleTable(db)
      fs2.Stream.eval(Http1Client[IO]().flatMap { client =>
        val populator = ScheduleTablePopulator(client, scheduleTable, config.networkRailConfig)
        populator.populateTable()
      })
    }
    .compile
    .drain
    .unsafeRunSync()

}
