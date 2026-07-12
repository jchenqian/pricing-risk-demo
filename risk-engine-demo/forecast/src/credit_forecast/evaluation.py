"""Classification metrics and recall-constrained threshold selection."""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np
from sklearn.metrics import (
    accuracy_score,
    average_precision_score,
    f1_score,
    precision_score,
    recall_score,
    roc_auc_score,
)

__author__ = "qianchen"


@dataclass(frozen=True)
class ThresholdChoice:
    threshold: float
    precision: float
    recall: float
    f1: float


def classification_metrics(
    y_true: np.ndarray,
    probabilities: np.ndarray,
    threshold: float,
) -> dict[str, float]:
    """Calculate probability and threshold-based binary metrics."""

    predicted = (np.asarray(probabilities) >= threshold).astype(int)
    return {
        "roc_auc": float(roc_auc_score(y_true, probabilities)),
        "average_precision": float(average_precision_score(y_true, probabilities)),
        "accuracy": float(accuracy_score(y_true, predicted)),
        "precision": float(precision_score(y_true, predicted, zero_division=0)),
        "recall": float(recall_score(y_true, predicted, zero_division=0)),
        "f1": float(f1_score(y_true, predicted, zero_division=0)),
    }


def select_threshold(
    y_true: np.ndarray,
    probabilities: np.ndarray,
    min_recall: float = 0.8,
) -> ThresholdChoice:
    """Maximise F1 among thresholds meeting the minimum recall."""

    if not 0.0 <= min_recall <= 1.0:
        raise ValueError("min_recall must be between 0 and 1")
    y_true = np.asarray(y_true, dtype=int)
    probabilities = np.asarray(probabilities, dtype=float)
    if y_true.shape != probabilities.shape or y_true.ndim != 1:
        raise ValueError("y_true and probabilities must be matching one-dimensional arrays")
    if set(np.unique(y_true)) - {0, 1}:
        raise ValueError("y_true must be binary")
    if np.any((probabilities < 0.0) | (probabilities > 1.0)):
        raise ValueError("probabilities must be between 0 and 1")

    choices: list[ThresholdChoice] = []
    for threshold in np.unique(np.concatenate(([0.0], probabilities))):
        predicted = (probabilities >= threshold).astype(int)
        recall = float(recall_score(y_true, predicted, zero_division=0))
        if recall >= min_recall:
            choices.append(
                ThresholdChoice(
                    threshold=float(threshold),
                    precision=float(precision_score(y_true, predicted, zero_division=0)),
                    recall=recall,
                    f1=float(f1_score(y_true, predicted, zero_division=0)),
                )
            )
    if not choices:
        raise ValueError("No threshold can meet the requested recall")
    return max(choices, key=lambda choice: (choice.f1, choice.precision, choice.threshold))
