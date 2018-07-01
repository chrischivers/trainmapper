package trainmapper

import akka.actor.ActorSystem
import cats.effect.IO
import com.typesafe.scalalogging.StrictLogging
import redis.RedisClient
import stompa.fs2.Fs2MessageHandler
import stompa.{Message, StompClient}
import trainmapper.Shared.MovementPacket
import trainmapper.cache.RedisCache
import trainmapper.server.ServerWithWebsockets
import trainmapper.networkrail._

import scala.concurrent.ExecutionContext.Implicits.global

object Main extends App with StrictLogging {

  import stompa.support.IOSupport._

  implicit val actorSystem = ActorSystem()

  val config = Config()

  val app = for {
    inboundMessageQueue  <- fs2.async.mutable.Queue.unbounded[IO, Message]
    outboundMessageQueue <- fs2.async.mutable.Queue.unbounded[IO, MovementPacket]
    stompHandler         <- IO(Fs2MessageHandler[IO](inboundMessageQueue))
    networkRailClient    <- IO(NetworkRailClient())
    stompClient          <- IO(StompClient[IO](config.stompConfig))
    _                    <- networkRailClient.subscribeToTopic(config.movementTopic, stompClient, stompHandler)
    redisClient          <- IO(RedisClient())
    activationCache      <- IO(RedisCache(redisClient))
    _ <- MovementMessageHandler(activationCache)
      .handleIncomingMessages(inboundMessageQueue, outboundMessageQueue)
      .concurrently { ServerWithWebsockets(outboundMessageQueue, config.googleMapsApiKey).stream(List.empty, IO.unit) }
      .compile
      .drain
  } yield ()

  app.unsafeRunSync()

}
