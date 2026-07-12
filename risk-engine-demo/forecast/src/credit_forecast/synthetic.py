"""Deterministic, entirely fictional issuer observations."""

from __future__ import annotations

import numpy as np
import pandas as pd

from .schema import TARGET_COLUMN

__author__ = "qianchen"

DEFAULT_SEED = 731_021


def generate_synthetic_issuers(
    n_rows: int = 1_000,
    seed: int = DEFAULT_SEED,
) -> pd.DataFrame:
    """Generate generic issuer-like data without external inputs."""

    if n_rows < 100:
        raise ValueError("n_rows must be at least 100")

    rng = np.random.default_rng(seed)
    business = rng.choice(["segment_1", "segment_2", "segment_3"], n_rows)
    area = rng.choice(["area_1", "area_2", "area_3", "area_4"], n_rows)
    ownership = rng.choice(["type_1", "type_2"], n_rows, p=[0.6, 0.4])

    debt_ratio = np.clip(rng.beta(2.8, 2.4, n_rows), 0.02, 0.98)
    cash_coverage = np.clip(rng.lognormal(0.15, 0.55, n_rows), 0.1, 6.0)
    operating_margin = np.clip(rng.normal(0.1, 0.12, n_rows), -0.4, 0.5)
    sales_change = np.clip(rng.normal(0.04, 0.2, n_rows), -0.7, 0.8)
    asset_scale = rng.normal(8.0, 0.85, n_rows)
    price_variability = np.clip(rng.lognormal(-1.7, 0.5, n_rows), 0.03, 0.9)

    segment_effect = pd.Series(business).map(
        {"segment_1": -0.25, "segment_2": 0.1, "segment_3": 0.35}
    ).to_numpy()
    score = (
        -2.0
        + 3.2 * (debt_ratio - 0.5)
        - 0.55 * np.log1p(cash_coverage)
        - 2.2 * operating_margin
        - 0.7 * sales_change
        - 0.12 * (asset_scale - 8.0)
        + 2.7 * price_variability
        + segment_effect
        + 0.2 * (ownership == "type_2")
        + rng.normal(0.0, 0.3, n_rows)
    )
    probability = 1.0 / (1.0 + np.exp(-score))
    target = rng.binomial(1, probability)

    frame = pd.DataFrame(
        {
            "entity_id": [f"ENTITY_{i:06d}" for i in range(1, n_rows + 1)],
            "snapshot_date": pd.Timestamp("2025-01-01")
            - pd.to_timedelta(rng.integers(0, 730, n_rows), unit="D"),
            "debt_ratio": debt_ratio,
            "cash_coverage": cash_coverage,
            "operating_margin": operating_margin,
            "sales_change": sales_change,
            "asset_scale": asset_scale,
            "price_variability": price_variability,
            "business_group": business,
            "area_group": area,
            "ownership_group": ownership,
            TARGET_COLUMN: target.astype(int),
        }
    )
    for column in ("cash_coverage", "operating_margin", "sales_change", "business_group"):
        frame.loc[rng.random(n_rows) < 0.03, column] = np.nan
    return frame
