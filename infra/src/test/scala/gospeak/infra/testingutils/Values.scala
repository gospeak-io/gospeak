package gospeak.infra.testingutils

import java.util.UUID

import gospeak.core.GsConf
import gospeak.core.domain.utils.TemplateData
import gospeak.core.services.storage.DbConf
import gospeak.infra.services.storage.sql.GsRepoSql
import gospeak.libs.scala.domain.MustacheTmpl.MustacheMarkdownTmpl

object Values {
  private val dbConf = DbConf.H2(s"jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1")
  private val gsConf = GsConf(
    event = GsConf.EventConf(
      description = MustacheMarkdownTmpl[TemplateData.EventInfo]("Default event description")))

  val db = new GsRepoSql(dbConf, gsConf)
}
