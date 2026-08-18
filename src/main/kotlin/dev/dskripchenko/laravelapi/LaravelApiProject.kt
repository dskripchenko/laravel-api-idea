package dev.dskripchenko.laravelapi

import com.intellij.openapi.project.Project
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.jetbrains.php.PhpIndex

/**
 * Whether this project actually uses the package.
 *
 * Without the check the plugin would colour `@input` and `@output` in every PHP
 * project on the machine — those are ordinary words, and other tools use them
 * for other things. Nothing here fires unless `BaseApi` is on the project's
 * classpath.
 */
object LaravelApiProject {

    private const val BASE_API = "\\Dskripchenko\\LaravelApi\\Components\\BaseApi"

    fun isEnabled(project: Project): Boolean =
        CachedValuesManager.getManager(project).getCachedValue(project) {
            val present = PhpIndex.getInstance(project).getClassesByFQN(BASE_API).isNotEmpty()

            // Invalidated on any PHP change: the answer flips exactly once, when
            // composer install brings the package in, and caching it per
            // modification count keeps the index lookup off the typing path.
            CachedValueProvider.Result.create(present, PsiModificationTracker.MODIFICATION_COUNT)
        }
}
