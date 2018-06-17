package trainmapper.http

import fs2.StreamApp.ExitCode
import fs2._
import fs2.async.mutable.Queue
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.server.websocket._
import org.http4s.websocket.WebsocketBits._

import scala.concurrent.ExecutionContext.Implicits.global
import _root_.io.circe.syntax._
import cats.effect.Effect

object WsServer {
  def apply[F[_]](queue: Queue[F, MovementPacket])(implicit F: Effect[F]) = new WsServer[F](queue)
}

protected class WsServer[F[_]](queue: Queue[F, MovementPacket])(implicit F: Effect[F])
    extends StreamApp[F]
    with Http4sDsl[F] {

  def route = HttpService[F] {

    case GET -> Root / "ws" =>
      val toClient: Stream[F, WebSocketFrame] =
        queue.dequeue
          .map(msg => Text(msg.asJson.noSpaces))

      val fromClient: Sink[F, WebSocketFrame] = _.evalMap { ws: WebSocketFrame =>
        ws match {
          case Text(t, _) => F.delay(println(t))
          case f          => F.delay(println(s"Unknown type: $f"))
        }
      }
      WebSocketBuilder[F].build(toClient, fromClient)
  }

  override def stream(args: List[String], requestShutdown: F[Unit]): Stream[F, ExitCode] =
    for {
      exitCode <- BlazeBuilder[F]
        .bindHttp(8080)
        .withWebSockets(true)
        .mountService(route)
        .serve
    } yield exitCode

}
