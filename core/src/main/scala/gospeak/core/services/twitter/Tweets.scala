package gospeak.core.services.twitter

import java.time.format.DateTimeFormatter
import java.util.Locale

import gospeak.core.domain.utils.Constants
import gospeak.core.domain.{ExternalCfp, User}

object Tweets {
  private val smallDate = DateTimeFormatter.ofPattern("MMMM dd").withZone(Constants.defaultZoneId.normalized()).withLocale(Locale.ENGLISH)

  def externalCfpCreated(cfp: ExternalCfp, cfpUrl: String, by: User): String = {
    val name = cfp.event.twitterAccount.map(_.handle).getOrElse(cfp.name.value)
    val date = cfp.event.start.map(d => s" on ${smallDate.format(d)}").getOrElse("")
    val place = cfp.event.location.map(l => s" in${l.locality.map(ll => s" $ll,").getOrElse("")} ${l.country}").getOrElse("")
    val endDate = cfp.close.map(d => s" before ${smallDate.format(d)}").getOrElse("")
    val user = by.social.twitter.map(_.handle).getOrElse(by.name.value)
    val tags = (cfp.event.twitterHashtag.map(" " + _.handle).toList ++ cfp.tags.map(t => s" #${t.value.replace(" ", "")}")).mkString("")
    s"""!!Speaker announcement!!
       |$name is happening$date$place
       |Submit your proposals$endDate at $cfpUrl
       |#speaking$tags
       |Thanks $user for the post!
       |""".stripMargin.trim
  }
}
