package trainmapper.networkrail

import cats.effect.IO
import com.itv.bucky.CirceSupport.unmarshallerFromDecodeJson
import com.itv.bucky.{Ack, RequeueConsumeAction, RequeueHandler}
import com.typesafe.scalalogging.StrictLogging
import io.circe.HCursor
import trainmapper.Shared.{EventType, MovementPacket, ServiceCode, StanoxCode, TOC, TrainId, VariationStatus}
import trainmapper.cache.ListCache
import trainmapper.clients.ActivationLookupClient
import trainmapper.db.ScheduleTable
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
            cache: ListCache[TrainId, MovementPacket],
            cacheExpiry: Option[FiniteDuration]) =
    new RequeueHandler[IO, TrainMovementMessage] {
      override def apply(msg: TrainMovementMessage): IO[RequeueConsumeAction] =
        for {
          activationRecord <- activationLookupClient.fetch(msg.trainId)
          scheduleRecord <- activationRecord.fold(IO.pure(List.empty[ScheduleRecord]))(activation =>
            scheduleTable.scheduleFor(activation.scheduleTrainId))
          movementPacketOpt = activationRecord.map(
            activation =>
              MovementPacket(
                msg.trainId,
                activation.scheduleTrainId,
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
                scheduleRecord.map(_.toScheduleDetailsRecord)
            ))
          _ <- movementPacketOpt.fold {
            IO(logger.info(s"No activation record found for train Id ${msg.trainId.value}"))
          }(packet => cache.push(msg.trainId, packet)(cacheExpiry))
        } yield Ack

    }

  private def emptyStringOptionToNone[A](in: Option[String])(f: String => A): Option[A] =
    if (in.contains("")) None else in.map(f)

}
