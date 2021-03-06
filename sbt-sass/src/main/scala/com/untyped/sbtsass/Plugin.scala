package com.untyped.sbtsass

import java.util.Properties
import sbt._
import sbt.Keys._

object Plugin extends sbt.Plugin {

  object SassKeys {
    val sass                = TaskKey[Seq[File]]("sass", "Compile Sass CSS sources.")
    val sourceGraph         = TaskKey[Graph]("sass-source-graph", "List of Sass CSS sources.")
    val templateProperties  = SettingKey[Properties]("sass-template-properties", "Properties to use in Sass CSS templates")
    val downloadDirectory   = SettingKey[File]("sass-download-directory", "Temporary directory to download Sass CSS files to")
    val filenameSuffix      = SettingKey[String]("sass-filename-suffix", "Suffix to append to the output file names before '.css'")
    val prettyPrint         = SettingKey[Boolean]("sass-pretty-print", "Whether to pretty print CSS (default false)")
    val sassVersion         = SettingKey[SassVersion]("sass-version", "The version of the Sass CSS compiler to use")
    val useCommandLine      = SettingKey[Boolean]("sass-use-command-line", "Use the Sass CSS command line script instead of Rhino")
    val sassOutputStyle     = SettingKey[Symbol]("sass-output-style", "Sets output style used when compiling")
  }

  sealed trait SassVersion {
    val version: String
    override def toString = version
  }

  object SassVersion {
    val Sass3214   = new SassVersion { val version = "3.2.14" }
    val Sass332 = new SassVersion { val version = "3.3.2" }
  }

  import SassKeys._

  def time[T](out: TaskStreams, msg: String)(func: => T): T = {
    val startTime = java.lang.System.currentTimeMillis
    val result = func
    val endTime = java.lang.System.currentTimeMillis
    out.log.debug("TIME sbt-sass " + msg + ": " + (endTime - startTime) + "ms")
    result
  }

  def unmanagedSourcesTask = // : Def.Initialize[Task[Seq[File]]] =
    (streams, sourceDirectories in sass, includeFilter in sass, excludeFilter in sass) map {
      (out, sourceDirs, includeFilter, excludeFilter) =>
        time(out, "unmanagedSourcesTask") {
          out.log.debug("sourceDirectories: " + sourceDirs)
          out.log.debug("includeFilter: " + includeFilter)
          out.log.debug("excludeFilter: " + excludeFilter)

          sourceDirs.foldLeft(Seq[File]()) {
            (accum, sourceDir) =>
              accum ++ com.untyped.sbtgraph.Descendents(sourceDir, includeFilter, excludeFilter).get
          }
        }
    }

  def sourceGraphTask = // : Def.Initialize[Task[Graph]] =
    (streams,
      sourceDirectories in sass,
      resourceManaged in sass,
      unmanagedSources in sass,
      templateProperties in sass,
      downloadDirectory in sass,
      filenameSuffix in sass,
      prettyPrint in sass,
      sassVersion in sass,
      useCommandLine in sass,
      sassOutputStyle in sass) map {
      (out, sourceDirs, targetDir, sourceFiles, templateProperties,
       downloadDir, filenameSuffix, prettyPrint, sassVersion, useCommandLine, sassOutputStyle) =>
        time(out, "sourceGraphTask") {
          out.log.debug("sbt-sass template properties " + templateProperties)

          val graph = Graph(
            log                = out.log,
            sourceDirs         = sourceDirs,
            targetDir          = targetDir,
            templateProperties = templateProperties,
            downloadDir        = downloadDir,
            filenameSuffix     = filenameSuffix,
            sassVersion        = sassVersion,
            prettyPrint        = prettyPrint,
            useCommandLine     = useCommandLine,
            compilerOptions    = Map(":style" -> (":"+sassOutputStyle.name))
          )

          sourceFiles.foreach(graph += _)

          graph
        }
    }

  def watchSourcesTask =
    (streams, sourceGraph in sass) map {
      (out, graph) =>
        graph.sources.map(_.src) : Seq[File]
    }

  def compileTask = {
    (streams, unmanagedSources in sass, sourceGraph in sass) map {
      (out, sourceFiles, graph: Graph) =>
        time(out, "compileTask") {
          graph.compileAll(sourceFiles.filterNot(_.getName.startsWith("_")))
        }
    }
  }

  def cleanTask =
    (streams, sourceGraph in sass) map {
      (out, graph) =>
        graph.sources.foreach(_.clean())
    }

  def sassSettingsIn(conf: Configuration): Seq[Setting[_]] = {
    inConfig(conf)(Seq(
      prettyPrint                  :=  false,
      includeFilter in sass        :=  "*.sass" || "*.scss",
      excludeFilter in sass        :=  (".*" - ".") || HiddenFileFilter,
      sassVersion in sass          :=  SassVersion.Sass332,
      sassOutputStyle in sass      :=  'nested,
      useCommandLine in sass       :=  false,
      sourceDirectory in sass      <<= (sourceDirectory in conf),
      sourceDirectories in sass    <<= (sourceDirectory in (conf, sass)) { Seq(_) },
      unmanagedSources in sass     <<= unmanagedSourcesTask,
      resourceManaged in sass      <<= (resourceManaged in conf),
      templateProperties           :=  new Properties,
      downloadDirectory            <<= (target in conf) { _ / "sbt-sass" / "downloads" },
      filenameSuffix               := "",
      sourceGraph                  <<= sourceGraphTask,
      sources in sass              <<= watchSourcesTask,
      watchSources in sass         <<= watchSourcesTask,
      clean in sass                <<= cleanTask,
      sass                         <<= compileTask
    )) ++ Seq(
      cleanFiles                   <+=  (resourceManaged in sass in conf),
      watchSources                 <++= (watchSources in sass in conf)
    )
  }

  def sassSettings: Seq[Setting[_]] =
    sassSettingsIn(Compile) ++
    sassSettingsIn(Test)

}
