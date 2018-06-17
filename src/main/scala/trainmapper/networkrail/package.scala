package trainmapper

import io.circe.Decoder.Result
import io.circe.{Decoder, HCursor}

package object networkrail {

  sealed trait IncomingMessage

  object IncomingMessage {

    implicit val decoder: Decoder[IncomingMessage] = (c: HCursor) =>
      for {
        messageType <- c.downField("header").downField("msg_type").as[String]
        decoded <- messageType match {
          //          case "0001"  => c.as[TrainActivationRecord](TrainActivationRecord.trainActivationDecoder)
          //          case "0002"  => c.as[TrainCancellationRecord](TrainCancellationRecord.trainCancellationDecoder)
          case "0003" => c.as[TrainMovementMessage](TrainMovementMessage.decoder)
          //          case "0006"  => c.as[TrainChangeOfOriginRecord](TrainChangeOfOriginRecord.trainChangeOfOriginDecoder)
          case unknown => Right(UnhandledTrainMessage(unknown))
        }
      } yield decoded
  }

  case class UnhandledTrainMessage(unhandledType: String) extends IncomingMessage

  case class TrainMovementMessage(trainId: TrainId,
                                  trainServiceCode: ServiceCode,
                                  eventType: EventType,
                                  toc: TOC,
                                  actualTimestamp: Long,
                                  plannedTimestamp: Option[Long],
                                  plannedPassengerTimestamp: Option[Long],
                                  stanoxCode: Option[StanoxCode],
                                  variationStatus: Option[VariationStatus])
      extends IncomingMessage

  object TrainMovementMessage {

    private def emptyStringOptionToNone[A](in: Option[String])(f: String => A): Option[A] =
      if (in.contains("")) None else in.map(f)

    implicit val decoder: Decoder[TrainMovementMessage] {
      def apply(c: HCursor): Result[TrainMovementMessage]
    } = new Decoder[TrainMovementMessage] {

      override def apply(c: HCursor): Result[TrainMovementMessage] = {
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
    }
  }
}
