package fr.gospeak.libs.scalautils.utils

import java.security.MessageDigest

object Crypto {
  def md5(str: String): String =
    MessageDigest.getInstance("MD5").digest(str.getBytes).map("%02x".format(_)).mkString
}
