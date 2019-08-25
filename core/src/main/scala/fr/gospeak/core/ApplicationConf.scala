package fr.gospeak.core

import fr.gospeak.libs.scalautils.Crypto.AesSecretKey

final case class ApplicationConf(env: ApplicationConf.Env,
                                 aesKey: AesSecretKey)

object ApplicationConf {

  sealed trait Env {
    def isLocal: Boolean = false

    def isDev: Boolean = false

    def isProd: Boolean = false
  }

  final case object Local extends Env {
    override def isLocal: Boolean = true
  }

  final case object Dev extends Env {
    override def isDev: Boolean = true
  }

  final case object Prod extends Env {
    override def isProd: Boolean = true
  }

}
