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
#
import json
import time

import better_exceptions
import matplotlib
from lxml import etree

from FSQApy.analytic.application.AnalyticsManager import AnalyticsManager
from FSQApy.data.configuration import GlobleSettings
from FSQApy.data.configuration.InputParameters import InputParameters
from FSQApy.data.configuration.OutputParameters import OutputParameters
from FSQApy.data.utilities.LogUtil import logger
from VVTpy.basics.VVTError import VVTError

better_exceptions.hook()

matplotlib.use('Agg')


class FSQACore(object):
    def __init__(self, xml, console: bool):

        logger.info("开始创建FSQA引擎实例")

        self._console = console
        self._xml = xml
        self._xmlconfig = None
        self._coreSettingMap = {}
        self._inputParas = None
        self._validateInputs()
        # 初始化各种工厂类
        #initBuilders()
        self.initCoreSettings()

    def initCoreSettings(self):
        logger.info('核心配置文件路径 ' + self._xml)
        self._xmlconfig = etree.parse(self._xml)
        root = self._xmlconfig.getroot()
        if root.tag != 'FSQA': raise VVTError('XML Node name ' + root.tag + ' does not match expected name ' + 'FSQA')

        # 加载Setup参数信息
        logger.info('开始从XML文件中读取 <Setup> 的参数配置' + self._xml)
        setupNodeName = root.xpath('/FSQA/Setup/Parameter/@name')
        setupNodeValue = root.xpath('/FSQA/Setup/Parameter/text()')

        if len(setupNodeName) == 0: raise VVTError("node <Setup> not found in parameter file： %s" % self._xml)

        i = 0
        setupNodeList = []
        for noteName in setupNodeName:
            setupNodeList.append((setupNodeName[i], setupNodeValue[i]))
            i = i + 1
        self._coreSettingMap['Setup'] = dict(setupNodeList)
        logger.debug('<Setup> 参数配置内容: ' + json.dumps(self._coreSettingMap['Setup']))
        logger.info('成功从XML文件中读取 <Setup> 参数配置')

        # 加载Markets参数信息
        logger.info('开始从XML文件中读取 <Markets> 的参数配置 ' + self._xml)
        marketsNodeName = root.xpath('/FSQA/Markets/Parameter/@name')
        marketsNodeValue = root.xpath('/FSQA/Markets/Parameter/text()')

        if len(marketsNodeName) == 0: raise VVTError("node <Markets> not found in parameter file： %s" % self._xml)

        i = 0
        marketsNodeList = []
        for noteName in marketsNodeName:
            marketsNodeList.append((marketsNodeName[i], marketsNodeValue[i]))
            i = i + 1
        self._coreSettingMap['Markets'] = dict(marketsNodeList)
        logger.debug('<Markets> 参数配置内容: ' + json.dumps(self._coreSettingMap['Markets']))
        logger.info('成功从XML文件中读取 <Markets> 参数配置')

        # 加载Analytics参数信息
        logger.info('开始从XML文件中读取 <Analytics> 的参数配置' + self._xml)
        analyticsNodeName = root.xpath('/FSQA/Analytics/Analytic/@type')

        if len(analyticsNodeName) == 0: raise VVTError("node <Analytics> not found in parameter file： %s" % self._xml)

        analyticsSettingMap = {}
        for noteName in analyticsNodeName:
            a = root.xpath("/FSQA/Analytics/Analytic[@type='" + noteName + "']")
            analyticParasNodeName = root.xpath("/FSQA/Analytics/Analytic[@type='" + noteName + "']/Parameter/@name")
            analyticParasNodeValue = root.xpath("/FSQA/Analytics/Analytic[@type='" + noteName + "']/Parameter/text()")

            if len(analyticParasNodeName) == 0: raise VVTError(
                "node <Analytics -- " + noteName + "> not found in parameter file： %s" % self._xml)

            i = 0
            analyticParasNodeList = []
            for noteParasName in analyticParasNodeName:
                analyticParasNodeList.append((analyticParasNodeName[i], analyticParasNodeValue[i]))
                i = i + 1
            analyticsSettingMap[noteName] = dict(analyticParasNodeList)

        self._coreSettingMap['Analytics'] = analyticsSettingMap

        logger.debug('<Analytics> 参数配置内容: ' + json.dumps(self._coreSettingMap['Analytics']))

        logger.info('成功从XML文件中读取 <Analytics> 参数配置')

    def printXML(self):
        logger.info(type(self._xmlconfig))
        # 将tree重新转化为字符串形式的xml文档，并输出
        logger.info(str(etree.tostring(self._xmlconfig, encoding="utf-8"), 'utf-8'))
        # 获得根节点
        root = self._xmlconfig.getroot()
        logger.info(type(root))
        # 输出根节点的名称
        # logger.info('root：', str(root.__repr__()))
        # 获得根节点的所有子节点
        children = root.getchildren()
        # logger.info('--------------输出产品信息--------------')
        # # 迭代这些子节点，并输出对应的属性和节点文本
        # for child in children:
        #     logger.info('product id = ', child.get('id'))
        #     logger.info('child[0].name = ', child[0].text)
        #     logger.info('child[1].price = ', child[1].text)

    def _validateInputs(self):

        # 检测xml文件是否存在
        try:
            f = open(self._xml)
            f.close()
        except IOError as e:
            logger.error(str(e.errno) + ': ' + e.strerror + ', filename: ' + str(e.filename))
            raise VVTError("FSQA启动必须的core.xml的配置文件路径及文件无法访问或不存在")

    def run(self):

        if self._coreSettingMap is None or len(self._coreSettingMap) == 0:
            raise VVTError("未找到效配置参数")

        self._initFromParams()

        self._analytics();

        end_time = time.time()

        execution_time = end_time - GlobleSettings.start_time

        logger.info(f'FSQA 本次运行时间:  {execution_time}')
        logger.info('FSQA 正常运行结束')

    def _analytics(self):
        logger.info('FSQA 计算任务启动')

        # 初始化全局计量参数
        # 估值日
        GlobleSettings.valuationDate = self._inputParas.valuationDate()

        # conventions参数
        GlobleSettings.conventions = self._inputParas._conventionConfigs

        # 初始化analytics manager
        logger.info('请求的计量任务清单: ' + json.dumps(self._inputParas.analytics()))
        self._analyticsManager = AnalyticsManager(self._inputParas)

        logger.info('FSQA 计算任务正常结束')
        # 日志参数和日志对象初始化

    def _initFromParams(self):
        # 从xml配置文件初始化引擎输入输出参数对象
        logger.info('初始化引擎输入参数<InputParameters>')
        self._inputParas = InputParameters(self._coreSettingMap)
        logger.info('初始化引擎输出参数<OutputParameters>')
        self._output_params = OutputParameters(self._coreSettingMap, self._inputParas)
