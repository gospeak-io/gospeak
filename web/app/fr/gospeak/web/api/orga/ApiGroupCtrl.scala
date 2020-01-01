package fr.gospeak.web.api.orga

import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.web.AppConf
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.utils.ApiCtrl
import play.api.mvc.ControllerComponents

class ApiGroupCtrl(cc: ControllerComponents,
                   silhouette: Silhouette[CookieEnv],
                   conf: AppConf) extends ApiCtrl(cc, silhouette, conf) {

}
