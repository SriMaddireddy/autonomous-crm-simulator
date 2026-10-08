#!/usr/bin/env python3
"""Exercise real HTTP APIs; fail if any invariant is violated. Python stdlib only."""
import argparse,json,time,uuid,urllib.request
p=argparse.ArgumentParser();p.add_argument('--url',default='http://127.0.0.1:8080');p.add_argument('--expect-mode',default=None);args=p.parse_args()
def request(path,data=None):
 req=urllib.request.Request(args.url+path,data=None if data is None else json.dumps(data).encode(),headers={'Content-Type':'application/json'})
 with urllib.request.urlopen(req,timeout=30) as r:return json.load(r)
assert request('/actuator/health')['status']=='UP'
key=str(uuid.uuid4());payload={'campaignId':'campaign-0001','idempotencyKey':key}
w=request('/api/workflows',payload);assert request('/api/workflows',payload)['id']==w['id']
deadline=time.monotonic()+60
while time.monotonic()<deadline:
 w=request('/api/workflows/'+w['id'])
 if w['status'] in ('SUCCEEDED','FAILED'):break
 time.sleep(.1)
assert w['status']=='SUCCEEDED',w
result=w['result'];assert len(result['decisions'])==5;assert len(result['retrieval']['logs'])==5
if args.expect_mode:assert result['retrieval']['mode']==args.expect_mode,result['retrieval']
assert all(d['evidenceIds'] for d in result['decisions'])
r=request('/mcp',{'jsonrpc':'2.0','id':1,'method':'tools/list'});assert len(r['result']['tools'])==7
r=request('/mcp',{'jsonrpc':'2.0','id':2,'method':'tools/call','params':{'name':'get_workflow','arguments':{'workflowId':w['id']}}});assert not r['result']['isError']
events=request('/api/workflows/'+w['id']+'/events');assert len(events)>=3
print(json.dumps({'status':'PASS','workflow':w['id'],'action':result['action'],'retrievalMode':result['retrieval']['mode'],'skills':5},indent=2))
