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
import os

import better_exceptions
import pandas as pd
import xmltodict
from addict import Dict
from lxml import etree

from FSQApy.data.utilities.LogUtil import logger
from VVTpy.basics.VVTDate import VVTDate
from VVTpy.basics.VVTError import VVTError
from VVTpy.basics.VVTSpecialHolidays import VVTSpecialHolidays

better_exceptions.hook()


class InputParameters():
    ''' FSQA输入参数类 '''

    ###########################################################################

    def __init__(self, coreSettingMap: dict):

        self._valuationDate = None
        self._inputPath = None
        self._outputPath = None
        self._calendarAdjustmentFile = None
        self._marketDataFile = None
        self._marketData = None
        self._fixingDataFile = None
        self._fixingData = None

        self._marketConfigFile = None
        self._marketConfigFileXML = None
        self._marketConfigs = None

        self._riskFactorConfigFile = None
        self._riskFactorConfigXML = None
        self._riskFactorConfigs = None
        # CurveConfigurationsManager

        self._conventionsFile = None
        self._conventionsXML = None
        self._conventionConfigs = None
        # Conventions

        self._pricingEnginesFile = None
        self._pricingEnginesXML = None
        self._pricingEngineConfigs = None

        self._portfolioFile = None
        self._portfolioXML = None
        self._portfolioData = None

        self._baseCurrency = None

        self._resultCurrency = None

        '''TODO'''
        self._continueOnError = False
        self._implyTodaysFixings = False
        # BasicReferenceDataManager = None
        self._refDataManager = None
        # EngineData
        self._pricingEngine = None
        # TodaysMarketParameters
        self._todaysMarketParams = None
        # Portfolio
        self._portfolio = None
        '''TODO'''

        # Analytics List
        self._analytics = []

        # NPV
        self._npvOutputFile = None
        outputAdditionalResults = None
        additionalResultsReportPrecision = None

        # FRTBSA SENSITIVITY OUTPUT
        self._frtbsa_sensitivity_reportingCurrency = None
        self._frtbsa_sensitivity_outputFileName_girr_delta = None
        self._frtbsa_sensitivity_outputFileName_girr_vega = None
        self._frtbsa_sensitivity_outputFileName_girr_curvature = None
        self._frtbsa_sensitivity_outputFileName_girr_csr_delta = None
        self._frtbsa_sensitivity_outputFileName_girr_ccbs_delta = None
        self._frtbsa_sensitivity_outputFileName_fx_delta = None
        self._frtbsa_sensitivity_outputFileName_fx_vega = None
        self._frtbsa_sensitivity_outputFileName_fx_curvature = None
        self._frtbsa_sensitivity_outputFileName_com_delta = None
        self._frtbsa_sensitivity_outputFileName_com_vega = None
        self._frtbsa_sensitivity_outputFileName_com_curvature = None

        self.initParameters(coreSettingMap)

    def initParameters(self, coreSettingMap: dict):
        logger.info('开始创建引擎输入参数实例')
        # 初始化SETUP参数
        if "Setup" not in coreSettingMap:
            raise VVTError("在core.xml中未找到'setup'参数组")
        elif coreSettingMap["Setup"] is None or len(coreSettingMap["Setup"]) == 0:
            raise VVTError("在core.xml中未找到'setup'参数组")

        # 估值日期
        if "valuationDate" not in coreSettingMap["Setup"]:
            raise VVTError('在Setup参数中未指定ValuationDate')
        elif coreSettingMap["Setup"]["valuationDate"] is None or len(coreSettingMap["Setup"]["valuationDate"]) == 0:
            raise VVTError('在Setup参数中未指定ValuationDate')

        self._valuationDate = VVTDate.fromString(coreSettingMap["Setup"]["valuationDate"], '%Y-%m-%d')

        # 输入配置文件的目录
        if "inputPath" not in coreSettingMap["Setup"]:
            raise VVTError('在Setup参数中未指定InputPath')
        elif coreSettingMap["Setup"]["inputPath"] is None or len(coreSettingMap["Setup"]["inputPath"]) == 0:
            raise VVTError('在Setup参数中未指定InputPath')
        self._inputPath = coreSettingMap["Setup"]["inputPath"]

        # 输出文件的目录
        if "outputPath" not in coreSettingMap["Setup"]:
            raise VVTError('在Setup参数中未指定OutputPath')
        elif coreSettingMap["Setup"]["outputPath"] is None or len(coreSettingMap["Setup"]["outputPath"]) == 0:
            raise VVTError('在Setup参数中未指定OutputPath')
        self._outputPath = coreSettingMap["Setup"]["outputPath"]

        # 日历调整文件
        if "calendarAdjustmentFile" not in coreSettingMap["Setup"]:
            raise VVTError('在Setup参数中未指定CalendarAdjustmentFile')
        elif coreSettingMap["Setup"]["calendarAdjustmentFile"] is None or len(
                coreSettingMap["Setup"]["calendarAdjustmentFile"]) == 0:
            raise VVTError('在Setup参数中未指定CalendarAdjustmentFile')
        elif os.path.exists(
                os.path.join(self._inputPath, coreSettingMap["Setup"]["calendarAdjustmentFile"])) is not True:
            raise VVTError(
                '在Setup参数中指定的CalendarAdjustmentFile不可访问或不存在 ：' + str(os.path.join(self._inputPath,
                                                                                                 coreSettingMap[
                                                                                                     "Setup"][
                                                                                                     "calendarAdjustmentFile"])))
        logger.info("从文件中加载特殊假日调整数据信息 : " + str(os.path.join(self._inputPath,
                                                                             coreSettingMap["Setup"][
                                                                                 "calendarAdjustmentFile"])))
        self._calendarAdjustmentFile = os.path.join(self._inputPath, coreSettingMap["Setup"]["calendarAdjustmentFile"])
        VVTSpecialHolidays._specialHolidaysPath = self._calendarAdjustmentFile
        logger.debug("特殊假日调整数据文件信息: " + str(VVTSpecialHolidays().specialHolidays.size))

        # 市场数据文件
        if "marketDataFile" not in coreSettingMap["Setup"]:
            raise VVTError('在Setup参数中未指定MarketDataFile')
        elif coreSettingMap["Setup"]["marketDataFile"] is None or len(coreSettingMap["Setup"]["marketDataFile"]) == 0:
            raise VVTError('在Setup参数中未指定MarketDataFile')
        elif os.path.exists(
                os.path.join(self._inputPath, coreSettingMap["Setup"]["marketDataFile"])) is not True:
            raise VVTError('在Setup参数中指定的MarketDataFile不可访问或不存在 ：' + str(os.path.join(self._inputPath,
                                                                                                    coreSettingMap[
                                                                                                        "Setup"][
                                                                                                        "marketDataFile"])))
        logger.info("从文件中加载今日及历史市场数据信息 : " + str(os.path.join(self._inputPath,
                                                                               coreSettingMap["Setup"][
                                                                                   "marketDataFile"])))
        self._marketDataFile = os.path.join(self._inputPath, coreSettingMap["Setup"]["marketDataFile"])

        self._marketData = pd.read_csv(self._marketDataFile,
                                       sep=' ', header=0, comment='#',
                                       names=['MKDT', 'MKID', 'VALUE'],
                                       dtype={'MKID': 'string', 'VALUE': 'float'}, parse_dates=['MKDT'])
        logger.debug("今日及历史市场数据文件信息: " + str(self._marketData.size))

        # 定盘数据文件
        if "fixingDataFile" not in coreSettingMap["Setup"]:
            raise VVTError('在Setup参数中未指定FixingDataFile')
        elif coreSettingMap["Setup"]["fixingDataFile"] is None or len(coreSettingMap["Setup"]["fixingDataFile"]) == 0:
            raise VVTError('在Setup参数中未指定FixingDataFile')
        elif os.path.exists(
                os.path.join(self._inputPath, coreSettingMap["Setup"]["fixingDataFile"])) is not True:
            raise VVTError('在Setup参数中指定的FixingDataFile不可访问或不存在 ：' + str(os.path.join(self._inputPath,
                                                                                                    coreSettingMap[
                                                                                                        "Setup"][
                                                                                                        "fixingDataFile"])))
        logger.info("从文件中加载今日及历史定盘数据信息 : " + str(os.path.join(self._inputPath,
                                                                               coreSettingMap["Setup"][
                                                                                   "fixingDataFile"])))
        self._fixingDataFile = os.path.join(self._inputPath, coreSettingMap["Setup"]["fixingDataFile"])

        self._fixingData = pd.read_csv(self._fixingDataFile,
                                       sep=' ', header=0, comment='#',
                                       names=['FIXDT', 'MKID', 'VALUE'],
                                       dtype={'MKID': 'string', 'VALUE': 'float'}, parse_dates=['FIXDT'])
        logger.debug("今日及历史定盘数据文件信息: " + str(self._fixingData.size))

        # 今日市场配置文件
        if "marketConfigFile" not in coreSettingMap["Setup"]:
            raise VVTError('在Setup参数中未指定MarketConfigFile')
        elif coreSettingMap["Setup"]["marketConfigFile"] is None or len(
                coreSettingMap["Setup"]["marketConfigFile"]) == 0:
            raise VVTError('在Setup参数中未指定MarketConfigFile参数')
        elif os.path.exists(os.path.join(self._inputPath, coreSettingMap["Setup"]["marketConfigFile"])) is not True:
            raise VVTError(
                '在Setup参数中指定的MarketConfigFile不可访问或不存在: ' + str(os.path.join(self._inputPath,
                                                                                           coreSettingMap["Setup"][
                                                                                               "marketConfigFile"])))
        logger.info("从文件中加载今日市场配置信息 : " + str(os.path.join(self._inputPath,
                                                                         coreSettingMap["Setup"][
                                                                             "marketConfigFile"])))

        self._marketConfigFile = os.path.join(self._inputPath, coreSettingMap["Setup"]["marketConfigFile"])
        self._marketConfigFileXML = etree.parse(self._marketConfigFile)
        logger.debug("今日市场配置文件信息: " + str(self._marketConfigFileXML.getroot().tag))
        with open(self._marketConfigFile) as f:
            my_dict = xmltodict.parse(f.read())
        self._marketConfigs = Dict(my_dict)

        # 风险因子配置文件
        if "riskFactorConfigFile" not in coreSettingMap["Setup"]:
            raise VVTError('在Setup参数中未指定RiskFactorConfigFile')
        elif coreSettingMap["Setup"]["riskFactorConfigFile"] is None or len(
                coreSettingMap["Setup"]["riskFactorConfigFile"]) == 0:
            raise VVTError('在Setup参数中未指定RiskFactorConfigFile参数')
        elif os.path.exists(os.path.join(self._inputPath, coreSettingMap["Setup"]["riskFactorConfigFile"])) is not True:
            raise VVTError(
                '在Setup参数中指定的RiskFactorConfigFile不可访问或不存在: ' + str(os.path.join(self._inputPath,
                                                                                               coreSettingMap["Setup"][
                                                                                                   "riskFactorConfigFile"])))
        logger.info("从文件中加载风险因子定义信息 : " + str(os.path.join(self._inputPath,
                                                                         coreSettingMap["Setup"][
                                                                             "riskFactorConfigFile"])))

        self._riskFactorConfigFile = os.path.join(self._inputPath, coreSettingMap["Setup"]["riskFactorConfigFile"])
        self._riskFactorConfigXML = etree.parse(self._riskFactorConfigFile)
        with open(self._riskFactorConfigFile) as f:
            my_dict = xmltodict.parse(f.read())
        self._riskFactorConfigs = Dict(my_dict)
        logger.debug("风险因子定义文件信息: " + str(self._riskFactorConfigXML.getroot().tag))

        # 惯例定义文件
        if "conventionsFile" not in coreSettingMap["Setup"]:
            raise VVTError('在Setup参数中未指定ConventionsFile')
        elif coreSettingMap["Setup"]["conventionsFile"] is None or len(coreSettingMap["Setup"]["conventionsFile"]) == 0:
            raise VVTError('在Setup参数中未指定ConventionsFile')
        elif os.path.exists(os.path.join(self._inputPath, coreSettingMap["Setup"]["conventionsFile"])) is not True:
            raise VVTError('在Setup参数中指定的ConventionsFile不可访问或不存在: ' + str(os.path.join(self._inputPath,
                                                                                                     coreSettingMap[
                                                                                                         "Setup"][
                                                                                                         "conventionsFile"])))
        logger.info("从文件中加载金融惯例定义信息 : " + str(os.path.join(self._inputPath,
                                                                         coreSettingMap["Setup"][
                                                                             "conventionsFile"])))

        self._conventionsFile = os.path.join(self._inputPath, coreSettingMap["Setup"]["conventionsFile"])
        self._conventionsXML = etree.parse(self._conventionsFile)
        with open(self._conventionsFile) as f:
            my_dict = xmltodict.parse(f.read())
        self._conventionConfigs = Dict(my_dict)
        logger.debug("金融惯例定义文件信息: " + str(self._conventionsXML.getroot().tag))

        # 产品和模型配置
        if "pricingEnginesFile" not in coreSettingMap["Setup"]:
            raise VVTError('在Setup参数中未指定PricingEnginesFile')
        elif coreSettingMap["Setup"]["pricingEnginesFile"] is None or len(
                coreSettingMap["Setup"]["pricingEnginesFile"]) == 0:
            raise VVTError('在Setup参数中未指定PricingEnginesFile')
        elif os.path.exists(os.path.join(self._inputPath, coreSettingMap["Setup"]["pricingEnginesFile"])) is not True:
            raise VVTError('在Setup参数中指定的PricingEnginesFile不可访问或不存在: ' + str(os.path.join(self._inputPath,
                                                                                                        coreSettingMap[
                                                                                                            "Setup"][
                                                                                                            "pricingEnginesFile"])))
        logger.info("从文件中加载产品和模型配置定义信息 : " + str(os.path.join(self._inputPath,
                                                                               coreSettingMap["Setup"][
                                                                                   "pricingEnginesFile"])))

        self._pricingEnginesFile = os.path.join(self._inputPath, coreSettingMap["Setup"]["pricingEnginesFile"])
        self._pricingEnginesXML = etree.parse(self._pricingEnginesFile)
        with open(self._pricingEnginesFile) as f:
            my_dict = xmltodict.parse(f.read())
        self._pricingEngineConfigs = Dict(my_dict)
        logger.debug("产品和模型定义文件信息: " + str(self._pricingEnginesXML.getroot().tag))

        # 交易数据文件
        if "portfolioFile" not in coreSettingMap["Setup"]:
            raise VVTError('在Setup参数中未指定PortfolioFile')
        elif coreSettingMap["Setup"]["portfolioFile"] is None or len(coreSettingMap["Setup"]["portfolioFile"]) == 0:
            raise VVTError('在Setup参数中未指定PortfolioFile')
        elif os.path.exists(os.path.join(self._inputPath, coreSettingMap["Setup"]["portfolioFile"])) is not True:
            raise VVTError('在Setup参数中指定的PortfolioFile不可访问或不存在: ' + str(os.path.join(self._inputPath,
                                                                                                   coreSettingMap[
                                                                                                       "Setup"][
                                                                                                       "portfolioFile"])))
        logger.info("从文件中加载组合持仓信息 : " + str(os.path.join(self._inputPath,
                                                                     coreSettingMap["Setup"][
                                                                         "portfolioFile"])))

        self._portfolioFile = os.path.join(self._inputPath, coreSettingMap["Setup"]["portfolioFile"])
        self._portfolioXML = etree.parse(self._portfolioFile)
        with open(self._portfolioFile) as f:
            my_dict = xmltodict.parse(f.read())
        self._portfolioData = Dict(my_dict)
        logger.debug("组合持仓文件信息: " + str(self._portfolioXML.getroot().tag))

        # 基准币种

        if "baseCurrency" not in coreSettingMap["Setup"]:
            if "npv" not in coreSettingMap["Analytics"]:
                raise VVTError('在<Setup>参数中或者<Analytics-NPV>参数中未定义基准货币参数')
            elif "baseCurrency" not in coreSettingMap["Analytics"]["npv"]:
                raise VVTError('在<Setup>参数中或者<Analytics-NPV>参数中未定义基准货币参数')
            else:
                self._baseCurrency = coreSettingMap["Analytics"]["npv"]["baseCurrency"]
        else:
            self._baseCurrency = coreSettingMap["Setup"]["baseCurrency"]

        # 报告币种

        if "resultCurrency" not in coreSettingMap["Setup"]:
            self._resultCurrency = self._baseCurrency
        else:
            tmp = coreSettingMap["Setup"]["resultCurrency"]
            if tmp != "":
                self._resultCurrency = tmp
            else:
                raise VVTError('在<Setup>参数中未定义报告货币参数')

        """
        NPV Analytics 
        """
        if "npv" in coreSettingMap["Analytics"]:
            if "active" in coreSettingMap["Analytics"]["npv"]:
                if coreSettingMap["Analytics"]["npv"]["active"] == "Y":
                    if "outputFileName" in coreSettingMap["Analytics"]["npv"]:
                        self._npvOutputFile = os.path.join(self._outputPath,
                                                           coreSettingMap["Analytics"]["npv"]["outputFileName"])
                    else:
                        raise VVTError('在<Analytics-NPV>中未定义outputFileName参数')
                    self._analytics.append("npv")
            else:
                raise VVTError('在<Analytics-NPV>中未定义active参数')

        """
        Frtbsa_sensitivity Analytics 
        """
        if "frtbsa_sensitivity" in coreSettingMap["Analytics"]:
            if "active" in coreSettingMap["Analytics"]["frtbsa_sensitivity"]:
                if coreSettingMap["Analytics"]["frtbsa_sensitivity"]["active"] == "Y":
                    if "reportingCurrency" in coreSettingMap["Analytics"]["frtbsa_sensitivity"]:
                        self._frtbsa_sensitivity_reportingCurrency = coreSettingMap["Analytics"]["frtbsa_sensitivity"][
                            "reportingCurrency"]
                    else:
                        self._frtbsa_sensitivity_reportingCurrency = self._baseCurrency

                    if "outputFileName_girr_delta" in coreSettingMap["Analytics"]["frtbsa_sensitivity"]:
                        self._frtbsa_sensitivity_outputFileName_girr_delta = os.path.join(self._outputPath,
                                                                                          coreSettingMap["Analytics"][
                                                                                              "frtbsa_sensitivity"][
                                                                                              "outputFileName_girr_delta"])
                    if "outputFileName_girr_vega" in coreSettingMap["Analytics"]["frtbsa_sensitivity"]:
                        self._frtbsa_sensitivity_outputFileName_girr_vega = os.path.join(self._outputPath,
                                                                                         coreSettingMap["Analytics"][
                                                                                             "frtbsa_sensitivity"][
                                                                                             "outputFileName_girr_vega"])
                    if "outputFileName_girr_curvature" in coreSettingMap["Analytics"]["frtbsa_sensitivity"]:
                        self._frtbsa_sensitivity_outputFileName_girr_curvature = os.path.join(self._outputPath,
                                                                                              coreSettingMap[
                                                                                                  "Analytics"][
                                                                                                  "frtbsa_sensitivity"][
                                                                                                  "outputFileName_girr_curvature"])
                    if "outputFileName_girr_csr_delta" in coreSettingMap["Analytics"]["frtbsa_sensitivity"]:
                        self._frtbsa_sensitivity_outputFileName_girr_csr_delta = os.path.join(self._outputPath,
                                                                                              coreSettingMap[
                                                                                                  "Analytics"][
                                                                                                  "frtbsa_sensitivity"][
                                                                                                  "outputFileName_girr_csr_delta"])
                    if "outputFileName_girr_ccbs_delta" in coreSettingMap["Analytics"]["frtbsa_sensitivity"]:
                        self._frtbsa_sensitivity_outputFileName_girr_ccbs_delta = os.path.join(self._outputPath,
                                                                                               coreSettingMap[
                                                                                                   "Analytics"][
                                                                                                   "frtbsa_sensitivity"][
                                                                                                   "outputFileName_girr_ccbs_delta"])
                    if "outputFileName_fx_delta" in coreSettingMap["Analytics"]["frtbsa_sensitivity"]:
                        self._frtbsa_sensitivity_outputFileName_fx_delta = os.path.join(self._outputPath,
                                                                                        coreSettingMap["Analytics"][
                                                                                            "frtbsa_sensitivity"][
                                                                                            "outputFileName_fx_delta"])
                    if "outputFileName_fx_vega" in coreSettingMap["Analytics"]["frtbsa_sensitivity"]:
                        self._frtbsa_sensitivity_outputFileName_fx_vega = os.path.join(self._outputPath,
                                                                                       coreSettingMap["Analytics"][
                                                                                           "frtbsa_sensitivity"][
                                                                                           "outputFileName_fx_vega"])
                    if "outputFileName_fx_curvature" in coreSettingMap["Analytics"]["frtbsa_sensitivity"]:
                        self._frtbsa_sensitivity_outputFileName_fx_curvature = os.path.join(self._outputPath,
                                                                                            coreSettingMap["Analytics"][
                                                                                                "frtbsa_sensitivity"][
                                                                                                "outputFileName_fx_curvature"])
                    if "outputFileName_com_delta" in coreSettingMap["Analytics"]["frtbsa_sensitivity"]:
                        self._frtbsa_sensitivity_outputFileName_com_delta = os.path.join(self._outputPath,
                                                                                         coreSettingMap["Analytics"][
                                                                                             "frtbsa_sensitivity"][
                                                                                             "outputFileName_com_delta"])
                    if "outputFileName_com_vega" in coreSettingMap["Analytics"]["frtbsa_sensitivity"]:
                        self._frtbsa_sensitivity_outputFileName_com_vega = os.path.join(self._outputPath,
                                                                                        coreSettingMap["Analytics"][
                                                                                            "frtbsa_sensitivity"][
                                                                                            "outputFileName_com_vega"])
                    if "outputFileName_com_curvature" in coreSettingMap["Analytics"]["frtbsa_sensitivity"]:
                        self._frtbsa_sensitivity_outputFileName_com_curvature = os.path.join(self._outputPath,
                                                                                             coreSettingMap[
                                                                                                 "Analytics"][
                                                                                                 "frtbsa_sensitivity"][
                                                                                                 "outputFileName_com_curvature"])

                    self._analytics.append("frtbsa_sensitivity")
            else:
                raise VVTError('在<Analytics-Frtbsa_sensitivity>中未定义active参数')

        logger.info('成功创建引擎输入参数实例')

    def valuationDate(self):
        return self._valuationDate

    def analytics(self):
        return self._analytics

    # def _initRiskFactors(self):
    #     with open(self._riskFactorConfigFile) as f:
    #         my_dict = xmltodict.parse(f.read())
    #     self._riskFactorConfigDict = Dict(my_dict)
    #     pprint(self._riskFactorConfigDict.RiskFactorConfiguration.FXSpots)
    #     pass
    @property
    def riskFactorConfigs(self):
        return self._riskFactorConfigs
