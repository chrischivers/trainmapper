package trainmapper

import java.util.UUID

import akka.util.ByteString
import cats.effect.IO
import cats.syntax.functor._
import cats.instances.list._
import cats.syntax.traverse._
import doobie.hikari.HikariTransactor
import fs2.async.Ref
import fs2.async.mutable.Queue
import fs2.{Chunk, Scheduler, Stream}
import org.scalatest.Assertion
import org.scalatest.Matchers.fail
import stompa.Message
import trainmapper.Shared.{MovementPacket, TrainId}
import trainmapper.cache.{Cache, RedisCache}
import trainmapper.db.DatabaseConfig
import trainmapper.networkrail.{MovementMessageHandler, TrainActivationMessage}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

trait TestFixture {

  val h2DbUrl          = s"jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false"
  val h2DatabaseConfig = DatabaseConfig("org.h2.Driver", h2DbUrl, "", "")

  case class TrainMapperApp(inboundMessageQueue: Queue[IO, stompa.Message],
                            outboundQueue: Queue[IO, MovementPacket],
                            movementMessageHandler: MovementMessageHandler,
                            redisCache: Cache[TrainId, TrainActivationMessage],
                            scheduler: Scheduler) {

    def sendIncomingMessage(message: Message): IO[Unit] = inboundMessageQueue.enqueue1(message)

    def getNextOutboundMessage: IO[MovementPacket] = outboundQueue.dequeue1

    def getOutboundMessages =
      outboundQueue.timedDequeueBatch1(Int.MaxValue, 1.second, scheduler).map(_.getOrElse(Chunk.empty).toList)

    def runMessageHandler() =
      IO(
        movementMessageHandler
          .handleIncomingMessages(inboundMessageQueue, outboundQueue)
          .compile
          .drain
          .unsafeRunTimed(1.second)).void

  }

  def withApp(activationMessages: List[TrainActivationMessage] = List.empty)(f: TrainMapperApp => IO[Assertion]) =
    (for {
      scheduler       <- Scheduler.allocate[IO](1).map(_._1)
      redisCacheRef   <- Ref[IO, Map[String, ByteString]](Map.empty)
      activationCache <- IO(RedisCache(StubRedisClient(redisCacheRef)))
      _ <- activationMessages.traverse[IO, Unit] { msg =>
        activationCache.put(msg.trainId, msg)(expiry = None)
      }
      inboundMessageQueue    <- fs2.async.mutable.Queue.unbounded[IO, Message]
      outboundMessageQueue   <- fs2.async.mutable.Queue.unbounded[IO, MovementPacket]
      movementMessageHandler <- IO(MovementMessageHandler(activationCache))
    } yield {
      TrainMapperApp(inboundMessageQueue, outboundMessageQueue, movementMessageHandler, activationCache, scheduler)
    }).flatMap(f).unsafeRunSync()

  def withDatabase[A](databaseConfig: DatabaseConfig = h2DatabaseConfig)(f: HikariTransactor[IO] => IO[A]): A =
    db.withTransactor(databaseConfig)(_.clean)(transactor => Stream.eval(f(transactor)))
      .compile
      .last
      .unsafeRunSync()
      .getOrElse(fail(s"Unable to perform the operation"))
}
