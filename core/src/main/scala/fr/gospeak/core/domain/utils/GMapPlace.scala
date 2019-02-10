package fr.gospeak.core.domain.utils

case class GMapPlace(id: String, // ChIJ0wnrwMdv5kcRuOvv_dXYoy4
                     name: String, // Zeenea Data Catalog
                     streetNo: Option[String], // 48
                     street: Option[String], // Rue de Ponthieu
                     postalCode: Option[String], // 75008
                     locality: Option[String], // Paris
                     country: String, // France
                     formatted: String, // 48 Rue de Ponthieu, 75008 Paris, France
                     input: String, // Zeenea Data Catalog, Rue de Ponthieu, Paris, France
                     lat: Double, // 48.8716827
                     lng: Double, // 2.3070390000000316
                     url: String, // https://maps.google.com/?cid=3360768160548514744
                     website: Option[String], // http://www.zeenea.com/
                     phone: Option[String]) { // +33 1 40 40 22 90
  def format: String = formatted
}
