package fr.gospeak.infra.services.storage.sql

import fr.gospeak.libs.scalautils.domain.Secret

sealed trait DbSqlConf extends Product with Serializable

final case class H2(driver: String, url: String) extends DbSqlConf

final case class PostgreSQL(url: String, user: String, pass: Secret) extends DbSqlConf
