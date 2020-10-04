package gospeak.libs.sql.jooq

import java.time.{Instant, LocalDate}

import doobie.util.Put
import doobie.util.fragment.{Elem, Fragment}
import org.jooq.Query
import org.jooq.codegen.GenerationTool
import org.jooq.meta.jaxb._

object JooqUtils {
  def queryToFragment(q: Query)(implicit i: Put[Instant], ld: Put[LocalDate]): Fragment = {
    // Fragment.const0(q.getSQL(ParamType.INLINED)) // old & basic impl
    import scala.collection.JavaConverters._
    val sql = q.getSQL
    val elems = q.getBindValues.asScala.toList.map {
      case p: java.lang.Integer => Elem.Arg(p.toInt, implicitly[Put[Int]])
      case p: java.lang.Long => Elem.Arg(p.toLong, implicitly[Put[Long]])
      case p: java.lang.Double => Elem.Arg(p.toDouble, implicitly[Put[Double]])
      case p: java.lang.String => Elem.Arg(p, implicitly[Put[String]])
      case p: java.lang.Boolean => Elem.Arg(p.booleanValue(), implicitly[Put[Boolean]])
      case p: Instant => Elem.Arg(p, implicitly[Put[Instant]])
      case p: LocalDate => Elem.Arg(p, implicitly[Put[LocalDate]])
      case p: Array[Byte] => Elem.Arg(p, implicitly[Put[Array[Byte]]])
      case p => throw new Exception(s"Unknown parameter ${p.getClass.getName}: $p")
    }
    Fragment(sql, elems)
  }

  def generateTables(driver: String, url: String, directory: String, packageName: String): Unit = {
    GenerationTool.generate(new Configuration()
      .withJdbc(new Jdbc()
        .withDriver(driver)
        .withUrl(url)
        .withUser("")
        .withPassword(""))
      .withGenerator(new Generator()
        .withName("org.jooq.codegen.ScalaGenerator")
        .withStrategy(new Strategy())
        //.withName("org.jooq.codegen.KeepNamesGeneratorStrategy")) // problem: more name conflicts (ex: fields)
        .withDatabase(new Database()
          .withName("org.jooq.meta.h2.H2Database")
          .withExcludes(".*flyway.*")
          .withInputSchema("PUBLIC"))
        .withGenerate(new Generate()
          .withGlobalCatalogReferences(false)
          .withGlobalIndexReferences(false)
          .withGlobalKeyReferences(false)
          .withGlobalLinkReferences(false)
          .withGlobalQueueReferences(false)
          .withGlobalRoutineReferences(false)
          .withGlobalSchemaReferences(false)
          .withGlobalSequenceReferences(false)
          .withGlobalTableReferences(true)
          .withGlobalUDTReferences(false))
        .withTarget(new Target().withDirectory(directory).withPackageName(packageName))
      ))
  }
}
