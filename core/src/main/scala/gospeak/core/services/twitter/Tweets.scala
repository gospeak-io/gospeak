package gospeak.core.services.twitter

import java.time.format.DateTimeFormatter
import java.util.Locale

import gospeak.core.domain.messages.Message
import gospeak.core.domain.utils.Constants

object Tweets {
  private val smallDate = DateTimeFormatter.ofPattern("MMMM dd").withZone(Constants.defaultZoneId.normalized()).withLocale(Locale.ENGLISH)

  def externalCfpCreated(msg: Message.ExternalCfpCreated): String = {
    val name = msg.event.twitterAccount.map(_.handle).getOrElse(msg.event.name.value)
    val date = msg.event.start.map(s => s" on ${smallDate.format(s)}").getOrElse("")
    val place = msg.event.location.map(l => s" in${l.locality.map(ll => s" $ll,").getOrElse("")} ${l.country}").getOrElse("")
    val endDate = msg.cfp.close.map(d => s" before ${smallDate.format(d)}").getOrElse("")
    val user = msg.by.links.twitter.map(_.handle).getOrElse(msg.by.name.value)
    val tags = (msg.event.twitterHashtag.map(" " + _.handle).toList ++ msg.event.tags.map(t => s" #${t.value.replace(" ", "")}")).mkString("")
    s"""!!Speaker announcement!!
       |$name is happening$date$place
       |Submit your proposals$endDate at ${msg.cfp.publicLink}
       |#speaking$tags
       |Thanks $user for the post!
       |""".stripMargin.trim
  }
}
