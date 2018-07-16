package trainmapper.http

import akka.util.ByteString
import cats.effect.IO
import com.itv.bucky.decl.DeclarationExecutor
import fs2.Stream
import fs2.async.Ref
import org.scalatest.FlatSpec
import trainmapper._
import com.itv.bucky.ext.{fs2 => extRabbitFs2}
import com.itv.bucky.future
import com.itv.bucky.fs2.ioMonadError
import org.http4s.Uri
import org.http4s.circe.jsonOf
import org.http4s.client.Client
import trainmapper.Shared.{ScheduleTrainId, ServiceCode, StanoxCode, TrainId}
import trainmapper.networkrail.ActivationMessageRmqHandler.TrainActivationMessage
import trainmapper.{ActivationMessageHandler, RabbitConfig, StubRedisClient}
import org.scalatest.Matchers._

class HttpServiceTest extends FlatSpec {

  import scala.concurrent.ExecutionContext.Implicits.global
  implicit val futureMonad   = future.futureMonad
  implicit val entityDecoder = jsonOf[IO, TrainActivationMessage]

  "Activation message service" should "query cache by train ID returning activation message" in evaluateStream {

    val expectedTrainId = TrainId("1234567")
    val expectedActivationMessage = TrainActivationMessage(ScheduleTrainId("ABC123"),
                                                           ServiceCode("XYZ934"),
                                                           expectedTrainId,
                                                           StanoxCode("2345"),
                                                           System.currentTimeMillis())

    //TODO extract as withApp
    for {
      redisCacheRef   <- Stream.eval(Ref[IO, Map[String, ByteString]](Map.empty))
      redisClient     <- Stream.eval(IO(StubRedisClient(redisCacheRef)))
      rabbitSimulator <- Stream.eval(IO(extRabbitFs2.rabbitSimulator))
      _               <- Stream.eval(IO(DeclarationExecutor(RabbitConfig.declarations, rabbitSimulator)))
      app             <- ActivationMessageHandler.appFrom(redisClient, rabbitSimulator)
      _               <- Stream.eval(IO.unit).concurrently(app.rabbit)
      _               <- Stream.eval(app.cache.put(expectedTrainId, expectedActivationMessage)(expiry = None))
      client          <- Stream.eval(IO(Client.fromHttpService(app.httpService)))
      response <- Stream.eval(
        client.expect[TrainActivationMessage](Uri(path = "/").withQueryParam("trainId", expectedTrainId.value)))
    } yield {
      response should ===(expectedActivationMessage)
    }
  }
}
