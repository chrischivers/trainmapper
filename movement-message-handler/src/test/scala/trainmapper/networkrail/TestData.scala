package trainmapper.networkrail
import java.time.{Instant, LocalDateTime, ZoneId, ZoneOffset}

import io.circe.parser.parse
import trainmapper.Shared.{
  CRS,
  DaysRun,
  EventType,
  LatLng,
  LocationType,
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
import trainmapper.db.ScheduleTable.ScheduleRecord

object TestData {

  val defaultTrainId         = TrainId("1234567")
  val defaultScheduleTrainId = ScheduleTrainId("7234AD")
  val defaultServiceCode     = ServiceCode("AAAA")
  val defaultStanoxCode      = StanoxCode("YEHFJS")
  val defaultToc             = TOC("AA")
  val defaultTiplocCode      = TipLocCode("PENZNCE")

  val defaultMovementDateTime  = LocalDateTime.of(2018, 8, 3, 20, 0, 0)
  val defaultMovementTimestamp = defaultMovementDateTime.toInstant(ZoneOffset.UTC).toEpochMilli

  val defaultActivationMessage = TrainActivationMessage(defaultScheduleTrainId,
                                                        defaultServiceCode,
                                                        defaultTrainId,
                                                        defaultStanoxCode,
                                                        defaultMovementTimestamp)

  val defaultEventType = EventType.Departure

  val defaultVariationStatus = VariationStatus.OnTime

  val defaultStopReferenceDetails =
    StopReferenceDetailsWithLatLng("Description",
                                   Some(CRS("Some CRS")),
                                   Some(defaultTiplocCode),
                                   Some(defaultStanoxCode),
                                   Some(LatLng(50.1225016380894, -5.531927740587754)))

  val defaultScheduleRecord = ScheduleRecord(
    None,
    defaultScheduleTrainId,
    1,
    defaultServiceCode,
    defaultTiplocCode,
    LocationType.OriginatingLocation,
    None,
    Some(defaultMovementDateTime.toLocalTime),
    DaysRun("1111100"),
    defaultMovementDateTime.toLocalDate,
    defaultMovementDateTime.toLocalDate.plusDays(1),
    Some(1)
  )

  val defaultMovementPacket = MovementPacket(
    defaultTrainId,
    defaultScheduleTrainId,
    defaultServiceCode,
    defaultToc,
    Some(defaultStanoxCode),
    Some(defaultStopReferenceDetails),
    defaultEventType,
    defaultMovementTimestamp,
    MovementPacket.timeStampToString(defaultMovementTimestamp),
    Some(defaultMovementTimestamp),
    Some(MovementPacket.timeStampToString(defaultMovementTimestamp)),
    Some(defaultMovementTimestamp),
    Some(MovementPacket.timeStampToString(defaultMovementTimestamp)),
    Some(VariationStatus.OnTime)
  )

  def createIncomingMovementMessageJson(trainId: TrainId = defaultTrainId,
                                        actualTimestamp: Long = defaultMovementTimestamp,
                                        serviceCode: ServiceCode = defaultServiceCode,
                                        stanoxCode: StanoxCode = defaultStanoxCode,
                                        eventType: EventType = defaultEventType,
                                        plannedTimestamp: Long = defaultMovementTimestamp,
                                        plannedPassengerTimestamp: Long = defaultMovementTimestamp,
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
