package com.github.iguissouma.nxconsole.cli.config

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.json.JsonReadFeature
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.text.CharSequenceReader
import one.util.streamex.StreamEx

class NxConfig(text: CharSequence, val angularJsonFile: VirtualFile, project: Project) {

    val projects: List<NxProject>

    val defaultProject: NxProject?

    init {
        val angularCliFolder = angularJsonFile.parent
        val mapper = ObjectMapper(
            JsonFactory.builder()
                .configure(JsonReadFeature.ALLOW_JAVA_COMMENTS, true)
                .configure(JsonReadFeature.ALLOW_SINGLE_QUOTES, true)
                .configure(JsonReadFeature.ALLOW_MISSING_VALUES, true)
                .configure(JsonReadFeature.ALLOW_TRAILING_COMMA, true)
                .configure(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES, true)
                .build()
        )
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)
        val angularJson = mapper.readValue(CharSequenceReader(text), AngularJson::class.java)
        if (angularJson.projects.isNotEmpty()) {
            projects = angularJson.projects.map { (name, ngProjectJson) ->
                NxProjectImpl(name, ngProjectJson, angularCliFolder, project)
            }
            defaultProject = angularJson.defaultProject?.let { defaultProject ->
                projects.find { it.name == defaultProject }
            }
        } else {
            projects = angularJson.legacyApps.map { app ->
                NxLegacyProjectImpl(angularJson, app, angularCliFolder, project)
            }
            defaultProject = projects.firstOrNull()
        }
    }

    fun getProject(context: VirtualFile): NxProject? {
        return StreamEx.of(projects)
            .map { Pair(it, it.proximity(context)) }
            .filter { it.second >= 0 }
            .minByInt { it.second }
            .map { it.first }
            .orElse(null)
    }

    override fun toString(): String {
        return """
      | NxConfig {
      |   defaultProject: ${defaultProject?.name}
      |   projects: [
      |     ${projects.joinToString(",\n     ") { it.toString() }}
      |   ]
      | }
    """.trimMargin()
    }
}
