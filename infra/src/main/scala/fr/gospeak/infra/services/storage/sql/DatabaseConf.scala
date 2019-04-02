package fr.gospeak.infra.services.storage.sql

import fr.gospeak.libs.scalautils.domain.Secret

sealed trait DatabaseConf extends Product with Serializable

object DatabaseConf {

  final case class H2(url: String) extends DatabaseConf

  final case class PostgreSQL(url: String, user: String, pass: Secret) extends DatabaseConf

}

