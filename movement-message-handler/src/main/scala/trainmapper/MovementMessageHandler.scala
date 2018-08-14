package trainmapper

import akka.actor.ActorSystem
import cats.effect.IO
import com.itv.bucky.Monad.Id
import com.itv.bucky.pattern.requeue.{RequeueOps, RequeuePolicy}
import com.itv.bucky.{AmqpClient, fs2 => rabbitfs2}
import com.typesafe.scalalogging.StrictLogging
import fs2.Stream
import org.http4s.HttpService
import org.http4s.client.Client
import org.http4s.client.blaze.Http1Client
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeBuilder
import redis.RedisClient
import trainmapper.ActivationLookupConfig._
import trainmapper.ServerConfig.ApplicationConfig
import trainmapper.Shared.{MovementPacket, TrainId}
import trainmapper.cache.{ListCache, MovementPacketCache}
import trainmapper.clients.{ActivationLookupClient, RailwaysCodesClient}
import trainmapper.db.{PolylineTable, ScheduleTable}
import trainmapper.http.{MovementsHttp, ScheduleHttp}
import trainmapper.http.MovementsHttp.MovementsHttpResponse
import trainmapper.networkrail.MovementMessageRmqHandler
import trainmapper.networkrail.MovementMessageRmqHandler.TrainMovementMessage
import trainmapper.reference.StopReference
import trainmapper.server.WebServer

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object MovementMessageHandler extends StrictLogging {

  type RabbitClient[E] = AmqpClient[Id, IO, E, fs2.Stream[IO, Unit]]

  case class MovementMessageHandlerApp(httpService: HttpService[IO],
                                       rabbitStream: fs2.Stream[IO, Unit],
                                       scheduleTable: ScheduleTable,
                                       cache: ListCache[TrainId, MovementPacket])

  def appFrom[E](redisClient: RedisClient,
                 rabbitClient: RabbitClient[E],
                 httpClient: Client[IO],
                 railwaysCodesClient: RailwaysCodesClient,
                 outboundQueue: fs2.async.mutable.Queue[IO, MovementPacket],
                 appConfig: ApplicationConfig,
                 databaseConfig: DatabaseConfig,
                 activationLookupConfig: ActivationLookupConfig)(implicit executionContext: ExecutionContext) =
    db.withTransactor(databaseConfig)() { dbTransactor =>
      for {
        cache                <- fs2.Stream.emit(MovementPacketCache(redisClient))
        scheduleTable        <- fs2.Stream.emit(ScheduleTable(dbTransactor))
        polylineTable        <- fs2.Stream.emit(PolylineTable(dbTransactor))
        movementsHttpService <- fs2.Stream.emit(MovementsHttp(cache, scheduleTable, polylineTable))
        scheduleHttpService  <- fs2.Stream.emit(ScheduleHttp(scheduleTable, polylineTable))
        activationClient     <- fs2.Stream.emit(ActivationLookupClient(activationLookupConfig.baseUri, httpClient))
        stopReference        <- fs2.Stream.emit(StopReference(railwaysCodesClient))

      } yield
        MovementMessageHandlerApp(
          Router(("/", movementsHttpService), ("/", scheduleHttpService)),
          startRabbit(rabbitClient, activationClient, outboundQueue, stopReference, cache, appConfig.movementExpiry),
          scheduleTable,
          cache
        )

    }

  private def startRabbit[E](rabbitClient: RabbitClient[E],
                             activationLookupClient: ActivationLookupClient,
                             outboundQueue: fs2.async.mutable.Queue[IO, MovementPacket],
                             stopReference: StopReference,
                             cache: ListCache[TrainId, MovementPacket],
                             cacheExpiry: Option[FiniteDuration]) =
    RequeueOps(rabbitClient)
      .requeueHandlerOf[TrainMovementMessage](
        RabbitConfig.movementQueue.name,
        MovementMessageRmqHandler(activationLookupClient, stopReference, outboundQueue, cache, cacheExpiry),
        RequeuePolicy(maximumProcessAttempts = 10, 3.minute),
        TrainMovementMessage.unmarshallFromIncomingJson
      )

}

object MovementMessageHandlerMain extends App {

  import scala.concurrent.ExecutionContext.Implicits.global
  implicit val actorSystem = ActorSystem()

  private def startServer(service: HttpService[IO], port: Int): Stream[IO, fs2.StreamApp.ExitCode] =
    BlazeBuilder[IO]
      .bindHttp(port, "0.0.0.0")
      .mountService(service)
      .serve

  val app =
    for {
      serverConfig           <- fs2.Stream.emit(ServerConfig.read)
      databaseConfig         <- fs2.Stream.emit(DatabaseConfig.read)
      activationLookupConfig <- fs2.Stream.emit(ActivationLookupConfig.read)
      rabbitConfig           <- fs2.Stream.emit(RabbitConfig.read)
      rabbitClient           <- rabbitfs2.clientFrom(rabbitConfig, RabbitConfig.declarations)
      redisClient            <- fs2.Stream.emit(RedisClient())
      httpClient             <- Http1Client.stream[IO]()
      railwayCodesClient     <- fs2.Stream.emit(RailwaysCodesClient())
      outboundMessageQueue   <- fs2.Stream.eval(fs2.async.mutable.Queue.unbounded[IO, MovementPacket])
      app <- MovementMessageHandler.appFrom(redisClient,
                                            rabbitClient,
                                            httpClient,
                                            railwayCodesClient,
                                            outboundMessageQueue,
                                            serverConfig,
                                            databaseConfig,
                                            activationLookupConfig)
      httpServices = Router(("/", app.httpService),
                            ("/", WebServer(outboundMessageQueue, serverConfig.googleMapsApiKey)))
      _ <- startServer(httpServices, serverConfig.port)
        .concurrently(app.rabbitStream)
    } yield ()

  app.compile.drain.unsafeRunSync()

}
