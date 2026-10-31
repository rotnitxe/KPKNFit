const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),os=require('node:os'),path=require('node:path'),zlib=require('node:zlib');
const {spawnSync}=require('node:child_process');
const root=fs.mkdtempSync(path.join(os.tmpdir(),'pulido-gate-tests-'));
function crc(buffer){let c=0xffffffff;for(const b of buffer){c^=b;for(let k=0;k<8;k++)c=c&1?0xedb88320^(c>>>1):c>>>1;}return (c^0xffffffff)>>>0;}
function chunk(type,data){const t=Buffer.from(type),len=Buffer.alloc(4),sum=Buffer.alloc(4);len.writeUInt32BE(data.length);sum.writeUInt32BE(crc(Buffer.concat([t,data])));return Buffer.concat([len,t,data,sum]);}
function fixture(name,w,h,stripe=0){const head=Buffer.alloc(13);head.writeUInt32BE(w);head.writeUInt32BE(h,4);head[8]=8;head[9]=2;
 const pixels=Buffer.alloc((w*3+1)*h);for(let y=0;y<h;y++)for(let x=0;x<stripe;x++)pixels.fill(255,y*(w*3+1)+1+x*3,y*(w*3+1)+4+x*3);
 const file=path.join(root,name+'.png');fs.writeFileSync(file,Buffer.concat([Buffer.from('89504e470d0a1a0a','hex'),chunk('IHDR',head),chunk('IDAT',zlib.deflateSync(pixels)),chunk('IEND',Buffer.alloc(0))]));return file;}
const a=fixture('reference',360,880),bad=fixture('ten-percent',360,880,36),wrong=fixture('wrong-height',360,740);
function gate(actual,id){const json=path.join(root,id+'.json');const r=spawnSync(process.execPath,[path.join(__dirname,'visual-gate.cjs'),a,actual,'--json='+json,'--out='+path.join(root,id+'.png')],{encoding:'utf8',timeout:90000});assert.ok(fs.existsSync(json),r.stderr);return {code:r.status,...JSON.parse(fs.readFileSync(json))};}
test('identical capture passes at 100 percent',()=>{const r=gate(a,'same');assert.equal(r.code,0);assert.equal(r.passed,true);assert.equal(r.parityPercent,100);assert.equal(r.meanError,0);});
test('ten-percent regression fails without averaging it away',()=>{const r=gate(bad,'regression');assert.equal(r.code,2);assert.equal(r.passed,false);assert.ok(Math.abs(r.parityPercent-90)<.001);assert.ok(Math.abs(r.meanError-25.5)<.001);});
test('wrong viewport fails instead of being stretched to fit',()=>{const r=gate(wrong,'viewport');assert.equal(r.code,2);assert.equal(r.passed,false);assert.match(r.error,/Incompatible viewport/);});
process.on('exit',()=>fs.rmSync(root,{recursive:true,force:true}));
