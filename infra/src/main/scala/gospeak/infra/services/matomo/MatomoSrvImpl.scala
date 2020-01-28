package gospeak.infra.services.matomo

import gospeak.core.services.matomo.MatomoSrv
import gospeak.infra.libs.matomo.MatomoClient

class MatomoSrvImpl(client: MatomoClient) extends MatomoSrv {
  /*
    Important params:
      - urlref: referer url
      - uid: user id
      - gt_ms: action duration in ms
      - custom dimentions: https://matomo.org/docs/custom-dimensions/
   */

}
