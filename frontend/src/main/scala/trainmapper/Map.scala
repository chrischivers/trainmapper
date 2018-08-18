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
    val url    = "ws://" + document.location.host + "/ws"
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
        case Left(err) => console.error(s"Error decoding incoming event message $msg. Error [$err]")
        case Right(packet) =>
          console.log(packet.toString)
          for {
            stopReferenceDetails <- packet.stopReferenceDetails
            latLng               <- stopReferenceDetails.latLng
          } yield {
            val position = new google.maps.LatLng(latLng.lat, latLng.lng)
            activeTrains.get(packet.trainId) match {
              case Some(existingMarker) =>
                existingMarker.setPosition(position)
                assert(activeTrains(packet.trainId).getPosition() == position)
              case None =>
                val marker = new google.maps.Marker(
                  google.maps.MarkerOptions(
                    position = position,
                    map = gmap,
                    title = packet.trainId.value
                  ))
                activeTrains.update(packet.trainId, marker)
            }
          }

      }
  }
}
