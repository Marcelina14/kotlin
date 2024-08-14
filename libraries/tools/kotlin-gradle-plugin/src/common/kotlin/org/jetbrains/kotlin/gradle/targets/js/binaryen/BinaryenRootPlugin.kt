/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js.binaryen

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.BasePlugin
import org.jetbrains.kotlin.gradle.plugin.internal.configurationTimePropertiesAccessor
import org.jetbrains.kotlin.gradle.plugin.internal.usedAtConfigurationTime
import org.jetbrains.kotlin.gradle.plugin.variantImplementationFactory
import org.jetbrains.kotlin.gradle.targets.js.MultiplePluginDeclarationDetector
import org.jetbrains.kotlin.gradle.targets.js.binaryen.BinaryenPlatform.Companion.parseBinaryenPlatform
import org.jetbrains.kotlin.gradle.targets.js.binaryen.BinaryenRootExtension.Companion.EXTENSION_NAME
import org.jetbrains.kotlin.gradle.targets.js.nodejs.UnameExecutor
import org.jetbrains.kotlin.gradle.tasks.CleanDataTask
import org.jetbrains.kotlin.gradle.tasks.registerTask
import org.jetbrains.kotlin.gradle.utils.castIsolatedKotlinPluginClassLoaderAware
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toLowerCaseAsciiOnly

open class BinaryenRootPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        MultiplePluginDeclarationDetector.detect(project)

        project.plugins.apply(BasePlugin::class.java)

        check(project == project.rootProject) {
            "BinaryenRootPlugin can be applied only to root project"
        }

        val settings = project.extensions.create(EXTENSION_NAME, BinaryenRootExtension::class.java, project)

        addPlatform(project, settings)

        project.registerTask<BinaryenSetupTask>(BinaryenSetupTask.NAME) {
            it.group = TASKS_GROUP_NAME
            it.description = "Download and install a binaryen"
            it.configuration = project.provider {
                project.configurations.detachedConfiguration(project.dependencies.create(it.ivyDependency))
                    .also { conf -> conf.isTransitive = false }
            }
        }

        project.registerTask<CleanDataTask>("binaryen" + CleanDataTask.NAME_SUFFIX) {
            it.cleanableStoreProvider = project.provider { settings.requireConfigured().cleanableStore }
            it.group = TASKS_GROUP_NAME
            it.description = "Clean unused local binaryen version"
        }
    }

    private fun addPlatform(project: Project, extension: BinaryenRootExtension) {
        val uname = project.variantImplementationFactory<UnameExecutor.UnameExecutorVariantFactory>()
            .getInstance(project)
            .unameExecResult

        extension.platform.value(
            project.providers.systemProperty("os.name")
                .usedAtConfigurationTime(project.configurationTimePropertiesAccessor)
                .zip(
                    project.providers.systemProperty("os.arch")
                        .usedAtConfigurationTime(project.configurationTimePropertiesAccessor)
                ) { name, arch ->
                    parseBinaryenPlatform(name.toLowerCaseAsciiOnly(), arch, uname)
                }
        ).disallowChanges()
    }

    companion object {
        const val TASKS_GROUP_NAME: String = "binaryen"

        fun apply(rootProject: Project): BinaryenRootExtension {
            check(rootProject == rootProject.rootProject)
            rootProject.plugins.apply(BinaryenRootPlugin::class.java)
            return rootProject.extensions.getByName(EXTENSION_NAME) as BinaryenRootExtension
        }

        val Project.kotlinBinaryenExtension: BinaryenRootExtension
            get() = extensions.getByName(EXTENSION_NAME).castIsolatedKotlinPluginClassLoaderAware()
    }
}
