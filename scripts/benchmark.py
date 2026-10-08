#!/usr/bin/env python3
"""Measure this deployment. Never substitute these results for historical claims."""
import argparse,json,time,uuid,urllib.request,statistics,pathlib,platform
p=argparse.ArgumentParser();p.add_argument('--url',default='http://127.0.0.1:8080');p.add_argument('--count',type=int,default=500);p.add_argument('--output',default='benchmark.json');args=p.parse_args()
if not 1<=args.count<=500:p.error('count must be 1..500')
def request(path,data=None):
 req=urllib.request.Request(args.url+path,data=None if data is None else json.dumps(data).encode(),headers={'Content-Type':'application/json'})
 with urllib.request.urlopen(req,timeout=60) as r:return json.load(r)
# Warm retrieval separately, then measure queue-to-completion wall time.
for _ in range(10):request('/api/campaigns/campaign-0001/evidence')
started=time.monotonic();rows=request('/api/workflows/batch',{'count':args.count,'runKey':str(uuid.uuid4())});ids={w['id'] for w in rows}
if len(ids)!=args.count:raise SystemExit(f'Expected {args.count} seeded campaigns; got {len(ids)}')
deadline=time.monotonic()+300
while time.monotonic()<deadline:
 # Fetch each submitted ID: avoids missing old rows if someone creates another batch.
 rows=[request('/api/workflows/'+id) for id in ids]
 done=sum(w['status'] in ('SUCCEEDED','FAILED') for w in rows)
 if done==len(ids):break
 time.sleep(.25)
else:raise SystemExit('Timed out waiting for workflow completion')
elapsed=time.monotonic()-started;ok=[w for w in rows if w['status']=='SUCCEEDED'];times=sorted(w['result']['retrieval']['latencyMs'] for w in ok)
percentile=lambda values,q:values[min(len(values)-1,int((len(values)-1)*q))] if values else None
report={'measuredAtUtc':time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime()),'clientPlatform':platform.platform(),'url':args.url,'submitted':len(rows),'succeeded':len(ok),'failed':len(rows)-len(ok),'wallSeconds':round(elapsed,3),'workflowsPerSecond':round(len(ok)/elapsed,2),'retrieval':{'mode':ok[0]['result']['retrieval']['mode'] if ok else None,'p50Ms':percentile(times,.5),'p95Ms':percentile(times,.95),'maxMs':max(times) if times else None},'serviceStats':request('/api/stats'),'caveats':['Synthetic data; one local run, not production evidence.','Includes HTTP polling overhead.','Retrieval durations are observed under workflow concurrency.','No manual-evaluation baseline or reduction percentage measured.']}
pathlib.Path(args.output).write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))
if len(ok)!=len(rows):raise SystemExit(1)
