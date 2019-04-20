package fr.gospeak.libs.scalautils.utils

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
}
