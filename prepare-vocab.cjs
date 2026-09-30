// Converts the MIT-licensed ECDICT CSV into a compact offline CET-6 asset.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const root = __dirname;
const csv = fs.readFileSync(path.join(root,'tools/ecdict.csv'),'utf8');
let row=[],field='',quoted=false,headers=null;
const words=[],seen=new Set();
function consume(){
  row.push(field);field='';
  if(!headers){headers=row;row=[];return;}
  const r=Object.fromEntries(headers.map((h,i)=>[h,row[i]||'']));row=[];
  if(!r.tag.split(' ').includes('cet6') || !r.translation || !/^[a-z][a-z '-]*$/i.test(r.word) || seen.has(r.word.toLowerCase()))return;
  seen.add(r.word.toLowerCase());
  words.push({w:r.word,p:r.phonetic,t:r.translation.replace(/\\n/g,'\n'),d:r.definition.replace(/\\n/g,'\n'),rank:Number(r.bnc)||Number(r.frq)||999999,f:Number(r.bnc)||Number(r.frq)||0,basis:Number(r.bnc)?'BNC':Number(r.frq)?'FRQ':'none'});
}
for(let i=0;i<csv.length;i++){
  const c=csv[i];
  if(c==='"'){if(quoted&&csv[i+1]==='"'){field+='"';i++;}else quoted=!quoted;}
  else if(c===','&&!quoted){row.push(field);field='';}
  else if(c==='\n'&&!quoted)consume();
  else if(c!=='\r')field+=c;
}
if(field||row.length)consume();
words.sort((a,b)=>a.rank-b.rank||a.w.localeCompare(b.w));words.forEach((w,i)=>{delete w.rank;w.order=i+1;w.tier=i<1500?'common':i<3500?'intermediate':'extended';});
if(words.length<1000)throw Error('Unexpectedly small CET-6 dataset');
const license=fs.readFileSync(path.join(root,'tools/ECDICT-LICENSE'),'utf8');
fs.writeFileSync(path.join(root,'app/assets/words.js'),'window.WORDLEAF_WORDS='+JSON.stringify(words)+';\nwindow.WORDLEAF_LICENSE='+JSON.stringify(license)+';\n');
fs.copyFileSync(path.join(root,'tools/ECDICT-LICENSE'),path.join(root,'app/assets/ECDICT-LICENSE.txt'));
const audit={source:'https://github.com/skywind3000/ECDICT',csvURL:'https://api.github.com/repos/skywind3000/ECDICT/contents/ecdict.csv',sourceSHA256:crypto.createHash('sha256').update(fs.readFileSync(path.join(root,'tools/ecdict.csv'))).digest('hex'),criteria:'cet6 tag; nonempty Chinese definition; English words/phrases; case-insensitive deduplication',count:words.length,withPhonetic:words.filter(w=>w.p).length,withEnglishDefinition:words.filter(w=>w.d).length,ranking:'BNC rank if present; FRQ rank otherwise; missing ranks last. General corpus reference, not CET-6 exam frequency.',tiers:{common:1500,intermediate:2000,extended:words.length-3500},tierPolicy:'Application-selected cohorts by reference order; not official proficiency or exam-frequency thresholds.',preparedDate:'2026-09-30'};
fs.writeFileSync(path.join(root,'vocabulary-source.json'),JSON.stringify(audit,null,2));
console.log(JSON.stringify({...audit,firstWords:words.slice(0,12).map(w=>w.w)},null,2));
