package trainmapper.networkrail

import akka.util.ByteString
import cats.effect.IO
import cats.syntax.functor._
import fs2.Scheduler
import fs2.async.Ref
import fs2.async.mutable.Queue
import org.scalatest.FlatSpec
import stompa.Message
import trainmapper.Shared._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import io.circe.parser._
import org.scalatest.Matchers._
import trainmapper.StubRedisClient
import trainmapper.cache.RedisCache

class MovementMessageHandlerTest extends FlatSpec {

  "Movement message handler" should "decode stomp movement message and turn into movement message" in {

    val expectedTrainId         = TrainId("1234567")
    val expectedServiceCode     = ServiceCode("ABC1234")
    val expectedActualTimestamp = System.currentTimeMillis()
    val incomingMessage =
      Message(Map.empty, movementMessageJson(expectedTrainId, expectedServiceCode, expectedActualTimestamp).noSpaces)

    //TODO put this into a withApp...
    (for {
      ref                    <- Ref[IO, Map[String, ByteString]](Map.empty)
      activationCache        <- IO(RedisCache(StubRedisClient(ref)))
      inboundMessageQueue    <- fs2.async.mutable.Queue.unbounded[IO, Message]
      outboundMessageQueue   <- fs2.async.mutable.Queue.unbounded[IO, MovementPacket]
      movementMessageHandler <- IO(MovementMessageHandler(activationCache))
      _                      <- inboundMessageQueue.enqueue1(incomingMessage)
      _                      <- runHandler(movementMessageHandler, inboundMessageQueue, outboundMessageQueue)
      message                <- outboundMessageQueue.dequeue1
    } yield {
      message shouldBe
        MovementPacket(expectedTrainId,
                       expectedServiceCode,
                       LatLng(53.372653341299724, -3.0108117652815505),
                       expectedActualTimestamp)
    }).unsafeRunSync()

  }

  it should "decode stomp activation message and persist to cache" in {

    val expectedTrainId                  = TrainId("1234567")
    val expectedScheduleTrainId          = ScheduleTrainId("SJDGD73")
    val expectedServiceCode              = ServiceCode("ABC1234")
    val expectedOriginStanox             = StanoxCode("846283")
    val expectedOriginDepartureTimestamp = System.currentTimeMillis()
    val incomingMessage =
      Message(
        Map.empty,
        activationMessageJson(expectedTrainId,
                              expectedScheduleTrainId,
                              expectedServiceCode,
                              expectedOriginStanox,
                              expectedOriginDepartureTimestamp).noSpaces
      )

    (for {
      ref                    <- Ref[IO, Map[String, ByteString]](Map.empty)
      scheduler              <- Scheduler.allocate[IO](1).map(_._1)
      activationCache        <- IO(RedisCache(StubRedisClient(ref)))
      inboundMessageQueue    <- fs2.async.mutable.Queue.unbounded[IO, Message]
      outboundMessageQueue   <- fs2.async.mutable.Queue.unbounded[IO, MovementPacket]
      movementMessageHandler <- IO(MovementMessageHandler(activationCache))
      _                      <- inboundMessageQueue.enqueue1(incomingMessage)
      _                      <- runHandler(movementMessageHandler, inboundMessageQueue, outboundMessageQueue)
      maybeMessage           <- outboundMessageQueue.timedDequeue1(1.second, scheduler)
    } yield {
      maybeMessage shouldBe None
      activationCache.get(expectedTrainId).unsafeRunSync() shouldBe Some(
        TrainActivationMessage(expectedScheduleTrainId,
                               expectedServiceCode,
                               expectedTrainId,
                               expectedOriginStanox,
                               expectedOriginDepartureTimestamp))

    }).unsafeRunSync()

  }

  private def runHandler(movementMessageHandler: MovementMessageHandler,
                         inboundQueue: Queue[IO, Message],
                         outboundQueue: Queue[IO, MovementPacket]) =
    IO(
      movementMessageHandler
        .handleIncomingMessages(inboundQueue, outboundQueue)
        .compile
        .drain
        .unsafeRunTimed(1.second)).void

  def movementMessageJson(trainId: TrainId = TrainId("382Y351718"),
                          serviceCode: ServiceCode = ServiceCode("22306003"),
                          actualTimestamp: Long = System.currentTimeMillis(),
                          locStanox: StanoxCode = StanoxCode("38201")) = {
    val str =
      s"""
         |[
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
         |         "loc_stanox":"${locStanox.value}",
         |         "auto_expected":"true",
         |         "direction_ind":"UP",
         |         "route":"0",
         |         "planned_event_type":"ARRIVAL",
         |         "next_report_stanox":"38202",
         |         "line_ind":""
         |      }
         |   }
         | ]
    """.stripMargin
    parse(str).right.get
  }

  def activationMessageJson(trainId: TrainId = TrainId("382Y351718"),
                            scheduleTrainId: ScheduleTrainId = ScheduleTrainId("73642"),
                            serviceCode: ServiceCode = ServiceCode("22306003"),
                            originStanox: StanoxCode = StanoxCode("38201"),
                            originDepartureTimestamp: Long = System.currentTimeMillis()) = {
    val str =
      s"""
         |[
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
         |         "origin_dep_timestamp":"$originDepartureTimestamp",
         |         "train_service_code":"${serviceCode.value}",
         |         "toc_id":"88",
         |         "d1266_record_number":"00000",
         |         "train_call_type":"AUTOMATIC",
         |         "train_uid":"${scheduleTrainId.value}",
         |         "train_call_mode":"NORMAL",
         |         "schedule_type":"P",
         |         "sched_origin_stanox":"${originStanox.value}",
         |         "schedule_wtt_id":"2A47M",
         |         "schedule_start_date":"2018-01-14"
         |      }
         |   }
         |]
       """.stripMargin
    parse(str).right.get
  }
}
