package trainmapper

import cats.effect.IO
import com.typesafe.scalalogging.StrictLogging
import stompa.fs2.Fs2MessageHandler
import stompa.{Message, StompClient}
import trainmapper.http.{MovementPacket, WsServer}
import trainmapper.networkrail._

import scala.concurrent.ExecutionContext.Implicits.global

object Main extends App with StrictLogging {

  import stompa.support.IOSupport._

  val config = Config()

  val app = for {
    inboundMessageQueue  <- fs2.async.mutable.Queue.unbounded[IO, Message]
    outboundMessageQueue <- fs2.async.mutable.Queue.unbounded[IO, MovementPacket]
    stompHandler         <- IO(Fs2MessageHandler[IO](inboundMessageQueue))
    networkRailClient    <- IO(NetworkRailClient())
    stompClient          <- IO(StompClient[IO](config.stompConfig))
    _                    <- networkRailClient.subscribeToTopic(config.movementTopic, stompClient, stompHandler)
    _ <- MovementMessageHandler()
      .handleIncomingMessages(inboundMessageQueue, outboundMessageQueue)
      .concurrently { WsServer(outboundMessageQueue).stream(List.empty, IO.unit) }
      .compile
      .drain
  } yield ()

  app.unsafeRunSync()

}
