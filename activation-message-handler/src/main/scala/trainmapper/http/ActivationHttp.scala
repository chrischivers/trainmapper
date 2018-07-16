package trainmapper.http

import cats.effect.IO
import com.typesafe.scalalogging.StrictLogging
import org.http4s.circe.jsonEncoderOf
import org.http4s.dsl.impl.QueryParamDecoderMatcher
import org.http4s.dsl.io._
import org.http4s.{HttpService, QueryParamDecoder}
import trainmapper.Shared.TrainId
import trainmapper.cache.Cache
import trainmapper.networkrail.ActivationMessageRmqHandler.TrainActivationMessage

import scala.concurrent.ExecutionContext

object ActivationHttp extends StrictLogging {

  implicit val entityEncoder                             = jsonEncoderOf[IO, TrainActivationMessage]
  implicit val statusDecoder: QueryParamDecoder[TrainId] = QueryParamDecoder[String].map(TrainId(_))

  object TrainIdMatcher extends QueryParamDecoderMatcher[TrainId]("trainId")

  def apply(cache: Cache[TrainId, TrainActivationMessage])(
      implicit executionContext: ExecutionContext): HttpService[IO] =
    HttpService[IO] {
      case GET -> Root / "activation" :? TrainIdMatcher(id) =>
        cache
          .get(id)
          .flatMap { _.fold(NotFound(s"Train id ${id.value} not found in cache"))(Ok(_)) }

    }

}
