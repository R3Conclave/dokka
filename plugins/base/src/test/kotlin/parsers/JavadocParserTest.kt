package parsers

import com.jetbrains.rd.util.first
import org.jetbrains.dokka.base.testApi.testRunner.BaseAbstractTest
import org.jetbrains.dokka.model.DEnum
import org.jetbrains.dokka.model.DModule
import org.jetbrains.dokka.model.doc.CodeBlock
import org.jetbrains.dokka.model.doc.CodeInline
import org.jetbrains.dokka.model.doc.P
import org.jetbrains.dokka.model.doc.Pre
import org.jetbrains.dokka.model.doc.Text
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import utils.*

class JavadocParserTest : BaseAbstractTest() {

    private val configuration = dokkaConfiguration {
        sourceSets {
            sourceSet {
                sourceRoots = listOf("src/")
                analysisPlatform = "jvm"
            }
        }
    }

    private fun performJavadocTest(testOperation: (DModule) -> Unit) {
        val configuration = dokkaConfiguration {
            sourceSets {
                sourceSet {
                    sourceRoots = listOf("src/main/java")
                }
            }
        }

        testInline(
            """
            |/src/main/java/sample/Date2.java
            |
            |package docs
            |/**
            | * class level docs
            | */
            |public enum AnEnumType {
            |    /**
            |     * content being refreshed, which can be a result of
            |     * invalidation, refresh that may contain content updates, or the initial load.
            |     */
            |    REFRESH
            |}
            """.trimIndent(),
            configuration
        ) {
            documentablesMergingStage = testOperation
        }
    }

    @Test
    fun `correctly parsed list`() {
        performJavadocTest { module ->
            val docs = (module.packages.single().classlikes.single() as DEnum).entries.single().documentation.values.single().children.single().root.text()
            assertEquals("content being refreshed, which can be a result of invalidation, refresh that may contain content updates, or the initial load.", docs.trimEnd())
        }
    }

    @Test
    fun `code tag`() {
        val source = """
            |/src/main/kotlin/test/Test.java
            |package example
            |
            | /**
            | * Identifies calls to {@code assertThat}.
            | *
            | * {@code
            | * Set<String> s;
            | * System.out.println("s1 = " + s);
            | * }
            | * <pre>{@code
            | * Set<String> s2;
            | * System.out
            | *         .println("s2 = " + s2);
            | * }</pre>
            | * 
            | */
            | public class Test {}
            """.trimIndent()
        testInline(
            source,
            configuration,
        ) {
            documentablesCreationStage = { modules ->
                val docs = modules.first().packages.first().classlikes.single().documentation.first().value
                val root = docs.children.first().root

                kotlin.test.assertEquals(
                    listOf(
                        Text(body = "Identifies calls to "),
                        CodeInline(children = listOf(Text(body = "assertThat"))),
                        Text(body = ". "),
                        CodeInline(children = listOf(Text(body = "\nSet<String> s;\nSystem.out.println(\"s1 = \" + s);\n")))
                    ),
                    root.children[0].children
                )
                kotlin.test.assertEquals(
                    CodeBlock(children = listOf(Text(body = "\nSet<String> s2;\nSystem.out\n        .println(\"s2 = \" + s2);\n"))),
                    root.children[1]
                )
            }
        }
    }

    @Test
    fun `literal tag`() {
        val source = """
            |/src/main/kotlin/test/Test.java
            |package example
            |
            | /**
            | * An example of using the literal tag
            | * {@literal @}Entity
            | * public class User {}
            | */
            | public class Test {}
            """.trimIndent()
        testInline(
            source,
            configuration,
        ) {
            documentablesCreationStage = { modules ->
                val docs = modules.first().packages.first().classlikes.single().documentation.first().value
                val root = docs.children.first().root

                kotlin.test.assertEquals(
                    listOf(
                        Text(body = "An example of using the literal tag "),
                        Text(body = "@"),
                        Text(body = "Entity public class User {}"),
                    ),
                    root.children.first().children
                )
            }
        }
    }

    @Test
    fun `literal tag nested under pre tag`() {
        val source = """
            |/src/main/kotlin/test/Test.java
            |package example
            |
            | /**
            | * An example of using the literal tag
            | * <pre>
            | * {@literal @}Entity
            | * public class User {}
            | * </pre>
            | */
            | public class Test  {}
            """.trimIndent()
        testInline(
            source,
            configuration,
        ) {
            documentablesCreationStage = { modules ->
                val docs = modules.first().packages.first().classlikes.single().documentation.first().value
                val root = docs.children.first().root

                kotlin.test.assertEquals(
                    listOf(
                        P(children = listOf(Text(body = "An example of using the literal tag "))),
                        Pre(children =
                            listOf(
                                Text(body = "@"),
                                Text(body = "Entity\npublic class User {}\n")
                            )
                        )
                    ),
                    root.children
                )
            }
        }
    }

    @Test
    fun `literal tag containing angle brackets`() {
        val source = """
            |/src/main/kotlin/test/Test.java
            |package example
            |
            | /**
            | * An example of using the literal tag
            | * {@literal a<B>c}
            | */
            | public class Test  {}
            """.trimIndent()
        testInline(
            source,
            configuration,
        ) {
            documentablesCreationStage = { modules ->
                val docs = modules.first().packages.first().classlikes.single().documentation.first().value
                val root = docs.children.first().root

                kotlin.test.assertEquals(
                    listOf(
                        P(children = listOf(
                            Text(body = "An example of using the literal tag "),
                            Text(body = "a<B>c")
                        )),
                    ),
                    root.children
                )
            }
        }
    }
}
