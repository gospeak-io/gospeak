package fr.gospeak.infra.testingutils

import java.util.UUID

import fr.gospeak.core.GospeakConf
import fr.gospeak.core.domain.utils.TemplateData
import fr.gospeak.core.services.storage.DatabaseConf
import fr.gospeak.infra.services.storage.sql.GospeakDbSql
import fr.gospeak.libs.scalautils.domain.MustacheTmpl.MustacheMarkdownTmpl

object Values {
  private val dbConf = DatabaseConf.H2(s"jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1")
  private val gsConf = GospeakConf(
    event = GospeakConf.EventConf(
      description = MustacheMarkdownTmpl[TemplateData.EventInfo]("Default event description")))

  val db = new GospeakDbSql(dbConf, gsConf)
}
