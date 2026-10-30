const crypto = require('crypto');

function b64url(input) {
  return Buffer.from(input).toString('base64').replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
}

const secret = "troque-esta-chave-em-cada-ambiente-por-uma-gerada-com-32bytes";
const now = Math.floor(Date.now() / 1000);
const header = { alg: "HS256", typ: "JWT" };
const payload = { sub: "sistema-teste", scope: "requests:write requests:read", iat: now, exp: now + 3600 };
const data = b64url(JSON.stringify(header)) + "." + b64url(JSON.stringify(payload));
const sig = crypto.createHmac('sha256', secret).update(data).digest('base64').replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');

console.log(data + "." + sig);
