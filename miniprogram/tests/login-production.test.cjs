const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const { ApiError } = require('../dist/services/client');
test('wechat timeout locks another login until status can be verified', async () => {
  let definition, codes = 0, posts = 0;
  const session = { loginWechat: async code => { posts++; assert.equal(code, 'fresh'); throw new ApiError(0, 'TIMEOUT', 'timeout'); }, me: async () => { throw new Error('offline'); } };
  const modules = {'../../services/config':{DEMO_LOGIN:false}, '../../services/client':{ApiError, errorMessage:()=>'error'}, '../../services/session':{session}};
  vm.runInNewContext(fs.readFileSync(require.resolve('../dist/pages/login/index.js'), 'utf8'), {exports:{}, require:id=>modules[id], Page:value=>definition=value, wx:{login: ({success})=>{codes++; success({code:'fresh'});}}, getCurrentPages:()=>[]});
  const page={...definition,data:{...definition.data},setData(v){Object.assign(this.data,v)}};
  await page.submit(); await page.submit(); await page.verify();
  assert.equal(codes,1); assert.equal(posts,1); assert.equal(page.data.uncertain,true);
});

test('definitive unauthenticated readback unlocks a fresh explicit WeChat login', async () => {
  let definition, codes = 0, posts = 0, navigated = 0;
  const session = {
    loginWechat: async code => { posts++; assert.equal(code, 'fresh-' + posts); if (posts === 1) throw new ApiError(503, 'SERVICE_UNAVAILABLE', 'unavailable'); },
    me: async () => { throw new ApiError(401, 'UNAUTHORIZED', 'login required'); },
  };
  const modules = {'../../services/config':{DEMO_LOGIN:false}, '../../services/client':{ApiError, errorMessage:()=>'error'}, '../../services/session':{session}};
  vm.runInNewContext(fs.readFileSync(require.resolve('../dist/pages/login/index.js'), 'utf8'), {exports:{}, require:id=>modules[id], Page:value=>definition=value,
    wx:{login: ({success})=>success({code:'fresh-' + (++codes)}), switchTab:()=>navigated++}, getCurrentPages:()=>[]});
  const page={...definition,data:{...definition.data},setData(v){Object.assign(this.data,v)}};
  await page.submit();
  assert.equal(page.data.uncertain,true);
  await page.verify();
  assert.equal(page.data.uncertain,false);
  assert.equal(posts,1);
  await page.submit();
  assert.equal(posts,2); assert.equal(codes,2); assert.equal(navigated,1);
});

test('successful readback recovers a lost login response without another code', async () => {
  let definition, codes=0, navigated=0;
  const session={loginWechat:async()=>{throw new ApiError(0,'TIMEOUT','timeout');},me:async()=>({roles:['STUDENT']})};
  const modules={'../../services/config':{DEMO_LOGIN:false},'../../services/client':{ApiError,errorMessage:()=>''},'../../services/session':{session}};
  vm.runInNewContext(fs.readFileSync(require.resolve('../dist/pages/login/index.js'),'utf8'),{exports:{},require:id=>modules[id],Page:v=>definition=v,wx:{login:({success})=>{codes++;success({code:'fresh'});},switchTab:()=>navigated++},getCurrentPages:()=>[]});
  const page={...definition,data:{...definition.data},setData(v){Object.assign(this.data,v)}};
  await page.submit();await page.verify();
  assert.equal(page.data.uncertain,false);assert.equal(codes,1);assert.equal(navigated,1);
});
