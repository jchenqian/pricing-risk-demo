"""Synthetic credit-risk forecasting utilities.

Author: qianchen
"""

from .evaluation import ThresholdChoice, classification_metrics, select_threshold
from .model import ModelConfig, build_model
from .schema import LeakageError, assert_no_leakage
from .synthetic import DEFAULT_SEED, generate_synthetic_issuers
from .trainer import TrainingConfig, TrainingResult, train_forecaster

__all__ = [
    "DEFAULT_SEED",
    "LeakageError",
    "ModelConfig",
    "ThresholdChoice",
    "TrainingConfig",
    "TrainingResult",
    "assert_no_leakage",
    "build_model",
    "classification_metrics",
    "generate_synthetic_issuers",
    "select_threshold",
    "train_forecaster",
]

__version__ = "0.1.0"
