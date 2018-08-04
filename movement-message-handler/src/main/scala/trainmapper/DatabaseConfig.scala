package trainmapper

import com.typesafe.config.ConfigFactory

case class DatabaseConfig(driverClassName: String,
                          url: String,
                          username: String,
                          password: String,
                          maximumPoolSize: Int = 2)

object DatabaseConfig {

  def read: DatabaseConfig = {
    val config = ConfigFactory.load()
    DatabaseConfig(
      config.getString("db.driverClassName"),
      config.getString("db.url"),
      config.getString("db.username"),
      config.getString("db.password"),
      config.getInt("db.maximumPoolSize")
    )
  }
}
