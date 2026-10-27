const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const { productionConfig } = require('./config.cjs');
const root = path.resolve(__dirname, '..');
const config = productionConfig(process.env);
execFileSync(process.execPath, [require.resolve('typescript/bin/tsc'), '--outDir', 'dist-production'], {cwd:root, stdio:'inherit'});
function copy(dir) {
  for (const item of fs.readdirSync(dir, {withFileTypes:true})) {
    const file = path.join(dir, item.name);
    if (item.isDirectory()) copy(file);
    else if (!item.name.endsWith('.ts')) {
      const dest = path.join(root, 'dist-production', path.relative(path.join(root, 'src'), file));
      fs.mkdirSync(path.dirname(dest), {recursive:true}); fs.copyFileSync(file, dest);
    }
  }
}
copy(path.join(root, 'src'));
fs.writeFileSync(path.join(root, 'dist-production/services/config.js'), `"use strict";\nObject.defineProperty(exports, "__esModule", { value: true });\nexports.API_ORIGIN = ${JSON.stringify(config.apiOrigin)};\nexports.DEMO_LOGIN = false;\n`);
const project = JSON.parse(fs.readFileSync(path.join(root, 'project.config.json'), 'utf8'));
project.appid = config.appid; project.miniprogramRoot = './'; project.description = 'CampusHub 学生端';
fs.writeFileSync(path.join(root, 'dist-production', 'project.config.json'), JSON.stringify(project, null, 2) + '\n');
console.log('PASS: production bundle created; official DevTools compilation is separate.');
