package com.efea.voiceassistant;

import android.content.Context;
import android.util.Log;

import com.google.android.play.core.assetpacks.AssetPackLocation;
import com.google.android.play.core.assetpacks.AssetPackManager;
import com.google.android.play.core.assetpacks.AssetPackManagerFactory;
import com.google.android.play.core.assetpacks.model.AssetPackStatus;
import com.google.mediapipe.tasks.genai.llminference.LlmInference;
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;

public class ModelManager {
    private static final String TAG = "GemmaManager";
    private static final String ASSET_PACK_NAME = "model_assets";
    private static final String MODEL_FILE_NAME = "gemma3-1b-it-int4.task";
    private float temp = 0.2f;

    private LlmInference llmInference;
    private final Context context;
    private final AssetPackManager assetPackManager;

    public ModelManager(Context context) {
        this.context = context.getApplicationContext();
        AssetPackManager manager = null;
        try {
            manager = AssetPackManagerFactory.getInstance(this.context);
        } catch (Exception | Error e) {
            Log.w(TAG, "AssetPackManager not available");
        }
        this.assetPackManager = manager;
    }

    public void initModel(OnLoadedCallback callback) {
        new Thread(() -> {
            try {
                try {
                    InputStream is = context.getAssets().open(MODEL_FILE_NAME);
                    is.close();
                    Log.d(TAG, "Model found in internal assets.");
                    loadFromInternalAssets(callback);
                    return;
                } catch (Exception e) {
                    Log.d(TAG, "Model not in internal assets, checking Asset Pack...");
                }

                if (assetPackManager == null) {
                    callback.onError("AssetPackManager not available.");
                    return;
                }

                AssetPackLocation location = assetPackManager.getPackLocation(ASSET_PACK_NAME);
                if (location == null) {
                    Log.d(TAG, "Asset pack not found, requesting download...");
                    startDownload(callback);
                    return;
                }

                loadModelFromPath(new File(location.assetsPath(), MODEL_FILE_NAME).getAbsolutePath(), callback);
            } catch (Exception e) {
                Log.e(TAG, "Failed to init model", e);
                callback.onError("Init failed: " + e.getMessage());
            }
        }).start();
    }

    private void loadFromInternalAssets(OnLoadedCallback callback) {
        try {
            File tempFile = new File(context.getCacheDir(), MODEL_FILE_NAME);
            if (!tempFile.exists()) {
                try (InputStream in = context.getAssets().open(MODEL_FILE_NAME);
                     OutputStream out = new FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                }
            }
            loadModelFromPath(tempFile.getAbsolutePath(), callback);
        } catch (Exception e) {
            callback.onError("Failed to copy asset: " + e.getMessage());
        }
    }

    private void startDownload(OnLoadedCallback callback) {
        if (assetPackManager == null) return;

        assetPackManager.registerListener(state -> {
            if (state.name().equals(ASSET_PACK_NAME)) {
                switch (state.status()) {
                    case AssetPackStatus.COMPLETED:
                        AssetPackLocation location = assetPackManager.getPackLocation(ASSET_PACK_NAME);
                        if (location != null) {
                            loadModelFromPath(new File(location.assetsPath(), MODEL_FILE_NAME).getAbsolutePath(), callback);
                        } else {
                            callback.onError("Download finished but location not found.");
                        }
                        break;
                    case AssetPackStatus.DOWNLOADING:
                        long total = state.totalBytesToDownload();
                        long progress = total > 0 ? (state.bytesDownloaded() * 100) / total : 0;
                        callback.onError("Downloading... " + progress + "%");
                        break;
                    case AssetPackStatus.FAILED:
                        callback.onError("Download failed: " + state.errorCode());
                        break;
                }
            }
        });

        assetPackManager.fetch(Collections.singletonList(ASSET_PACK_NAME));
    }

    private void loadModelFromPath(String absolutePath, OnLoadedCallback callback) {
        try {
            LlmInference.LlmInferenceOptions options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(absolutePath)
                    .setMaxTokens(256)
                    .build();

            llmInference = LlmInference.createFromOptions(context, options);
            callback.onSuccess();
        } catch (Exception e) {
            callback.onError("Model load error: " + e.getMessage());
        }
    }

    public void ask(String prompt, OnResultCallback callback) {
        if (llmInference == null) {
            callback.onError("Model not initialized");
            return;
        }

        new Thread(() -> {
            try {
                LlmInferenceSession.LlmInferenceSessionOptions sessionOptions =
                        LlmInferenceSession.LlmInferenceSessionOptions.builder()
                                .setTemperature(temp)
                                .setTopK(40)
                                .build();

                try (LlmInferenceSession session = LlmInferenceSession.createFromOptions(llmInference, sessionOptions)) {
                    session.addQueryChunk(prompt);
                    String result = session.generateResponse();
                    callback.onResult(result);
                }
            } catch (Exception e) {
                callback.onError(e.getMessage());
            }
        }).start();
    }

    public interface OnLoadedCallback {
        void onSuccess();
        void onError(String error);
    }

    public interface OnResultCallback {
        void onResult(String text);
        void onError(String error);
    }

    public void setTemp(float value) { temp = value; }
}
