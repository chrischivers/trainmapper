package trainmapper.networkrail

import cats.effect.IO
import cats.instances.list._
import cats.syntax.functor._
import cats.syntax.traverse._
import com.itv.bucky.CirceSupport.marshallerFromEncodeJson
import com.itv.bucky.PublishCommandBuilder.publishCommandBuilder
import com.itv.bucky._
import com.typesafe.scalalogging.StrictLogging
import io.circe.Json
import io.circe.parser.decode
import stompa.Message
import trainmapper.RabbitConfig
import _root_.fs2.async.mutable.Queue
import _root_.fs2.Stream

trait MovementMessageReceiver {
  def handleIncomingMessages(inboundQueue: Queue[IO, stompa.Message]): Stream[IO, Unit]
}

object MovementMessageReceiver extends StrictLogging {

  def apply(publisher: Publisher[IO, PublishCommand]) =
    new MovementMessageReceiver {
      override def handleIncomingMessages(inboundQueue: Queue[IO, Message]): Stream[IO, Unit] =
        inboundQueue.dequeue
          .evalMap(handleMessage)

      private def handleMessage(message: Message): IO[Unit] =
        decode[List[Json]](message.body).fold(
          err => IO(logger.error(s"Unable to decode Json list from [${message.body}]. Error [$err]")),
          _.traverse[IO, Unit] { msg =>
            routingKeyFromMsgJson(msg).fold(
              err => IO(logger.error(s"Unable to decode routing key from message [$msg]. Error [$err]")),
              routingKey => {
                IO(logger.info(s"publishing message to $routingKey")).flatMap(_ =>
                  publisher(publisherFrom(routingKey).toPublishCommand(msg)))
              }
            )
          }.void
        )

      private def publisherFrom(routingKey: RoutingKey): PublishCommandBuilder.Builder[Json] =
        publishCommandBuilder(marshallerFromEncodeJson[Json])
          .using(RabbitConfig.trainMovementsExchange.name)
          .using(routingKey)
          .using(MessageProperties.persistentBasic.copy(contentType = Some(ContentType("application/json"))))

      private def routingKeyFromMsgJson(json: Json) =
        json.hcursor.downField("header").downField("msg_type").as[String].map(RoutingKey)
    }
}
