const {execFileSync} = require('node:child_process');
const path = require('node:path');
const account = require('../upload-account.json');
execFileSync(process.execPath, [path.join(__dirname,'build-production.cjs')], {
  stdio:'inherit', env:{...process.env,CAMPUSHUB_APPID:account.appid,CAMPUSHUB_UPLOAD_ONLY:'true',CAMPUSHUB_API_ORIGIN:''}
});
