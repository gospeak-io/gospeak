package fr.gospeak.core.services.meetup.domain

import fr.gospeak.libs.scalautils.domain.Url

final case class MeetupInfo(authorizationUrl: Url,
                            revokeUrl: Url)
