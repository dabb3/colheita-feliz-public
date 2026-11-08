import {readFileSync} from 'node:fs';

// Match PublishedNotes' contract before deploying the same manifest in the image.
try {
  const {version,prNumber,items}=JSON.parse(readFileSync(process.argv[2],'utf8'));
  if(typeof version!=='string'||!/^[a-z0-9-]{1,80}$/.test(version)
      ||!Number.isInteger(prNumber)||prNumber<=0||prNumber>2147483647
      ||!Array.isArray(items)||items.length===0||items.length>30
      ||items.some(item=>typeof item!=='string'||item.trim()===''||item.length>600))
    throw new Error('Invalid manifest');
  process.stdout.write(`--env\nPUBLISHED_UPDATE_NOTES_VERSION=${version}\n--env\nPUBLISHED_UPDATE_NOTES_PR=${prNumber}\n`);
} catch {
  console.error('Invalid update notes manifest; deployment stopped before changing the API.');
  process.exitCode=1;
}
