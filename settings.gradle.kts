/*
 * ShizuCallRecorder: FOSS Call recording powered through ADB/Shizuku!
 *  Copyright (C) 2026-05-10 15 h 58 kitsumed (Med)
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    // Disable foojay as required by https://codeberg.org/IzzyOnDroid/repodata/issues/266 and https://gitlab.com/fdroid/rfp/-/work_items/4032
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        run {
            val token = providers.environmentVariable("N2K02_PACKAGES_TOKEN").orNull
            require(!token.isNullOrBlank()) { "N2K02_PACKAGES_TOKEN is required" }
            listOf("n2k02theme" to "theme", "n2k02updater" to "updater").forEach { (repo, artifact) ->
                maven {
                    name = "N2K02_" + artifact
                    url = uri("https://maven.pkg.github.com/N2K02/$repo")
                    credentials {
                        username = "N2K02"
                        password = token
                    }
                    content { includeModule("dev.n2k02", artifact) }
                }
            }
        }
    }
}

rootProject.name = "ShizuCallRecorder"
include(":app")
