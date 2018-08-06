package trainmapper
import java.util.UUID

import cats.effect.IO
import fs2.Stream
import org.http4s.Uri
import org.http4s.client.Client
import org.scalatest.FlatSpec
import trainmapper.clients.DirectionsApi
import trainmapper.db.{PolylineTable, ScheduleTable}
import trainmapper.networkrail.TestData
import trainmapper.populator.ScheduleTablePopulator
import trainmapper.reference.StopReference
import org.scalatest.Matchers._
import trainmapper.Shared.{ScheduleTrainId, TipLocCode}

class SchedulePopulatorTest extends FlatSpec {

  private val h2DatabaseConfig =
    DatabaseConfig("org.h2.Driver",
                   s"jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
                   "",
                   "")

  "Schedule populator" should "insert records into database" in {

    val scheduleTrainId = ScheduleTrainId("W60984")
    val stopReferenceDetails = List(
      TestData.defaultStopReferenceDetails.copy(tiploc = Some(TipLocCode("GTWK"))),
      TestData.defaultStopReferenceDetails.copy(tiploc = Some(TipLocCode("HORLEY"))),
      TestData.defaultStopReferenceDetails.copy(tiploc = Some(TipLocCode("SALFDS"))),
      TestData.defaultStopReferenceDetails.copy(tiploc = Some(TipLocCode("EARLSWD"))),
      TestData.defaultStopReferenceDetails.copy(tiploc = Some(TipLocCode("REDHILL"))),
      TestData.defaultStopReferenceDetails.copy(tiploc = Some(TipLocCode("MERSTHM"))),
      TestData.defaultStopReferenceDetails.copy(tiploc = Some(TipLocCode("COLSDNS"))),
      TestData.defaultStopReferenceDetails.copy(tiploc = Some(TipLocCode("PURLEY"))),
      TestData.defaultStopReferenceDetails.copy(tiploc = Some(TipLocCode("ECROYDN"))),
      TestData.defaultStopReferenceDetails.copy(tiploc = Some(TipLocCode("CLPHMJC"))),
      TestData.defaultStopReferenceDetails.copy(tiploc = Some(TipLocCode("VICTRIC")))
    )

    val networkRailConfig   = NetworkRailConfig("", "", Uri.unsafeFromString("/"))
    val directionsApiConfig = DirectionsApiConfig(Uri.unsafeFromString("/"), "")

    val stream = db.withTransactor(h2DatabaseConfig)() { dbTransactor =>
      for {
        httpClient         <- Stream.eval(IO(Client.fromHttpService(StubScheduleDownloadClient())))
        scheduleTable      <- Stream.eval(IO(ScheduleTable(dbTransactor)))
        polylineTable      <- Stream.eval(IO(PolylineTable(dbTransactor)))
        railwayCodesClient <- Stream.eval(IO(StubRailwayCodesClient(stopReferenceDetails.map(_.withoutLatLng))))
        stopReference      <- fs2.Stream.emit(StopReference(railwayCodesClient))
        directionsApi <- fs2.Stream.eval(
          IO(DirectionsApi(directionsApiConfig, Client.fromHttpService(StubDirectionsApiClient()))))
        scheduleTablePopulator = ScheduleTablePopulator(httpClient,
                                                        scheduleTable,
                                                        polylineTable,
                                                        directionsApi,
                                                        stopReference,
                                                        networkRailConfig)
        _     <- Stream.eval(scheduleTablePopulator.populateTable(limitTo = Some(List(scheduleTrainId))))
        route <- Stream.eval(scheduleTable.allRecords.map(_.filter(_.scheduleTrainId == scheduleTrainId)))
      } yield {
        route should have size 11
        //todo assert more here?
      }
    }
    stream.compile.drain.unsafeRunSync()
  }
}

/*
{"JsonScheduleV1":{"CIF_bank_holiday_running":null,"CIF_stp_indicator":"P","CIF_train_uid":"W60984","applicable_timetable":"Y","atoc_code":"SN","new_schedule_segment":{"traction_class":"","uic_code":""},"
schedule_days_runs":"1111100","schedule_end_date":"2018-12-07","schedule_segment":{"signalling_id":"1T89","CIF_train_category":"XX","CIF_headcode":"1003","CIF_course_indicator":1,"CIF_train_service_code":
"24745000","CIF_business_sector":"??","CIF_power_type":"EMU","CIF_timing_load":null,"CIF_speed":"100","CIF_operating_characteristics":"G","CIF_train_class":"B","CIF_sleepers":null,"CIF_reservations":null,
"CIF_connection_indicator":null,"CIF_catering_code":null,"CIF_service_branding":"","schedule_location":[{"location_type":"LO","record_identity":"LO","tiploc_code":"GTWK","tiploc_instance":null,"departure"
:"0720","public_departure":"0720","platform":"1","line":"SL","engineering_allowance":null,"pathing_allowance":null,"performance_allowance":null},{"location_type":"LI","record_identity":"LI","tiploc_code":
"HORLEY","tiploc_instance":null,"arrival":"0722H","departure":"0723H","pass":null,"public_arrival":"0723","public_departure":"0723","platform":null,"line":null,"path":null,"engineering_allowance":null,"pa
thing_allowance":null,"performance_allowance":null},{"location_type":"LI","record_identity":"LI","tiploc_code":"SALFDS","tiploc_instance":null,"arrival":"0727","departure":"0727H","pass":null,"public_arri
val":"0727","public_departure":"0727","platform":null,"line":null,"path":null,"engineering_allowance":null,"pathing_allowance":null,"performance_allowance":null},{"location_type":"LI","record_identity":"L
I","tiploc_code":"EARLSWD","tiploc_instance":null,"arrival":"0730H","departure":"0731H","pass":null,"public_arrival":"0731","public_departure":"0731","platform":null,"line":null,"path":null,"engineering_a
llowance":null,"pathing_allowance":null,"performance_allowance":null},{"location_type":"LI","record_identity":"LI","tiploc_code":"REDHILL","tiploc_instance":null,"arrival":"0735","departure":"0745","pass"
:null,"public_arrival":"0735","public_departure":"0745","platform":"0","line":null,"path":null,"engineering_allowance":null,"pathing_allowance":null,"performance_allowance":null},{"location_type":"LI","re
cord_identity":"LI","tiploc_code":"MERSTHM","tiploc_instance":null,"arrival":"0748H","departure":"0749","pass":null,"public_arrival":"0749","public_departure":"0749","platform":null,"line":null,"path":nul
l,"engineering_allowance":null,"pathing_allowance":null,"performance_allowance":null},{"location_type":"LI","record_identity":"LI","tiploc_code":"COLSDNS","tiploc_instance":null,"arrival":"0753H","departu
re":"0754H","pass":null,"public_arrival":"0754","public_departure":"0754","platform":null,"line":null,"path":null,"engineering_allowance":null,"pathing_allowance":null,"performance_allowance":null},{"loca
tion_type":"LI","record_identity":"LI","tiploc_code":"SNSTJN","tiploc_instance":null,"arrival":null,"departure":null,"pass":"0756","public_arrival":null,"public_departure":null,"platform":null,"line":"SL"
,"path":null,"engineering_allowance":null,"pathing_allowance":null,"performance_allowance":null},{"location_type":"LI","record_identity":"LI","tiploc_code":"PURLEY","tiploc_instance":null,"arrival":"0757H
","departure":"0758H","pass":null,"public_arrival":"0758","public_departure":"0758","platform":"3","line":null,"path":null,"engineering_allowance":null,"pathing_allowance":null,"performance_allowance":nul
l},{"location_type":"LI","record_identity":"LI","tiploc_code":"SCROYDN","tiploc_instance":null,"arrival":null,"departure":null,"pass":"0801H","public_arrival":null,"public_departure":null,"platform":"3","
line":null,"path":null,"engineering_allowance":null,"pathing_allowance":"H","performance_allowance":null},{"location_type":"LI","record_identity":"LI","tiploc_code":"ECROYDN","tiploc_instance":null,"arriv
al":"0803H","departure":"0804H","pass":null,"public_arrival":"0804","public_departure":"0804","platform":"4","line":null,"path":null,"engineering_allowance":null,"pathing_allowance":null,"performance_allo
wance":null},{"location_type":"LI","record_identity":"LI","tiploc_code":"WNDMLBJ","tiploc_instance":null,"arrival":null,"departure":null,"pass":"0805H","public_arrival":null,"public_departure":null,"platf
orm":null,"line":"SL","path":null,"engineering_allowance":null,"pathing_allowance":null,"performance_allowance":null},{"location_type":"LI","record_identity":"LI","tiploc_code":"SELHRST","tiploc_instance"
:null,"arrival":null,"departure":null,"pass":"0806H","public_arrival":null,"public_departure":null,"platform":"4","line":"FL","path":null,"engineering_allowance":null,"pathing_allowance":null,"performance
_allowance":null},{"location_type":"LI","record_identity":"LI","tiploc_code":"STRENJN","tiploc_instance":null,"arrival":null,"departure":null,"pass":"0810","public_arrival":null,"public_departure":null,"p
latform":null,"line":null,"path":null,"engineering_allowance":null,"pathing_allowance":null,"performance_allowance":null},{"location_type":"LI","record_identity":"LI","tiploc_code":"BALHAM","tiploc_instan
ce":null,"arrival":null,"departure":null,"pass":"0811","public_arrival":null,"public_departure":null,"platform":"4","line":null,"path":null,"engineering_allowance":null,"pathing_allowance":null,"performan
ce_allowance":null},{"location_type":"LI","record_identity":"LI","tiploc_code":"CLPHMJC","tiploc_instance":null,"arrival":"0814","departure":"0815","pass":null,"public_arrival":"0814","public_departure":"
0815","platform":"12","line":"FL","path":null,"engineering_allowance":null,"pathing_allowance":null,"performance_allowance":null},{"location_type":"LI","record_identity":"LI","tiploc_code":"BATRSPJ","tipl
oc_instance":null,"arrival":null,"departure":null,"pass":"0818","public_arrival":null,"public_departure":null,"platform":null,"line":null,"path":null,"engineering_allowance":null,"pathing_allowance":null,
"performance_allowance":"1"},{"location_type":"LT","record_identity":"LT","tiploc_code":"VICTRIC","tiploc_instance":null,"arrival":"0822","public_arrival":"0822","platform":"17","path":null}]},"schedule_s
tart_date":"2018-05-21","train_status":"P","transaction_type":"Create"}}
 */
