package trainmapper

import com.typesafe.config.ConfigFactory
import org.http4s.Uri

object ActivationLookupConfig {

  case class ActivationLookupConfig(baseUri: Uri)

  def read: ActivationLookupConfig = {
    val config = ConfigFactory.load()
    ActivationLookupConfig(Uri.unsafeFromString(config.getString("activationLookup.baseUri")))
  }
}
