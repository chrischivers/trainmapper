package trainmapper.http

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
import trainmapper.StubHttpClient.TrainActivationMessage
import trainmapper.StubRedisListClient.ByteStringListAndExpiry

class MovementHttpServiceTest extends FlatSpec {

  import scala.concurrent.ExecutionContext.Implicits.global
  implicit val futureMonad   = future.futureMonad
  implicit val entityDecoder = jsonOf[IO, List[MovementPacket]]

  "Movement message service" should "query cache by train ID returning list of train movements" in evaluateStream {

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
      Some(VariationStatus.OnTime),
      List.empty
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
      Some(VariationStatus.Early),
      List.empty
    )

    val activationRecord = TrainActivationMessage(scheduleTrainId,
                                                  serviceCode,
                                                  expectedTrainId,
                                                  StanoxCode("ORIGINSTANOX"),
                                                  System.currentTimeMillis() - 600000)

    for {
      redisCacheRef    <- Stream.eval(Ref[IO, Map[String, ByteStringListAndExpiry]](Map.empty))
      redisClient      <- Stream.eval(IO(StubRedisListClient(redisCacheRef)))
      rabbitSimulator  <- Stream.eval(IO(extRabbitFs2.rabbitSimulator))
      _                <- Stream.eval(IO(DeclarationExecutor(RabbitConfig.declarations, rabbitSimulator)))
      activationClient <- Stream.eval(IO(Client.fromHttpService(StubHttpClient(Some(activationRecord)))))
      railwayCodesClient <- Stream.eval(
        IO(StubRailwayCodesClient(List(movementPacket1.stopReferenceDetails.get.withoutLatLng,
                                       movementPacket2.stopReferenceDetails.get.withoutLatLng))))
      app <- MovementMessageHandler.appFrom(
        redisClient,
        rabbitSimulator,
        activationClient,
        railwayCodesClient,
        ApplicationConfig(0, "", None, getClass.getResource("/RailReferences.csv").getFile),
        ActivationLookupConfig(Uri(path = "/"))
      )
      _        <- Stream.eval(app.cache.push(expectedTrainId, movementPacket1)(expiry = None))
      _        <- Stream.eval(app.cache.push(expectedTrainId, movementPacket2)(expiry = None))
      http     <- Stream.eval(IO(Client.fromHttpService(app.httpService)))
      response <- Stream.eval(http.expect[List[MovementPacket]](Uri(path = s"/movements/${expectedTrainId.value}")))
    } yield {
      response should ===(List(movementPacket1, movementPacket2).reverse)
    }
  }

  def evaluateStream[T](f: Stream[IO, Assertion]) = f.compile.drain.unsafeRunSync()
}
