package trainmapper

import akka.actor.ActorSystem
import cats.effect.IO
import com.itv.bucky.Monad.Id
import com.itv.bucky.pattern.requeue.RequeuePolicy
import com.itv.bucky.{AmqpClient, fs2 => rabbitfs2}
import com.itv.bucky.pattern.requeue.RequeueOps
import com.typesafe.scalalogging.StrictLogging
import redis.RedisClient
import trainmapper.cache.RedisCache
import trainmapper.networkrail.ActivationMessageRmqHandler
import trainmapper.networkrail.ActivationMessageRmqHandler.TrainActivationMessage

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object ActivationMessageHandler extends StrictLogging {

  def appFrom[E](redisClient: RedisClient, rabbitClient: AmqpClient[Id, IO, E, fs2.Stream[IO, Unit]])(
      implicit executionContext: ExecutionContext) =
    for {
      cache <- fs2.Stream.eval(IO(RedisCache(redisClient)))
      _ <- fs2.Stream.eval(IO.unit).concurrently {
        RequeueOps(rabbitClient)
          .requeueHandlerOf[TrainActivationMessage](
            RabbitConfig.activationQueue.name,
            ActivationMessageRmqHandler(cache),
            RequeuePolicy(maximumProcessAttempts = 10, 3.minute),
            TrainActivationMessage.unmarshallFromIncomingJson
          )
      }
    } yield ()

}

object ActivationMessageHandlerMain extends App {

  import scala.concurrent.ExecutionContext.Implicits.global
  implicit val actorSystem = ActorSystem()

  val app = for {
    rabbitConfig <- fs2.Stream.eval(IO(RabbitConfig.read))
    rabbitClient <- rabbitfs2.clientFrom(rabbitConfig, RabbitConfig.declarations)
    redisClient  <- fs2.Stream.eval(IO(RedisClient()))
    _            <- ActivationMessageHandler.appFrom(redisClient, rabbitClient)
  } yield ()

  app.compile.drain.unsafeRunSync()

}
