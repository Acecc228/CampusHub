const { test } = require('node:test');
const assert = require('node:assert/strict');
const { productionConfig } = require('../scripts/config.cjs');

test('explicit upload-only build has no invented API and still requires formal AppID', () => {
  assert.deepEqual(productionConfig({CAMPUSHUB_UPLOAD_ONLY:'true',CAMPUSHUB_APPID:'wx1234567890abcdef'}),{apiOrigin:'',appid:'wx1234567890abcdef'});
  assert.throws(()=>productionConfig({CAMPUSHUB_UPLOAD_ONLY:'true'}));
  assert.throws(()=>productionConfig({CAMPUSHUB_UPLOAD_ONLY:'true',CAMPUSHUB_APPID:'wx1234567890abcdef',CAMPUSHUB_API_ORIGIN:'https://example.com/api/v1'}));
});

test('upload-only bundle rejects requests before transport with clear configuration error', async () => {
  const vm=require('node:vm'),fs=require('node:fs');
  const module={exports:{}};
  vm.runInNewContext(fs.readFileSync(require.resolve('../dist/services/client'),'utf8'),{exports:module.exports,require:()=>({API_ORIGIN:'',DEMO_LOGIN:false})});
  let calls=0;
  const client=module.exports.createClient(async()=>{calls++;});
  await assert.rejects(client.request('/auth/csrf'), e=>e.code==='API_NOT_CONFIGURED');
  assert.equal(calls,0);
});
test('production requires HTTPS origin and a valid appid; demo is forbidden', () => {
  for (const env of [{}, { CAMPUSHUB_API_ORIGIN: 'http://example.com/api/v1', CAMPUSHUB_APPID: 'wx1234567890abcdef' }, { CAMPUSHUB_API_ORIGIN: 'https://example.com/api/v1', CAMPUSHUB_APPID: 'wx1234567890abcdef', CAMPUSHUB_DEMO_LOGIN: 'true' }, { CAMPUSHUB_API_ORIGIN: 'https://localhost/api/v1', CAMPUSHUB_APPID: 'wx1234567890abcdef' }]) assert.throws(() => productionConfig(env));
  assert.deepEqual(productionConfig({CAMPUSHUB_API_ORIGIN:'https://campus.example.com/api/v1', CAMPUSHUB_APPID:'wx1234567890abcdef'}), {apiOrigin:'https://campus.example.com/api/v1', appid:'wx1234567890abcdef'});
});
test('wechat login resets CSRF after rotating the session', async () => {
  const { createClient } = require('../dist/services/client');
  const calls=[]; const replies=[{data:{headerName:'X-CSRF',token:'old'}},{cookies:['JSESSIONID=new']},{data:{headerName:'X-CSRF',token:'new'}},{}];
  const client=createClient(async input => { calls.push(input); return {statusCode:200,header:{},...replies.shift()}; });
  await client.request('/auth/wechat-login','POST',{code:'one-use'}); await client.request('/auth/logout','POST');
  assert.equal(calls[2].url.endsWith('/auth/csrf'),true); assert.equal(calls[3].header['X-CSRF'],'new');
});

test('production bundle refuses the demo endpoint before transport', async () => {
  const vm = require('node:vm');
  const fs = require('node:fs');
  const module = { exports: {} };
  vm.runInNewContext(fs.readFileSync(require.resolve('../dist/services/client'), 'utf8'), {exports: module.exports, require: () => ({API_ORIGIN: 'https://campus.example.com/api/v1', DEMO_LOGIN: false})});
  const { createClient } = module.exports;
  let calls = 0;
  const client = createClient(async () => { calls++; });
  await assert.rejects(client.request('/auth/demo-login', 'POST'), e => e.code === 'DEMO_DISABLED');
  assert.equal(calls, 0);
});
