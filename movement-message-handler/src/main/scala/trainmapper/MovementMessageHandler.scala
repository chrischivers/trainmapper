package trainmapper

import akka.actor.ActorSystem
import cats.effect.IO
import com.itv.bucky.Monad.Id
import com.itv.bucky.pattern.requeue.{RequeueOps, RequeuePolicy}
import com.itv.bucky.{AmqpClient, fs2 => rabbitfs2}
import com.typesafe.scalalogging.StrictLogging
import fs2.Stream
import org.http4s.client.Client
import org.http4s.client.blaze.Http1Client
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.{HttpService, Uri}
import redis.RedisClient
import trainmapper.ActivationLookupConfig._
import trainmapper.Shared.{MovementPacket, TrainId}
import trainmapper.cache.{ListCache, MovementPacketCache}
import trainmapper.clients.{ActivationLookupClient, RailwaysCodesClient}
import trainmapper.http.MovementsHttp
import trainmapper.networkrail.MovementMessageRmqHandler
import trainmapper.networkrail.MovementMessageRmqHandler.TrainMovementMessage
import trainmapper.reference.StopReference

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object MovementMessageHandler extends StrictLogging {

  type RabbitClient[E] = AmqpClient[Id, IO, E, fs2.Stream[IO, Unit]]

  case class MovementMessageHandlerApp(httpService: HttpService[IO],
                                       rabbit: fs2.Stream[IO, Unit],
                                       cache: ListCache[TrainId, MovementPacket])

  def appFrom[E](redisClient: RedisClient,
                 rabbitClient: RabbitClient[E],
                 httpClient: Client[IO],
                 railwaysCodesClient: RailwaysCodesClient,
                 cacheExpiry: Option[FiniteDuration],
                 activationLookupConfig: ActivationLookupConfig)(implicit executionContext: ExecutionContext) =
    for {
      cache            <- fs2.Stream.eval(IO(MovementPacketCache(redisClient)))
      httpService      <- fs2.Stream.eval(IO(MovementsHttp(cache)))
      activationClient <- fs2.Stream.eval(IO(ActivationLookupClient(activationLookupConfig.baseUri, httpClient)))
      stopReference    <- fs2.Stream.eval(IO(StopReference(railwaysCodesClient)))
    } yield
      MovementMessageHandlerApp(httpService,
                                startRabbit(rabbitClient, activationClient, stopReference, cache, cacheExpiry),
                                cache)

  private def startRabbit[E](rabbitClient: RabbitClient[E],
                             activationLookupClient: ActivationLookupClient,
                             stopReference: StopReference,
                             cache: ListCache[TrainId, MovementPacket],
                             cacheExpiry: Option[FiniteDuration]) =
    RequeueOps(rabbitClient)
      .requeueHandlerOf[TrainMovementMessage](
        RabbitConfig.movementQueue.name,
        MovementMessageRmqHandler(activationLookupClient, stopReference, cache, cacheExpiry),
        RequeuePolicy(maximumProcessAttempts = 10, 3.minute),
        TrainMovementMessage.unmarshallFromIncomingJson
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
    serverConfig           <- fs2.Stream.eval(IO(ServerConfig.read))
    activationLookupConfig <- fs2.Stream.eval(IO(ActivationLookupConfig.read))
    rabbitConfig           <- fs2.Stream.eval(IO(RabbitConfig.read))
    rabbitClient           <- rabbitfs2.clientFrom(rabbitConfig, RabbitConfig.declarations)
    redisClient            <- fs2.Stream.eval(IO(RedisClient()))
    httpClient             <- Http1Client.stream[IO]()
    railwayCodesClient     <- fs2.Stream.eval(IO(RailwaysCodesClient()))
    app <- MovementMessageHandler.appFrom(redisClient,
                                          rabbitClient,
                                          httpClient,
                                          railwayCodesClient,
                                          Some(serverConfig.movementExpiry),
                                          activationLookupConfig)
    _ <- startServer(app.httpService, serverConfig.port).concurrently {
      app.rabbit
    }
  } yield ()

  app.compile.drain.unsafeRunSync()

}
