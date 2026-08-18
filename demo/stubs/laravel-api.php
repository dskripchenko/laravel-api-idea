<?php

/**
 * Just enough of dskripchenko/laravel-api for the IDE to recognise this as a
 * project that uses it. The plugin keys off `BaseApi` being on the classpath —
 * without it nothing fires, because `@input` and `@output` are ordinary words
 * in any other project.
 *
 * A stub rather than a real `composer install`: the demo exists to be
 * photographed, not to run.
 */

namespace Dskripchenko\LaravelApi\Components;

abstract class BaseApi
{
    public static function getMethods(): array
    {
        return [];
    }

    public static function getOpenApiTemplates(): array
    {
        return [];
    }

    public static function getOpenApiSecurityDefinitions(): array
    {
        return [];
    }
}

namespace Dskripchenko\LaravelApi\Controllers;

class ApiController
{
    public function success($payload = [])
    {
        return $payload;
    }
}
