package gospeak.infra.testingutils

import java.util.UUID

import gospeak.core.GsConf
import gospeak.core.domain.messages.Message
import gospeak.core.services.storage.DbConf
import gospeak.infra.services.storage.sql.GsRepoSql
import gospeak.libs.scala.domain.Mustache

object Values {
  private val dbConf = DbConf.H2(s"jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1")
  private val gsConf = GsConf(
    event = GsConf.EventConf(
      description = Mustache.Markdown[Message.EventInfo]("Default event description")),
    proposal = GsConf.ProposalConf(
      tweet = Mustache.Text[Message.ProposalInfo]("tweet")))

  val db = new GsRepoSql(dbConf, gsConf)
}
