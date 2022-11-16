import Fula from '../interfaces/fulaNativeModule';

/**
 * Get gets the value corresponding to the given key from the local datastore.
// The key must be a valid ipld.Link.
 * @param key
 * @returns value
 */
export const get = (key: string): Promise<string> => {
  return Fula.get(key);
};

/**
 * Has checks whether the value corresponding to the given key is present in the local datastore.
// The key must be a valid ipld.Link.
 * @param key
 * @returns boolean
 */
export const has = (key: string): Promise<boolean> => {
  return Fula.has(key);
};

/**
 * Pull downloads the data corresponding to the given key from the given addr.
// The key must be a valid ipld.Link, and the addr must be a valid multiaddr that includes peer ID.
// See peer.AddrInfoFromString.
 * @param addr, key
 * @returns null or error
 */
export const pull = (addr: string, key: string): Promise<string> => {
  return Fula.pull(addr, key);
};

/**
 * Push requests the given addr to download the given key from this node.
// The key must be a valid ipld.Link, and the addr must be a valid multiaddr that includes peer ID.
// The value corresponding to the given key must be stored in the local datastore prior to calling
// this function.
 * @param addr, key
 * @returns null or error
 */
export const push = (addr: string, key: string): Promise<string> => {
  return Fula.push(addr, key);
};

/**
 * Put stores the given key value onto the local datastore.
// The key must be a valid ipld.Link and the value must be the valid encoded ipld.Node corresponding
// to the given key.
 * @param key, value
 * @returns null or string
 */
export const put = (key: string, value: string): Promise<string> => {
  return Fula.put(key, value);
};

/**
 * Shutdown closes all resources used by Client.
// After calling this function Client must be discarded.
 * @param
 * @returns
 */
export const shutdown = (): Promise<void> => {
  return Fula.shutdown();
};
