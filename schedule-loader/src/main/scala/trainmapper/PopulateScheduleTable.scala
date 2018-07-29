package trainmapper

import cats.effect.IO
import com.typesafe.scalalogging.StrictLogging
import org.http4s.client.blaze.Http1Client
import trainmapper.clients.{DirectionsApi, RailwaysCodesClient}
import trainmapper.db.{PolylineTable, ScheduleTable}
import trainmapper.populator.ScheduleTablePopulator
import trainmapper.reference.StopReference

object PopulateScheduleTable extends App with StrictLogging {

  val databaseConfig      = DatabaseConfig.read
  val networkRailConfig   = NetworkRailConfig.read
  val directionsApiConfig = DirectionsApiConfig.read

  db.withTransactor(databaseConfig)() { db =>
      for {
        client             <- Http1Client.stream[IO]()
        scheduleTable      <- fs2.Stream.emit(ScheduleTable(db))
        polylineTable      <- fs2.Stream.emit(PolylineTable(db))
        directionsApi      <- fs2.Stream.emit(DirectionsApi(directionsApiConfig, client))
        railwayCodesClient <- fs2.Stream.emit(RailwaysCodesClient())
        stopReference      <- fs2.Stream.emit(StopReference(railwayCodesClient))
        populator <- fs2.Stream.emit(
          ScheduleTablePopulator(client, scheduleTable, polylineTable, directionsApi, stopReference, networkRailConfig))
        _ <- fs2.Stream.eval(populator.populateTable())

      } yield logger.info("populating table complete")
    }
    .compile
    .drain
    .unsafeRunSync()

}
