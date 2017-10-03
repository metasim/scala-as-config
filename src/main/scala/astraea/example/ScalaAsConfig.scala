package astraea.example

import astraea.example.Model.Neighborhood
import com.typesafe.scalalogging.LazyLogging

import scala.reflect.internal.util.BatchSourceFile
import scala.reflect.io.VirtualDirectory
import scala.tools.nsc.{Global, Settings}
import scala.reflect.internal.util.AbstractFileClassLoader

object ScalaAsConfig extends App with LazyLogging {

  val myExternalConfig = """
  |  Neighborhood(
  |    Home(
  |      Person("Fred", "Fintstone"),
  |      Address(1313, "Cobblestone Way")
  |    ),
  |    Home(
  |      Person("Barney", "Rubble"),
  |      Address(345, "Stonecave Road")
  |    )
  |  )
  """.stripMargin

  val config = ConfigCompiler(myExternalConfig)

  logger.info("Config: \n\t" + config)
}

object Model {
  case class Address(number: Short, street: String)
  case class Person(first: String, last: String)
  case class Home(owner: Person, address: Address)
  case class Neighborhood(homes: Home*)
}

object ConfigCompiler extends LazyLogging {
  private val compilerOutputDir = new VirtualDirectory("<vfs>", None)

  private def makeCompiler() = {
    val settings = new Settings(msg ⇒ logger.error(msg))
    settings.embeddedDefaults[ScalaAsConfig.type]
    settings.usejavacp.value = true
    settings.outputDirs.setSingleOutput(compilerOutputDir)
    new Global(settings)
  }

  private def createEnvelope(code: String): String  =
    s"""
       | package foo
       | import astraea.example.Model._
       | class Config extends Function0[Neighborhood] {
       |   def apply(): Neighborhood = {
       |     $code
       |   }
       | }
      """.stripMargin

  def apply(source: String): Option[Neighborhood] = {

    val compiler = makeCompiler()

    val runner = new compiler.Run

    runner.compileSources(List(new BatchSourceFile("config", createEnvelope(source))))
    if(compiler.reporter.hasErrors) {
      logger.error("oops")
      None
    }
    else {
      val cl = new AbstractFileClassLoader(compilerOutputDir, getClass.getClassLoader)
      val configClass = cl.loadClass("foo.Config").asInstanceOf[Class[() ⇒ Neighborhood]]
      Some(configClass.newInstance()())
    }
  }
}
