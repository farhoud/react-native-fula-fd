import { NativeModules, Platform } from 'react-native';
//import type { Config } from '../interfaces';

interface FulaNativeModule {
  init: (
    identity: string | null, //Private key of did identity
    storePath: string | null //You can leave empty
  ) => Promise<[string]>;
  get: (key: string) => Promise<string>;
  has: (key: Uint8Array) => Promise<boolean>;
  pull: (addr: string, key: Uint8Array) => Promise<string>;
  push: (addr: string, key: Uint8Array) => Promise<string>;
  mkdir: (path: string) => Promise<string>;
  writeFile: (path: string, content: string) => Promise<string>;
  ls: (path: string) => Promise<string>;
  readFile: (path: string) => Promise<string>;

  shutdown: () => Promise<void>;
}

const LINKING_ERROR =
  `The package 'react-native-fula' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ ios: "- You have run 'pod install'\n", default: '' }) +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo managed workflow\n';

const Fula = NativeModules.FulaModule
  ? NativeModules.FulaModule
  : new Proxy(
      {},
      {
        get() {
          throw new Error(LINKING_ERROR);
        },
      }
    );

export default Fula as FulaNativeModule;
