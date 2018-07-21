package trainmapper.cache

import akka.util.ByteString
import cats.effect.IO
import cats.syntax.functor._
import io.circe.parser.parse
import redis.{ByteStringDeserializer, ByteStringSerializer, RedisClient}
import trainmapper.Shared.{MovementPacket, TrainId}
import io.circe.syntax._
import cats.syntax.functor._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

object MovementPacketCache {

  implicit val byteStringSerializer = new ByteStringSerializer[MovementPacket] {
    override def serialize(data: MovementPacket): ByteString =
      ByteString(data.asJson.noSpaces)
  }

  implicit val byteStringDeserializer = new ByteStringDeserializer[MovementPacket] {
    override def deserialize(bs: ByteString): MovementPacket =
      (for {
        json    <- parse(bs.utf8String)
        decoded <- json.as[MovementPacket]
      } yield decoded).fold(err => throw err, identity)
  }

  def apply(redisClient: RedisClient)(implicit executionContext: ExecutionContext) =
    new ListCache[TrainId, MovementPacket] {

      override def push(key: TrainId, rec: MovementPacket)(expiry: Option[FiniteDuration]): IO[Unit] =
        IO.fromFuture(IO(redisClient.lpush(key.value, rec))).flatMap { _ =>
          expiry.fold(IO.unit)(ex => IO.fromFuture(IO(redisClient.expire(key.value, ex.toSeconds))).void)
        }

      override def getList(key: TrainId): IO[List[MovementPacket]] =
        IO.fromFuture(IO(redisClient.lrange(key.value, 0L, Long.MaxValue))).map(_.toList)
    }
}
