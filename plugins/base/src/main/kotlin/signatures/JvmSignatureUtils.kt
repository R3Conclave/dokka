package org.jetbrains.dokka.base.signatures

import org.jetbrains.dokka.base.translators.documentables.PageContentBuilder
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.model.*
import org.jetbrains.dokka.model.properties.WithExtraProperties
import org.jetbrains.dokka.pages.*
import org.jetbrains.dokka.DokkaConfiguration.DokkaSourceSet
import org.jetbrains.dokka.base.signatures.KotlinSignatureUtils.drisOfAllNestedBounds
import org.jetbrains.dokka.model.AnnotationTarget
import org.jetbrains.dokka.model.doc.DocumentationNode

interface JvmSignatureUtils {

    fun PageContentBuilder.DocumentableContentBuilder.annotationsBlock(d: AnnotationTarget)

    fun PageContentBuilder.DocumentableContentBuilder.annotationsInline(d: AnnotationTarget)

    fun <T : Documentable> WithExtraProperties<T>.modifiers(): SourceSetDependent<Set<ExtraModifiers>>

    fun Collection<ExtraModifiers>.toSignatureString(): String =
        joinToString("") { it.name.toLowerCase() + " " }

    fun <T : AnnotationTarget> WithExtraProperties<T>.annotations(): SourceSetDependent<List<Annotations.Annotation>> =
        extra[Annotations]?.directAnnotations ?: emptyMap()

    @Suppress("UNCHECKED_CAST")
    operator fun <T : Iterable<*>> SourceSetDependent<T>.plus(other: SourceSetDependent<T>): SourceSetDependent<T> =
        LinkedHashMap(this).apply {
            for ((k, v) in other) {
                put(k, get(k).let { if (it != null) (it + v) as T else v })
            }
        }

    fun DProperty.annotations(): SourceSetDependent<List<Annotations.Annotation>> =
        (extra[Annotations]?.directAnnotations ?: emptyMap()) +
        (getter?.annotations() ?: emptyMap()).mapValues { it.value.map { it.copy( scope = Annotations.AnnotationScope.GETTER) } } +
        (setter?.annotations() ?: emptyMap()).mapValues { it.value.map { it.copy( scope = Annotations.AnnotationScope.SETTER) } }

    private fun PageContentBuilder.DocumentableContentBuilder.annotations(
        d: AnnotationTarget,
        ignored: Set<Annotations.Annotation>,
        styles: Set<Style>,
        operation: PageContentBuilder.DocumentableContentBuilder.(Annotations.Annotation) -> Unit
    ): Unit = when (d) {
        is DFunction -> d.annotations()
        is DProperty -> d.annotations()
        is DClass -> d.annotations()
        is DInterface -> d.annotations()
        is DObject -> d.annotations()
        is DEnum -> d.annotations()
        is DAnnotation -> d.annotations()
        is DTypeParameter -> d.annotations()
        is DEnumEntry -> d.annotations()
        is DTypeAlias -> d.annotations()
        is DParameter -> d.annotations()
        is TypeParameter -> d.annotations()
        is GenericTypeConstructor -> d.annotations()
        is FunctionalTypeConstructor -> d.annotations()
        is JavaObject -> d.annotations()
        else -> null
    }?.let {
        it.entries.forEach {
            it.value.filter { it !in ignored && it.mustBeDocumented }.takeIf { it.isNotEmpty() }?.let { annotations ->
                group(sourceSets = setOf(it.key), styles = styles, kind = ContentKind.Annotations) {
                    annotations.forEach {
                        operation(it)
                    }
                }
            }
        }
    } ?: Unit

    fun PageContentBuilder.DocumentableContentBuilder.toSignatureString(
        a: Annotations.Annotation,
        renderAtStrategy: AtStrategy,
        listBrackets: Pair<Char, Char>,
        classExtension: String
    ) {

        when (renderAtStrategy) {
            is All, is OnlyOnce -> {
                text("@")
                when(a.scope) {
                    Annotations.AnnotationScope.GETTER -> text("get:")
                    Annotations.AnnotationScope.SETTER -> text("set:")
                }
            }
            is Never -> Unit
        }
        link(a.dri.classNames!!, a.dri)
        val isNoWrappedBrackets = a.params.entries.isEmpty() && renderAtStrategy is OnlyOnce
        listParams(
            a.params.entries,
            if (isNoWrappedBrackets) null else Pair('(', ')')
        ) {
            text(it.key + " = ")
            when (renderAtStrategy) {
                is All -> All
                is Never, is OnlyOnce -> Never
            }.let { strategy ->
                valueToSignature(it.value, strategy, listBrackets, classExtension)
            }
        }
    }

    private fun PageContentBuilder.DocumentableContentBuilder.valueToSignature(
        a: AnnotationParameterValue,
        renderAtStrategy: AtStrategy,
        listBrackets: Pair<Char, Char>,
        classExtension: String
    ): Unit = when (a) {
        is AnnotationValue -> toSignatureString(a.annotation, renderAtStrategy, listBrackets, classExtension)
        is ArrayValue -> {
            listParams(a.value, listBrackets) { valueToSignature(it, renderAtStrategy, listBrackets, classExtension) }
        }
        is EnumValue -> link(a.enumName, a.enumDri)
        is ClassValue -> link(a.className + classExtension, a.classDRI)
        is StringValue -> group(styles = setOf(TextStyle.Breakable)) { text( "\"${a.text()}\"") }
        is LiteralValue -> group(styles = setOf(TextStyle.Breakable)) { text(a.text()) }
    }

    private fun<T> PageContentBuilder.DocumentableContentBuilder.listParams(
        params: Collection<T>,
        listBrackets: Pair<Char, Char>?,
        outFn: PageContentBuilder.DocumentableContentBuilder.(T) -> Unit
    ) {
        listBrackets?.let{ text(it.first.toString()) }
        params.forEachIndexed { i, it ->
            group(styles = setOf(TextStyle.BreakableAfter)) {
                this.outFn(it)
                if (i != params.size - 1) text(", ")
            }
        }
        listBrackets?.let{ text(it.second.toString()) }
    }

    fun PageContentBuilder.DocumentableContentBuilder.annotationsBlockWithIgnored(
        d: AnnotationTarget,
        ignored: Set<Annotations.Annotation>,
        renderAtStrategy: AtStrategy,
        listBrackets: Pair<Char, Char>,
        classExtension: String
    ) {
        annotations(d, ignored, setOf(TextStyle.Block)) {
            group {
                toSignatureString(it, renderAtStrategy, listBrackets, classExtension)
            }
        }
    }

    fun PageContentBuilder.DocumentableContentBuilder.annotationsInlineWithIgnored(
        d: AnnotationTarget,
        ignored: Set<Annotations.Annotation>,
        renderAtStrategy: AtStrategy,
        listBrackets: Pair<Char, Char>,
        classExtension: String
    ) {
        annotations(d, ignored, setOf(TextStyle.Span)) {
            toSignatureString(it, renderAtStrategy, listBrackets, classExtension)
            text(Typography.nbsp.toString())
        }
    }

    fun <T : Documentable> WithExtraProperties<T>.stylesIfDeprecated(sourceSetData: DokkaSourceSet): Set<TextStyle> =
        if (extra[Annotations]?.directAnnotations?.get(sourceSetData)?.any {
                it.dri == DRI("kotlin", "Deprecated")
                        || it.dri == DRI("java.lang", "Deprecated")
            } == true) setOf(TextStyle.Strikethrough) else emptySet()

    infix fun DFunction.uses(t: DTypeParameter): Boolean {
        val allDris: List<DRI> = (listOfNotNull(receiver?.dri, *receiver?.type?.drisOfAllNestedBounds?.toTypedArray() ?: emptyArray()) +
                parameters.flatMap { listOf(it.dri) + it.type.drisOfAllNestedBounds })
        return t.dri in allDris
    }
}

sealed class AtStrategy
object All : AtStrategy()
object OnlyOnce : AtStrategy()
object Never : AtStrategy()
