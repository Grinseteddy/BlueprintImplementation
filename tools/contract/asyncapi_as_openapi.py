#!/usr/bin/env python3
"""QS-15: an AsyncAPI spec's message payloads as a synthetic OpenAPI document, so that oasdiff can
judge their compatibility with its schema rules.

A message the service sends becomes a response (consumers read it); a message it receives becomes a
request body (producers write it). The component schemas are copied as they are; AsyncAPI's string
discriminator becomes OpenAPI's object form. Headers are not compared here.
Usage: asyncapi_as_openapi.py <asyncapi.yaml> <openapi.yaml>
"""
import sys

import yaml


def resolve(document, node):
    """Follows local $refs until a node without one."""
    while isinstance(node, dict) and "$ref" in node:
        target = document
        for part in node["$ref"].lstrip("#/").split("/"):
            target = target[part]
        node = target
    return node


def openapi_schemas(schemas):
    """The component schemas; a string discriminator becomes {propertyName: ...}."""
    def convert(node):
        if isinstance(node, dict):
            converted = {key: convert(value) for key, value in node.items()}
            if isinstance(converted.get("discriminator"), str):
                converted["discriminator"] = {"propertyName": converted["discriminator"]}
            return converted
        if isinstance(node, list):
            return [convert(value) for value in node]
        return node
    return convert(schemas)


def main(source, target):
    document = yaml.safe_load(open(source))
    paths = {}
    for name, operation in document["operations"].items():
        for reference in operation["messages"]:
            payload = {"$ref": resolve(document, reference)["payload"]["$ref"]}
            content = {"application/json": {"schema": payload}}
            if operation["action"] == "send":
                post = {"operationId": name,
                        "responses": {"200": {"description": "published", "content": content}}}
            else:
                post = {"operationId": name, "requestBody": {"required": True, "content": content},
                        "responses": {"200": {"description": "consumed"}}}
            paths[f"/{name}"] = {"post": post}
    synthetic = {
        "openapi": "3.1.0",
        "info": {"title": document["info"]["title"] + " (messages)",
                 "version": document["info"]["version"]},
        "paths": paths,
        "components": {"schemas": openapi_schemas(document["components"]["schemas"])},
    }
    yaml.safe_dump(synthetic, open(target, "w"), sort_keys=False, allow_unicode=True)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
