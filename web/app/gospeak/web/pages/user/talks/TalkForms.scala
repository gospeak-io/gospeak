package gospeak.web.pages.user.talks

import gospeak.core.domain.Performance
import gospeak.core.domain.Talk
import gospeak.web.utils.Mappings._
import play.api.data.Forms._
import play.api.data.{Form, Mapping}

object TalkForms {
  val talkMappings: Mapping[Talk.Data] = mapping(
    "slug" -> talkSlug,
    "title" -> talkTitle,
    "duration" -> duration,
    "description" -> markdown,
    "slides" -> optional(slides),
    "video" -> optional(video),
    "tags" -> tags
  )(Talk.Data.apply)(Talk.Data.unapply)
  val create: Form[Talk.Data] = Form(talkMappings)

  val performanceMappings: Mapping[Performance.Data] = mapping(
    "venue" -> nonEmptyText,
    "title" -> nonEmptyText,
    "description" -> markdown
  )(Performance.Data.apply)(Performance.Data.unapply)
  val createPerformance: Form[Performance.Data] = Form(performanceMappings)
}

