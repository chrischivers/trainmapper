package trainmapper

import java.time.Clock

import akka.actor.ActorSystem
import akka.util.ByteString
import cats.effect.IO
import fs2.async.Ref
import redis.{ByteStringDeserializer, ByteStringSerializer, RedisClient}

import scala.concurrent.Future

object StubRedisClient {

  implicit val actorSystem = ActorSystem()

  type ByteStringAndExpiry = (ByteString, Long)

  def apply(ref: Ref[IO, Map[String, ByteStringAndExpiry]]) =
    new RedisClient() {

      override def get[R](key: String)(
          implicit byteStringDeserializer: ByteStringDeserializer[R]): Future[Option[R]] = {
        val now = System.currentTimeMillis()
        ref.get
          .map(m => m.get(key))
          .map(v =>
            v.flatMap {
              case (byteString, expiry) =>
                if (expiry > now) Some(byteStringDeserializer.deserialize(byteString))
                else None
          })
          .unsafeToFuture()
      }

      override def set[V](key: String,
                          value: V,
                          exSeconds: Option[Long],
                          pxMilliseconds: Option[Long],
                          NX: Boolean,
                          XX: Boolean)(implicit byteStringSerializer: ByteStringSerializer[V]): Future[Boolean] =
        ref
          .modify(m =>
            m + (key -> (byteStringSerializer.serialize(value), pxMilliseconds.fold(Long.MaxValue)(expiry =>
              System.currentTimeMillis() + expiry))))
          .map(_ => true)
          .unsafeToFuture()
    }
}
