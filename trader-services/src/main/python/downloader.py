#!/usr/bin/python3
# -*- coding: UTF-8 -*-

import sys
import os
from datetime import date, datetime
from contextlib import closing
from tqsdk import TqApi, TqSim
from tqsdk.tools import DataDownloader
import time

instrumentId = sys.argv[1]
beginTimeStr = sys.argv[2]
endTimestr = sys.argv[3]

beginTime = datetime.strptime(beginTimeStr, "%Y%m%d%H%M%S")# 20200203090000
endTime = datetime.strptime(endTimestr, "%Y%m%d%H%M%S") # 202000205150000

api = TqApi(TqSim())
download_tasks = {}

print('Download '+instrumentId+' from '+str(beginTime)+' to '+str(endTime))

download_tasks[instrumentId] = DataDownloader(
        api,
        symbol_list=[instrumentId],
        dur_sec=0,
        start_dt=datetime(2020, 6, 12, 9, 0, 0),
        end_dt=datetime(2020, 6, 15, 15, 0, 0),
        csv_file_name=instrumentId+'-'+beginTimeStr+'-'+endTimestr+"-tick.csv")

# 使用with closing机制确保下载完成后释放对应的资源
with closing(api):
    while not all([v.is_finished() for v in download_tasks.values()]):
        api.wait_update()
        print("progress: ", { k:("%.2f%%" % v.get_progress()) for k,v in download_tasks.items() })
