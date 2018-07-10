package trainmapper.networkrail

import io.circe.Json
import io.circe.parser._
import org.scalatest.FlatSpec
import org.scalatest.Matchers._
import stompa.Message
import trainmapper.Shared._
import trainmapper.TestFixture

class MovementMessageHandlerTest extends FlatSpec with TestFixture {

  "Movement message handler" should "decode stomp activation message and persist to cache" in {

    val expectedTrainId                  = TrainId("1234567")
    val expectedScheduleTrainId          = ScheduleTrainId("SJDGD73")
    val expectedServiceCode              = ServiceCode("ABC1234")
    val expectedOriginStanox             = StanoxCode("846283")
    val expectedOriginDepartureTimestamp = System.currentTimeMillis()
    val incomingMessage =
      Message(
        Map.empty,
        Json
          .arr(
            activationMessageJson(expectedTrainId,
                                  expectedScheduleTrainId,
                                  expectedServiceCode,
                                  expectedOriginStanox,
                                  expectedOriginDepartureTimestamp))
          .noSpaces
      )

    withApp() { app =>
      for {
        _                      <- app.sendIncomingMessage(incomingMessage)
        _                      <- app.runMessageHandler()
        outboundMessages       <- app.getOutboundMessages
        activationMsgFromCache <- app.redisCache.get(expectedTrainId)
      } yield {
        outboundMessages shouldBe empty
        activationMsgFromCache should ===(
          Some(
            TrainActivationMessage(expectedScheduleTrainId,
                                   expectedServiceCode,
                                   expectedTrainId,
                                   expectedOriginStanox,
                                   expectedOriginDepartureTimestamp)))

      }
    }
  }

  it should "decode stomp movement message and turn into movement packet (where activation record exists)" in {

    val expectedTrainId         = TrainId("1234567")
    val expectedScheduleTrainId = ScheduleTrainId("AAAAA")
    val expectedServiceCode     = ServiceCode("ABC1234")
    val expectedActualTimestamp = System.currentTimeMillis()
    val expectedOriginStanox    = StanoxCode("87722")
    val expectedOriginDeparture = expectedActualTimestamp - 3600000 //TODO use default values?

    val incomingMessage =
      Message(Map.empty,
              Json.arr(movementMessageJson(expectedTrainId, expectedServiceCode, expectedActualTimestamp)).noSpaces)
    val activationMessages = List(
      TrainActivationMessage(expectedScheduleTrainId,
                             expectedServiceCode,
                             expectedTrainId,
                             expectedOriginStanox,
                             expectedOriginDeparture))

    withApp(activationMessages) { app =>
      for {
        _       <- app.sendIncomingMessage(incomingMessage)
        _       <- app.runMessageHandler()
        message <- app.getNextOutboundMessage
      } yield {
        message should ===(
          MovementPacket(
            expectedTrainId,
            expectedScheduleTrainId,
            expectedServiceCode,
            LatLng(53.372653341299724, -3.0108117652815505),
            expectedActualTimestamp,
            JourneyDetails("Redhill Rail Station", expectedOriginDeparture),
            List.empty
          ))
      }
    }
  }

  it should "decode stomp movement message and NOT turn into movement packet (where activation record does not exist)" in {

    val expectedTrainId         = TrainId("1234567")
    val expectedServiceCode     = ServiceCode("ABC1234")
    val expectedActualTimestamp = System.currentTimeMillis()

    val incomingMessage =
      Message(Map.empty,
              Json.arr(movementMessageJson(expectedTrainId, expectedServiceCode, expectedActualTimestamp)).noSpaces)

    withApp() { app =>
      for {
        _        <- app.sendIncomingMessage(incomingMessage)
        _        <- app.runMessageHandler()
        messages <- app.getOutboundMessages
      } yield {
        messages shouldBe empty
      }
    }
  }

  it should "decode an array of an activation message and movement message and persist to cache" in {

    val expectedTrainId                  = TrainId("1234567")
    val expectedScheduleTrainId          = ScheduleTrainId("SJDGD73")
    val expectedServiceCode              = ServiceCode("ABC1234")
    val expectedOriginStanox             = StanoxCode("87722")
    val expectedOriginDepartureTimestamp = System.currentTimeMillis()
    val expectedActualTimestamp          = System.currentTimeMillis()

    val incomingMessage =
      Message(
        Map.empty,
        Json
          .arr(
            activationMessageJson(expectedTrainId,
                                  expectedScheduleTrainId,
                                  expectedServiceCode,
                                  expectedOriginStanox,
                                  expectedOriginDepartureTimestamp),
            movementMessageJson(expectedTrainId, expectedServiceCode, expectedActualTimestamp)
          )
          .noSpaces
      )

    withApp() { app =>
      for {
        _                      <- app.sendIncomingMessage(incomingMessage)
        _                      <- app.runMessageHandler()
        activationMsgFromCache <- app.redisCache.get(expectedTrainId)
        movementMessage        <- app.getNextOutboundMessage
      } yield {
        activationMsgFromCache should ===(
          Some(
            TrainActivationMessage(expectedScheduleTrainId,
                                   expectedServiceCode,
                                   expectedTrainId,
                                   expectedOriginStanox,
                                   expectedOriginDepartureTimestamp)))
        movementMessage should ===(
          MovementPacket(
            expectedTrainId,
            expectedScheduleTrainId,
            expectedServiceCode,
            LatLng(53.372653341299724, -3.0108117652815505),
            expectedActualTimestamp,
            JourneyDetails("Redhill Rail Station", expectedOriginDepartureTimestamp),
            List.empty
          ))

      }
    }
  }

  private def movementMessageJson(trainId: TrainId = TrainId("382Y351718"),
                                  serviceCode: ServiceCode = ServiceCode("22306003"),
                                  actualTimestamp: Long = System.currentTimeMillis(),
                                  locStanox: StanoxCode = StanoxCode("38201")) = {
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
         |         "loc_stanox":"${locStanox.value}",
         |         "auto_expected":"true",
         |         "direction_ind":"UP",
         |         "route":"0",
         |         "planned_event_type":"ARRIVAL",
         |         "next_report_stanox":"38202",
         |         "line_ind":""
         |      }
         |   }
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
       """.stripMargin
    parse(str).right.get
  }
}
