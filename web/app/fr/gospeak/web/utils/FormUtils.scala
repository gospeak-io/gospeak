package fr.gospeak.web.utils

import play.api.data.validation.Constraint
import play.api.data.{Form, FormError, Mapping}

object FormUtils {
  def empty[A]: Form[A] = Form(new Mapping[A] {
    override val key: String = "empty"
    override val mappings: Seq[Mapping[_]] = Seq()
    override val constraints: Seq[Constraint[A]] = Seq()

    override def bind(data: Map[String, String]): Either[Seq[FormError], A] = Left(Seq())

    override def unbind(value: A): Map[String, String] = Map()

    override def unbindAndValidate(value: A): (Map[String, String], Seq[FormError]) = (Map(), Seq())

    override def withPrefix(prefix: String): Mapping[A] = this

    override def verifying(constraints: Constraint[A]*): Mapping[A] = this
  })
}
