"""Random-forest model pipeline."""

from __future__ import annotations

from dataclasses import dataclass

from sklearn.compose import ColumnTransformer
from sklearn.ensemble import RandomForestClassifier
from sklearn.impute import SimpleImputer
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder

from .schema import CATEGORICAL_FEATURES, NUMERIC_FEATURES

__author__ = "qianchen"


@dataclass(frozen=True)
class ModelConfig:
    n_estimators: int = 200
    max_depth: int | None = 8
    min_samples_leaf: int = 4
    random_state: int = 731_021
    n_jobs: int = 1


def build_model(config: ModelConfig | None = None) -> Pipeline:
    """Build an unfitted preprocessing and classifier pipeline."""

    config = config or ModelConfig()
    numeric = Pipeline([("impute", SimpleImputer(strategy="median"))])
    categorical = Pipeline(
        [
            ("impute", SimpleImputer(strategy="most_frequent")),
            ("encode", OneHotEncoder(handle_unknown="ignore")),
        ]
    )
    preprocessing = ColumnTransformer(
        [
            ("numeric", numeric, list(NUMERIC_FEATURES)),
            ("categorical", categorical, list(CATEGORICAL_FEATURES)),
        ]
    )
    classifier = RandomForestClassifier(
        n_estimators=config.n_estimators,
        max_depth=config.max_depth,
        min_samples_leaf=config.min_samples_leaf,
        class_weight="balanced",
        random_state=config.random_state,
        n_jobs=config.n_jobs,
    )
    return Pipeline([("prepare", preprocessing), ("classifier", classifier)])
