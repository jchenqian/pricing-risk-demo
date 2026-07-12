#  Copyright (C) Kpmg Advisory (China) Limited - All Rights Reserved
#  This source code is protected under international copyright law.  All rights
#  reserved and protected by the copyright holders.
#  This file is confidential and only available to authorized individuals with the
#  permission of the copyright holders.  If you encounter this file and do not have
#  permission, please contact the copyright holders and delete this file.
#  KPMG Advisory (China) FRM TGM 2024
#
#
#
#

from numpy import power

from VVTpy.basics.VVTDate import VVTDate
from VVTpy.models.black_scholes import BlackScholes
from VVTpy.basics.VVTDayCount import DayCountTypes
from VVTpy.market.curves.VVTDiscountCurveZeros import VVTDiscountCurveZeros
from VVTpy.instruments.fx.VVTSingleBarrierOptionFX import VVTSingleBarrierOptionFX
from VVTpy.basics.VVTGlobalTypes import OptionCallPut, BarrierOptionKnockType, BarrierOptionBarrierDirection,CompoundingTypes, LongShort, SettlementType,BarrierOptionObservationType
from VVTpy.basics.VVTCalendar import BusDayAdjustTypes,  CalendarTypes
from VVTpy.math.VVTInterpolator import InterpTypes
from VVTpy.basics.VVTFrequency import FrequencyTypes

###############################################################################
expiryDate = VVTDate(10, 1, 2023)
settlementDate = VVTDate(13, 1, 2023)
strikeFxRate = 6.8
currencyPair = 'USD/CNY'
barrierDirection = BarrierOptionBarrierDirection.UP
callPut = OptionCallPut.CALL
knockoutType = BarrierOptionKnockType.IN
observationType = BarrierOptionObservationType.CONTINUOUS
barrierHitTime = VVTDate(1, 1, 2024)  # 大于到期日
vannaVolgaAdjustment = False
barrierLevel = 6.77
numObservationsPerYear = 0
rebate = 0.03
notional = 1
settlementType = SettlementType.CASH
daycountBasis = DayCountTypes.ACT_365F
longShort = LongShort.LONG
notionalCurrency = 'CNY'

##其他数据
valuationDate = VVTDate(28, 4, 2022)

##市场数据
spotFxRate = 6.74
volatility = 0.0873486258112418
volatilityatm= 0.0862175066279373
volatility25C = 0.0854383646071728
volatility75C = 0.0865908332346749
model = BlackScholes(volatility)
modelatm=BlackScholes(volatilityatm)
model25C = BlackScholes(volatility25C)
model75C = BlackScholes(volatility75C)
freqType = FrequencyTypes.ANNUAL
calendarTypes = CalendarTypes.WEEKEND
busDayAdjustTypes = BusDayAdjustTypes.MODIFIED_FOLLOWING
interpTypes = InterpTypes.LINEAR_ZERO_RATES
compoundingTypes = CompoundingTypes.CONTINUOUS
dayCountType = DayCountTypes.ACT_365F
domDfDates = [VVTDate(10, 1, 2023)]
domDfRates = [0.023616595113043500]
domDiscountCurve = VVTDiscountCurveZeros(valuationDate, domDfDates, domDfRates,
                                         freqType, dayCountType, calendarTypes, busDayAdjustTypes,
                                         interpTypes, compoundingTypes)

forDfDates = [VVTDate(10, 1, 2023)]
forDfRates = [0.016269565217391300]
forDiscountCurve = VVTDiscountCurveZeros(valuationDate, forDfDates, forDfRates,
                                         freqType, dayCountType, calendarTypes, busDayAdjustTypes,
                                         interpTypes, compoundingTypes)
premDfDates = [VVTDate(13, 1, 2023)]
premDfRates = [0.023633301910869600]
premDiscountCurve = VVTDiscountCurveZeros(valuationDate, premDfDates, premDfRates,
                                          freqType, dayCountType, calendarTypes, busDayAdjustTypes,
                                          interpTypes, compoundingTypes)
class TestSingleBarrierFX:
    def testUpAndInCall(self):
        barrierDirection = BarrierOptionBarrierDirection.UP
        callPut = OptionCallPut.CALL
        knockoutType = BarrierOptionKnockType.IN
        ESB = VVTSingleBarrierOptionFX(longShort,
                                       expiryDate,
                                       settlementDate,
                                       strikeFxRate,
                                       currencyPair,
                                       barrierDirection,
                                       callPut,
                                       knockoutType,
                                       barrierLevel,
                                       observationType,
                                       rebate,
                                       notional,
                                       notionalCurrency,
                                       daycountBasis)
        value = ESB.value(valuationDate, spotFxRate, domDiscountCurve, forDiscountCurve, premDiscountCurve,vannaVolgaAdjustment, model, modelatm,
                          model25C, model75C)
        print(ESB)
        print(value)
        assert abs(value/0.1844159693929930-1)<power(0.1,8)

    def testUpAndOutPut(self):
        barrierDirection = BarrierOptionBarrierDirection.UP
        callPut = OptionCallPut.PUT
        knockoutType = BarrierOptionKnockType.OUT
        ESB = VVTSingleBarrierOptionFX(longShort,
                                       expiryDate,
                                       settlementDate,
                                       strikeFxRate,
                                       currencyPair,
                                       barrierDirection,
                                       callPut,
                                       knockoutType,
                                       barrierLevel,
                                       observationType,
                                       rebate,
                                       notional,
                                       notionalCurrency,
                                       daycountBasis,
                                       numObservationsPerYear,
                                       barrierHitTime,
                                       settlementType)
        value = ESB.value(valuationDate, spotFxRate, domDiscountCurve, forDiscountCurve, premDiscountCurve, vannaVolgaAdjustment, model, modelatm,
                          model25C, model75C)
        print(value)
        assert  abs(value/0.055998952126591900-1)<power(0.1,8)


    def testUpAndInPut(self):
        barrierDirection = BarrierOptionBarrierDirection.UP
        callPut = OptionCallPut.PUT
        knockoutType = BarrierOptionKnockType.IN
        ESB = VVTSingleBarrierOptionFX(longShort,
                                       expiryDate,
                                       settlementDate,
                                       strikeFxRate,
                                       currencyPair,
                                       barrierDirection,
                                       callPut,
                                       knockoutType,
                                       barrierLevel,
                                       observationType,
                                       rebate,
                                       notional,
                                       notionalCurrency,
                                       daycountBasis,
                                       numObservationsPerYear,
                                       barrierHitTime,
                                       settlementType)
        value = ESB.value(valuationDate, spotFxRate, domDiscountCurve, forDiscountCurve, premDiscountCurve,vannaVolgaAdjustment, model, modelatm,
                          model25C, model75C)
        print(value)
        assert abs(value / 0.181172736230279000 - 1) < power(0.1, 8)

    def testDownAndInCall(self):
        barrierDirection = BarrierOptionBarrierDirection.DOWN
        callPut = OptionCallPut.CALL
        knockoutType = BarrierOptionKnockType.IN
        ESB = VVTSingleBarrierOptionFX(longShort,
                                       expiryDate,
                                       settlementDate,
                                       strikeFxRate,
                                       currencyPair,
                                       barrierDirection,
                                       callPut,
                                       knockoutType,
                                       barrierLevel,
                                       observationType,
                                       rebate,
                                       notional,
                                       notionalCurrency,
                                       daycountBasis,
                                       numObservationsPerYear,
                                       barrierHitTime,
                                       settlementType)
        value = ESB.value(valuationDate, spotFxRate, domDiscountCurve, forDiscountCurve, premDiscountCurve, vannaVolgaAdjustment, model, modelatm,
                          model25C, model75C)
        print(value)
        assert abs(value / 0.183047691697986000 - 1) < power(0.1, 8)

#0.028130909222540800 UP AND OUT CALL
#0.029499186917548500 down AND out CALL
#0.029499186917548534 down and out put
#0.20767250143932234 down and in put
