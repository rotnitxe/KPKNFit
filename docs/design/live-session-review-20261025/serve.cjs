const http=require('node:http');
const fs=require('node:fs');
const path=require('node:path');
const root=__dirname;
const server=http.createServer((req,res)=>{
  const url=new URL(req.url,'http://127.0.0.1');
  const relative=decodeURIComponent(url.pathname)==='/'?'prototipos.html':decodeURIComponent(url.pathname).replace(/^\/+/, '');
  const target=path.resolve(root,relative);
  if(!target.startsWith(root+path.sep)){res.writeHead(403);res.end();return;}
  if(!fs.existsSync(target)||!fs.statSync(target).isFile()){res.writeHead(404);res.end();return;}
  const mime={'.html':'text/html; charset=utf-8','.md':'text/plain; charset=utf-8','.png':'image/png','.json':'application/json','.zip':'application/zip'}[path.extname(target)]||'text/plain; charset=utf-8';
  res.writeHead(200,{'Content-Type':mime,'Cache-Control':'no-store'});fs.createReadStream(target).pipe(res);
});
server.listen(8769,'127.0.0.1',()=>console.log('KPKN prototypes: http://127.0.0.1:8769'));
