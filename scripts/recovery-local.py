#!/usr/bin/env python3
"""Isolated H2 process-crash/persistence check. Extracts the H2 driver from the built app.
Requires JDK 21. Never touches the running dashboard database.
"""
import argparse,json,os,pathlib,subprocess,tempfile,time,urllib.request,uuid,zipfile,socket
p=argparse.ArgumentParser();p.add_argument('--java',default='java');p.add_argument('--jar',default='target/autonomous-crm-simulator-1.0.0.jar');args=p.parse_args()
jar=pathlib.Path(args.jar).resolve()
with socket.socket() as sock:sock.bind(('127.0.0.1',0));port=sock.getsockname()[1]
url=f'http://127.0.0.1:{port}'
def request(path,data=None):
 req=urllib.request.Request(url+path,data=None if data is None else json.dumps(data).encode(),headers={'Content-Type':'application/json'})
 with urllib.request.urlopen(req,timeout=5) as r:return json.load(r)
with tempfile.TemporaryDirectory(prefix='crm-recovery-') as temp:
 temp=pathlib.Path(temp);dburl='jdbc:h2:file:'+str(temp/'crm')+';WRITE_DELAY=0'
 with zipfile.ZipFile(jar) as z:
  driver=next(x for x in z.namelist() if x.startswith('BOOT-INF/lib/h2-'))
  h2=temp/'h2.jar';h2.write_bytes(z.read(driver))
 env=dict(os.environ,DATABASE_URL=dburl,DATABASE_USER='sa',DATABASE_PASSWORD='',CRM_SEED_HISTORY='100',CRM_SEED_CAMPAIGNS='5',OPENAI_API_KEY='',PORT=str(port))
 log=open(temp/'server.log','wb');proc=None
 def start():
  proc=subprocess.Popen([args.java,'-jar',str(jar)],env=env,stdout=log,stderr=subprocess.STDOUT,cwd=temp)
  deadline=time.monotonic()+30
  while time.monotonic()<deadline:
   if proc.poll() is not None:raise RuntimeError((temp/'server.log').read_text())
   try:
    if request('/api/campaigns/campaign-0001')['id']=='campaign-0001':return proc
   except (OSError,ValueError):pass
   time.sleep(.1)
  proc.kill();proc.wait();raise RuntimeError('Readiness timed out')
 try:
  proc=start()
  # Commit one genuine result, then SIGKILL the process to test persisted data across an abrupt stop.
  saved=request('/api/workflows',{'campaignId':'campaign-0001','idempotencyKey':'committed-before-crash'})
  deadline=time.monotonic()+15
  while time.monotonic()<deadline:
   saved=request('/api/workflows/'+saved['id'])
   if saved['status']=='SUCCEEDED':break
   time.sleep(.05)
  assert saved['status']=='SUCCEEDED',saved
  proc.kill();proc.wait();proc=None
  # Inject a claimed row representing a crash after lease acquisition, before result commit.
  id=str(uuid.uuid4());now=int(time.time()*1000)
  statement=f"INSERT INTO workflows(id,campaign_id,idempotency_key,status,attempts,owner,lease_until,created_at,updated_at) VALUES ('{id}','campaign-0002','expired-claim','RUNNING',1,'dead-worker',{now-1},{now},{now});"
  completed=subprocess.run([args.java,'-cp',str(h2),'org.h2.tools.Shell','-url',dburl,'-user','sa','-password','','-sql',statement],capture_output=True,text=True,check=True)
  assert 'Error:' not in completed.stdout,completed.stdout
  proc=start();deadline=time.monotonic()+15
  while time.monotonic()<deadline:
   w=request('/api/workflows/'+id)
   if w['status'] in ('SUCCEEDED','FAILED'):break
   time.sleep(.1)
  assert w['status']=='SUCCEEDED' and w['attempts']==2,w
  assert len(w['result']['decisions'])==5
  restored=request('/api/workflows/'+saved['id']);assert restored['result']==saved['result']
  assert request('/api/workflows',{'campaignId':'campaign-0001','idempotencyKey':'committed-before-crash'})['id']==saved['id']
  assert request('/api/stats')['workflows']['decisions']==10
  print(json.dumps({'status':'PASS','mode':'H2 file database','committedResultSurvivedSigkill':True,'expiredClaimRecovered':True,'recoveredAttempt':2,'persistedDecisions':10,'caveat':'Claimed row injected offline; not a random kill during an active model call.'},indent=2))
 finally:
  if proc and proc.poll() is None:proc.terminate();proc.wait(timeout=25)
  log.close()
