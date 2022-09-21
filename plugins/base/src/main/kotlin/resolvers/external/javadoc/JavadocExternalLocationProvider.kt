package org.jetbrains.dokka.base.resolvers.external.javadoc

import org.jetbrains.dokka.base.resolvers.external.DefaultExternalLocationProvider
import org.jetbrains.dokka.base.resolvers.shared.ExternalDocumentation
import org.jetbrains.dokka.links.*
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.utilities.htmlEscape

open class JavadocExternalLocationProvider(
        externalDocumentation: ExternalDocumentation,
        val brackets: String,
        val separator: String,
        dokkaContext: DokkaContext
) : DefaultExternalLocationProvider(externalDocumentation, ".html", dokkaContext) {

    override fun DRI.constructPath(): String {
        val packageLink = packageName?.replace(".", "/")
        val modulePart = packageName?.let { packageName ->
            externalDocumentation.packageList.moduleFor(packageName)?.let {
                if (it.isNotBlank())
                    "$it/"
                else
                    ""
            }
        }.orEmpty()

        val docWithModule = docURL + modulePart

        if (classNames == null) {
            return "$docWithModule$packageLink/package-summary$extension".htmlEscape()
        }

        // in Kotlin DRI of enum entry is not callable
        if (DRIExtraContainer(extra)[EnumEntryDRIExtra] != null) {
            val (classSplit, enumEntityAnchor) = if (callable == null) {
                val lastIndex = classNames?.lastIndexOf(".") ?: 0
                classNames?.substring(0, lastIndex) to classNames?.substring(lastIndex + 1)
            } else
                classNames to callable?.name

            val classLink =
                if (packageLink == null) "${classSplit}$extension" else "$packageLink/${classSplit}$extension"
            return "$docWithModule$classLink#$enumEntityAnchor".htmlEscape()
        }

        val classLink = if (packageLink == null) "${classNames}$extension" else "$packageLink/${classNames}$extension"
        val callableChecked = callable ?: return "$docWithModule$classLink".htmlEscape()

        return ("$docWithModule$classLink#" + anchorPart(callableChecked)).htmlEscape()
    }

    protected open fun anchorPart(callable: Callable) = callable.name +
            "${brackets.first()}" +
            callable.params.joinToString(separator) +
            "${brackets.last()}"

}
