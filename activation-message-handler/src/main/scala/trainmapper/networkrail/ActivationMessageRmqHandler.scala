package trainmapper.networkrail

import cats.effect.IO
import com.itv.bucky.CirceSupport.unmarshallerFromDecodeJson
import com.itv.bucky._
import com.typesafe.scalalogging.StrictLogging
import io.circe.HCursor
import trainmapper.Shared.{ScheduleTrainId, ServiceCode, StanoxCode, TrainId}
import trainmapper.cache.Cache

import scala.concurrent.duration.FiniteDuration

object ActivationMessageRmqHandler extends StrictLogging {

  case class TrainActivationMessage(scheduleTrainId: ScheduleTrainId,
                                    trainServiceCode: ServiceCode,
                                    trainId: TrainId,
                                    originStanox: StanoxCode,
                                    originDepartureTimestamp: Long)

  object TrainActivationMessage {

    import io.circe.generic.semiauto._

    val unmarshallFromIncomingJson = unmarshallerFromDecodeJson[TrainActivationMessage] { c: HCursor =>
      val bodyObject = c.downField("body")
      for {
        trainId              <- bodyObject.downField("train_id").as[TrainId]
        trainServiceCode     <- bodyObject.downField("train_service_code").as[ServiceCode]
        scheduleTrainId      <- bodyObject.downField("train_uid").as[ScheduleTrainId]
        originStanox         <- bodyObject.downField("sched_origin_stanox").as[StanoxCode]
        originDepartTimstamp <- bodyObject.downField("origin_dep_timestamp").as[Long]
      } yield {
        TrainActivationMessage(scheduleTrainId, trainServiceCode, trainId, originStanox, originDepartTimstamp)
      }
    }

    implicit val encoder = deriveEncoder[TrainActivationMessage]
    implicit val decoder = deriveDecoder[TrainActivationMessage]
  }

  def apply(cache: Cache[TrainId, TrainActivationMessage], cacheExpiry: Option[FiniteDuration]) =
    new RequeueHandler[IO, TrainActivationMessage] {
      override def apply(msg: TrainActivationMessage): IO[RequeueConsumeAction] = {
        logger.info(s"Putting message with train id ${msg.trainId} into cache with expiry $cacheExpiry")
        cache.put(msg.trainId, msg)(expiry = cacheExpiry).map(_ => Ack)
      }
    }

}
