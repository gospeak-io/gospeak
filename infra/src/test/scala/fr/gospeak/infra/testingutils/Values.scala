package fr.gospeak.infra.testingutils

import java.util.UUID

import fr.gospeak.core.GospeakConf
import fr.gospeak.core.domain.utils.TemplateData
import fr.gospeak.infra.services.storage.sql.{DatabaseConf, GospeakDbSql}
import fr.gospeak.libs.scalautils.domain.MustacheTmpl.MustacheMarkdownTmpl

object Values {
  def dbConf = DatabaseConf.H2(s"jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1")

  def gsConf = GospeakConf(
    event = GospeakConf.EventConf(
      description = MustacheMarkdownTmpl[TemplateData.EventInfo]("Default event description")))

  def db = new GospeakDbSql(dbConf, gsConf)
}
