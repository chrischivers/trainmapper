package trainmapper.http

import cats.effect.IO
import cats.instances.list._
import cats.syntax.traverse._
import com.typesafe.scalalogging.StrictLogging
import org.http4s.circe.{jsonEncoderOf, _}
import org.http4s.dsl.io._
import org.http4s.{EntityEncoder, HttpService}
import trainmapper.Shared.{ScheduleDetailRecord, ScheduleTrainId}
import trainmapper.db.{PolylineTable, ScheduleTable}

import scala.concurrent.ExecutionContext

object ScheduleHttp extends StrictLogging {

  implicit val entityEncoder: EntityEncoder[IO, List[ScheduleDetailRecord]] =
    jsonEncoderOf[IO, List[ScheduleDetailRecord]]

  def apply(scheduleTable: ScheduleTable, polylineTable: PolylineTable)(
      implicit executionContext: ExecutionContext): HttpService[IO] =
    HttpService[IO] {
      case GET -> Root / "schedule" / scheduleTrainId =>
        val result = for {
          scheduleRecords <- scheduleTable.scheduleFor(ScheduleTrainId(scheduleTrainId))
          scheduleDetailsRecords <- scheduleRecords.traverse[IO, ScheduleDetailRecord](
            schedule =>
              schedule.polylineIdToNext
                .fold(IO(schedule.toScheduleDetailsRecord(None)))(
                  polylineTable
                    .polyLineFor(_)
                    .map(schedule.toScheduleDetailsRecord)))

        } yield scheduleDetailsRecords

        Ok(result)
    }
}
