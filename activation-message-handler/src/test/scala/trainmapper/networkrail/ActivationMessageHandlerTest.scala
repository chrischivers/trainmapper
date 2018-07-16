package trainmapper.networkrail

import java.time.Clock

import _root_.fs2.Stream
import _root_.fs2.async.Ref
import _root_.io.circe.Json
import _root_.io.circe.parser.parse
import akka.util.ByteString
import cats.effect.IO
import com.itv.bucky.CirceSupport.{JsonPayloadMarshaller, marshallerFromEncodeJson}
import com.itv.bucky.PublishCommandBuilder.publishCommandBuilder
import com.itv.bucky.decl.DeclarationExecutor
import com.itv.bucky.ext.{fs2 => extRabbitFs2}
import com.itv.bucky.fs2.ioMonadError
import com.itv.bucky.{fs2 => rabbitFs2, _}
import org.scalatest.Matchers._
import org.scalatest.{Assertion, FlatSpec}
import trainmapper.Shared.TrainId
import trainmapper.StubRedisClient.ByteStringAndExpiry
import trainmapper.networkrail.ActivationMessageRmqHandler.TrainActivationMessage
import trainmapper.{ActivationMessageHandler, RabbitConfig, StubRedisClient}

class ActivationMessageHandlerTest extends FlatSpec {

  import scala.concurrent.ExecutionContext.Implicits.global
  import com.itv.bucky.UnmarshalResultOps._
  implicit val futureMonad = future.futureMonad

  val activationPublishingConfig: PublishCommandBuilder.Builder[Json] =
    publishCommandBuilder(marshallerFromEncodeJson[Json])
      .using(RabbitConfig.trainMovementsExchange.name)
      .using(RabbitConfig.activationRoutingKey)
      .using(MessageProperties.persistentBasic.copy(contentType = Some(ContentType("application/json"))))

  "Activation message handler" should "decode incoming activation message and persist to cache" in evaluateStream {

    val expectedTrainId = TrainId("1234567")
    val incomingMessage = sampleIncomingActivationMessage(expectedTrainId)

    for {
      redisCacheRef   <- Stream.eval(Ref[IO, Map[String, ByteStringAndExpiry]](Map.empty))
      redisClient     <- Stream.eval(IO(StubRedisClient(redisCacheRef)))
      rabbitSimulator <- Stream.eval(IO(extRabbitFs2.rabbitSimulator))
      _               <- Stream.eval(IO(DeclarationExecutor(RabbitConfig.declarations, rabbitSimulator)))
      app             <- ActivationMessageHandler.appFrom(redisClient, rabbitSimulator, cacheExpiry = None)
      _               <- Stream.eval(IO.unit).concurrently(app.rabbit) //todo is there a better way?
      _               <- Stream.eval(rabbitSimulator.publish(activationPublishingConfig.toPublishCommand(incomingMessage)))
      _               <- Stream.eval(rabbitSimulator.waitForMessagesToBeProcessed())
      cacheRef        <- Stream.eval(redisCacheRef.get)
      getFromCache    <- Stream.eval(app.cache.get(expectedTrainId))
    } yield {
      cacheRef should have size 1
      val expectedMsg = TrainActivationMessage.unmarshallFromIncomingJson
        .unmarshal(JsonPayloadMarshaller.apply(incomingMessage))
        .success
      getFromCache should ===(Some(expectedMsg))
    }
  }

  "Activation message handler" should "expire messages in cache after period has passed" in evaluateStream {

    import scala.concurrent.duration._

    val expectedTrainId = TrainId("1234567")
    val incomingMessage = sampleIncomingActivationMessage(expectedTrainId)

    val cacheExpiry = 1.second

    for {
      redisCacheRef   <- Stream.eval(Ref[IO, Map[String, ByteStringAndExpiry]](Map.empty))
      redisClient     <- Stream.eval(IO(StubRedisClient(redisCacheRef)))
      rabbitSimulator <- Stream.eval(IO(extRabbitFs2.rabbitSimulator))
      _               <- Stream.eval(IO(DeclarationExecutor(RabbitConfig.declarations, rabbitSimulator)))
      app             <- ActivationMessageHandler.appFrom(redisClient, rabbitSimulator, Some(cacheExpiry))
      _               <- Stream.eval(IO.unit).concurrently(app.rabbit) //todo is there a better way?
      _               <- Stream.eval(rabbitSimulator.publish(activationPublishingConfig.toPublishCommand(incomingMessage)))
      _               <- Stream.eval(rabbitSimulator.waitForMessagesToBeProcessed())
      cacheRef        <- Stream.eval(redisCacheRef.get)
      getFromCache1   <- Stream.eval(app.cache.get(expectedTrainId))
      _               <- Stream.eval(IO(cacheRef should have size 1))
      _               <- Stream.eval(IO.sleep(cacheExpiry.plus(100.millisecond)))
      getFromCache2   <- Stream.eval(app.cache.get(expectedTrainId))
    } yield {
      getFromCache2 should ===(None)
    }
  }

  def evaluateStream[T](f: Stream[IO, Assertion]) = f.compile.drain.unsafeRunSync()

  def sampleIncomingActivationMessage(trainId: TrainId) = {
    val str =
      s"""
             |   {
             |      "header":{
             |         "msg_type":"0001",
             |         "source_dev_id":"",
             |         "user_id":"",
             |         "original_data_source":"TSIA",
             |         "msg_queue_timestamp":"1515939674000",
             |         "source_system_id":"TRUST"
             |      },
             |      "body":{
             |         "schedule_source":"C",
             |         "train_file_address":null,
             |         "schedule_end_date":"2018-01-14",
             |         "train_id":"${trainId.value}",
             |         "tp_origin_timestamp":"2018-01-14",
             |         "creation_timestamp":"1515939673000",
             |         "tp_origin_stanox":"",
             |         "origin_dep_timestamp":"${System.currentTimeMillis()}",
             |         "train_service_code":"23456",
             |         "toc_id":"88",
             |         "d1266_record_number":"00000",
             |         "train_call_type":"AUTOMATIC",
             |         "train_uid":"123456",
             |         "train_call_mode":"NORMAL",
             |         "schedule_type":"P",
             |         "sched_origin_stanox":"ABC123",
             |         "schedule_wtt_id":"2A47M",
             |         "schedule_start_date":"2018-01-14"
             |      }
             |   }
           """.stripMargin
    parse(str).right.get
  }

}
