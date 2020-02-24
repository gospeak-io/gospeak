package gospeak.infra.services.matomo

import gospeak.core.services.matomo.{MatomoConf, MatomoSrv}
import gospeak.libs.matomo.MatomoClient

class MatomoSrvImpl(client: MatomoClient) extends MatomoSrv {
  /*
    Important params:
      - urlref: referer url
      - uid: user id
      - gt_ms: action duration in ms
      - custom dimentions: https://matomo.org/docs/custom-dimensions/
   */

}

object MatomoSrvImpl {
  def from(conf: MatomoConf): MatomoSrvImpl =
    new MatomoSrvImpl(new MatomoClient(MatomoClient.Conf(
      baseUrl = conf.baseUrl,
      site = conf.site,
      token = conf.token)))
}
