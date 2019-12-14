package fr.gospeak.core.services.storage

import fr.gospeak.libs.scalautils.domain.Secret

sealed trait DatabaseConf extends Product with Serializable

object DatabaseConf {
  private val h2Regex = "(jdbc:h2:mem:.*)".r
  private val postgreSQLRegex = "postgres://([^:]+):([^@]+)@([a-z0-9-.:/]+)".r

  def from(url: String): Either[String, DatabaseConf] = url match {
    case h2Regex(value) => Right(H2(value))
    case postgreSQLRegex(login, pass, url) => Right(PostgreSQL(s"jdbc:postgresql://$url", login, Secret(pass)))
    case _ => Left(s"Unknown db url: $url")
  }

  final case class H2(url: String) extends DatabaseConf

  final case class PostgreSQL(url: String, user: String, pass: Secret) extends DatabaseConf

}
