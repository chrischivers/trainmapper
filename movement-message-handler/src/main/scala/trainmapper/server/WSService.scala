package trainmapper.server

import _root_.io.circe.syntax._
import cats.effect.Effect
import fs2._
import fs2.async.mutable.Queue
import org.http4s.dsl.Http4sDsl
import org.http4s.server.websocket._
import org.http4s.websocket.WebsocketBits._
import trainmapper.Shared.MovementPacket
import trainmapper.http.MovementsHttp.MovementsHttpResponse

object WSService {

  def apply[F[_]](queue: Queue[F, MovementPacket])(implicit F: Effect[F]) = {

    object dsl extends Http4sDsl[F]
    import dsl._

    org.http4s.HttpService[F] {

      case GET -> Root =>
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
  }
}
