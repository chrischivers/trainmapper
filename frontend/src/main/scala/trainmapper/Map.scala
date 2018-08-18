package trainmapper

import google.maps.{IconSequence, LatLng, Polyline, PolylineOptions, Symbol}
import org.scalajs.dom._
import org.scalajs.dom
import io.circe.parser._
import trainmapper.Shared.{MovementPacket, TrainId}

import scala.scalajs.js.Dynamic.literal._
import scala.collection.mutable
import scala.scalajs.js
import scala.scalajs.js.annotation.JSExportTopLevel
import scala.scalajs.js.timers._

object Map {

//  val activeTrains = mutable.Map[TrainId, google.maps.Marker]()

  val activePolylines = mutable.Map[TrainId, google.maps.Polyline]()

  @JSExportTopLevel("initialize")
  def initialize() = {
    val url    = "ws://" + document.location.host + "/ws"
    val socket = new dom.WebSocket(url)

    val opts = google.maps.MapOptions(center = new google.maps.LatLng(51.201203, -1.724370),
                                      zoom = 8,
                                      panControl = false,
                                      streetViewControl = false,
                                      mapTypeControl = false)

    val gmap = new google.maps.Map(document.getElementById("map-canvas"), opts)

    val lineSymbol =
      js.Dynamic
        .literal("path" -> google.maps.SymbolPath.FORWARD_CLOSED_ARROW, "scale" -> 3, "strokeColor" -> "#393")
        .asInstanceOf[Symbol]

    val iconSequence =
      js.Dynamic.literal("icon" -> lineSymbol, "offset" -> "100%", "repeat" -> "0").asInstanceOf[IconSequence]

    def onMessage(msg: MessageEvent): Unit =
      parse(msg.data.toString).flatMap(_.as[MovementPacket]) match {
        case Left(err) => console.error(s"Error decoding incoming event message $msg. Error [$err]")
        case Right(packet) =>
          console.log(packet.toString)
          for {
            stopReferenceDetails <- packet.stopReferenceDetails
            latLng               <- stopReferenceDetails.latLng
            scheduleToNext       <- packet.scheduleToNextStop //todo do we want to fail if no polyline?
            polylineToNext       <- scheduleToNext.polyLineToNext
          } yield {
//            val position = new google.maps.LatLng(latLng.lat, latLng.lng)

            val decodedPolyline =
              google.maps.geometry.encoding.decodePath(polylineToNext.value).map(_.asInstanceOf[js.Any])

            activePolylines.get(packet.trainId) match {
              case Some(existingPolyline) =>
                existingPolyline.setMap(null)
                val newPolyline = createPolyline(decodedPolyline)
                activePolylines.update(packet.trainId, newPolyline)
                animatePolylineIcon(newPolyline, 120000)
              case None =>
                val newPolyline = createPolyline(decodedPolyline)
                activePolylines.update(packet.trainId, newPolyline)
                animatePolylineIcon(newPolyline, 120000)
            }
          }
      }

    def createPolyline(decodedPolyLine: js.Array[js.Any]): Polyline =
      new google.maps.Polyline(
        PolylineOptions(strokeColor = "#FF0000",
                        strokeOpacity = 1.0,
                        strokeWeight = 0.0,
                        path = decodedPolyLine,
                        icons = js.Array(iconSequence),
                        map = gmap))

    def animatePolylineIcon(polyline: google.maps.Polyline, durationInMs: Int) {
      (1 to 100).foreach { i =>
        val timeout = durationInMs * (i / 100.0)
        setTimeout(timeout) {
          console.log(
            s"Running setter for polyline ${polyline.toString} with i = $i. And will set offset to ${i.toString + "%"}")
          val icons = polyline.get("icons").asInstanceOf[js.Array[IconSequence]]
          icons.head.offset = i.toString + "%"
          polyline.set("icons", icons)
        }
      }
    }
    socket.onopen = (_: Event) => console.log("websocket opened")
    socket.onmessage = (event: MessageEvent) => onMessage(event)
    //  socket.onclose = (_: CloseEvent) => setFeedback("Connection closed.")
    //  socket.onerror = (_: ErrorEvent) => setFeedback("Connection error.")
  }
}
