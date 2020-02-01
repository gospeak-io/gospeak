package gospeak.core.services.storage

import gospeak.libs.scala.domain.Secret

sealed trait DbConf extends Product with Serializable

object DbConf {
  private val h2Regex = "(jdbc:h2:mem:.*)".r
  private val postgreSQLRegex = "postgres://([^:]+):([^@]+)@([a-z0-9-.:/]+)".r

  def from(url: String): Either[String, DbConf] = url match {
    case h2Regex(value) => Right(H2(value))
    case postgreSQLRegex(login, pass, url) => Right(PostgreSQL(s"jdbc:postgresql://$url", login, Secret(pass)))
    case _ => Left(s"Unknown db url: $url")
  }

  final case class H2(url: String) extends DbConf

  final case class PostgreSQL(url: String, user: String, pass: Secret) extends DbConf

}
