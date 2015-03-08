
import sbt.Keys._
import sbt._

import com.typesafe.sbt.SbtNativePackager.autoImport._
import spray.revolver.RevolverPlugin._

object Packaging {
  import com.typesafe.sbt.SbtNativePackager._

  val packagingSettings = Seq(
    name := Settings.appName,
    NativePackagerKeys.packageName := "waldoaufderspringe"
  
  ) ++ Seq(packageArchetype.java_application:_*) ++ buildSettings

}

object TopLevelBuild extends Build {


  lazy val root = Project (
    id = Settings.appName,
    base = file ("."),
    settings = Settings.buildSettings ++
      Packaging.packagingSettings ++
      Revolver.settings ++
      Seq (
        resolvers ++= Resolvers.allResolvers,
        libraryDependencies ++= Dependencies.allDependencies
      )
  )
}

