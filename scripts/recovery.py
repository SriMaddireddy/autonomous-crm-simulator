#!/usr/bin/env python3
"""Docker/Postgres crash recovery test: seed an expired claimed workflow, kill/restart app, verify recovery.
This exercises process restart and persistent lease recovery, not arbitrary database loss.
"""
import json,subprocess,time,uuid,urllib.request
url='http://127.0.0.1:8080'
def request(path,data=None):
 req=urllib.request.Request(url+path,data=None if data is None else json.dumps(data).encode(),headers={'Content-Type':'application/json'})
 with urllib.request.urlopen(req,timeout=10) as r:return json.load(r)
def sql(statement):
 return subprocess.check_output(['docker','compose','exec','-T','postgres','psql','-U','crm','-d','crm','-At','-c',statement],text=True).strip()
subprocess.run(['docker','compose','kill','-s','SIGKILL','app'],check=True)
id=str(uuid.uuid4());key=str(uuid.uuid4());now=int(time.time()*1000)
sql(f"INSERT INTO workflows(id,campaign_id,idempotency_key,status,attempts,owner,lease_until,created_at,updated_at) VALUES ('{id}','campaign-0001','{key}','RUNNING',1,'dead-worker',{now-1},{now},{now});")
subprocess.run(['docker','compose','up','-d','app'],check=True)
deadline=time.monotonic()+120;w=None
while time.monotonic()<deadline:
 try:
  w=request('/api/workflows/'+id)
  if w['status'] in ('SUCCEEDED','FAILED'):break
 except (OSError,ValueError):pass
 time.sleep(1)
assert w and w['status']=='SUCCEEDED',w
assert w['attempts']==2,w
assert sql(f"SELECT COUNT(*) FROM decisions WHERE workflow_id='{id}';")=='5'
assert request('/api/workflows',{'campaignId':'campaign-0001','idempotencyKey':key})['id']==id
print(json.dumps({'status':'PASS','workflow':id,'attempts':w['attempts'],'persistedDecisions':5},indent=2))
