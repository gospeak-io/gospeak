package gospeak.libs.sql.doobie

import doobie.util.fragment.Fragment
import doobie.util.fragment.Fragment.const0

final case class Field(name: String, prefix: String, alias: String = "") {
  def fullName: String = if (prefix.isEmpty) name else s"$prefix.$name"

  def value: String = fullName + (if (alias.isEmpty) "" else s" as $alias")

  def label: String = if (alias.isEmpty) fullName else alias
}

final case class CustomField(formula: Fragment, name: String) {
  val value: Fragment = formula ++ const0(s" as $name")
}

final case class AggregateField(formula: String, name: String) {
  def value: String = s"$formula as $name"
}
