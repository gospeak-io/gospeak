package fr.gospeak.web.auth.exceptions

import com.mohiva.play.silhouette.api.exceptions.ProviderException
import gospeak.core.domain.User

case class DuplicateSlugException(slug: User.Slug) extends ProviderException(s"User slug ${slug.value} already exists")
