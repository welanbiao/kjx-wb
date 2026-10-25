/** Browser-only music library. Blobs stay in IndexedDB — never sent to the member API. */

const DB_NAME = "kjx-dream-phone-music";
const DB_VERSION = 1;
const STORE = "tracks";

export type LocalTrackMeta = {
  id: string;
  title: string;
  artist: string;
  fileName: string;
  mime: string;
  size: number;
  addedAt: number;
};

export type LocalTrack = LocalTrackMeta & { blob: Blob };

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains(STORE)) {
        db.createObjectStore(STORE, { keyPath: "id" });
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error ?? new Error("indexedDB open failed"));
  });
}

function reqToPromise<T>(req: IDBRequest<T>): Promise<T> {
  return new Promise((resolve, reject) => {
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error ?? new Error("indexedDB request failed"));
  });
}

function titleFromFile(file: File) {
  return file.name.replace(/\.[^.]+$/, "") || "未命名曲目";
}

export async function listLocalTracks(): Promise<LocalTrackMeta[]> {
  const db = await openDb();
  try {
    const tx = db.transaction(STORE, "readonly");
    const all = await reqToPromise(tx.objectStore(STORE).getAll());
    return (all as LocalTrack[])
      .map(({ id, title, artist, fileName, mime, size, addedAt }) => ({
        id,
        title,
        artist,
        fileName: fileName || title,
        mime,
        size,
        addedAt,
      }))
      .sort((a, b) => b.addedAt - a.addedAt);
  } finally {
    db.close();
  }
}

export async function getLocalTrack(id: string): Promise<LocalTrack | null> {
  const db = await openDb();
  try {
    const tx = db.transaction(STORE, "readonly");
    const row = await reqToPromise(tx.objectStore(STORE).get(id));
    return (row as LocalTrack) || null;
  } finally {
    db.close();
  }
}

export async function addLocalTracks(files: FileList | File[]): Promise<LocalTrackMeta[]> {
  const list = Array.from(files).filter((f) => f.type.startsWith("audio/") || /\.(mp3|m4a|aac|wav|ogg|flac)$/i.test(f.name));
  if (!list.length) {
    return [];
  }
  const db = await openDb();
  const added: LocalTrackMeta[] = [];
  try {
    const tx = db.transaction(STORE, "readwrite");
    const store = tx.objectStore(STORE);
    for (const file of list) {
      const meta: LocalTrackMeta = {
        id: `loc_${crypto.randomUUID().replace(/-/g, "").slice(0, 12)}`,
        title: titleFromFile(file),
        artist: "本地上传",
        fileName: file.name,
        mime: file.type || "audio/mpeg",
        size: file.size,
        addedAt: Date.now(),
      };
      store.put({ ...meta, blob: file });
      added.push(meta);
    }
    await new Promise<void>((resolve, reject) => {
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error ?? new Error("indexedDB write failed"));
    });
  } finally {
    db.close();
  }
  return added;
}

export async function removeLocalTrack(id: string): Promise<void> {
  const db = await openDb();
  try {
    const tx = db.transaction(STORE, "readwrite");
    tx.objectStore(STORE).delete(id);
    await new Promise<void>((resolve, reject) => {
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error ?? new Error("indexedDB delete failed"));
    });
  } finally {
    db.close();
  }
}

export async function clearLocalTracks(): Promise<void> {
  const db = await openDb();
  try {
    const tx = db.transaction(STORE, "readwrite");
    tx.objectStore(STORE).clear();
    await new Promise<void>((resolve, reject) => {
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error ?? new Error("indexedDB clear failed"));
    });
  } finally {
    db.close();
  }
}
