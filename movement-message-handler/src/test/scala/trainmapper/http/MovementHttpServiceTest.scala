package trainmapper.http

import java.util.UUID

import cats.effect.IO
import com.itv.bucky.decl.DeclarationExecutor
import com.itv.bucky.ext.{fs2 => extRabbitFs2}
import com.itv.bucky.fs2.ioMonadError
import com.itv.bucky.future
import fs2.Stream
import fs2.async.Ref
import org.http4s.Uri
import org.http4s.circe.jsonOf
import org.http4s.client.Client
import org.scalatest.Matchers._
import org.scalatest.{Assertion, FlatSpec}
import trainmapper.ActivationLookupConfig.ActivationLookupConfig
import trainmapper.ServerConfig.ApplicationConfig
import trainmapper._
import trainmapper.Shared.{
  EventType,
  LatLng,
  MovementPacket,
  ScheduleTrainId,
  ServiceCode,
  StanoxCode,
  StopReferenceDetails,
  StopReferenceDetailsWithLatLng,
  TOC,
  TrainId,
  VariationStatus
}
import trainmapper.StubActivationLookupClient.TrainActivationMessage
import trainmapper.StubRedisListClient.ByteStringListAndExpiry
import trainmapper.db.ScheduleTable.ScheduleRecord
import trainmapper.http.MovementsHttp.MovementsHttpResponse
import trainmapper.networkrail.TestData

class MovementHttpServiceTest extends FlatSpec {

  import scala.concurrent.ExecutionContext.Implicits.global
  implicit val futureMonad   = future.futureMonad
  implicit val entityDecoder = jsonOf[IO, MovementsHttpResponse]

  "Movement message service" should "query cache by train ID returning list of train movements and schedule data" in evaluateStream {

    val expectedTrainId  = TrainId("1234567")
    val scheduleTrainId  = ScheduleTrainId("7234AD")
    val serviceCode      = ServiceCode("AAAA")
    val stanoxCode1      = StanoxCode("YEHFJS")
    val stanoxCode2      = StanoxCode("HDJDSF")
    val actualTimestamp1 = System.currentTimeMillis()
    val actualTimestamp2 = actualTimestamp1 + 120000
    val movementPacket1 = MovementPacket(
      expectedTrainId,
      scheduleTrainId,
      serviceCode,
      TOC("AA"),
      Some(stanoxCode1),
      Some(StopReferenceDetailsWithLatLng("description1", None, None, Some(stanoxCode1), Some(LatLng(0.0, 0.0)))),
      EventType.Arrival,
      actualTimestamp1,
      MovementPacket.timeStampToString(actualTimestamp1),
      Some(actualTimestamp1),
      Some(MovementPacket.timeStampToString(actualTimestamp1)),
      Some(actualTimestamp1),
      Some(MovementPacket.timeStampToString(actualTimestamp1)),
      Some(VariationStatus.OnTime)
    )
    val movementPacket2 = MovementPacket(
      expectedTrainId,
      scheduleTrainId,
      serviceCode,
      TOC("AA"),
      Some(stanoxCode2),
      Some(StopReferenceDetailsWithLatLng("description2", None, None, Some(stanoxCode2), Some(LatLng(0.0, 0.0)))),
      EventType.Departure,
      actualTimestamp2,
      MovementPacket.timeStampToString(actualTimestamp2),
      Some(actualTimestamp2 + 60000),
      Some(MovementPacket.timeStampToString(actualTimestamp2 + 60000)),
      Some(actualTimestamp2 + 60000),
      Some(MovementPacket.timeStampToString(actualTimestamp2 + 60000)),
      Some(VariationStatus.Early)
    )

    val scheduleRecord =
      TestData.defaultScheduleRecord.copy(scheduleTrainId = scheduleTrainId, serviceCode = serviceCode)

    val activationRecord = TrainActivationMessage(scheduleTrainId,
                                                  serviceCode,
                                                  expectedTrainId,
                                                  StanoxCode("ORIGINSTANOX"),
                                                  System.currentTimeMillis() - 600000)

    for {
      redisCacheRef        <- Stream.eval(Ref[IO, Map[String, ByteStringListAndExpiry]](Map.empty))
      redisClient          <- Stream.eval(IO(StubRedisListClient(redisCacheRef)))
      outboundMessageQueue <- Stream.eval(fs2.async.mutable.Queue.unbounded[IO, MovementPacket])
      rabbitSimulator      <- Stream.eval(IO(extRabbitFs2.rabbitSimulator))
      _                    <- Stream.eval(IO(DeclarationExecutor(RabbitConfig.declarations, rabbitSimulator)))
      activationClient <- Stream.eval(
        IO(Client.fromHttpService(StubActivationLookupClient(Map(expectedTrainId -> activationRecord)))))
      railwayCodesClient <- Stream.eval(
        IO(StubRailwayCodesClient(List(movementPacket1.stopReferenceDetails.get.withoutLatLng,
                                       movementPacket2.stopReferenceDetails.get.withoutLatLng))))
      app <- MovementMessageHandler.appFrom(
        redisClient,
        rabbitSimulator,
        activationClient,
        railwayCodesClient,
        outboundMessageQueue,
        ApplicationConfig(0, "", None),
        h2DatabaseConfig,
        ActivationLookupConfig(Uri(path = "/"))
      )
      _        <- Stream.eval(app.scheduleTable.safeInsertRecord(scheduleRecord))
      _        <- Stream.eval(app.cache.push(expectedTrainId, movementPacket1)(expiry = None))
      _        <- Stream.eval(app.cache.push(expectedTrainId, movementPacket2)(expiry = None))
      http     <- Stream.eval(IO(Client.fromHttpService(app.httpService)))
      response <- Stream.eval(http.expect[MovementsHttpResponse](Uri(path = s"/movements/${expectedTrainId.value}")))
    } yield {
      response.packets should ===(List(movementPacket1, movementPacket2).reverse)
      response.scheduleDetails should ===(List(scheduleRecord.toScheduleDetailsRecord(None)))
    }
  }

  def evaluateStream[T](f: Stream[IO, Assertion]) = f.compile.drain.unsafeRunSync()

  val h2DatabaseConfig =
    DatabaseConfig("org.h2.Driver",
                   s"jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
                   "",
                   "")
}
