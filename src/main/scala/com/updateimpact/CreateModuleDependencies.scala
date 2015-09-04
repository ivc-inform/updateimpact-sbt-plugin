package com.updateimpact

import com.updateimpact.report.{Dependency, ModuleDependencies, DependencyId}
import org.apache.ivy.Ivy
import org.apache.ivy.core.module.descriptor.ModuleDescriptor
import org.apache.ivy.core.module.id.ModuleRevisionId
import sbt.Keys._
import sbt.{Configurations, Configuration, ModuleID, Logger}
import scala.collection.JavaConversions._
import org.apache.ivy.core.module.descriptor.{Configuration => IvyCfg}

class CreateModuleDependencies(ivy: Ivy, log: Logger, rootMd: ModuleDescriptor,
  projectIdToIvyId: Map[ModuleID, ModuleRevisionId]) {

  private val fdd = new FindDependencyDescriptors(ivy)
  private val rootId = toDepId(rootMd.getModuleRevisionId)

  def forClasspath(cfg: Configuration, cpCfg: Configuration, cp: Classpath): ModuleDependencies = {

    val (classpathDepIds, depsWithoutModuleIDs) = depIdsFromClasspath(cp)

    val classpathDepVersions = classpathDepIds.map { id => OrgAndName(id) -> id.getVersion }.toMap

    val classpathIdToDepIds = findClasspathIdToDepIds(classpathDepIds, cpCfg, classpathDepVersions)

    val idToDepsWithLocal = classpathIdToDepIds + (rootId -> (classpathIdToDepIds.getOrElse(rootId, Nil) ++ depsWithoutModuleIDs))

    // classpathIdToDepIds only contains entries for dependencies which are on the classpath. However, the dependencies
    // of each dependency may include items not included in the classpath (e.g. evicted version). We need to add empty
    // entries into the map for them too.
    val idToDepsWithMissing = addMissingEmptyDeps(idToDepsWithLocal)

    val deps = idToDepsWithMissing.map { case (id, idDeps) =>
      val evictedBy = classpathDepVersions.get(OrgAndName(id)) match {
        case None => "exclude" // no other version present on the classpath, dep must have been excluded
        case Some(v) if v != id.getVersion => v
        case _ => null // not evicted, versions match
      }

      new Dependency(id, evictedBy, false, idDeps)
    }

    new ModuleDependencies(rootId, cfg.name, deps)
  }

  private def depIdsFromClasspath(cp: Classpath) = {
    cp.foldLeft((List.empty[DependencyId], List.empty[DependencyId])) { case ((depIds, withoutModuleId), f) =>
      f.metadata.get(moduleID.key) match {
        case Some(mid) =>
          // If it's a project in this build, we need the special translation which adds the scala version suffix.
          val id = projectIdToIvyId.get(mid).map(toDepId).getOrElse(toDepId(mid))
          (id :: depIds, withoutModuleId)
        case None =>
          val depName = f.data.getName
          log.warn(s"Cannot find Ivy module ID for dependency $depName. Adding to the result as a top-level dependency")
          (depIds, new DependencyId("local", depName, "?", null, null) :: withoutModuleId)
      }
    }
  }

  private def findClasspathIdToDepIds(classpathDepIds: List[DependencyId], cpCfg: Configuration,
    classpathDepVersions: Map[OrgAndName, String]): Map[DependencyId, Seq[DependencyId]] = {

    val includedConfigs = getConfigsClosure(cpCfg.name, rootMd.getConfigurations)
    val includedConfigsWithoutOptional = includedConfigs - Configurations.Optional.name

    // If a dependency's version is specified using a range, we check if the chosen version is in that range and if
    // so, replace the version. Otherwise, it means that the dependency is evicted.
    def replaceVersionIfMatchesCp(id: DependencyId): DependencyId = {
      classpathDepVersions.get(OrgAndName(id)) match {
        case Some(v) if v != id.getVersion =>
          if (ivy.getSettings.getVersionMatcher.accept(
            ModuleRevisionId.newInstance("x", "x", id.getVersion),
            ModuleRevisionId.newInstance("x", "x", v)
          )) id.withVersion(v) else id

        case _ => id
      }
    }

    classpathDepIds.map { id =>
      id -> (fdd.forDependencyId(id) match {
        case None =>
          log.warn(s"Cannot get dependencies for module ${id.toStr}")
          Nil
        case Some(ds) =>
          val r = ds.filter { d =>
            // include optional deps only for the root
            val included = if (id == rootId) includedConfigs else includedConfigsWithoutOptional
            included.intersect(d.getModuleConfigurations.toSet).nonEmpty
          }.map(d => replaceVersionIfMatchesCp(toDepId(d.getDependencyRevisionId)))
          r
      })
    }.toMap
  }

  /**
   * Set of configurations closed under "extends" relation
   */
  private def getConfigsClosure(root: String, cfgs: Seq[IvyCfg]): Set[String] = {
    cfgs.find(_.getName == root) match {
      case None => Set()
      case Some(cfg) =>
        cfg.getExtends.foldLeft(Set(root)) { (acc, cfg) => acc ++ getConfigsClosure(cfg, cfgs) }
    }
  }

  private def addMissingEmptyDeps(depsMap: Map[DependencyId, Seq[DependencyId]]): Map[DependencyId, Seq[DependencyId]] = {
    var result = depsMap
    depsMap.values.flatten.toSet.foreach { (id: DependencyId) =>
      if (!result.containsKey(id)) result += id -> Nil
    }

    result
  }

  private case class OrgAndName(org: String, name: String)
  private object OrgAndName {
    def apply(id: DependencyId): OrgAndName = OrgAndName(id.getGroupId, id.getArtifactId)
  }

  private def toDepId(mrid: ModuleRevisionId): DependencyId = new DependencyId(mrid.getOrganisation, mrid.getName,
    mrid.getRevision, null, null)
  private def toDepId(mid: ModuleID): DependencyId = new DependencyId(mid.organization, mid.name, mid.revision, null, null)

  private implicit class RichDependencyId(id: DependencyId) {
    def toStr = s"${id.getGroupId}:${id.getArtifactId}:${id.getVersion}:${id.getType}:${id.getClassifier}"
    def withVersion(v: String) = new DependencyId(id.getGroupId, id.getArtifactId, v, id.getType, id.getClassifier)
  }
}