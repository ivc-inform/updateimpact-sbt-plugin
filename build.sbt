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

pomIncludeRepository := { _ => false }

pomExtra := (
  <scm>
      <url>git@gihub.com/updateimpact/updateimpact-sbt-plugin.git</url>
      <connection>scm:git:git@github.com/updateimpact/updateimpact-sbt-plugin.git</connection>
  </scm>
    <developers>
        <developer>
            <id>adamw</id>
            <name>Adam Warski</name>
            <url>http://www.warski.org</url>
        </developer>
    </developers>
  )

licenses := ("Apache2", new java.net.URL("http://www.apache.org/licenses/LICENSE-2.0.txt")) :: Nil

homepage := Some(new java.net.URL("http://updateimpact.com"))

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
