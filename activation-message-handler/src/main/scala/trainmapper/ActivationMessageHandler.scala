package trainmapper

import akka.actor.ActorSystem
import cats.effect.IO
import com.itv.bucky.Monad.Id
import com.itv.bucky.pattern.requeue.RequeuePolicy
import com.itv.bucky.{AmqpClient, fs2 => rabbitfs2}
import com.itv.bucky.pattern.requeue.RequeueOps
import com.typesafe.scalalogging.StrictLogging
import fs2.Stream
import org.http4s.HttpService
import org.http4s.server.blaze.BlazeBuilder
import redis.RedisClient
import trainmapper.Shared.TrainId
import trainmapper.cache.{Cache, ActivationCache}
import trainmapper.http.ActivationHttp
import trainmapper.networkrail.ActivationMessageRmqHandler
import trainmapper.networkrail.ActivationMessageRmqHandler.TrainActivationMessage

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object ActivationMessageHandler extends StrictLogging {

  type RabbitClient[E] = AmqpClient[Id, IO, E, fs2.Stream[IO, Unit]]

  case class ActivationMessageHandlerApp(httpService: HttpService[IO],
                                         rabbit: fs2.Stream[IO, Unit],
                                         cache: Cache[TrainId, TrainActivationMessage])

  def appFrom[E](redisClient: RedisClient, rabbitClient: RabbitClient[E], cacheExpiry: Option[FiniteDuration])(
      implicit executionContext: ExecutionContext) =
    for {
      cache       <- fs2.Stream.eval(IO(ActivationCache(redisClient)))
      httpService <- fs2.Stream.eval(IO(ActivationHttp(cache)))
    } yield ActivationMessageHandlerApp(httpService, startRabbit(rabbitClient, cache, cacheExpiry), cache)

  private def startRabbit[E](rabbitClient: RabbitClient[E],
                             cache: Cache[TrainId, TrainActivationMessage],
                             cacheExpiry: Option[FiniteDuration]) =
    RequeueOps(rabbitClient)
      .requeueHandlerOf[TrainActivationMessage](
        RabbitConfig.activationQueue.name,
        ActivationMessageRmqHandler(cache, cacheExpiry),
        RequeuePolicy(maximumProcessAttempts = 10, 3.minute),
        TrainActivationMessage.unmarshallFromIncomingJson
      )

}

object ActivationMessageHandlerMain extends App {

  import scala.concurrent.ExecutionContext.Implicits.global
  implicit val actorSystem = ActorSystem()

  private def startServer(service: HttpService[IO], port: Int): Stream[IO, fs2.StreamApp.ExitCode] =
    BlazeBuilder[IO]
      .bindHttp(port, "0.0.0.0")
      .mountService(service)
      .serve

  val app = for {
    appConfig    <- fs2.Stream.eval(IO(ServerConfig.read))
    rabbitConfig <- fs2.Stream.eval(IO(RabbitConfig.read))
    rabbitClient <- rabbitfs2.clientFrom(rabbitConfig, RabbitConfig.declarations)
    redisClient  <- fs2.Stream.eval(IO(RedisClient()))
    app          <- ActivationMessageHandler.appFrom(redisClient, rabbitClient, Some(appConfig.activationExpiry))
    _ <- startServer(app.httpService, appConfig.port).concurrently {
      app.rabbit
    }
  } yield ()

  app.compile.drain.unsafeRunSync()

}
