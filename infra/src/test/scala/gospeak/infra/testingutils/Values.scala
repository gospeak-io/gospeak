package gospeak.infra.testingutils

import java.util.UUID

import gospeak.core.GsConf
import gospeak.core.domain.messages.Message
import gospeak.core.services.storage.DbConf
import gospeak.infra.services.storage.sql.GsRepoSql
import gospeak.libs.scala.domain.{Liquid, LiquidMarkdown}

object Values {
  private def dbConf = DbConf.H2(s"jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1")
  val gsConf: GsConf = GsConf(
    event = GsConf.EventConf(
      description = LiquidMarkdown[Message.EventInfo]("Default event description")),
    proposal = GsConf.ProposalConf(
      tweet = Liquid[Message.ProposalInfo]("tweet")))

  def db: GsRepoSql = new GsRepoSql(dbConf, gsConf)
}
