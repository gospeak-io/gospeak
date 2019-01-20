package fr.gospeak.infra.services.storage.sql

import fr.gospeak.core.domain.utils.Password

sealed trait DbSqlConf extends Product with Serializable

case class H2(driver: String, url: String) extends DbSqlConf

case class PostgreSQL(url: String, user: String, pass: Password) extends DbSqlConf
