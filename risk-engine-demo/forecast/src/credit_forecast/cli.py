"""Command-line demonstration using synthetic data only.

Author: qianchen
"""

from __future__ import annotations

import argparse
import json
from collections.abc import Sequence

from .model import ModelConfig
from .synthetic import DEFAULT_SEED, generate_synthetic_issuers
from .trainer import TrainingConfig, train_forecaster


def build_parser() -> argparse.ArgumentParser:
    """Create the command-line argument parser."""

    parser = argparse.ArgumentParser(
        description="Train a deterministic credit forecast on synthetic observations."
    )
    parser.add_argument("--rows", type=int, default=500)
    parser.add_argument("--seed", type=int, default=DEFAULT_SEED)
    parser.add_argument("--repeats", type=int, default=2)
    parser.add_argument("--estimators", type=int, default=40)
    parser.add_argument("--max-depth", type=int, default=6)
    parser.add_argument("--min-recall", type=float, default=0.8)
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    """Generate synthetic data, train the model, and print a JSON summary."""

    args = build_parser().parse_args(argv)
    frame = generate_synthetic_issuers(n_rows=args.rows, seed=args.seed)
    model_config = ModelConfig(
        n_estimators=args.estimators,
        max_depth=args.max_depth,
        random_state=args.seed,
    )
    config = TrainingConfig(
        repeats=args.repeats,
        min_recall=args.min_recall,
        random_state=args.seed,
        model=model_config,
    )
    result = train_forecaster(frame, config)
    print(json.dumps(result.summary, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
