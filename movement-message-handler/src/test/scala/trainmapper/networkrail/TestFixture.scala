package trainmapper.networkrail
import java.util.UUID

import cats.effect.IO
import cats.syntax.traverse._
import cats.instances.list._
import com.itv.bucky.CirceSupport.marshallerFromEncodeJson
import com.itv.bucky.PublishCommandBuilder.publishCommandBuilder
import com.itv.bucky.decl.DeclarationExecutor
import com.itv.bucky.ext.fs2.Fs2AmqpSimulator
import com.itv.bucky.fs2.ioMonadError
import fs2.Stream
import fs2.async.Ref
import org.http4s.Uri
import org.http4s.client.Client
import org.scalatest.Assertion
import trainmapper.ActivationLookupConfig.ActivationLookupConfig
import trainmapper.MovementMessageHandler.MovementMessageHandlerApp
import trainmapper.Shared.{EventType, MovementPacket, StopReferenceDetailsWithLatLng, TrainId, VariationStatus}
import trainmapper.StubActivationLookupClient.TrainActivationMessage
import trainmapper._
import trainmapper.StubRedisListClient.ByteStringListAndExpiry
import com.itv.bucky.ext.{fs2 => extRabbitFs2}
import com.itv.bucky._
import io.circe.Json
import trainmapper.ServerConfig.ApplicationConfig
import cats.syntax.functor._
import trainmapper.db.ScheduleTable.ScheduleRecord

import scala.concurrent.ExecutionContext.Implicits.global

trait TestFixture {

  case class TestApp(app: MovementMessageHandlerApp,
                     rabbitSimulator: Fs2AmqpSimulator,
                     redisCacheRef: Ref[IO, Map[String, ByteStringListAndExpiry]]) {
    def publishIncoming(message: Json): IO[Unit] =
      rabbitSimulator.publish(movementPublishingConfig.toPublishCommand(message)).void
    def waitForMessagesToBeProcessed: IO[Unit]                           = rabbitSimulator.waitForMessagesToBeProcessed().void
    def getFromCache(expectedTrainId: TrainId): IO[List[MovementPacket]] = app.cache.getList(expectedTrainId)
    def cacheSize                                                        = redisCacheRef.get.map(_.keySet.size)
  }

  implicit val futureMonad = future.futureMonad

  val defaultApplicationConfig = ApplicationConfig(0, "", None)
  private val h2DatabaseConfig =
    DatabaseConfig("org.h2.Driver",
                   s"jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
                   "",
                   "")

  val movementPublishingConfig: PublishCommandBuilder.Builder[Json] =
    publishCommandBuilder(marshallerFromEncodeJson[Json])
      .using(RabbitConfig.trainMovementsExchange.name)
      .using(RabbitConfig.movementRoutingKey)
      .using(MessageProperties.persistentBasic.copy(contentType = Some(ContentType("application/json"))))

  def withApp(activationRecords: Map[TrainId, TrainActivationMessage] = Map(
                TestData.defaultActivationMessage.trainId -> TestData.defaultActivationMessage),
              scheduleRecords: List[ScheduleRecord] = List(TestData.defaultScheduleRecord),
              stopReferenceDetails: List[StopReferenceDetailsWithLatLng] = List(TestData.defaultStopReferenceDetails),
              applicationConfig: ApplicationConfig = defaultApplicationConfig)(f: TestApp => IO[Assertion]) = {

    val result = for {
      redisCacheRef      <- Stream.eval(Ref[IO, Map[String, ByteStringListAndExpiry]](Map.empty))
      redisClient        <- Stream.eval(IO(StubRedisListClient(redisCacheRef)))
      rabbitSimulator    <- Stream.eval(IO(extRabbitFs2.rabbitSimulator))
      _                  <- Stream.eval(IO(DeclarationExecutor(RabbitConfig.declarations, rabbitSimulator)))
      httpClient         <- Stream.eval(IO(Client.fromHttpService(StubActivationLookupClient(respondWith = activationRecords))))
      railwayCodesClient <- Stream.eval(IO(StubRailwayCodesClient(stopReferenceDetails.map(_.withoutLatLng))))
      app <- MovementMessageHandler.appFrom(redisClient,
                                            rabbitSimulator,
                                            httpClient,
                                            railwayCodesClient,
                                            applicationConfig,
                                            h2DatabaseConfig,
                                            ActivationLookupConfig(Uri(path = "/")))
      _ <- Stream.eval(scheduleRecords.traverse[IO, Unit](rec => app.scheduleTable.safeInsertRecord(rec)))
      _ <- Stream.eval(IO.unit).concurrently(app.rabbitStream) //todo is there a better way?
      testApp = TestApp(app, rabbitSimulator, redisCacheRef)
      testResult <- Stream.eval(f(testApp).attempt)
      _          <- Stream.eval(app.scheduleTable.deleteAllRecords)

    } yield testResult.fold(err => throw err, identity)

    result.compile.drain.unsafeRunSync()

  }

}
