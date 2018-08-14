package trainmapper.server

import cats.effect.IO
import fs2.async.mutable.Queue
import org.http4s.HttpService
import org.http4s.server.Router
import trainmapper.Shared.MovementPacket
import trainmapper.http.MovementsHttp.MovementsHttpResponse

import scala.concurrent.ExecutionContext

object WebServer {
  def apply(queue: Queue[IO, MovementPacket], googleMapsApiKey: String)(
      implicit executionContext: ExecutionContext): HttpService[IO] =
    Router(
      ("/", MapService[IO](googleMapsApiKey)),
      ("/ws", WSService(queue))
    )
}
