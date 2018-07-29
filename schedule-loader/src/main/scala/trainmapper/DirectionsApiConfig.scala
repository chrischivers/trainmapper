package trainmapper

import com.typesafe.config.ConfigFactory
import org.http4s.Uri

case class DirectionsApiConfig(baseUri: Uri, apiKey: String)

object DirectionsApiConfig {

  def read: DirectionsApiConfig = {
    val config = ConfigFactory.load()
    DirectionsApiConfig(
      Uri.unsafeFromString(config.getString("directionsApi.baseUri")),
      config.getString("directionsApi.apiKey")
    )
  }
}
