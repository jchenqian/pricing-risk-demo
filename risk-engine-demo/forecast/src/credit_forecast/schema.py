"""Public feature contract and target-leakage safeguards."""

from __future__ import annotations

import re
from collections.abc import Iterable

import pandas as pd

__author__ = "qianchen"

NUMERIC_FEATURES = (
    "debt_ratio",
    "cash_coverage",
    "operating_margin",
    "sales_change",
    "asset_scale",
    "price_variability",
)
CATEGORICAL_FEATURES = ("business_group", "area_group", "ownership_group")
FEATURE_COLUMNS = NUMERIC_FEATURES + CATEGORICAL_FEATURES
METADATA_COLUMNS = ("entity_id", "snapshot_date")
TARGET_COLUMN = "event_in_horizon"

_LEAKAGE_TOKENS = (
    "event_in_horizon",
    "event_date",
    "default",
    "delinquen",
    "past_due",
    "recovery",
    "write_off",
    "outcome",
    "post_event",
    "after_snapshot",
    "future_",
)


class LeakageError(ValueError):
    """Raised when a prospective feature can reveal the target or future."""


def _normalise(name: str) -> str:
    return re.sub(r"[^a-z0-9]+", "_", name.lower()).strip("_")


def assert_no_leakage(columns: Iterable[str]) -> None:
    """Reject target, outcome, and post-observation fields used as features."""

    bad = []
    for column in columns:
        normalised = _normalise(str(column))
        if any(token in normalised for token in _LEAKAGE_TOKENS):
            bad.append(str(column))
    if bad:
        raise LeakageError(f"Potential target leakage columns rejected: {sorted(bad)}")


def validate_training_frame(frame: pd.DataFrame) -> None:
    """Validate the strict feature schema and binary target."""

    missing = sorted(set(FEATURE_COLUMNS + (TARGET_COLUMN,)) - set(frame.columns))
    if missing:
        raise ValueError(f"Missing required columns: {missing}")

    extras = set(frame.columns) - set(FEATURE_COLUMNS + METADATA_COLUMNS + (TARGET_COLUMN,))
    if extras:
        assert_no_leakage(extras)
        raise ValueError(f"Columns outside the feature schema: {sorted(extras)}")

    assert_no_leakage(FEATURE_COLUMNS)
    target_values = set(frame[TARGET_COLUMN].dropna().unique())
    if frame[TARGET_COLUMN].isna().any() or not target_values <= {0, 1}:
        raise ValueError(f"{TARGET_COLUMN} must contain only 0 and 1")
    if len(target_values) < 2:
        raise ValueError(f"{TARGET_COLUMN} must contain both classes")


def select_features(frame: pd.DataFrame) -> pd.DataFrame:
    """Return features in canonical order after validating the full frame."""

    validate_training_frame(frame)
    return frame.loc[:, FEATURE_COLUMNS].copy()
