#!/usr/bin/env python3



from __future__ import annotations

import argparse
import json
import mimetypes
import math
import struct
from pathlib import Path
from urllib.parse import unquote, urlparse


JSON_CHUNK = 0x4E4F534A
BIN_CHUNK = 0x004E4942


def local_resource(base: Path, uri: str) -> Path:
    native_path = Path(unquote(uri))
    if native_path.is_absolute():
        candidate = native_path.resolve()
        base = base.resolve()
        if candidate != base and base not in candidate.parents:
            raise ValueError(f"absolute resource escapes the glTF directory: {uri}")
        return candidate
    parsed = urlparse(uri)
    if parsed.scheme or parsed.netloc or parsed.query or parsed.fragment:
        raise ValueError(f"external or decorated URI is not supported: {uri}")
    candidate = (base / unquote(parsed.path)).resolve()
    base = base.resolve()
    if candidate != base and base not in candidate.parents:
        raise ValueError(f"resource escapes the glTF directory: {uri}")
    return candidate


def align4(data: bytearray, padding: int = 0) -> None:
    data.extend(bytes([padding]) * ((-len(data)) & 3))


def normalize_joint_accessors(
    document: dict[str, object], views: list[dict[str, object]], binary: bytearray
) -> None:

    accessors = document.get("accessors", [])
    joint_accessors = {
        int(primitive["attributes"]["JOINTS_0"])
        for mesh in document.get("meshes", [])
        for primitive in mesh.get("primitives", [])
        if "JOINTS_0" in primitive.get("attributes", {})
    }
    for accessor_index in sorted(joint_accessors):
        accessor = accessors[accessor_index]
        if int(accessor.get("componentType", 0)) != 5126:
            continue
        if accessor.get("type") != "VEC4":
            raise ValueError(f"FLOAT JOINTS_0 accessor {accessor_index} is not VEC4")
        view = views[int(accessor["bufferView"])]
        source_offset = int(view.get("byteOffset", 0)) + int(accessor.get("byteOffset", 0))
        source_stride = int(view.get("byteStride", 16))
        count = int(accessor["count"])
        converted = bytearray()
        component_min = [65535, 65535, 65535, 65535]
        component_max = [0, 0, 0, 0]
        for vertex in range(count):
            values = struct.unpack_from("<4f", binary, source_offset + vertex * source_stride)
            joints: list[int] = []
            for component, value in enumerate(values):
                if not math.isfinite(value) or value < 0 or value > 65535 or value != int(value):
                    raise ValueError(
                        f"invalid joint {value} in accessor {accessor_index}, vertex {vertex}"
                    )
                joint = int(value)
                joints.append(joint)
                component_min[component] = min(component_min[component], joint)
                component_max[component] = max(component_max[component], joint)
            converted.extend(struct.pack("<4H", *joints))
        align4(binary)
        accessor["bufferView"] = len(views)
        accessor["byteOffset"] = 0
        accessor["componentType"] = 5123
        accessor["min"] = component_min
        accessor["max"] = component_max
        accessor.pop("normalized", None)
        new_view: dict[str, object] = {
            "buffer": 0,
            "byteOffset": len(binary),
            "byteLength": len(converted),
        }
        if "target" in view:
            new_view["target"] = view["target"]
        views.append(new_view)
        binary.extend(converted)


def sanitize_assimp_document(
    document: dict[str, object], views: list[dict[str, object]]
) -> None:

    position_accessors = {
        int(primitive["attributes"]["POSITION"])
        for mesh in document.get("meshes", [])
        for primitive in mesh.get("primitives", [])
        if "POSITION" in primitive.get("attributes", {})
    }
    for index, accessor in enumerate(document.get("accessors", [])):


        if index not in position_accessors:
            accessor.pop("min", None)
            accessor.pop("max", None)

    for node in document.get("nodes", []):
        node.pop("jointName", None)
        node.pop("skeletons", None)
        matrix = node.get("matrix")
        if matrix is not None:
            if len(matrix) != 16:
                raise ValueError("node matrix does not contain 16 elements")
            node["matrix"] = [matrix[column * 4 + row] for row in range(4) for column in range(4)]



    skinned_nodes = {
        index for index, node in enumerate(document.get("nodes", [])) if "skin" in node
    }
    if skinned_nodes:
        for node in document.get("nodes", []):
            if "children" in node:
                node["children"] = [
                    child for child in node["children"] if int(child) not in skinned_nodes
                ]
                if not node["children"]:
                    node.pop("children")
        for scene in document.get("scenes", []):
            roots = list(scene.get("nodes", []))
            for node_index in sorted(skinned_nodes):
                if node_index not in roots:
                    roots.append(node_index)
            scene["nodes"] = roots

    for skin in document.get("skins", []):
        skin.pop("bindShapeMatrix", None)
        inverse_bind = skin.get("inverseBindMatrices")
        if inverse_bind is not None:
            accessor = document["accessors"][int(inverse_bind)]
            views[int(accessor["bufferView"])].pop("target", None)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()

    document = json.loads(args.input.read_text(encoding="utf-8-sig"))
    if document.get("asset", {}).get("version") != "2.0":
        raise ValueError("only glTF 2.0 input is supported")

    binary = bytearray()
    buffer_bases: list[int] = []
    for buffer in document.get("buffers", []):
        uri = buffer.get("uri")
        if not isinstance(uri, str):
            raise ValueError("every source buffer must have a local URI")
        align4(binary)
        buffer_bases.append(len(binary))
        payload = local_resource(args.input.parent, uri).read_bytes()
        declared = int(buffer.get("byteLength", len(payload)))
        if len(payload) < declared:
            raise ValueError(f"short buffer {uri}: expected {declared}, got {len(payload)}")
        binary.extend(payload[:declared])

    views = document.setdefault("bufferViews", [])
    for view in views:
        source_buffer = int(view.get("buffer", 0))
        if source_buffer >= len(buffer_bases):
            raise ValueError(f"bufferView references missing buffer {source_buffer}")
        view["byteOffset"] = buffer_bases[source_buffer] + int(view.get("byteOffset", 0))
        view["buffer"] = 0

    normalize_joint_accessors(document, views, binary)
    sanitize_assimp_document(document, views)

    for image in document.get("images", []):
        uri = image.pop("uri", None)
        if uri is None:
            continue
        if not isinstance(uri, str):
            raise ValueError("image URI must be a string")
        path = local_resource(args.input.parent, uri)
        payload = path.read_bytes()
        align4(binary)
        image["bufferView"] = len(views)
        image["mimeType"] = mimetypes.guess_type(path.name)[0] or "application/octet-stream"
        views.append(
            {
                "buffer": 0,
                "byteOffset": len(binary),
                "byteLength": len(payload),
            }
        )
        binary.extend(payload)

    align4(binary)
    document["buffers"] = [{"byteLength": len(binary)}]
    json_data = bytearray(
        json.dumps(document, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    )
    align4(json_data, 0x20)

    total_length = 12 + 8 + len(json_data) + 8 + len(binary)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("wb") as output:
        output.write(struct.pack("<4sII", b"glTF", 2, total_length))
        output.write(struct.pack("<II", len(json_data), JSON_CHUNK))
        output.write(json_data)
        output.write(struct.pack("<II", len(binary), BIN_CHUNK))
        output.write(binary)
    print(f"Packed {args.output} ({total_length:,} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
