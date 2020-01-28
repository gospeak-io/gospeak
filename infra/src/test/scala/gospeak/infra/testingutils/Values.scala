package gospeak.infra.testingutils

import java.util.UUID

import gospeak.core.GospeakConf
import gospeak.core.domain.utils.TemplateData
import gospeak.core.services.storage.DatabaseConf
import gospeak.infra.services.storage.sql.GospeakDbSql
import gospeak.libs.scala.domain.MustacheTmpl.MustacheMarkdownTmpl

object Values {
  private val dbConf = DatabaseConf.H2(s"jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1")
  private val gsConf = GospeakConf(
    event = GospeakConf.EventConf(
      description = MustacheMarkdownTmpl[TemplateData.EventInfo]("Default event description")))

  val db = new GospeakDbSql(dbConf, gsConf)
}
