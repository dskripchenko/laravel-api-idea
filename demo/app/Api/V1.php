<?php

declare(strict_types=1);

namespace Demo\Api;

use Demo\Api\Controllers\OrderController;
use Dskripchenko\LaravelApi\Components\BaseApi;

/**
 * Demo API v1
 *
 * The route map. To the IDE this is a nested array of strings; at runtime it is
 * the only thing tying a URL to a method.
 */
class V1 extends BaseApi
{
    public static function getMethods(): array
    {
        return [
            'controllers' => [
                'order' => [
                    'controller' => OrderController::class,
                    'actions' => [
                        // Each of these carries a gutter arrow to the method it
                        // routes — except the last one, which has nothing to
                        // point at.
                        'create' => ['action' => 'store', 'method' => ['post']],
                        'show' => ['action' => 'show', 'method' => ['get']],

                        // The method was renamed and the map was not. At runtime
                        // this answers 404 — the same 404 as a mistyped URL.
                        'export' => ['action' => 'exportOrders', 'method' => ['get']],
                    ],
                ],
            ],
        ];
    }

    public static function getOpenApiTemplates(): array
    {
        return [
            'OrderResponse' => [
                'id' => 'integer!',
                'title' => 'string!',
                'customer' => '@Customer',
            ],
            'Customer' => [
                'id' => 'integer!',
                'email' => 'string(email)',
            ],
            'ValidationError' => [
                'message' => 'string!',
            ],
        ];
    }

    public static function getOpenApiSecurityDefinitions(): array
    {
        return [
            'BearerAuth' => [
                'type' => 'apiKey',
                'name' => 'Authorization',
                'in' => 'header',
            ],
        ];
    }
}
