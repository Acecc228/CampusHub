// Real Edge + production Vue build + local Spring/MySQL; no mocked API or WeChat claim.
const {chromium, expect} = require('../admin-web/node_modules/@playwright/test');
const fs = require('node:fs');
const path = require('node:path');
if (process.env.CH_M1_UI_APPLY !== 'true' || !process.env.CH_M1_UI_PASSWORD) throw new Error('Use Test-M1ProductionUi.ps1 -Apply');
const evidence = path.resolve(__dirname, '../docs/verification/m1-production-ui');
fs.mkdirSync(evidence, {recursive:true});
let stage='launch';
(async () => {
  const browser = await chromium.launch({channel:'msedge',headless:true});
  const context = await browser.newContext({baseURL:'http://127.0.0.1:4173',viewport:{width:1440,height:960}});
  const page = await context.newPage();
  const failures=[];
  page.on('pageerror', () => failures.push('browser JavaScript error'));
  let authPath='';
  page.on('request', request => { if (request.method()==='POST' && request.url().includes('/auth/')) authPath=new URL(request.url()).pathname; });
  try {
    stage='login page';
    await page.goto('http://127.0.0.1:4173/login');
    await expect(page.locator('#username')).toBeVisible();
    await expect(page.getByText('演示账号', {exact:false})).toHaveCount(0);
    await page.screenshot({path:path.join(evidence,'login-1440.png'),fullPage:true});
    await page.locator('#username').fill(process.env.CH_M1_UI_USERNAME);
    await page.locator('#password').fill(process.env.CH_M1_UI_PASSWORD);
    stage='formal login';
    await page.getByRole('button',{name:'登录管理中心',exact:true}).click();
    await expect(page).not.toHaveURL(/login/);
    expect(authPath).toBe('/api/v1/auth/login');
    stage='create form';
    await page.getByRole('link',{name:'创建活动',exact:true}).click();
    const title='M1 production UI '+Date.now();
    await page.locator('#title').fill(title);
    await page.locator('#summary').fill('正式账号登录与活动发布本地回归');
    const categories=await (await page.request.get('/api/v1/activity-categories')).json();
    await page.getByRole('combobox',{name:'活动分类',exact:true}).press('ArrowDown');
    await page.getByRole('option',{name:categories[0].name,exact:true}).click();
    await page.locator('#location').fill('测试教学楼');
    await page.locator('#content').fill('真实浏览器生产构建联调，仅测试数据。');
    const localTime = hours => new Date(Date.now()+(hours+8)*3600000).toISOString().slice(0,19).replace('T',' ');
    for (const [key,hours] of [['registrationStart',-1],['registrationEnd',24],['startTime',48],['endTime',50]]) {
      await page.locator('#'+key).fill(localTime(hours));
    }
    await page.getByRole('button',{name:'创建草稿',exact:true}).click();
    stage='save draft';
    await expect(page).toHaveURL(/\/activities$/);
    await page.getByRole('link',{name:title,exact:true}).click();
    await expect(page).toHaveURL(url=>/^\/activities\/\d+$/.test(url.pathname));
    const activityId=Number(new URL(page.url()).pathname.split('/').pop());
    expect(Number.isSafeInteger(activityId) && activityId>0).toBe(true);
    stage='publish';
    await page.getByRole('button',{name:'发布活动',exact:true}).click();
    await page.getByRole('dialog').getByRole('button',{name:'发布活动',exact:true}).click();
    await expect(page.getByRole('button',{name:'下架活动',exact:true})).toBeVisible();
    await page.screenshot({path:path.join(evidence,'published-1440.png'),fullPage:true});
    await page.setViewportSize({width:900,height:960});
    await page.screenshot({path:path.join(evidence,'published-900.png'),fullPage:true});
    const cookies=await context.cookies();
    stage='session attributes';
    const sessionCookie=cookies.find(c=>c.name==='JSESSIONID');
    expect(sessionCookie?.httpOnly).toBe(true);
    expect(sessionCookie?.sameSite).toBe('Strict');
    expect(failures).toEqual([]);
    stage='logout';
    const csrf=await (await page.request.get('/api/v1/auth/csrf')).json();
    expect((await page.request.post('/api/v1/auth/logout',{headers:{[csrf.headerName]:csrf.token}})).status()).toBe(204);
    expect((await page.request.get('/api/v1/auth/me')).status()).toBe(401);
    fs.writeFileSync(path.join(evidence,'result.json'),JSON.stringify({status:'PASS',browser:'real Edge headless',frontend:'production build preview',backend:'local dev profile / real campushub_test MySQL',formalLogin:true,createdPublishedActivityId:activityId,logout:true,browserErrors:failures.length,publicHttps:'NOT_RUN',wechat:'NOT_RUN'},null,2)+'\n');
    console.log('PASS: real Edge formal login, create/publish, logout; local test only');
  } finally { await context.close();await browser.close(); }
})().catch(error=>{
  const safe=String(error.message).replaceAll(process.env.CH_M1_UI_PASSWORD,'[REDACTED]').slice(0,700);
  fs.writeFileSync(path.resolve(__dirname,'../.local/m1-ui-failure.txt'),stage+'\n'+safe);
  console.error('FAIL: UI acceptance at '+stage+'; sanitized diagnostic in .local/m1-ui-failure.txt');process.exitCode=1;
});
