import {build} from 'esbuild';
import {mkdir,readFile} from 'node:fs/promises';
import {createServer} from 'node:http';
import {fileURLToPath} from 'node:url';
import {dirname,resolve,sep,extname} from 'node:path';

const web=resolve(dirname(fileURLToPath(import.meta.url)),'..');
const root=resolve(web,'..'),output=resolve(root,'.tools/web-visual-review');
await mkdir(output,{recursive:true});
await build({entryPoints:[resolve(web,'review/scenario.ts')],outfile:resolve(output,'review.js'),bundle:true,target:'es2022'});
const types={'.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.css':'text/css; charset=utf-8','.png':'image/png','.wav':'audio/wav','.mp3':'audio/mpeg'};
createServer(async(request,response)=>{
 try{
  const pathname=decodeURIComponent(new URL(request.url,'http://localhost').pathname);
  let paths;
  if(pathname==='/')paths=[resolve(web,'review/index.html')];
  else if(pathname==='/review.js')paths=[resolve(output,'review.js')];
  else if(pathname==='/style.css')paths=[resolve(web,'style.css')];
  else if(pathname.startsWith('/assets/')){
   const relative=pathname.slice(1),asset=resolve(root,relative),overlay=resolve(web,'public',relative);
   if(!asset.startsWith(resolve(root,'assets')+sep)||!overlay.startsWith(resolve(web,'public/assets')+sep))throw new Error('Invalid asset path');
   paths=[overlay,asset];
  }else throw new Error('Unknown file');
  let bytes;for(const path of paths){try{bytes=await readFile(path);break;}catch{}}
  if(!bytes)throw new Error('Missing file');
  response.writeHead(200,{'Content-Type':types[extname(paths[0])]??'application/octet-stream','Cache-Control':'no-store'});response.end(bytes);
 }catch{response.writeHead(404);response.end('Not found');}
}).listen(5174,'127.0.0.1',()=>console.log('Revisão isolada: http://127.0.0.1:5174 — sem API, contas ou saves'));
