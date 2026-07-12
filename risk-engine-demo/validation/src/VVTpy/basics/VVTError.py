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
"""
Valuation & Validation Tools python
@desc
@author (c) KPMG Advisory(China) LTD. FRM



TODO: -
TODO: -
"""

# 可直接抛出到JupyterLab中显示
import sys
import traceback

import better_exceptions

from FSQApy.data.utilities.LogUtil import logger

better_exceptions.hook()
ipython = None

try:
    from IPython import get_ipython

    ipython = get_ipython()
except Exception:
    pass


###############################################################################

def _hide_traceback(exc_tuple=None, filename=None, tb_offset=None,
                    exception_only=False, running_compiled_code=False):
    etype, value, _ = sys.exc_info()
    ip = ipython.InteractiveTB

    if ipython is not None:
        msg = ipython._showtraceback(etype, value,
                                     ip.get_exception_only(etype, value))
    else:
        msg = None
    return msg


def func_name():
    return traceback.extract_stack(None, 2)[0][2]


def suppressTraceback():
    sys.tracebacklimit = 0
    ipython.showtraceback = _hide_traceback


###############################################################################


class VVTError(Exception):
    ''' VVT中定义的基础异常类,继承Exception。'''

    def __init__(self,
                 message: str):  # 异常信息
        ''' 由错误信息初始化 '''
        self._message = message
        logger.error(self._message)

    def _print(self):
        ''' 打印函数 '''
        logger.error(self._message)

###############################################################################
