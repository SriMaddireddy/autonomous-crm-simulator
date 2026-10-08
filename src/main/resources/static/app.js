const $=id=>document.getElementById(id); let selected=null, workflows=[], busy=false;
const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
async function api(path,options){const r=await fetch(path,options);if(!r.ok)throw Error(`Request failed (${r.status})`);return r.json();}
function pill(s){return `<span class="pill ${esc(s)}">${esc(s)}</span>`;}
async function refresh(){if(busy)return;busy=true;try{
 const [stats,rows]=await Promise.all([api('/api/stats'),api('/api/workflows?limit=500')]);workflows=rows;
 $('completed').textContent=stats.workflows.SUCCEEDED;$('active').textContent=`${stats.workers.active} / ${stats.workers.capacity}`;
 $('decisions').textContent=stats.workflows.decisions;$('latency').textContent=`${stats.retrieval.meanLatencyMs.toFixed(1)} ms`;
 $('queue').textContent=`${stats.workflows.QUEUED} queued · ${stats.workflows.FAILED} failed`;$('health').textContent='● Connected';
 $('ledger').innerHTML=rows.length?rows.map(w=>`<tr tabindex="0" role="button" aria-label="Inspect ${esc(w.campaignId)}" data-id="${esc(w.id)}" class="${w.id===selected?'selected':''}"><td>${esc(w.campaignId)}</td><td>${pill(w.status)}</td><td>${w.result?pill(w.result.action):'—'}</td><td>${w.attempts}</td></tr>`).join(''):'<tr><td colspan="4">No workflows yet. Evaluate the seeded campaigns to begin.</td></tr>';
 document.querySelectorAll('[data-id]').forEach(row=>{row.onclick=()=>{selected=row.dataset.id;inspect();};row.onkeydown=e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();row.click();}};});
 if(selected)await inspect();
 }catch(e){$('health').textContent='● Disconnected';$('notice').textContent=e.message;}finally{busy=false;}}
async function inspect(){const id=selected;const w=workflows.find(x=>x.id===id);if(!w)return;
 try{const events=await api(`/api/workflows/${id}/events`);if(selected!==id)return;const r=w.result;
 $('inspect').innerHTML=`<div class="eyebrow">${esc(w.campaignId)}</div><div class="result">${r?esc(r.action):esc(w.status)}</div>${r?`<p class="summary">${esc(r.explanation)}</p><p class="notice">${esc(r.explanationMode)} · ${r.retrieval.latencyMs.toFixed(2)} ms retrieval</p>${r.decisions.map(d=>`<div class="skill"><div class="skill-head"><span>${esc(d.skill)}</span>${pill(d.action)}</div><p>${esc(d.reason)}</p></div>`).join('')}<details class="evidence"><summary>Historical evidence (${r.retrieval.logs.length})</summary>${r.retrieval.logs.map(log=>`<p><b>#${log.id} · similarity ${log.similarity.toFixed(3)}</b><br>${esc(log.content)}</p>`).join('')}</details>`:'<p class="summary">The worker will claim this campaign and commit its complete result.</p>'}${w.error?`<p class="summary">${esc(w.error)}</p>`:''}<details open><summary>State transitions</summary>${events.map(e=>`<div class="event">${esc(new Date(e.CREATED_AT??e.created_at).toLocaleTimeString())} · ${esc(e.EVENT_TYPE??e.event_type)} · attempt ${esc(e.ATTEMPT??e.attempt)}</div>`).join('')}</details>`;
 }catch(e){$('notice').textContent=e.message;}}
$('run').onclick=async()=>{const b=$('run');b.disabled=true;try{const rows=await api('/api/workflows/batch',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({count:500,runKey:crypto.randomUUID()})});$('notice').textContent=`Queued ${rows.length} workflows. Refreshes every 2 seconds.`;selected=rows[0]?.id;await refresh();}catch(e){$('notice').textContent=e.message;}finally{b.disabled=false;}};
$('refresh').onclick=refresh;refresh();setInterval(refresh,2000);
