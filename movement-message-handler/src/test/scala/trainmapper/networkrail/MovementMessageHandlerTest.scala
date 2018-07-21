package trainmapper.networkrail

import _root_.fs2.Stream
import _root_.fs2.async.Ref
import _root_.io.circe.Json
import _root_.io.circe.parser.parse
import cats.effect.IO
import com.itv.bucky.CirceSupport.marshallerFromEncodeJson
import com.itv.bucky.PublishCommandBuilder.publishCommandBuilder
import com.itv.bucky._
import com.itv.bucky.decl.DeclarationExecutor
import com.itv.bucky.ext.{fs2 => extRabbitFs2}
import com.itv.bucky.fs2.ioMonadError
import org.http4s.Uri
import org.http4s.client.Client
import org.scalatest.Matchers._
import org.scalatest.{Assertion, FlatSpec}
import trainmapper.ActivationLookupConfig.ActivationLookupConfig
import trainmapper.Shared.{JourneyDetails, LatLng, MovementPacket, ScheduleTrainId, ServiceCode, StanoxCode, TrainId}
import trainmapper.StubHttpClient.TrainActivationMessage
import trainmapper.StubRedisListClient.ByteStringListAndExpiry
import trainmapper.{MovementMessageHandler, RabbitConfig, StubHttpClient, StubRedisListClient}

class MovementMessageHandlerTest extends FlatSpec {

  import scala.concurrent.ExecutionContext.Implicits.global
  implicit val futureMonad = future.futureMonad

  val movementPublishingConfig: PublishCommandBuilder.Builder[Json] =
    publishCommandBuilder(marshallerFromEncodeJson[Json])
      .using(RabbitConfig.trainMovementsExchange.name)
      .using(RabbitConfig.movementRoutingKey)
      .using(MessageProperties.persistentBasic.copy(contentType = Some(ContentType("application/json"))))

  "Movement message handler" should "decode incoming movement message and push to list cache" in evaluateStream {

    val expectedTrainId = TrainId("1234567")
    val scheduleTrainId = ScheduleTrainId("7234AD")
    val serviceCode     = ServiceCode("AAAA")
    val stanoxCode      = StanoxCode("YEHFJS")
    val activationRecord = TrainActivationMessage(scheduleTrainId,
                                                  serviceCode,
                                                  expectedTrainId,
                                                  StanoxCode("ORIGINSTANOX"),
                                                  System.currentTimeMillis() - 600000)
    val actualTimestamp = System.currentTimeMillis()
    val incomingMessage = sampleIncomingMovementMessage(expectedTrainId, actualTimestamp, serviceCode, stanoxCode)

    for {
      redisCacheRef   <- Stream.eval(Ref[IO, Map[String, ByteStringListAndExpiry]](Map.empty))
      redisClient     <- Stream.eval(IO(StubRedisListClient(redisCacheRef)))
      rabbitSimulator <- Stream.eval(IO(extRabbitFs2.rabbitSimulator))
      _               <- Stream.eval(IO(DeclarationExecutor(RabbitConfig.declarations, rabbitSimulator)))
      httpClient      <- Stream.eval(IO(Client.fromHttpService(StubHttpClient(respondWith = Some(activationRecord)))))
      app <- MovementMessageHandler.appFrom(redisClient,
                                            rabbitSimulator,
                                            httpClient,
                                            cacheExpiry = None,
                                            ActivationLookupConfig(Uri(path = "/")))
      _            <- Stream.eval(IO.unit).concurrently(app.rabbit) //todo is there a better way?
      _            <- Stream.eval(rabbitSimulator.publish(movementPublishingConfig.toPublishCommand(incomingMessage)))
      _            <- Stream.eval(rabbitSimulator.waitForMessagesToBeProcessed())
      cacheRef     <- Stream.eval(redisCacheRef.get)
      getFromCache <- Stream.eval(app.cache.getList(expectedTrainId))
    } yield {
      cacheRef should have size 1
      val expectedRecord = MovementPacket(expectedTrainId,
                                          scheduleTrainId,
                                          serviceCode,
                                          LatLng(0.0, 0.0),
                                          Some(stanoxCode),
                                          actualTimestamp,
                                          JourneyDetails("", 0L),
                                          List.empty)
      getFromCache should ===(List(expectedRecord))
    }
  }

  it should "decode multiple incoming movement with the same train id messages and push to list" in evaluateStream {

    val expectedTrainId = TrainId("1234567")
    val scheduleTrainId = ScheduleTrainId("7234AD")
    val serviceCode     = ServiceCode("AAAA")

    val activationRecord = TrainActivationMessage(scheduleTrainId,
                                                  serviceCode,
                                                  expectedTrainId,
                                                  StanoxCode("POSDC"),
                                                  System.currentTimeMillis() - 600000)
    val actualTimestamp1 = System.currentTimeMillis()
    val actualTimestamp2 = actualTimestamp1 + 120000
    val stanoxCode1      = StanoxCode("AYENDD")
    val stanoxCode2      = StanoxCode("SHDJS2")
    val incomingMessage1 = sampleIncomingMovementMessage(expectedTrainId, actualTimestamp1, serviceCode, stanoxCode1)
    val incomingMessage2 = sampleIncomingMovementMessage(expectedTrainId, actualTimestamp2, serviceCode, stanoxCode2)

    for {
      redisCacheRef   <- Stream.eval(Ref[IO, Map[String, ByteStringListAndExpiry]](Map.empty))
      redisClient     <- Stream.eval(IO(StubRedisListClient(redisCacheRef)))
      rabbitSimulator <- Stream.eval(IO(extRabbitFs2.rabbitSimulator))
      _               <- Stream.eval(IO(DeclarationExecutor(RabbitConfig.declarations, rabbitSimulator)))
      httpClient      <- Stream.eval(IO(Client.fromHttpService(StubHttpClient(respondWith = Some(activationRecord)))))
      app <- MovementMessageHandler.appFrom(redisClient,
                                            rabbitSimulator,
                                            httpClient,
                                            cacheExpiry = None,
                                            ActivationLookupConfig(Uri(path = "/")))
      _            <- Stream.eval(IO.unit).concurrently(app.rabbit) //todo is there a better way?
      _            <- Stream.eval(rabbitSimulator.publish(movementPublishingConfig.toPublishCommand(incomingMessage1)))
      _            <- Stream.eval(rabbitSimulator.publish(movementPublishingConfig.toPublishCommand(incomingMessage2)))
      _            <- Stream.eval(rabbitSimulator.waitForMessagesToBeProcessed())
      cacheRef     <- Stream.eval(redisCacheRef.get)
      getFromCache <- Stream.eval(app.cache.getList(expectedTrainId))
    } yield {
      cacheRef should have size 1
      cacheRef(expectedTrainId.value)._1 should have size 2
      val expectedRecord1 = MovementPacket(expectedTrainId,
                                           scheduleTrainId,
                                           serviceCode,
                                           LatLng(0.0, 0.0),
                                           Some(stanoxCode1),
                                           actualTimestamp1,
                                           JourneyDetails("", 0L),
                                           List.empty)
      val expectedRecord2 = MovementPacket(expectedTrainId,
                                           scheduleTrainId,
                                           serviceCode,
                                           LatLng(0.0, 0.0),
                                           Some(stanoxCode2),
                                           actualTimestamp2,
                                           JourneyDetails("", 0L),
                                           List.empty)
      getFromCache should ===(List(expectedRecord1, expectedRecord2).reverse)
    }
  }

  it should "expire movement message list after a set period of time" in evaluateStream {
    import scala.concurrent.duration._
    val cacheExpiry = 1.second

    val expectedTrainId = TrainId("1234567")
    val scheduleTrainId = ScheduleTrainId("7234AD")
    val serviceCode     = ServiceCode("AAAA")
    val stanoxCode      = StanoxCode("YEHFJS")
    val activationRecord = TrainActivationMessage(scheduleTrainId,
                                                  serviceCode,
                                                  expectedTrainId,
                                                  StanoxCode("ORIGINSTANOX"),
                                                  System.currentTimeMillis() - 600000)
    val actualTimestamp = System.currentTimeMillis()
    val incomingMessage = sampleIncomingMovementMessage(expectedTrainId, actualTimestamp, serviceCode, stanoxCode)

    for {
      redisCacheRef   <- Stream.eval(Ref[IO, Map[String, ByteStringListAndExpiry]](Map.empty))
      redisClient     <- Stream.eval(IO(StubRedisListClient(redisCacheRef)))
      rabbitSimulator <- Stream.eval(IO(extRabbitFs2.rabbitSimulator))
      _               <- Stream.eval(IO(DeclarationExecutor(RabbitConfig.declarations, rabbitSimulator)))
      httpClient      <- Stream.eval(IO(Client.fromHttpService(StubHttpClient(respondWith = Some(activationRecord)))))
      app <- MovementMessageHandler.appFrom(redisClient,
                                            rabbitSimulator,
                                            httpClient,
                                            cacheExpiry = Some(cacheExpiry),
                                            ActivationLookupConfig(Uri(path = "/")))
      _             <- Stream.eval(IO.unit).concurrently(app.rabbit) //todo is there a better way?
      _             <- Stream.eval(rabbitSimulator.publish(movementPublishingConfig.toPublishCommand(incomingMessage)))
      _             <- Stream.eval(rabbitSimulator.waitForMessagesToBeProcessed())
      getFromCache1 <- Stream.eval(app.cache.getList(expectedTrainId))
      _             <- Stream.eval(IO(getFromCache1 should have size 1))
      _             <- Stream.eval(IO.sleep(cacheExpiry.plus(10.millisecond)))
      getFromCache2 <- Stream.eval(app.cache.getList(expectedTrainId))

    } yield {
      getFromCache2 shouldBe empty
    }
  }

  def evaluateStream[T](f: Stream[IO, Assertion]) = f.compile.drain.unsafeRunSync()

  def sampleIncomingMovementMessage(trainId: TrainId,
                                    actualTimestamp: Long,
                                    serviceCode: ServiceCode,
                                    stanoxCode: StanoxCode) = {
    val str =
      s"""
             |   {
             |      "header":{
             |         "msg_type":"0003",
             |         "source_dev_id":"",
             |         "user_id":"",
             |         "original_data_source":"SMART",
             |         "msg_queue_timestamp":"1529354392000",
             |         "source_system_id":"TRUST"
             |      },
             |      "body":{
             |         "event_type":"ARRIVAL",
             |         "gbtt_timestamp":"1529358120000",
             |         "original_loc_stanox":"",
             |         "planned_timestamp":"1529358090000",
             |         "timetable_variation":"1",
             |         "original_loc_timestamp":"",
             |         "current_train_id":"",
             |         "delay_monitoring_point":"true",
             |         "next_report_run_time":"2",
             |         "reporting_stanox":"38201",
             |         "actual_timestamp":"$actualTimestamp",
             |         "correction_ind":"false",
             |         "event_source":"AUTOMATIC",
             |         "train_file_address":null,
             |         "platform":" 1",
             |         "division_code":"64",
             |         "train_terminated":"false",
             |         "train_id":"${trainId.value}",
             |         "offroute_ind":"false",
             |         "variation_status":"EARLY",
             |         "train_service_code":"${serviceCode.value}",
             |         "toc_id":"64",
             |         "loc_stanox":"${stanoxCode.value}",
             |         "auto_expected":"true",
             |         "direction_ind":"UP",
             |         "route":"0",
             |         "planned_event_type":"ARRIVAL",
             |         "next_report_stanox":"38202",
             |         "line_ind":""
             |      }
             |   }
             |   """.stripMargin
    parse(str).right.get
  }

}
