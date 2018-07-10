package trainmapper

import java.time.Clock

import akka.actor.ActorSystem
import cats.effect.IO
import com.typesafe.scalalogging.StrictLogging
import redis.RedisClient
import stompa.fs2.Fs2MessageHandler
import stompa.{Message, StompClient, StompConfig}
import trainmapper.Shared.MovementPacket
import trainmapper.cache.RedisCache
import trainmapper.server.ServerWithWebsockets
import trainmapper.networkrail._
import trainmapper.schedule.ScheduleTable
import trainmapper.scripts.PopulateScheduleTable.config

import scala.concurrent.ExecutionContext.Implicits.global

object Main extends App with StrictLogging {

  import stompa.support.IOSupport._

  implicit val actorSystem = ActorSystem()

  val config = Config()

  val clock = Clock.systemUTC()

  val app = db.withTransactor(config.databaseConfig)() { db =>
    for {
      inboundMessageQueue  <- fs2.async.mutable.Queue.unbounded[IO, Message]
      outboundMessageQueue <- fs2.async.mutable.Queue.unbounded[IO, MovementPacket]
      stompHandler         <- IO(Fs2MessageHandler[IO](inboundMessageQueue))
      networkRailClient    <- IO(NetworkRailClient())
      stompConfig <- IO(
        StompConfig(config.networkRailConfig.stompUrl.renderString,
                    config.networkRailConfig.stompPort,
                    config.networkRailConfig.username,
                    config.networkRailConfig.password))
      stompClient     <- IO(StompClient[IO](stompConfig))
      _               <- networkRailClient.subscribeToTopic(config.networkRailConfig.movementTopic, stompClient, stompHandler)
      redisClient     <- IO(RedisClient())
      activationCache <- IO(RedisCache(redisClient))
      scheduleTable   <- IO(ScheduleTable(db))
    } yield {
      MovementMessageHandler(activationCache, scheduleTable)(clock)
        .handleIncomingMessages(inboundMessageQueue, outboundMessageQueue)
        .concurrently {
          ServerWithWebsockets(outboundMessageQueue, config.googleMapsApiKey).stream(List.empty, IO.unit)
        }
    }
  }

  app.compile.drain.unsafeRunSync()

}
