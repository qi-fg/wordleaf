const http=require('node:http'),fs=require('node:fs'),path=require('node:path');
const root=path.join(__dirname,'app/assets');
http.createServer((req,res)=>{
  const p=decodeURIComponent(new URL(req.url,'http://localhost').pathname);
  if(p==='/qa-blank'){res.setHeader('Content-Type','text/html');res.end('<!doctype html><html><head><title>QA storage fixture</title></head><body></body></html>');return;}
  const file=path.resolve(root,'.'+(p==='/'?'/index.html':p));
  if(!file.startsWith(root+path.sep)){res.writeHead(403);res.end();return;}
  fs.readFile(file,(err,data)=>{if(err){res.writeHead(404);res.end();return;}
    res.setHeader('Content-Type',({'.html':'text/html; charset=utf-8','.css':'text/css; charset=utf-8','.js':'application/javascript; charset=utf-8','.svg':'image/svg+xml'})[path.extname(file)]||'text/plain');res.end(data);});
}).listen(8765,'127.0.0.1',()=>console.log('Wordleaf preview: http://127.0.0.1:8765'));
