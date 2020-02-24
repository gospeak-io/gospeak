package gospeak.core.services.twitter

import java.time.format.DateTimeFormatter
import java.util.Locale

import gospeak.core.domain.utils.Constants
import gospeak.core.domain.{ExternalCfp, ExternalEvent, User}

object Tweets {
  private val smallDate = DateTimeFormatter.ofPattern("MMMM dd").withZone(Constants.defaultZoneId.normalized()).withLocale(Locale.ENGLISH)

  def externalCfpCreated(cfp: ExternalCfp, event: ExternalEvent, cfpUrl: String, by: User): String = {
    val name = event.twitterAccount.map(_.handle).getOrElse(event.name.value)
    val date = event.start.map(s => s" on ${smallDate.format(s)}").getOrElse("")
    val place = event.location.map(l => s" in${l.locality.map(ll => s" $ll,").getOrElse("")} ${l.country}").getOrElse("")
    val endDate = cfp.close.map(d => s" before ${smallDate.format(d)}").getOrElse("")
    val user = by.social.twitter.map(_.handle).getOrElse(by.name.value)
    val tags = (event.twitterHashtag.map(" " + _.handle).toList ++ event.tags.map(t => s" #${t.value.replace(" ", "")}")).mkString("")
    s"""!!Speaker announcement!!
       |$name is happening$date$place
       |Submit your proposals$endDate at $cfpUrl
       |#speaking$tags
       |Thanks $user for the post!
       |""".stripMargin.trim
  }
}
