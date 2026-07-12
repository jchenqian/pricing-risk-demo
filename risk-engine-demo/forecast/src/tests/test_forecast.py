"""Behavior tests for the synthetic forecasting workflow.

Author: qianchen
"""

from __future__ import annotations

import unittest

import numpy as np
from pandas.testing import assert_frame_equal

from credit_forecast.evaluation import select_threshold
from credit_forecast.model import ModelConfig
from credit_forecast.schema import LeakageError, assert_no_leakage
from credit_forecast.synthetic import generate_synthetic_issuers
from credit_forecast.trainer import TrainingConfig, train_forecaster


class SyntheticDataTests(unittest.TestCase):
    def test_fixed_seed_reproduces_identical_frame(self) -> None:
        first = generate_synthetic_issuers(n_rows=200, seed=19)
        second = generate_synthetic_issuers(n_rows=200, seed=19)

        assert_frame_equal(first, second)


class LeakageTests(unittest.TestCase):
    def test_future_or_target_fields_are_rejected(self) -> None:
        with self.assertRaises(LeakageError):
            assert_no_leakage(["debt_ratio", "future_payment_status"])


class ThresholdTests(unittest.TestCase):
    def test_selected_threshold_meets_recall_constraint(self) -> None:
        y_true = np.array([0, 0, 0, 1, 1, 1])
        probabilities = np.array([0.05, 0.20, 0.45, 0.40, 0.70, 0.90])

        choice = select_threshold(y_true, probabilities, min_recall=2 / 3)

        self.assertGreaterEqual(choice.recall, 2 / 3)
        predicted = (probabilities >= choice.threshold).astype(int)
        observed_recall = predicted[y_true == 1].mean()
        self.assertAlmostEqual(observed_recall, choice.recall)


class TrainingTests(unittest.TestCase):
    def test_fast_training_is_reproducible(self) -> None:
        frame = generate_synthetic_issuers(n_rows=240, seed=23)
        config = TrainingConfig(
            repeats=2,
            holdout_size=0.25,
            min_recall=0.75,
            random_state=23,
            model=ModelConfig(
                n_estimators=20,
                max_depth=5,
                min_samples_leaf=3,
                random_state=23,
                n_jobs=1,
            ),
        )

        first = train_forecaster(frame, config)
        second = train_forecaster(frame, config)

        self.assertEqual(first.threshold, second.threshold)
        self.assertEqual(first.summary, second.summary)
        features = frame.loc[:, first.model.feature_names_in_]
        first_probabilities = first.model.predict_proba(features)[:, 1]
        second_probabilities = second.model.predict_proba(features)[:, 1]
        np.testing.assert_array_equal(first_probabilities, second_probabilities)


if __name__ == "__main__":
    unittest.main()
