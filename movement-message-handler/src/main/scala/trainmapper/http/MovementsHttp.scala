package trainmapper.http

import cats.effect.IO
import com.typesafe.scalalogging.StrictLogging
import io.circe.Encoder
import io.circe.generic.semiauto._
import org.http4s.circe.jsonEncoderOf
import org.http4s.dsl.io._
import org.http4s.dsl._
import org.http4s.{HttpService, QueryParamDecoder}
import trainmapper.Shared.{LatLng, MovementPacket, ScheduleDetailRecord, TrainId}
import trainmapper.cache.ListCache
import org.http4s.circe._
import _root_.io.circe.syntax._
import _root_.io.circe._
import trainmapper.db.{PolylineTable, ScheduleTable}
import trainmapper.db.ScheduleTable.ScheduleRecord
import cats.syntax.traverse._
import cats.instances.list._

import scala.concurrent.ExecutionContext

object MovementsHttp extends StrictLogging {

  case class MovementsPayload(packets: List[MovementPacket], scheduleDetails: List[ScheduleDetailRecord])

  object MovementsPayload {
    implicit val encoder: Encoder[MovementsPayload] = deriveEncoder[MovementsPayload]
    implicit val decoder: Decoder[MovementsPayload] = deriveDecoder[MovementsPayload]
    implicit val entityEncoder                      = jsonEncoderOf[IO, MovementsPayload]
  }
  implicit val statusDecoder: QueryParamDecoder[TrainId] = QueryParamDecoder[String].map(TrainId(_))

  def apply(cache: ListCache[TrainId, MovementPacket], scheduleTable: ScheduleTable, polylineTable: PolylineTable)(
      implicit executionContext: ExecutionContext): HttpService[IO] =
    HttpService[IO] {
      case GET -> Root / "movements" / trainId =>
        val result = for {
          packets <- cache.getList(TrainId(trainId)).map(_.sortWith(_.actualTimeStamp > _.actualTimeStamp))
          scheduleRecords <- packets.headOption.fold(IO(List.empty[ScheduleRecord]))(headPacket =>
            scheduleTable.scheduleFor(headPacket.scheduleTrainId))
          scheduleDetailsRecords <- scheduleRecords.traverse[IO, ScheduleDetailRecord](
            schedule =>
              schedule.polylineIdToNext
                .fold(IO(schedule.toScheduleDetailsRecord(None)))(
                  polylineTable
                    .polyLineFor(_)
                    .map(schedule.toScheduleDetailsRecord)))

        } yield MovementsPayload(packets, scheduleDetailsRecords).asJson

        Ok(result)
    }
}
