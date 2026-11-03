const fs = require('fs');
const vm = require('vm');
const assert = require('assert');
const path = require('path');
const script = fs.readFileSync(path.join(__dirname, '../partner_auto/ui.html'), 'utf8').match(/<script>([\s\S]*)<\/script>/)[1];
const PP = '{"startPoint":{"x":20,"y":20},"lines":[]}';
const result = {status:'complete', entries:[], selected_entry:null, settings:{field_size_in:144,auto_duration_s:30}, actions:[], timeline:[], reservations:[], diagnostics:[]};

function makePage(storage = new Map(), response = async () => ({ok:true,status:200,json:async()=>result}), protocol='http:') {
  const fields=[];
  class Element {
    constructor(tag,id='') { this.tag=tag; this.id=id; this.value=''; this.children=[]; this.attrs={}; this.events={}; this.textContent=''; }
    addEventListener(name,fn) { this.events[name]=fn; }
    replaceChildren(...values) { this.children=values; }
    append(...values) { this.children.push(...values); }
    setAttribute(k,v) { this.attrs[k]=String(v); }
    querySelectorAll() { return fields; }
    add(value) { this.children.push(value); }
    click() { return this.events.click?.(); }
    focus() {}
  }
  const ids=new Map(); const el=id=>{if(!ids.has(id))ids.set(id,new Element('div',id));return ids.get(id);};
  for(const [key,value] of Object.entries({field_size_in:144,speed_in_s:30,robot_length_in:18,robot_width_in:18,acceleration_in_s2:40,turn_rate_rad_s:Math.PI,clearance_in:2,timing_uncertainty_s:.5,start_delay_s:0,auto_duration_s:30})) { const item=el(key); item.value=String(value); fields.push(item); }
  el('format').value='auto'; el('entry').value=''; el('clock').value='0';
  const requests=[];
  const context={document:{getElementById:el,createElement:tag=>new Element(tag),createElementNS:(_,tag)=>new Element(tag)},Option:class{constructor(text,value){this.text=text;this.value=value;}},localStorage:{getItem:key=>storage.get(key)||null,setItem:(key,value)=>storage.set(key,value)},location:{protocol,hostname:'127.0.0.1'},navigator:{onLine:true},fetch:async(url,options)=>{requests.push({url,options,body:options?.body?JSON.parse(options.body):null});return response(url,options);},URL:{createObjectURL:()=>'',revokeObjectURL:()=>{}},Blob:class{},setTimeout,console};
  vm.runInNewContext(script,context);
  return {el,requests,storage,context};
}

(async()=>{
  let fail=true;
  const page=makePage(new Map(),async()=>{if(fail)throw new TypeError('NetworkError when attempting to fetch resource.');return{ok:true,status:200,json:async()=>result};});
  page.el('paste').value=PP;
  await page.el('import').click();
  assert.equal(page.el('paste').value,PP,'Network failure must preserve exact pasted text');
  assert(page.el('error').textContent.includes('Python on this computer'));
  assert(page.el('error').textContent.includes('Wi-Fi can stay connected'));
  assert.equal(Object.keys(page.requests[0].body.sources).length,1);
  fail=false;
  await page.el('import').click();
  assert.equal(Object.keys(page.requests[1].body.sources).length,1,'Retry must not duplicate the pasted class');
  assert.equal(page.el('paste').value,PP,'Successful imports also retain editable source');
  assert.equal(page.el('export').disabled,false);
  assert(page.requests.every(r=>r.url==='/api/import' && r.options.mode==='same-origin'),'Import must only contact its local relative endpoint');
  page.el('paste').value=PP+'\n';
  page.el('paste').events.input();
  await page.el('import').click();
  assert.equal(Object.keys(page.requests[2].body.sources).length,1,'Editing and retrying updates the same source');
  assert.equal(Object.values(page.requests[2].body.sources)[0],PP+'\n');
  const restored=makePage(page.storage);
  assert.equal(restored.el('paste').value,PP+'\n','Reload must restore the draft');
  await restored.el('import').click();
  assert.equal(Object.keys(restored.requests[0].body.sources).length,1);
  const errorPage=makePage(new Map(),async()=>({ok:false,status:400,json:async()=>({error:'Missing superclass PartnerBase'})}));
  errorPage.el('paste').value='class PartnerAuto extends PartnerBase {}';
  await errorPage.el('import').click();
  assert.equal(errorPage.el('paste').value,'class PartnerAuto extends PartnerBase {}');
  assert(errorPage.el('error').textContent.includes('PartnerBase'));
  const filePage=makePage(new Map(),async()=>{throw new Error('No request expected');},'file:');
  filePage.el('paste').value=PP;
  await filePage.el('import').click();
  assert.equal(filePage.requests.length,0);
  assert(filePage.el('error').textContent.includes('launch.bat'));
  assert.equal(filePage.el('paste').value,PP);
  restored.el('new-paste').click();
  assert.equal(restored.el('paste').value,'');
  restored.el('paste').value='class Another {}';
  await restored.el('import').click();
  assert.equal(Object.keys(restored.requests[1].body.sources).length,2,'New paste retains the earlier file');
  console.log('UI regression checks passed: draft retention, failed/edited retries, local-only fetch, reload and multi-file editing.');
})().catch(error=>{console.error(error);process.exitCode=1;});
