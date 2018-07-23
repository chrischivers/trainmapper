package trainmapper

import org.scalajs.dom._
import org.scalajs.dom
import io.circe.parser._
import trainmapper.Shared.{MovementPacket, TrainId}

import scala.collection.mutable
import scala.scalajs.js.annotation.JSExportTopLevel

object Map {

  val activeTrains = mutable.Map[TrainId, google.maps.Marker]()

  @JSExportTopLevel("initialize")
  def initialize() = {
    val url    = "ws://localhost:8080/ws"
    val socket = new dom.WebSocket(url)

    val opts = google.maps.MapOptions(center = new google.maps.LatLng(51.201203, -1.724370),
                                      zoom = 8,
                                      panControl = false,
                                      streetViewControl = false,
                                      mapTypeControl = false)

    val gmap = new google.maps.Map(document.getElementById("map-canvas"), opts)

    socket.onopen = (_: Event) => console.log("websocket opened")
    socket.onmessage = (event: MessageEvent) => onMessage(event)
//  socket.onclose = (_: CloseEvent) => setFeedback("Connection closed.")
//  socket.onerror = (_: ErrorEvent) => setFeedback("Connection error.")

    def onMessage(msg: MessageEvent): Unit =
      parse(msg.data.toString).flatMap(_.as[MovementPacket]) match {
        case Left(err)     => console.error(s"Error decoding incoming event message $msg. Error [$err]")
        case Right(packet) =>
          //todo remove gets
          val newLatLng = new google.maps.LatLng(packet.stopReferenceDetails.get.latLng.get.lat,
                                                 packet.stopReferenceDetails.get.latLng.get.lng)
          activeTrains.get(packet.trainId) match {
            case Some(existingMarker) =>
              existingMarker.setPosition(newLatLng)
              assert(activeTrains(packet.trainId).getPosition() == newLatLng)
            case None =>
              val marker = new google.maps.Marker(
                google.maps.MarkerOptions(
                  position = newLatLng,
                  map = gmap,
                  title = packet.trainId.value
                ))
              activeTrains.update(packet.trainId, marker)
          }
      }
  }
}
