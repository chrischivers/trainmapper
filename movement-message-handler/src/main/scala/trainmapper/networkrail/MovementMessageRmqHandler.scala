package trainmapper.networkrail

import cats.data.OptionT
import cats.syntax.traverse._
import cats.instances.list._
import cats.effect.IO
import com.itv.bucky.CirceSupport.unmarshallerFromDecodeJson
import com.itv.bucky.{Ack, RequeueConsumeAction, RequeueHandler}
import com.typesafe.scalalogging.StrictLogging
import io.circe.HCursor
import trainmapper.Shared.{
  EventType,
  MovementPacket,
  ScheduleDetailRecord,
  ServiceCode,
  StanoxCode,
  TOC,
  TrainId,
  VariationStatus
}
import trainmapper.cache.ListCache
import trainmapper.clients.ActivationLookupClient
import trainmapper.db.{PolylineTable, ScheduleTable}
import trainmapper.db.ScheduleTable.ScheduleRecord
import trainmapper.reference.StopReference

import scala.concurrent.duration.FiniteDuration

object MovementMessageRmqHandler extends StrictLogging {

  case class TrainMovementMessage(trainId: TrainId,
                                  trainServiceCode: ServiceCode,
                                  eventType: EventType,
                                  toc: TOC,
                                  actualTimestamp: Long,
                                  plannedTimestamp: Option[Long],
                                  plannedPassengerTimestamp: Option[Long],
                                  stanoxCode: Option[StanoxCode],
                                  variationStatus: Option[VariationStatus])

  object TrainMovementMessage {

    import io.circe.generic.semiauto._

    val unmarshallFromIncomingJson = unmarshallerFromDecodeJson[TrainMovementMessage] { c: HCursor =>
      val bodyObject = c.downField("body")
      for {
        trainId          <- bodyObject.downField("train_id").as[TrainId]
        trainServiceCode <- bodyObject.downField("train_service_code").as[ServiceCode]
        eventType        <- bodyObject.downField("event_type").as[EventType]
        toc              <- bodyObject.downField("toc_id").as[TOC]
        actualTimestamp <- bodyObject
          .downField("actual_timestamp")
          .as[String]
          .map(_.toLong)
        plannedTimestamp <- bodyObject
          .downField("planned_timestamp")
          .as[Option[String]]
          .map(emptyStringOptionToNone(_)(_.toLong))
        plannedPassengerTimestamp <- bodyObject
          .downField("gbtt_timestamp")
          .as[Option[String]]
          .map(emptyStringOptionToNone(_)(_.toLong))
        stanoxCode <- bodyObject
          .downField("loc_stanox")
          .as[Option[String]]
          .map(emptyStringOptionToNone(_)(StanoxCode(_)))
        variationStatus <- bodyObject.downField("variation_status").as[Option[VariationStatus]]

      } yield {
        TrainMovementMessage(trainId,
                             trainServiceCode,
                             eventType,
                             toc,
                             actualTimestamp,
                             plannedTimestamp,
                             plannedPassengerTimestamp,
                             stanoxCode,
                             variationStatus)
      }
    }

//    implicit val encoder = deriveEncoder[TrainMovementMessage]
    implicit val decoder = deriveDecoder[TrainMovementMessage]
  }

  def apply(activationLookupClient: ActivationLookupClient,
            stopReference: StopReference,
            scheduleTable: ScheduleTable,
            polylineTable: PolylineTable,
            cache: ListCache[TrainId, MovementPacket],
            cacheExpiry: Option[FiniteDuration]) =
    new RequeueHandler[IO, TrainMovementMessage] {
      override def apply(msg: TrainMovementMessage): IO[RequeueConsumeAction] = {
        val result = for {
          activationRecord <- OptionT(activationLookupClient.fetch(msg.trainId))
          scheduleRecord   <- OptionT.liftF(scheduleTable.scheduleFor(activationRecord.scheduleTrainId))
          scheduleDetailsRecords <- OptionT.liftF(scheduleRecord.traverse[IO, ScheduleDetailRecord](rec =>
            rec.polylineIdToNext.fold(IO(rec.toScheduleDetailsRecord(None)))(idToNext =>
              polylineTable.polyLineFor(idToNext).map(polyLine => rec.toScheduleDetailsRecord(polyLine)))))
          movementPacket = MovementPacket(
            msg.trainId,
            activationRecord.scheduleTrainId,
            msg.trainServiceCode,
            msg.toc,
            msg.stanoxCode,
            msg.stanoxCode.flatMap(stopReference.referenceDetailsFor),
            msg.eventType,
            msg.actualTimestamp,
            MovementPacket.timeStampToString(msg.actualTimestamp),
            msg.plannedTimestamp,
            msg.plannedTimestamp.map(MovementPacket.timeStampToString),
            msg.plannedPassengerTimestamp,
            msg.plannedPassengerTimestamp.map(MovementPacket.timeStampToString),
            msg.variationStatus,
            scheduleDetailsRecords
          )
          _ <- OptionT.liftF(cache.push(msg.trainId, movementPacket)(cacheExpiry))
        } yield ()

        result.value.map(_.fold {
          logger.info(s"No activation record found for trainId ${msg.trainId} [$msg]")
          Ack
        }(_ => Ack))
      }
    }

  private def emptyStringOptionToNone[A](in: Option[String])(f: String => A): Option[A] =
    if (in.contains("")) None else in.map(f)

}
