organization := "com.updateimpact"

name := "updateimpact-sbt-plugin"

version := "2.1.2.1"

sbtPlugin := true

libraryDependencies += "com.updateimpact" % "updateimpact-plugin-common" % "1.3.2"

publishTo := {
    val corporateRepo = "http://toucan.simplesys.lan/"
    if (isSnapshot.value)
        Some("snapshots" at corporateRepo + "artifactory/libs-snapshot-local")
    else
        Some("releases" at corporateRepo + "artifactory/libs-release-local")
}
credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")

publishMavenStyle := true

enablePlugins(BuildInfoPlugin)

buildInfoPackage := "com.updateimpact"

buildInfoObject := "UpdateimpactSbtBuildInfo"

// Testing

ScriptedPlugin.projectSettings

scriptedLaunchOpts := {
    scriptedLaunchOpts.value ++
      Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
}

scriptedBufferLog := false

scriptedRun := (scriptedRun dependsOn publishLocal).value
