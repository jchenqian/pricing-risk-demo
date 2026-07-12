#  Copyright (C) Kpmg Advisory (China) Limited - All Rights Reserved
#  This source code is protected under international copyright law.  All rights
#  reserved and protected by the copyright holders.
#  This file is confidential and only available to authorized individuals with the
#  permission of the copyright holders.  If you encounter this file and do not have
#  permission, please contact the copyright holders and delete this file.
#  KPMG Advisory (China) FRM TGM 2024


from typing import Protocol

from VVTpy.basics.VVTDate import VVTDate
import better_exceptions
better_exceptions.hook()


class Arguments:
    def __init__(self):
        pass

    def validate(self):
        pass


class Results:
    def __init__(self):
        self._valuationDate: VVTDate = None
        self._value: float = None
        self._errorEstimate: float = None
        self._additionalResults: dict = dict()

    def reset(self):
        self._valuationDate: VVTDate = None
        self._value: float = None
        self._errorEstimate: float = None
        self._additionalResults.clear()


class VVTPricingEngine(Protocol):
    ''' 金融工具基础类 '''

    def __init__(self):
        self._arguments: Arguments = None
        self._results: Results = None

    def getArguments(self):
        return self._arguments

    def getResults(self):
        return self._results

    def reset(self):
        self._results.reset()

    def calculate(self):
        pass

    def update(self):
        # notifyObservers()
        pass
