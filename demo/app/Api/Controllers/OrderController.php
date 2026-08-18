<?php

declare(strict_types=1);

namespace Demo\Api\Controllers;

use Dskripchenko\LaravelApi\Controllers\ApiController;

class OrderController extends ApiController
{
    /**
     * Create an order
     *
     * Everything on this page is coloured because the generator understands it.
     *
     * @input string $title Order title
     * @input string(email) $email Where to send the receipt
     * @input string $status Status [draft,paid,shipped]
     * @input object $address Delivery address
     * @input string $address.city City
     * @input array $items Line items
     * @input integer $items[].id Product id
     *
     * @output integer $id Order identifier
     * @output @Customer $customer Who ordered it
     *
     * @response 200 {OrderResponse}
     * @response 422 {ValidationError}
     *
     * @security BearerAuth
     *
     * @default $status draft
     */
    public function store()
    {
        return $this->success();
    }

    /**
     * The three mistakes this plugin exists for
     *
     * None of them stops the application from starting, and none of them is
     * visible in PHP: the generator drops what it cannot read, calls what it
     * does not recognise a string, and writes references to templates nobody
     * declared into a spec that still validates.
     *
     * @input datetime $placedAt When it was placed
     * @input string missingDollarSign The dollar is not optional
     *
     * @response 404 {TemplateNobodyDeclared}
     *
     * @security SchemeNobodyDeclared
     */
    public function show()
    {
        return $this->success();
    }

    /**
     * A method the route map no longer reaches
     *
     * Public, and nothing points at it — visible with the `--unrouted` check of
     * `api:lint`, and by the absence of a gutter arrow here.
     */
    public function legacyExport()
    {
        return $this->success();
    }
}
