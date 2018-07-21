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
import trainmapper.{MovementMessageHandler, RabbitConfig, StubHttpClient, StubRedisListClient}
import trainmapper.Shared.{JourneyDetails, LatLng, MovementPacket, ScheduleTrainId, ServiceCode, StanoxCode, TrainId}
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
    val movementPacket1 = MovementPacket(expectedTrainId,
                                         scheduleTrainId,
                                         serviceCode,
                                         LatLng(0.0, 0.0),
                                         Some(stanoxCode1),
                                         actualTimestamp1,
                                         JourneyDetails("", 0L),
                                         List.empty)
    val movementPacket2 = MovementPacket(expectedTrainId,
                                         scheduleTrainId,
                                         serviceCode,
                                         LatLng(0.0, 0.0),
                                         Some(stanoxCode2),
                                         actualTimestamp2,
                                         JourneyDetails("", 0L),
                                         List.empty)

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
      app <- MovementMessageHandler.appFrom(redisClient,
                                            rabbitSimulator,
                                            activationClient,
                                            cacheExpiry = None,
                                            ActivationLookupConfig(Uri(path = "/")))
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
