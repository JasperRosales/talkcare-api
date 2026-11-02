import crypto from 'node:crypto';
import fs from 'node:fs/promises';
import path from 'node:path';
import { config } from '../config.js';
import { ValidationError } from '../errors.js';

const SIGNATURES = {
  'image/png': (b) => b.length > 8 && b.subarray(0, 8).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a])),
  'image/jpeg': (b) => b.length > 3 && b[0] === 0xff && b[1] === 0xd8 && b[2] === 0xff,
  'image/gif': (b) => b.length > 6 && ['GIF87a', 'GIF89a'].includes(b.subarray(0, 6).toString('ascii')),
  'image/webp': (b) => b.length > 12 && b.subarray(0, 4).toString('ascii') === 'RIFF' && b.subarray(8, 12).toString('ascii') === 'WEBP',
};
const EXTENSIONS = { 'image/png': 'png', 'image/jpeg': 'jpg', 'image/gif': 'gif', 'image/webp': 'webp' };

export const IMAGE_TYPES = Object.freeze({
  PHOTO: ['image/png', 'image/jpeg'],
  DOCUMENT: ['image/png', 'image/jpeg', 'image/gif', 'image/webp'],
});

/**
 * Stores uploaded images on the local filesystem and serves them under /uploads.
 * On serverless hosts (Vercel, Netlify) the filesystem is ephemeral: swap this class for an
 * object-storage implementation (S3, R2, Vercel Blob) exposing the same save/remove methods.
 */
export class LocalStorage {
  constructor(directory = config.uploadDir, publicBaseUrl = config.publicBaseUrl) {
    this.directory = directory;
    this.publicBaseUrl = publicBaseUrl;
  }

  /** Decodes a `data:image/...;base64,` URL, checks type/size/magic bytes, and writes it. */
  async saveDataUrl(dataUrl, { prefix, allowedTypes = IMAGE_TYPES.PHOTO, maxBytes = 5 * 1024 * 1024, label = 'image' }) {
    const match = /^data:([a-z]+\/[a-z0-9.+-]+);base64,(.+)$/is.exec(String(dataUrl ?? '').trim());
    const mimeType = match?.[1].toLowerCase().replace('image/jpg', 'image/jpeg');
    if (!match || !allowedTypes.includes(mimeType)) {
      throw new ValidationError(`Please upload the ${label} as ${allowedTypes.map((t) => EXTENSIONS[t].toUpperCase()).join(', ')}.`);
    }
    const bytes = Buffer.from(match[2], 'base64');
    if (bytes.length === 0 || !SIGNATURES[mimeType](bytes)) {
      throw new ValidationError(`The ${label} file is not a valid ${EXTENSIONS[mimeType].toUpperCase()} image.`);
    }
    if (bytes.length > maxBytes) {
      throw new ValidationError(`The ${label} must be smaller than ${Math.round(maxBytes / 1024 / 1024)} MB.`);
    }
    await fs.mkdir(this.directory, { recursive: true });
    const filename = `${prefix}_${crypto.randomUUID()}.${EXTENSIONS[mimeType]}`;
    await fs.writeFile(path.join(this.directory, filename), bytes);
    return { url: `${this.publicBaseUrl}/uploads/${filename}`, mimeType, byteSize: bytes.length };
  }

  /** Best-effort delete of a file previously returned by saveDataUrl. */
  async remove(url) {
    if (!url) return;
    const filename = path.basename(String(url));
    if (!/^[\w-]+\.(png|jpg|gif|webp)$/.test(filename)) return;
    await fs.rm(path.join(this.directory, filename), { force: true }).catch((err) => {
      console.error(`Could not remove upload ${filename}:`, err.message);
    });
  }
}

export const storage = new LocalStorage();
