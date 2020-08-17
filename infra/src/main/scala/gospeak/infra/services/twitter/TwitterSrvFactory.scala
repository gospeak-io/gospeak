package gospeak.infra.services.twitter

import gospeak.core.services.twitter.{TwitterConf, TwitterSrv}

object TwitterSrvFactory {
  def from(conf: TwitterConf): TwitterSrv = conf match {
    case _: TwitterConf.Console => new ConsoleTwitterSrv()
    case c: TwitterConf.Twitter => new RealTwitterSrv(c)
  }
}
