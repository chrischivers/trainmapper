package trainmapper.cache

import cats.effect.IO

import scala.concurrent.duration.FiniteDuration

trait Cache[K, V] {
  def put(key: K, rec: V)(expiry: Option[FiniteDuration]): IO[Unit]
  def get(key: K): IO[Option[V]]
}
