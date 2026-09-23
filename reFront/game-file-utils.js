function gameIsZipFile(file) {
  return file.name.toLowerCase().endsWith(".zip") || file.type === "application/zip" || file.type === "application/x-zip-compressed";
}

function gameIsJpeg(bytes) {
  return bytes.length >= 3 && bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff;
}

function gameIsPng(bytes) {
  return bytes.length >= 8 && [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a].every((value, index) => bytes[index] === value);
}

function gameReadZipString(bytes, start, length) {
  return new TextDecoder("utf-8", { fatal: false }).decode(bytes.slice(start, start + length));
}

function gameIsZipDirectory(name, versionMadeBy, externalAttributes) {
  if (name.endsWith("/")) return true;
  const hostSystem = versionMadeBy >>> 8;
  if (hostSystem === 0) return (externalAttributes & 0x10) !== 0;
  return ((externalAttributes >>> 16) & 0xf000) === 0x4000;
}

async function gameInflateRaw(bytes) {
  if (typeof DecompressionStream !== "function") {
    throw new Error("このブラウザはZIP解凍に対応していません。最新のブラウザで再実行してください。");
  }
  const stream = new Blob([bytes]).stream().pipeThrough(new DecompressionStream("deflate-raw"));
  return new Uint8Array(await new Response(stream).arrayBuffer());
}

async function gameUnzipReceiptImages(zipFile) {
  const archive = new Uint8Array(await zipFile.arrayBuffer());
  const minimumEndRecord = 22;
  const searchStart = Math.max(0, archive.length - 0xffff - minimumEndRecord);
  let endOffset = -1;
  for (let offset = archive.length - minimumEndRecord; offset >= searchStart; offset--) {
    if (new DataView(archive.buffer, archive.byteOffset, archive.byteLength).getUint32(offset, true) === 0x06054b50) {
      endOffset = offset;
      break;
    }
  }
  if (endOffset < 0) throw new Error("ZIPファイルを読み込めません。ZIPが壊れている可能性があります。");

  const view = new DataView(archive.buffer, archive.byteOffset, archive.byteLength);
  const entryCount = view.getUint16(endOffset + 10, true);
  const centralSize = view.getUint32(endOffset + 12, true);
  const centralOffset = view.getUint32(endOffset + 16, true);
  if (centralOffset + centralSize > archive.length) throw new Error("ZIPファイルの構造が不正です。");

  const files = [];
  let cursor = centralOffset;
  for (let index = 0; index < entryCount; index++) {
    if (cursor + 46 > archive.length || view.getUint32(cursor, true) !== 0x02014b50) {
      throw new Error("ZIPファイルの中身を読み込めません。");
    }
    const flags = view.getUint16(cursor + 8, true);
    const method = view.getUint16(cursor + 10, true);
    const compressedSize = view.getUint32(cursor + 20, true);
    const uncompressedSize = view.getUint32(cursor + 24, true);
    const nameLength = view.getUint16(cursor + 28, true);
    const extraLength = view.getUint16(cursor + 30, true);
    const commentLength = view.getUint16(cursor + 32, true);
    const localOffset = view.getUint32(cursor + 42, true);
    const versionMadeBy = view.getUint16(cursor + 6, true);
    const externalAttributes = view.getUint32(cursor + 38, true);
    const name = gameReadZipString(archive, cursor + 46, nameLength);
    cursor += 46 + nameLength + extraLength + commentLength;

    if (gameIsZipDirectory(name, versionMadeBy, externalAttributes)) continue;
    if ((flags & 1) !== 0) throw new Error("ZIP内のファイル「" + name + "」は暗号化されています。処理を中断しました。");
    if (!/\.(jpe?g|png)$/i.test(name)) {
      throw new Error("ZIP内にJPEG/PNG以外のファイル「" + name + "」があるため、処理を中断しました。");
    }
    if (localOffset + 30 > archive.length || view.getUint32(localOffset, true) !== 0x04034b50) {
      throw new Error("ZIP内のファイル「" + name + "」を読み込めません。");
    }

    const localNameLength = view.getUint16(localOffset + 26, true);
    const localExtraLength = view.getUint16(localOffset + 28, true);
    const dataStart = localOffset + 30 + localNameLength + localExtraLength;
    const dataEnd = dataStart + compressedSize;
    if (dataEnd > archive.length) throw new Error("ZIP内のファイル「" + name + "」が壊れています。");

    const compressed = archive.slice(dataStart, dataEnd);
    let bytes;
    if (method === 0) bytes = compressed;
    else if (method === 8) bytes = await gameInflateRaw(compressed);
    else throw new Error("ZIP内のファイル「" + name + "」は未対応の圧縮方式です。処理を中断しました。");

    if (bytes.length !== uncompressedSize || (!gameIsJpeg(bytes) && !gameIsPng(bytes))) {
      throw new Error("ZIP内のファイル「" + name + "」がJPEG/PNG画像ではないため、処理を中断しました。");
    }
    const type = gameIsPng(bytes) ? "image/png" : "image/jpeg";
    files.push(new File([bytes], name.split("/").pop(), { type }));
  }

  if (!files.length) throw new Error("ZIPファイルにレシート画像がありません。");
  return files;
}

async function gameExpandSelectedFile(file) {
  if (gameIsZipFile(file)) return gameUnzipReceiptImages(file);
  const bytes = new Uint8Array(await file.slice(0, 8).arrayBuffer());
  if (!gameIsJpeg(bytes) && !gameIsPng(bytes)) {
    throw new Error("JPEGまたはPNG画像、またはZIPファイルを選択してください。");
  }
  return [file];
}

window.receiptGameFileUtils = {
  expandSelectedFile: gameExpandSelectedFile
};
