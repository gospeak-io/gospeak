package gospeak.libs.sql.jooq

import java.time.{Instant, LocalDate}

import doobie.util.Put
import doobie.util.fragment.Fragment
import doobie.util.param.Param
import org.jooq.Query
import org.jooq.codegen.GenerationTool
import org.jooq.meta.jaxb._

object JooqUtils {
  def queryToFragment(q: Query): Fragment = {
    // Fragment.const0(q.getSQL(ParamType.INLINED)) // old & basic impl
    import scala.collection.JavaConverters._
    val sql = q.getSQL
    val elems = q.getBindValues.asScala.toList.map {
      case p: java.lang.Integer => Param.Elem.Arg(p.toInt, implicitly[Put[Int]])
      case p: java.lang.Long => Param.Elem.Arg(p.toLong, implicitly[Put[Long]])
      case p: java.lang.Double => Param.Elem.Arg(p.toDouble, implicitly[Put[Double]])
      case p: java.lang.String => Param.Elem.Arg(p, implicitly[Put[String]])
      case p: java.lang.Boolean => Param.Elem.Arg(p.booleanValue(), implicitly[Put[Boolean]])
      case p: Instant => Param.Elem.Arg(p, implicitly[Put[Instant]])
      case p: LocalDate => Param.Elem.Arg(p, implicitly[Put[LocalDate]])
      case p: Array[Byte] => Param.Elem.Arg(p, implicitly[Put[Array[Byte]]])
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
