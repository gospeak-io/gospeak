package gospeak.libs.scala

import java.text.Normalizer

object StringUtils {
  def leftPad(str: String, length: Int = 10, char: Char = ' '): String = {
    val toPad = (length - str.length).max(0)
    (char.toString * toPad) + str
  }

  def rightPad(str: String, length: Int = 10, char: Char = ' '): String = {
    val toPad = (length - str.length).max(0)
    str + (char.toString * toPad)
  }

  def removeDiacritics(str: String): String =
    Normalizer.normalize(str, Normalizer.Form.NFD)
      .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")

  def slugify(str: String): String =
    removeDiacritics(str).trim.toLowerCase()
      .replaceAll("[ _+'\"]", "-")
      .replaceAll("--+", "-")
      .replaceAll("[^a-z0-9-]", "")

  def identicalPrefixLength(s1: String, s2: String): Int = {
    var i = 0
    while (i < s1.length && i < s2.length && s1(i) == s2(i)) {
      i += 1
    }
    i
  }

  def identicalSuffixLength(s1: String, s2: String): Int = {
    var i = 0
    while (i < s1.length && i < s2.length && s1(s1.length - 1 - i) == s2(s2.length - 1 - i)) {
      i += 1
    }
    i
  }

  def stripIdenticalPrefix(s1: String, s2: String): (String, String) = {
    val i = identicalPrefixLength(s1, s2)
    if (i > 0) {
      val prefix = s"[..$i..]"
      (prefix + s1.drop(i), prefix + s2.drop(i))
    } else {
      (s1, s2)
    }
  }

  def stripIdenticalSuffix(s1: String, s2: String): (String, String) = {
    val i = identicalSuffixLength(s1, s2)
    if (i > 0) {
      val suffix = s"[..$i..]"
      (s1.dropRight(i) + suffix, s2.dropRight(i) + suffix)
    } else {
      (s1, s2)
    }
  }
}
