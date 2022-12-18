package land.fx.fula;

import android.util.Log;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.module.annotations.ReactModule;

import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.Contract;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import javax.crypto.SecretKey;

import org.json.JSONObject;
import org.json.JSONArray;

import fulamobile.Config;
import fulamobile.Fulamobile;

import land.fx.wnfslib.Fs;

@ReactModule(name = FulaModule.NAME)
public class FulaModule extends ReactContextBaseJavaModule {


  @Override
  public void initialize() {
    System.loadLibrary("wnfslib");
    System.loadLibrary("gojni");
  }


  public static final String NAME = "FulaModule";
  fulamobile.Client fula;
  Client client;
  String appDir;
  String fulaStorePath;
  String privateForest;
  land.fx.wnfslib.Config rootConfig;
  SharedPreferenceHelper sharedPref;
  static String PRIVATE_KEY_STORE_ID = "PRIVATE_KEY";

  public static class Client implements land.fx.wnfslib.Datastore {

    private final fulamobile.Client internalClient;

    Client(fulamobile.Client clientInput) {
      this.internalClient = clientInput;
    }

    @NonNull
    @Override
    public byte[] get(@NonNull byte[] cid) {
      try {
        Log.d("ReactNative", Arrays.toString(cid));
        return this.internalClient.get(cid);
      } catch (Exception e) {
        e.printStackTrace();
      }
      Log.d("ReactNative","Error get");
      return cid;
    }

    @NonNull
    @Override
    public byte[] put(@NonNull byte[] data, long codec) {
      try {
        //Log.d("ReactNative", "data="+ Arrays.toString(data) +" ;codec="+codec);
        return this.internalClient.put(data, codec);
      } catch (Exception e) {
        Log.d("ReactNative", "put Error="+e.getMessage());
        e.printStackTrace();
      }
      Log.d("ReactNative","Error put");
      return data;
    }
  }

  public FulaModule(ReactApplicationContext reactContext) {
    super(reactContext);
    appDir = reactContext.getFilesDir().toString();
    fulaStorePath = appDir + "/fula";
    File storeDir = new File(fulaStorePath);
    sharedPref = SharedPreferenceHelper.getInstance(reactContext.getApplicationContext());
    boolean success = true;
    if (!storeDir.exists()) {
      success = storeDir.mkdirs();
    }
    if (success) {
      Log.d(NAME, "Fula store folder created");
    } else {
      Log.d(NAME, "Unable to create fula store folder!");
    }
  }

  @Override
  @NonNull
  public java.lang.String getName() {
    return NAME;
  }


  private byte[] toByte(@NonNull String input) {
    return input.getBytes(StandardCharsets.UTF_8);
  }

  @NonNull
  @Contract("_ -> new")
  private String toString(byte[] input) {
    return new String(input, StandardCharsets.UTF_8);
  }

  @NonNull
  private static int[] stringArrToIntArr(@NonNull String[] s) {
    int[] result = new int[s.length];
    for (int i = 0; i < s.length; i++) {
      result[i] = Integer.parseInt(s[i]);
    }
    return result;
  }

  @NonNull
  @Contract(pure = true)
  private static byte[] convertIntToByte(@NonNull int[] input) {
    byte[] result = new byte[input.length];
    for (int i = 0; i < input.length; i++) {
      byte b = (byte) input[i];
      result[i] = b;
    }
    return result;
  }

  @NonNull
  private static byte[] convertStringToByte(@NonNull String data) {
    String[] keyInt_S = data.split(",");
    int[] keyInt = stringArrToIntArr(keyInt_S);

    return convertIntToByte(keyInt);
  }

  @ReactMethod
  public void init(String identityString, String storePath, String bloxAddr, String exchange, String rootConfig, Promise promise) {
    Log.d("ReactNative", "init started");
    ThreadUtils.runOnExecutor(() -> {
      try {
        WritableMap resultData = new WritableNativeMap();
        Log.d("ReactNative", "init storePath= " + storePath);
        byte[] identity = toByte(identityString);
        Log.d("ReactNative", "init identity= " + identityString);
        String[] obj = initInternal(identity, storePath, bloxAddr, exchange, rootConfig);
        Log.d("ReactNative", "init object created: [ " + obj[0] + ", " + obj[1] + ", " + obj[2] + " ]");
        resultData.putString("peerId", obj[0]);
        resultData.putString("rootCid", obj[1]);
        resultData.putString("private_ref", obj[2]);
        promise.resolve(resultData);
      } catch (Exception e) {
        Log.d("ReactNative", "init failed with Error: " + e.getMessage());
        promise.reject("Error", e.getMessage());
      }
    });
  }

  @ReactMethod
  public void logout(String identityString, String storePath, Promise promise) {
    Log.d("ReactNative", "logout started");
    ThreadUtils.runOnExecutor(() -> {
      try {
        byte[] identity = toByte(identityString);
        boolean obj = logoutInternal(identity, storePath);
        Log.d("ReactNative", "logout completed");
        promise.resolve(obj);
      } catch (Exception e) {
        Log.d("ReactNative", "logout failed with Error: " + e.getMessage());
        promise.reject("Error", e.getMessage());
      }
    });
  }

  @NonNull
  private byte[] createPeerIdentity(byte[] privateKey) throws Exception {
    try {
      // 1: First: create public key from provided private key
      // 2: Should read the local keychain store (if it is key-value, key is public key above,
      // 3: if found, decrypt using the private key
      // 4: If not found or decryption not successful, generate an identity
      // 5: then encrypt and store in keychain

      String encryptedKey = sharedPref.getValue(PRIVATE_KEY_STORE_ID);
      SecretKey secretKey = Cryptography.generateKey(privateKey);
      if (encryptedKey == null) {
        byte[] autoGeneratedIdentity = Fulamobile.generateEd25519Key();
        encryptedKey = Cryptography.encryptMsg(StaticHelper.bytesToBase64(autoGeneratedIdentity), secretKey);
        sharedPref.add(PRIVATE_KEY_STORE_ID, encryptedKey);
      }
      return StaticHelper.base64ToBytes(Cryptography.decryptMsg(encryptedKey, secretKey));

    } catch (Exception e) {
      Log.d("ReactNative", "createPeerIdentity failed with Error: " + e.getMessage());
      throw (e);
    }
  }

  private void loadForestInternal(String privateRef, String cid) throws Exception {
    try {
      this.privateForest = Fs.createPrivateForest(this.client);
    } catch (Exception e) {
      Log.d("ReactNative", "loadForestInternal failed with Error: " + e.getMessage());
      throw (e);
    }
  }

  private void createNewRootConfig(FulaModule.Client iClient, SecretKey secretKey, String identity_encrypted, byte[] identity) throws Exception {
    this.privateForest = Fs.createPrivateForest(iClient);
    Log.d("ReactNative", "privateForest is created: " + this.privateForest);
    this.rootConfig = Fs.createRootDir(iClient, this.privateForest, identity);
    String cid_encrypted = Cryptography.encryptMsg(this.rootConfig.getCid(), secretKey);
    String private_ref_encrypted = Cryptography.encryptMsg(this.rootConfig.getPrivate_ref(), secretKey);
    sharedPref.add("cid_encrypted_"+ identity_encrypted, cid_encrypted);
    sharedPref.add("private_ref_encrypted_"+ identity_encrypted, private_ref_encrypted);
  }

  private String getPrivateRef(FulaModule.Client iClient, byte[] wnfsKey, String rootCid) throws Exception {
    String privateRef = Fs.getPrivateRef(iClient, wnfsKey, rootCid);
    return privateRef;
  }

  @NonNull
  private boolean logoutInternal(byte[] identity, String storePath) throws Exception {
    try {
      SecretKey secretKey = Cryptography.generateKey(identity);
      String identity_encrypted =Cryptography.encryptMsg(Arrays.toString(identity), secretKey);
      sharedPref.remove("cid_encrypted_"+ identity_encrypted);
      sharedPref.remove("private_ref_encrypted_"+identity_encrypted);

      //TODO: Should also remove peerid @Mahdi

      sharedPref.remove("cid_encrypted_"+ identity_encrypted);
      sharedPref.remove("private_ref_encrypted_"+ identity_encrypted);

      this.rootConfig = null;

      if (storePath == null || storePath.trim().isEmpty()) {
        storePath = this.fulaStorePath;
      }

      File file = new File(storePath);
      FileUtils.deleteDirectory(file);
      return true;

    } catch (Exception e) {
      Log.d("ReactNative", "logout internal failed with Error: " + e.getMessage());
      throw (e);
    }
  }

  @NonNull
  private String[] initInternal(byte[] identity, String storePath, String bloxAddr, String exchange, String rootCid) throws Exception {
    try {
      Config config_ext = new Config();
      if (storePath == null || storePath.trim().isEmpty()) {
        config_ext.setStorePath(this.fulaStorePath);
      } else {
        config_ext.setStorePath(storePath);
      }
      Log.d("ReactNative", "storePath is set: " + config_ext.getStorePath());

      byte[] peerIdentity = createPeerIdentity(identity);
      config_ext.setIdentity(peerIdentity);
      Log.d("ReactNative", "peerIdentity is set: " + toString(config_ext.getIdentity()));
      config_ext.setBloxAddr(bloxAddr);
      Log.d("ReactNative", "bloxAddr is set: " + config_ext.getBloxAddr());
      config_ext.setExchange(exchange);
      this.fula = Fulamobile.newClient(config_ext);
      this.client = new Client(this.fula);
      Log.d("ReactNative", "fula initialized: " + this.fula.id());

      if (this.rootConfig == null) {

        //Load from keystore
        SecretKey secretKey = Cryptography.generateKey(identity);
        String identity_encrypted =Cryptography.encryptMsg(Arrays.toString(identity), secretKey);
        String cid = sharedPref.getValue("cid_encrypted_"+ identity_encrypted);
        String private_ref = sharedPref.getValue("private_ref_encrypted_"+identity_encrypted);

        if((cid != null && cid.isEmpty()) || (rootCid !=null && rootCid.isEmpty()) ){
          if(rootCid !=null && rootCid.isEmpty()){
            cid = rootCid;
          }
          if(private_ref == null || private_ref.isEmpty()){
            private_ref = getPrivateRef(this.client, identity, cid);
          }
          this.rootConfig = new land.fx.wnfslib.Config(cid, private_ref);
          String cid_encrypted = Cryptography.encryptMsg(this.rootConfig.getCid(), secretKey);
          String private_ref_encrypted = Cryptography.encryptMsg(this.rootConfig.getPrivate_ref(), secretKey);
          sharedPref.add("cid_encrypted_"+ identity_encrypted, cid_encrypted);
          sharedPref.add("private_ref_encrypted_"+ identity_encrypted, private_ref_encrypted);
        } else if(cid != null && !cid.isEmpty() && private_ref != null && !private_ref.isEmpty()) {
          String cid_decrypted = Cryptography.decryptMsg(cid, secretKey);
          String private_ref_decrypted = Cryptography.decryptMsg(private_ref, secretKey);
          if(cid_decrypted != null && !cid_decrypted.isEmpty() && private_ref_decrypted!=null && !private_ref_decrypted.isEmpty()) {
            this.rootConfig = new land.fx.wnfslib.Config(cid_decrypted, private_ref_decrypted);
          } else{
            createNewRootConfig(this.client, secretKey, identity_encrypted, identity);
          }
        } else{
          //Create new root and store cid and private_ref
          createNewRootConfig(this.client, secretKey, identity_encrypted, identity);
        }


        Log.d("ReactNative", "creating rootConfig");

        /*
        byte[] testbyte = convertStringToByte("-104,40,24,-93,24,100,24,114,24,111,24,111,24,116,24,-126,24,-126,0,0,24,-128,24,103,24,118,24,101,24,114,24,115,24,105,24,111,24,110,24,101,24,48,24,46,24,49,24,46,24,48,24,105,24,115,24,116,24,114,24,117,24,99,24,116,24,117,24,114,24,101,24,100,24,104,24,97,24,109,24,116");
        long testcodec = 85;
        byte[] testputcid = this.client.put(testbyte, testcodec);
        Log.d("ReactNative", "client.put test done"+ Arrays.toString(testputcid));
        byte[] testfetchedcid = convertStringToByte("1,113,18,32,-6,-63,-128,79,-102,-89,57,77,-8,67,-98,8,-81,40,-87,123,122,29,-52,-124,-60,-53,100,105,125,123,-5,-99,41,106,-124,-64");
        byte[] testfetchedbytes = this.client.get(testfetchedcid);
        Log.d("ReactNative", "client.get test done"+ Arrays.toString(testfetchedbytes));
        */


        Log.d("ReactNative", "rootConfig is created: " + this.rootConfig.getCid());
      } else {
        Log.d("ReactNative", "rootConfig existed: " + this.rootConfig.getCid());
      }
      String peerId = this.fula.id();
      String[] obj = new String[3];
      obj[0] = peerId;
      obj[1] = this.rootConfig.getCid();
      obj[2] = this.rootConfig.getPrivate_ref();
      Log.d("ReactNative", "initInternal is completed successfully");
      return obj;
    } catch (Exception e) {
      Log.d("ReactNative", "init internal failed with Error: " + e.getMessage());
      throw (e);
    }
  }

  @ReactMethod
  public void mkdir(String path, Promise promise) {
    ThreadUtils.runOnExecutor(() -> {
      Log.d("ReactNative", "mkdir: path = " + path);
      try {
        land.fx.wnfslib.Config config = Fs.mkdir(this.client, this.rootConfig.getCid(), this.rootConfig.getPrivate_ref(), path);
        this.rootConfig = config;
        promise.resolve(config.getCid());
      } catch (Exception e) {
        Log.d("get", e.getMessage());
        promise.reject(e);
      }
    });
  }

  @ReactMethod
  public void writeFile(String fulaTargetFilename, String localFilename, Promise promise) {
    /*
    // reads content of the file form localFilename (should include full absolute path to local file with read permission
    // writes content to the specified location by fulaTargetFilename in Fula filesystem
    // fulaTargetFilename: a string including full path and filename of target file on Fula (e.g. root/pictures/cat.jpg)
    // localFilename: a string containing full path and filename of local file on hte device (e.g /usr/bin/cat.jpg)
    // Returns: new cid of the root after this file is placed in the tree
     */
    ThreadUtils.runOnExecutor(() -> {
      Log.d("ReactNative", "writeFile to : path = " + fulaTargetFilename + ", from: " + localFilename);
      try {
        land.fx.wnfslib.Config config = Fs.writeFileFromPath(this.client, this.rootConfig.getCid(), this.rootConfig.getPrivate_ref(), fulaTargetFilename, localFilename);
        this.rootConfig = config;
        promise.resolve(config.getCid());
      } catch (Exception e) {
        Log.d("get", e.getMessage());
        promise.reject(e);
      }
    });
  }

  @ReactMethod
  public void writeFileContent(String path, String contentString, Promise promise) {
    ThreadUtils.runOnExecutor(() -> {
      Log.d("ReactNative", "writeFile: contentString = " + contentString);
      Log.d("ReactNative", "writeFile: path = " + path);
      try {
        byte[] content = convertStringToByte(contentString);
        land.fx.wnfslib.Config config = Fs.writeFile(this.client, this.rootConfig.getCid(), this.rootConfig.getPrivate_ref(), path, content);
        this.rootConfig = config;
        promise.resolve(config.getCid());
      } catch (Exception e) {
        Log.d("get", e.getMessage());
        promise.reject(e);
      }
    });
  }

  @ReactMethod
  public void ls(String path, Promise promise) {
    ThreadUtils.runOnExecutor(() -> {
      Log.d("ReactNative", "ls: path = " + path);
      try {
        byte[] res = Fs.ls(this.client, this.rootConfig.getCid(), this.rootConfig.getPrivate_ref(), path);
        Log.d("ReactNative", "ls: res = " + res);
        //JSONArray jsonArray = new JSONArray(res);
        String s = new String(res, StandardCharsets.UTF_8);
        promise.resolve(s);
      } catch (Exception e) {
        Log.d("get", e.getMessage());
        promise.reject(e);
      }
    });
  }

  @ReactMethod
  public void rm(String path, Promise promise) {
    ThreadUtils.runOnExecutor(() -> {
      Log.d("ReactNative", "rm: path = " + path);
      try {
        land.fx.wnfslib.Config config = Fs.rm(this.client, this.rootConfig.getCid(), this.rootConfig.getPrivate_ref(), path);
        this.rootConfig = config;
        promise.resolve(config.getCid());
      } catch (Exception e) {
        Log.d("get", e.getMessage());
        promise.reject(e);
      }
    });
  }

  @ReactMethod
  public void readFile(String fulaTargetFilename, String localFilename, Promise promise) {
    /*
    // reads content of the file form localFilename (should include full absolute path to local file with read permission
    // writes content to the specified location by fulaTargetFilename in Fula filesystem
    // fulaTargetFilename: a string including full path and filename of target file on Fula (e.g. root/pictures/cat.jpg)
    // localFilename: a string containing full path and filename of local file on hte device (e.g /usr/bin/cat.jpg)
    // Returns: new cid of the root after this file is placed in the tree
     */
    ThreadUtils.runOnExecutor(() -> {
      Log.d("ReactNative", "readFile: fulaTargetFilename = " + fulaTargetFilename);
      try {
        String path = Fs.readFileToPath(this.client, this.rootConfig.getCid(), this.rootConfig.getPrivate_ref(), fulaTargetFilename, localFilename);
        promise.resolve(path);
      } catch (Exception e) {
        Log.d("get", e.getMessage());
        promise.reject(e);
      }
    });
  }

  @ReactMethod
  public void readFileContent(String path, Promise promise) {
    ThreadUtils.runOnExecutor(() -> {
      Log.d("ReactNative", "readFileContent: path = " + path);
      try {
        byte[] res = Fs.readFile(this.client, this.rootConfig.getCid(), this.rootConfig.getPrivate_ref(), path);
        String resString = toString(res);
        promise.resolve(resString);
      } catch (Exception e) {
        Log.d("get", e.getMessage());
        promise.reject(e);
      }
    });
  }

  @ReactMethod
  public void get(String keyString, Promise promise) {
    ThreadUtils.runOnExecutor(() -> {
      Log.d("ReactNative", "get: keyString = " + keyString);
      try {
        byte[] key = convertStringToByte(keyString);
        byte[] value = getInternal(key);
        String valueString = toString(value);
        promise.resolve(valueString);
      } catch (Exception e) {
        Log.d("get", e.getMessage());
        promise.reject(e);
      }
    });
  }

  @NonNull
  private byte[] getInternal(byte[] key) throws Exception {
    try {
      Log.d("ReactNative", "getInternal: key.toString() = " + toString(key));
      Log.d("ReactNative", "getInternal: key.toString().bytes = " + Arrays.toString(key));
      byte[] value = this.fula.get(key);
      Log.d("ReactNative", "getInternal: value.toString() = " + toString(value));
      return value;
    } catch (Exception e) {
      Log.d("ReactNative", "getInternal: error = " + e.getMessage());
      Log.d("getInternal", e.getMessage());
      throw (e);
    }
  }

  @ReactMethod
  public void has(String keyString, Promise promise) {
    ThreadUtils.runOnExecutor(() -> {
      Log.d("ReactNative", "has: keyString = " + keyString);
      try {
        byte[] key = convertStringToByte(keyString);
        boolean result = hasInternal(key);
        promise.resolve(result);
      } catch (Exception e) {
        Log.d("get", e.getMessage());
        promise.reject(e);
      }
    });
  }

  private boolean hasInternal(byte[] key) throws Exception {
    try {
      boolean res = this.fula.has(key);
      return res;
    } catch (Exception e) {
      Log.d("hasInternal", e.getMessage());
      throw (e);
    }
  }

  private void pullInternal(byte[] key) throws Exception {
    try {
      this.fula.pull(key);
    } catch (Exception e) {
      Log.d("pullInternal", e.getMessage());
      throw (e);
    }
  }

  @ReactMethod
  public void push(Promise promise) {
    ThreadUtils.runOnExecutor(() -> {
      Log.d("ReactNative", "push started");
      try {
        pushInternal(convertStringToByte(this.rootConfig.getCid()));
        promise.resolve(this.rootConfig.getCid());
      } catch (Exception e) {
        Log.d("get", e.getMessage());
        promise.reject(e);
      }
    });
  }

  private void pushInternal(byte[] key) throws Exception {
    try {
      if (this.fula.has(key)) {
        this.fula.push(key);
      } else {
        Log.d("pushInternal", "error: key wasn't found");
        throw new Exception("key wasn't found in local storage");
      }
    } catch (Exception e) {
      Log.d("pushInternal", e.getMessage());
      throw (e);
    }
  }

  @ReactMethod
  public void put(String valueString, String codecString, Promise promise) {
    ThreadUtils.runOnExecutor(() -> {
      Log.d("ReactNative", "put: codecString = " + codecString);
      Log.d("ReactNative", "put: valueString = " + valueString);
      try {
        //byte[] codec = convertStringToByte(CodecString);
        long codec = Long.parseLong(codecString);


        Log.d("ReactNative", "put: codec = " + codec);
        byte[] value = toByte(valueString);

        Log.d("ReactNative", "put: value.toString() = " + toString(value));
        byte[] key = putInternal(value, codec);
        Log.d("ReactNative", "put: key.toString() = " + toString(key));
        promise.resolve(toString(key));
      } catch (Exception e) {
        Log.d("ReactNative", "put: error = " + e.getMessage());
        Log.d("put", e.getMessage());
        promise.reject(e);
      }
    });
  }

  @NonNull
  private byte[] putInternal(byte[] value, long codec) throws Exception {
    try {
      byte[] key = this.fula.put(value, codec);
      return key;
    } catch (Exception e) {
      Log.d("putInternal", e.getMessage());
      throw (e);
    }
  }

  @ReactMethod
  public void shutdown(Promise promise) {
    ThreadUtils.runOnExecutor(() -> {
      try {
        fula.shutdown();
        promise.resolve(true);
      } catch (Exception e) {
        promise.reject(e);
        Log.d("shutdown", e.getMessage());
      }
    });

  }

}
