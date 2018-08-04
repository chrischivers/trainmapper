package trainmapper.networkrail
import java.time.{Instant, LocalDateTime, ZoneId}

import io.circe.parser.parse
import trainmapper.Shared.{
  CRS,
  EventType,
  LatLng,
  MovementPacket,
  ScheduleTrainId,
  ServiceCode,
  StanoxCode,
  StopReferenceDetailsWithLatLng,
  TOC,
  TipLocCode,
  TrainId,
  VariationStatus
}
import trainmapper.StubActivationLookupClient.TrainActivationMessage

object TestData {

  val defaultTrainId         = TrainId("1234567")
  val defaultScheduleTrainId = ScheduleTrainId("7234AD")
  val defaultServiceCode     = ServiceCode("AAAA")
  val defaultStanoxCode      = StanoxCode("YEHFJS")
  val defaultToc             = TOC("AA")

  val defaultTimestamp = LocalDateTime.of(2018, 8, 3, 20, 0, 0).atZone(ZoneId.of("UTC")).toInstant.toEpochMilli

  val defaultActivationMessage = TrainActivationMessage(defaultScheduleTrainId,
                                                        defaultServiceCode,
                                                        defaultTrainId,
                                                        StanoxCode("ORIGINSTANOX"),
                                                        defaultTimestamp - 600000)

  val defaultEventType = EventType.Arrival

  val defaultVariationStatus = VariationStatus.OnTime

  val defaultStopReferenceDetails =
    StopReferenceDetailsWithLatLng("Description",
                                   Some(CRS("Some CRS")),
                                   Some(TipLocCode("PENZNCE")),
                                   Some(defaultStanoxCode),
                                   Some(LatLng(50.1225016380894, -5.531927740587754)))

  val defaultMovementPacket = MovementPacket(
    defaultTrainId,
    defaultScheduleTrainId,
    defaultServiceCode,
    defaultToc,
    Some(defaultStanoxCode),
    Some(defaultStopReferenceDetails),
    EventType.Arrival,
    defaultTimestamp,
    MovementPacket.timeStampToString(defaultTimestamp),
    Some(defaultTimestamp),
    Some(MovementPacket.timeStampToString(defaultTimestamp)),
    Some(defaultTimestamp),
    Some(MovementPacket.timeStampToString(defaultTimestamp)),
    Some(VariationStatus.OnTime),
    List.empty
  )

  def createIncomingMovementMessageJson(trainId: TrainId = defaultTrainId,
                                        actualTimestamp: Long = defaultTimestamp,
                                        serviceCode: ServiceCode = defaultServiceCode,
                                        stanoxCode: StanoxCode = defaultStanoxCode,
                                        eventType: EventType = defaultEventType,
                                        plannedTimestamp: Long = defaultTimestamp,
                                        plannedPassengerTimestamp: Long = defaultTimestamp,
                                        variationStatus: VariationStatus = defaultVariationStatus,
                                        toc: TOC = defaultToc) = {
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
         |         "event_type":"${eventType.string}",
         |         "gbtt_timestamp":"$plannedPassengerTimestamp",
         |         "original_loc_stanox":"",
         |         "planned_timestamp":"$plannedTimestamp",
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
         |         "variation_status":"${variationStatus.string}",
         |         "train_service_code":"${serviceCode.value}",
         |         "toc_id":"${toc.value}",
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
