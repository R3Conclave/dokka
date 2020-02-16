package org.jetbrains.dokka.base

import org.jetbrains.dokka.CoreExtensions
import org.jetbrains.dokka.base.transformers.descriptors.DefaultDescriptorToDocumentationTranslator
import org.jetbrains.dokka.base.transformers.documentables.DefaultDocumentableMerger
import org.jetbrains.dokka.base.transformers.documentables.DefaultDocumentablesToPageTranslator
import org.jetbrains.dokka.plugability.DokkaPlugin

class DokkaBase: DokkaPlugin() {
    val descriptorToDocumentationTranslator by extending(isFallback = true) {
        CoreExtensions.descriptorToDocumentationTranslator providing ::DefaultDescriptorToDocumentationTranslator
    }

    val documentableMerger by extending(isFallback = true) {
        CoreExtensions.documentableMerger with DefaultDocumentableMerger
    }

    val documentablesToPageTranslator by extending(isFallback = true) {
        CoreExtensions.documentablesToPageTranslator with DefaultDocumentablesToPageTranslator
    }
}