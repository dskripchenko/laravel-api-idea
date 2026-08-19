package dev.dskripchenko.laravelapi.routes

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Naming the version an endpoint answers under.
 *
 * The map and the version live in different classes, and an endpoint list that
 * shows only the map is a list of names that repeat: two versions declaring
 * `print-form.create` are two different endpoints spelled identically.
 */
class ApiVersionLookupTest : BasePlatformTestCase() {

    private fun addPackage() {
        myFixture.addFileToProject(
            "vendor/dskripchenko/laravel-api/src/Components/BaseApi.php",
            """
            <?php
            namespace Dskripchenko\LaravelApi\Components;
            abstract class BaseApi {}
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "vendor/dskripchenko/laravel-api/src/Components/BaseModule.php",
            """
            <?php
            namespace Dskripchenko\LaravelApi\Components;
            class BaseModule
            {
                public function getApiVersionList(): array
                {
                    return [];
                }
            }
            """.trimIndent()
        )
    }

    private fun addApi(namespace: String, className: String, controller: String, action: String) {
        myFixture.addFileToProject(
            "app/Api/$className.php",
            """
            <?php
            namespace $namespace;

            use Dskripchenko\LaravelApi\Components\BaseApi;

            class $className extends BaseApi
            {
                public static function getMethods(): array
                {
                    return [
                        'controllers' => [
                            '$controller' => [
                                'controller' => \App\Api\Controllers\ItemController::class,
                                'actions' => ['$action'],
                            ],
                        ],
                    ];
                }
            }
            """.trimIndent()
        )
    }

    fun `test reads the version a module maps a class to`() {
        addPackage()
        addApi("App\\Api", "IntegrationV1", "print-form", "create")

        myFixture.addFileToProject(
            "app/Api/Module.php",
            """
            <?php
            namespace App\Api;

            use Dskripchenko\LaravelApi\Components\BaseModule;

            class Module extends BaseModule
            {
                public function getApiVersionList(): array
                {
                    return [
                        ...parent::getApiVersionList(),
                        'integration' => IntegrationV1::class,
                    ];
                }
            }
            """.trimIndent()
        )

        val versions = ApiVersionLookup.versionsByApi(project)

        assertEquals(listOf("integration"), versions["\\App\\Api\\IntegrationV1"])
    }

    fun `test an entry carries the version it was declared under`() {
        addPackage()
        addApi("App\\Api", "DraftsV1", "form", "show")

        myFixture.addFileToProject(
            "app/Api/Module.php",
            """
            <?php
            namespace App\Api;

            use Dskripchenko\LaravelApi\Components\BaseModule;

            class Module extends BaseModule
            {
                public function getApiVersionList(): array
                {
                    return ['drafts' => DraftsV1::class];
                }
            }
            """.trimIndent()
        )

        val versions = ApiVersionLookup.versionsByApi(project)
        val entry = RouteMapLookup.allActions(project).single()

        assertEquals("\\App\\Api\\DraftsV1", entry.apiFqn)
        assertEquals(listOf("drafts"), ApiVersionLookup.versionsOf(entry, versions))
    }

    fun `test a class exposed twice keeps both names`() {
        addPackage()
        addApi("App\\Api", "PublicV1", "order", "list")

        myFixture.addFileToProject(
            "app/Api/Module.php",
            """
            <?php
            namespace App\Api;

            use Dskripchenko\LaravelApi\Components\BaseModule;

            class Module extends BaseModule
            {
                public function getApiVersionList(): array
                {
                    return [
                        'v1' => PublicV1::class,
                        'current' => PublicV1::class,
                    ];
                }
            }
            """.trimIndent()
        )

        val entry = RouteMapLookup.allActions(project).single()

        // Two names, two lines in the list: `v1.order.list` and
        // `current.order.list` are two URLs, and a reader looking for either
        // has to find it spelled the way it is called.
        val names = ApiVersionLookup.versionsOf(entry, ApiVersionLookup.versionsByApi(project))

        assertEquals(listOf("v1", "current"), names)
        assertEquals(
            listOf("v1.order.list  →  list()", "current.order.list  →  list()"),
            names.map { EndpointLabel.of(entry, it) },
        )
    }

    /**
     * `AdminApiModule` assembles its panel versions from a registry resolved at
     * runtime. Nothing static can know those names, and a made-up one would be
     * worse than an absent one: the whole point of the column is to be trusted.
     */
    fun `test says nothing when the module builds its list dynamically`() {
        addPackage()
        addApi("App\\Api", "PanelV1", "user", "list")

        myFixture.addFileToProject(
            "app/Api/Module.php",
            """
            <?php
            namespace App\Api;

            use Dskripchenko\LaravelApi\Components\BaseModule;

            class Module extends BaseModule
            {
                public function getApiVersionList(): array
                {
                    ${'$'}versions = [];
                    foreach (app(Registry::class)->all() as ${'$'}panel) {
                        ${'$'}versions[${'$'}panel->key()] = PanelV1::class;
                    }

                    return ${'$'}versions;
                }
            }
            """.trimIndent()
        )

        val entry = RouteMapLookup.allActions(project).single()

        assertTrue(ApiVersionLookup.versionsOf(entry, ApiVersionLookup.versionsByApi(project)).isEmpty())
    }

    fun `test a module naming the class as a string is read too`() {
        addPackage()
        addApi("App\\Api", "LegacyV1", "report", "build")

        myFixture.addFileToProject(
            "app/Api/Module.php",
            """
            <?php
            namespace App\Api;

            use Dskripchenko\LaravelApi\Components\BaseModule;

            class Module extends BaseModule
            {
                public function getApiVersionList(): array
                {
                    return ['legacy' => 'App\Api\LegacyV1'];
                }
            }
            """.trimIndent()
        )

        val entry = RouteMapLookup.allActions(project).single()

        assertEquals(listOf("legacy"), ApiVersionLookup.versionsOf(entry, ApiVersionLookup.versionsByApi(project)))
    }

    fun `test the label leads with the version`() {
        addPackage()
        addApi("App\\Api", "IntegrationV1", "print-form", "create")

        myFixture.addFileToProject(
            "app/Api/Module.php",
            """
            <?php
            namespace App\Api;

            use Dskripchenko\LaravelApi\Components\BaseModule;

            class Module extends BaseModule
            {
                public function getApiVersionList(): array
                {
                    return ['integration' => IntegrationV1::class];
                }
            }
            """.trimIndent()
        )

        val entry = RouteMapLookup.allActions(project).single()
        val version = ApiVersionLookup.versionsOf(entry, ApiVersionLookup.versionsByApi(project)).single()
        val label = EndpointLabel.of(entry, version)

        // The name the package registers the route under, minus its `api.`
        // prefix — one spelling, whether it is searched for here or in a log.
        assertTrue("the version has to lead, dotted: $label", label.startsWith("integration.print-form.create"))
        assertTrue(label.endsWith("create()"))
    }

    fun `test the label drops the prefix rather than inventing one`() {
        addPackage()
        addApi("App\\Api", "OrphanV1", "order", "list")

        val entry = RouteMapLookup.allActions(project).single()
        val label = EndpointLabel.of(
            entry,
            ApiVersionLookup.versionsOf(entry, ApiVersionLookup.versionsByApi(project)).firstOrNull(),
        )

        assertTrue("no module names this class: $label", label.startsWith("order.list"))
    }
}
