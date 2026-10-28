const { URL } = require('node:url');
function productionConfig(env) {
  if (env.CAMPUSHUB_DEMO_LOGIN) throw new Error('Production demo login is forbidden');
  const apiOrigin = env.CAMPUSHUB_API_ORIGIN || '';
  const appid = env.CAMPUSHUB_APPID || '';
  if (!/^wx[0-9a-f]{16}$/i.test(appid)) throw new Error('Production AppID is required');
  if (env.CAMPUSHUB_UPLOAD_ONLY === 'true') {
    if (apiOrigin) throw new Error('Upload-only build must leave API unconfigured');
    return {apiOrigin:'', appid};
  }
  let url;
  try { url = new URL(apiOrigin); } catch { throw new Error('Production API origin is required'); }
  if (url.protocol !== 'https:' || url.username || url.password || url.port || url.search || url.hash || url.pathname !== '/api/v1' || url.hostname === 'localhost' || !url.hostname.includes('.') || /^\d+\.\d+\.\d+\.\d+$/.test(url.hostname) || url.origin + url.pathname !== apiOrigin) throw new Error('Production API origin must be an HTTPS domain ending in /api/v1');
  return { apiOrigin, appid };
}
module.exports = { productionConfig };
