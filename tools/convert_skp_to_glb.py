"""Convert a SketchUp model to GLB with the OpenSKP parser.

Install the converter into a temporary folder first:

python -m pip install --target build/openskp-python openskp==0.2.0

Then expose that folder through PYTHONPATH and run this script.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from openskp import SkpFile
from openskp.export import glb


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("input_skp", type=Path)
    parser.add_argument("output_glb", type=Path)
    args = parser.parse_args()

    input_path = args.input_skp.resolve()
    output_path = args.output_glb.resolve()
    output_path.parent.mkdir(parents=True, exist_ok=True)

    skp_file = SkpFile.open(str(input_path))
    model = skp_file.parse()
    exported_path = Path(glb.export(skp_file, str(output_path))).resolve()

    report = {
        "input": str(input_path),
        "output": str(exported_path),
        "version": model.version,
        "definitions": len(model.definitions),
        "instances": sum(len(definition.instances) for definition in model.definitions.values()),
        "layers": [layer.name for layer in model.layers],
        "materials": [material.name for material in model.materials],
        "vertices": sum(len(definition.vertices) for definition in model.definitions.values()),
        "faces": sum(len(definition.faces) for definition in model.definitions.values()),
        "size_bytes": exported_path.stat().st_size,
    }
    print("CODEX_SKP_CONVERSION=" + json.dumps(report))


if __name__ == "__main__":
    main()
