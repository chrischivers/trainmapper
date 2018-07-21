package trainmapper.cache

import cats.effect.IO

import scala.concurrent.duration.FiniteDuration

trait ListCache[K, V] {
  def push(key: K, rec: V)(expiry: Option[FiniteDuration]): IO[Unit]
  def getList(key: K): IO[List[V]]
}
