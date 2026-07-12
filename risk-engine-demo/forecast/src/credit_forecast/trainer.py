"""Repeated stratified holdout training with held-out threshold selection."""

from __future__ import annotations

from dataclasses import asdict, dataclass
from statistics import mean, pstdev

import numpy as np
import pandas as pd
from sklearn.model_selection import StratifiedShuffleSplit
from sklearn.pipeline import Pipeline

from .evaluation import classification_metrics, select_threshold
from .model import ModelConfig, build_model
from .schema import TARGET_COLUMN, select_features, validate_training_frame

__author__ = "qianchen"


@dataclass(frozen=True)
class TrainingConfig:
    repeats: int = 5
    holdout_size: float = 0.25
    min_recall: float = 0.8
    random_state: int = 731_021
    model: ModelConfig = ModelConfig()


@dataclass
class TrainingResult:
    model: Pipeline
    threshold: float
    summary: dict[str, object]


def train_forecaster(
    frame: pd.DataFrame,
    config: TrainingConfig | None = None,
) -> TrainingResult:
    """Train deterministically and choose a threshold from repeated holdouts."""

    config = config or TrainingConfig()
    if config.repeats < 2:
        raise ValueError("repeats must be at least 2")
    validate_training_frame(frame)
    features = select_features(frame)
    target = frame[TARGET_COLUMN].to_numpy(dtype=int)
    splitter = StratifiedShuffleSplit(
        n_splits=config.repeats,
        test_size=config.holdout_size,
        random_state=config.random_state,
    )

    heldout_targets: list[np.ndarray] = []
    heldout_probabilities: list[np.ndarray] = []
    split_auc: list[float] = []
    for train_index, holdout_index in splitter.split(features, target):
        candidate = build_model(config.model)
        candidate.fit(features.iloc[train_index], target[train_index])
        probabilities = candidate.predict_proba(features.iloc[holdout_index])[:, 1]
        heldout_targets.append(target[holdout_index])
        heldout_probabilities.append(probabilities)
        split_auc.append(
            classification_metrics(target[holdout_index], probabilities, 0.5)["roc_auc"]
        )

    oof_target = np.concatenate(heldout_targets)
    oof_probability = np.concatenate(heldout_probabilities)
    choice = select_threshold(oof_target, oof_probability, config.min_recall)
    final_model = build_model(config.model)
    final_model.fit(features, target)

    summary: dict[str, object] = {
        "rows": len(frame),
        "positive_rate": float(target.mean()),
        "threshold": choice.threshold,
        "oof_metrics": classification_metrics(oof_target, oof_probability, choice.threshold),
        "split_roc_auc_mean": mean(split_auc),
        "split_roc_auc_std": pstdev(split_auc),
        "config": asdict(config),
    }
    return TrainingResult(final_model, choice.threshold, summary)
