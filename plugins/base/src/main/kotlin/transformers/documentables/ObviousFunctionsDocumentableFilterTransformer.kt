package org.jetbrains.dokka.base.transformers.documentables

import org.jetbrains.dokka.model.*
import org.jetbrains.dokka.plugability.DokkaContext

class ObviousFunctionsDocumentableFilterTransformer(context: DokkaContext) : SuppressedByConditionDocumentableFilterTransformer(context) {
    override fun shouldBeSuppressed(d: Documentable): Boolean {
        return when (d) {
            is DFunction -> {
                context.configuration.suppressObviousFunctions && d.extra[ObviousMember] != null
            }
            is DProperty -> {
                val inherited = d.extra[InheritedMember]?.inheritedFrom?.map { "${it.value?.packageName.orEmpty()}.${it.value?.classNames.orEmpty()}" }
                context.configuration.suppressObviousFunctions &&
                        inherited?.any { it == "kotlin.Any" || it == "kotlin.Enum" || it == "kotlin.Throwable" } ?: false
            }
            else -> {
                false
            }
        }
    }
}